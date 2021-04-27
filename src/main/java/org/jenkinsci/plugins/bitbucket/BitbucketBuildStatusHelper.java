/*
 * The MIT License
 *
 * Copyright 2015 Flagbit GmbH & Co. KG.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.bitbucket;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hudson.model.*;
import hudson.plugins.git.GitSCM;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.SCM;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.util.LogTaskListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.jgit.transport.URIish;

import org.jenkinsci.plugins.bitbucket.api.BitbucketApi;
import org.jenkinsci.plugins.bitbucket.api.BitbucketApiService;
import org.jenkinsci.plugins.bitbucket.model.BitbucketBuildStatus;
import org.jenkinsci.plugins.bitbucket.model.BitbucketBuildStatusResource;
import org.jenkinsci.plugins.bitbucket.model.BitbucketBuildStatusSerializer;
import org.jenkinsci.plugins.bitbucket.scm.GitScmAdapter;
import org.jenkinsci.plugins.bitbucket.scm.MercurialScmAdapter;
import org.jenkinsci.plugins.bitbucket.scm.ScmAdapter;
import org.jenkinsci.plugins.bitbucket.validator.BitbucketHostValidator;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.scribe.model.*;

class BitbucketBuildStatusHelper {
    private static final Logger logger = Logger.getLogger(BitbucketBuildStatusHelper.class.getName());
    private static final BitbucketHostValidator hostValidator = new BitbucketHostValidator();

    private static List<BitbucketBuildStatusResource> createBuildStatusResources(final SCM scm,
                                                                                 final Run<?, ?> build) throws Exception {
        List<BitbucketBuildStatusResource> buildStatusResources = new ArrayList<BitbucketBuildStatusResource>();

        if (scm == null) {
            throw new Exception("Bitbucket build notifier only works with SCM");
        }

        ScmAdapter scmAdapter;
        if (scm instanceof GitSCM) {
            scmAdapter = new GitScmAdapter((GitSCM) scm, build);
        } else if (scm instanceof MercurialSCM) {
            scmAdapter = new MercurialScmAdapter((MercurialSCM) scm, build);
        } else {
            throw new Exception("Bitbucket build notifier requires a git repo or a mercurial repo as SCM");
        }

        Map<String, URIish> commitRepoMap = scmAdapter.getCommitRepoMap();
        for (Map.Entry<String, URIish> commitRepoPair : commitRepoMap.entrySet()) {

            // if repo is not hosted in bitbucket.org then log it and remove repo from being notified
            URIish repoUri = commitRepoPair.getValue();
            if (!hostValidator.isValid(repoUri.getHost())) {
                logger.log(Level.INFO, hostValidator.renderError());
                continue;
            }

            // expand parameters on repo url
            String repoUrl = build.getEnvironment(new LogTaskListener(logger, Level.INFO)).expand(repoUri.getPath());
            if (repoUrl.endsWith("/")) {
                //fix JENKINS-49902
                repoUrl = repoUrl.substring(0, repoUrl.length() - 1);
            }

            // extract bitbucket user name and repository name from repo URI
            String repoName = repoUrl.substring(
                    repoUrl.lastIndexOf("/") + 1,
                    repoUrl.contains(".git") ? repoUrl.indexOf(".git") : repoUrl.length()
            );

            if (repoName.isEmpty()) {
                logger.log(Level.INFO, "Bitbucket build notifier could not extract the repository name from the repository URL: " + repoUrl);
                continue;
            }

            String userName = repoUrl.substring(0, repoUrl.lastIndexOf("/" + repoName));
            if (userName.contains("/")) {
                userName = userName.substring(userName.indexOf("/") + 1, userName.length());
            }
            if (userName.isEmpty()) {
                logger.log(Level.INFO, "Bitbucket build notifier could not extract the user name from the repository URL: " + repoUrl + " with repository name: " + repoName);
                continue;
            }

            String commitId = commitRepoPair.getKey();
            if (commitId == null) {
                logger.log(Level.INFO, "Commit ID could not be found!");
                continue;
            }

            buildStatusResources.add(new BitbucketBuildStatusResource(userName, repoName, commitId));
        }

        return buildStatusResources;
    }

    public static List<BitbucketBuildStatusResource> createBuildStatusResources(final Run<?, ?> build) throws Exception {
        Job<?, ?> project = build.getParent();
        List<BitbucketBuildStatusResource> buildStatusResources = new ArrayList<BitbucketBuildStatusResource>();

        if (project instanceof WorkflowJob) {
            Collection<? extends SCM> scms = ((WorkflowJob)project).getSCMs();

            for (SCM scm : scms) {
                buildStatusResources.addAll(createBuildStatusResources(scm, build));
            }
        } else if (project instanceof AbstractProject) {
            SCM scm = ((AbstractProject)project).getScm();
            buildStatusResources = createBuildStatusResources(scm, build);
        }

        return buildStatusResources;
    }

    public static String defaultBitbucketBuildKeyFromBuild(Run<?, ?> build) {
        Job<?, ?> project = build.getParent();
        return DigestUtils.md5Hex(project.getFullName() + "#" + build.getNumber());
    }

    public static String uniqueBitbucketBuildKeyFromBuild(Run<?, ?> build) {
        Job<?, ?> project = build.getParent();
        return DigestUtils.md5Hex(project.getFullName());
    }

    public static String defaultBitbucketBuildNameFromBuild(Run<?, ?> build) {
        Job<?, ?> project = build.getParent();
        return project.getFullName() + " #" + build.getNumber();
    }

    public static String uniqueBitbucketBuildNameFromBuild(Run<?, ?> build) {
        Job<?, ?> project = build.getParent();
        return project.getFullName();
    }

    public static String defaultBitbucketBuildDescriptionFromBuild(Run<?, ?> build) {
        AbstractTestResultAction testResult = build.getAction(AbstractTestResultAction.class);
        String description = "";
        if (testResult != null) {
            int passedCount = testResult.getTotalCount() - testResult.getFailCount();
            description = passedCount + " of " + testResult.getTotalCount() + " tests passed";
        }
        return description;
    }

    public static String buildUrlFromBuild(Run<?, ?> build) {
        return DisplayURLProvider.get().getRunURL(build);
    }

    private static BitbucketBuildStatus createBitbucketBuildStatusFromBuild(Run<?, ?> build, boolean overrideLatestBuild) throws Exception {
        String buildKey;
        String buildName;
        String buildState = guessBitbucketBuildState(build.getResult());
        // bitbucket requires the key to be shorter than 40 chars
        if (overrideLatestBuild) {
            buildKey = uniqueBitbucketBuildKeyFromBuild(build);
            buildName = uniqueBitbucketBuildNameFromBuild(build);
        } else {
            buildKey = defaultBitbucketBuildKeyFromBuild(build);
            buildName = defaultBitbucketBuildNameFromBuild(build);
        }
        String buildUrl = buildUrlFromBuild(build);
        String description = defaultBitbucketBuildDescriptionFromBuild(build);

        return new BitbucketBuildStatus(buildState, buildKey, buildUrl, buildName, description);
    }

    private static String guessBitbucketBuildState(final Result result) {

        String state;

        // possible statuses SUCCESS, UNSTABLE, FAILURE, NOT_BUILT, ABORTED
        if (result == null) {
            state = BitbucketBuildStatus.INPROGRESS;
        } else if (Result.SUCCESS == result) {
            state = BitbucketBuildStatus.SUCCESSFUL;
        } else if (Result.UNSTABLE == result || Result.FAILURE == result || Result.ABORTED == result) {
            state = BitbucketBuildStatus.FAILED;
        } else {
            // return empty status for every other result (NOT_BUILT)
            state = null;
        }

        return state;
    }

    public static void notifyBuildStatus(UsernamePasswordCredentials credentials, boolean overrideLatestBuild,
                                         final Run<?, ?> build, final TaskListener listener) throws Exception {
        notifyBuildStatus(credentials, overrideLatestBuild, build, listener, createBitbucketBuildStatusFromBuild(build, overrideLatestBuild), null, null);
    }

    public static void notifyBuildStatus(UsernamePasswordCredentials credentials, boolean overrideLatestBuild,
                                         final Run<?, ?> build, final TaskListener listener,
                                         BitbucketBuildStatus buildStatus, String repoSlug, String commitId) throws Exception {

        List<BitbucketBuildStatusResource> buildStatusResources = createBuildStatusResources(build);

        Run<?, ?> prevBuild = build.getPreviousBuild();
        List<BitbucketBuildStatusResource> prevBuildStatusResources = new ArrayList<BitbucketBuildStatusResource>();
        if (prevBuild != null && prevBuild.getResult() != null && prevBuild.getResult() == Result.ABORTED) {
            prevBuildStatusResources = createBuildStatusResources(prevBuild);
        }

        for (BitbucketBuildStatusResource buildStatusResource : buildStatusResources) {

            // if previous build was manually aborted by the user and revision is the same than the current one
            // then update the bitbucket build status resource with current status and current build number
            for (BitbucketBuildStatusResource prevBuildStatusResource : prevBuildStatusResources) {
                if (prevBuildStatusResource.getCommitId().equals(buildStatusResource.getCommitId())) {
                    BitbucketBuildStatus prevBuildStatus = createBitbucketBuildStatusFromBuild(prevBuild, overrideLatestBuild);
                    buildStatus.setKey(prevBuildStatus.getKey());

                    break;
                }
            }

            if (repoSlug != null && commitId != null) {
                buildStatusResource = new BitbucketBuildStatusResource(buildStatusResource.getOwner(), repoSlug, commitId);
            }

            sendBuildStatusNotification(credentials, build, buildStatusResource, buildStatus, listener);
        }
    }

    public static void sendBuildStatusNotification(final UsernamePasswordCredentials credentials,
                                                   final Run<?, ?> build,
                                                   final BitbucketBuildStatusResource buildStatusResource,
                                                   final BitbucketBuildStatus buildStatus,
                                                   final TaskListener listener) throws Exception {
        if (credentials == null) {
            throw new Exception("Credentials could not be found!");
        }

        OAuthConfig config = new OAuthConfig(credentials.getUsername(), credentials.getPassword().getPlainText());
        BitbucketApiService apiService = (BitbucketApiService) new BitbucketApi().createService(config);

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(BitbucketBuildStatus.class, new BitbucketBuildStatusSerializer());
        gsonBuilder.setPrettyPrinting();
        Gson gson = gsonBuilder.create();

        OAuthRequest request = new OAuthRequest(Verb.POST, buildStatusResource.generateUrl(Verb.POST));
        request.addHeader("Content-type", "application/json");
        request.addPayload(gson.toJson(buildStatus));

        Token token = apiService.getAccessToken(OAuthConstants.EMPTY_TOKEN, null);
        apiService.signRequest(token, request);

        Response response = request.send();

        logger.info("This request was sent: " + request.getBodyContents());
        logger.info("This response was received: " + response.getBody());
        listener.getLogger().println("Sending build status " + buildStatus.getState() +
                " for commit " + buildStatusResource.getCommitId() + " to BitBucket is done!");
    }

    public static StandardUsernamePasswordCredentials getCredentials(String credentialsId, Job<?,?> owner) {
        if (credentialsId != null) {
            for (StandardUsernamePasswordCredentials c : CredentialsProvider.lookupCredentials(
                    StandardUsernamePasswordCredentials.class, owner, null,
                    URIRequirementBuilder.fromUri(BitbucketApi.OAUTH_ENDPOINT).build())) {
                if (c.getId().equals(credentialsId)) {
                    return c;
                }
            }
        }

        return null;
    }
}
