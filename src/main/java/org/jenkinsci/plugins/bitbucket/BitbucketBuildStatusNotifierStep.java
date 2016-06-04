/*
 * The MIT License
 *
 * Copyright 2015 Marco Marche (aka trik).
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
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.XmlFile;
import hudson.model.*;

import hudson.plugins.git.GitSCM;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.SCM;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.bitbucket.api.BitbucketApi;
import org.jenkinsci.plugins.bitbucket.api.BitbucketApiService;
import org.jenkinsci.plugins.bitbucket.model.BitbucketBuildStatus;
import org.jenkinsci.plugins.bitbucket.model.BitbucketBuildStatusResource;
import org.jenkinsci.plugins.bitbucket.model.BitbucketBuildStatusSerializer;
import org.jenkinsci.plugins.bitbucket.validator.BitbucketHostValidator;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.*;

import org.kohsuke.stapler.*;
import org.scribe.model.*;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BitbucketBuildStatusNotifierStep extends AbstractStepImpl {

    private static final Logger logger = Logger.getLogger(BitbucketBuildStatusNotifierStep.class.getName());
    private static final BitbucketHostValidator hostValidator = new BitbucketHostValidator();

    private String credentialsId;
    public String getCredentialsId() {
        if (credentialsId == null) {
            return getDescriptor().getGlobalCredentialsId();
        }
        return this.credentialsId;
    }

    @DataBoundSetter public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    private String buildStatus;
    public String getBuildStatus() { return this.buildStatus; }

    @DataBoundConstructor
    public BitbucketBuildStatusNotifierStep(final String buildStatus) {
        this.credentialsId = credentialsId;
        this.buildStatus = buildStatus;
    }

    private List<BitbucketBuildStatusResource> createBuildStatusResources(final Run<?, ?> build) throws Exception {
        WorkflowJob project = (WorkflowJob)build.getParent();
        Collection<? extends SCM> scms = project.getSCMs();
        List<BitbucketBuildStatusResource> buildStatusResources = new ArrayList<BitbucketBuildStatusResource>();

        for(SCM scm: scms) {
            if (scm == null) {
                throw new Exception("Bitbucket build notifier only works with SCM");
            }

            BitbucketBuildStatusNotifierStep.ScmAdapter scmAdapter;
            if (scm instanceof GitSCM) {
                scmAdapter = new BitbucketBuildStatusNotifierStep.GitScmAdapter((GitSCM) scm, build);
            } else if (scm instanceof MercurialSCM) {
                scmAdapter = new BitbucketBuildStatusNotifierStep.MercurialScmAdapter((MercurialSCM) scm);
            } else if (scm instanceof MultiSCM) {
                scmAdapter = new BitbucketBuildStatusNotifierStep.MultiScmAdapter(build);
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

                // extract bitbucket user name and repository name from repo URI
                String repoName = repoUrl.substring(
                        repoUrl.lastIndexOf("/") + 1,
                        repoUrl.indexOf(".git") > -1 ? repoUrl.indexOf(".git") : repoUrl.length()
                );

                if (repoName.isEmpty()) {
                    logger.log(Level.INFO, "Bitbucket build notifier could not extract the repository name from the repository URL");
                    continue;
                }

                String userName = repoUrl.substring(0, repoUrl.indexOf("/" + repoName));
                if (userName.indexOf("/") != -1) {
                    userName = userName.substring(userName.indexOf("/") + 1, userName.length());
                }
                if (userName.isEmpty()) {
                    logger.log(Level.INFO, "Bitbucket build notifier could not extract the user name from the repository URL");
                    continue;
                }

                String commitId = commitRepoPair.getKey();
                if (commitId == null) {
                    logger.log(Level.INFO, "Commit ID could not be found!");
                    continue;
                }

                buildStatusResources.add(new BitbucketBuildStatusResource(userName, repoName, commitId));
            }
        }

        return buildStatusResources;
    }

    private void notifyBuildStatus(final Run<?, ?> build, final TaskListener listener,
                                   final StepContext context) throws Exception {

        listener.getLogger().println(getDescriptor().getId().replace("Step", ""));
        WorkflowJob project = (WorkflowJob)build.getParent();
        UsernamePasswordCredentials credentials = BitbucketBuildStatusNotifierStep.getCredentials(getCredentialsId(), project);
        List<BitbucketBuildStatusResource> buildStatusResources = this.createBuildStatusResources(build);

        Run<?, ?> prevBuild = build.getPreviousBuild();
        List<BitbucketBuildStatusResource> prevBuildStatusResources = new ArrayList<BitbucketBuildStatusResource>();
        if (prevBuild != null && prevBuild.getResult() != null && prevBuild.getResult() == Result.ABORTED) {
            prevBuildStatusResources = this.createBuildStatusResources(prevBuild);
        }

        for (Iterator<BitbucketBuildStatusResource> i = buildStatusResources.iterator(); i.hasNext(); ) {

            BitbucketBuildStatusResource buildStatusResource = i.next();
            BitbucketBuildStatus buildStatus = this.createBitbucketBuildStatusFromBuild(build);

            // if previous build was manually aborted by the user and revision is the same than the current one
            // then update the bitbucket build status resource with current status and current build number
            for (Iterator<BitbucketBuildStatusResource> j = prevBuildStatusResources.iterator(); j.hasNext(); ) {
                BitbucketBuildStatusResource prevBuildStatusResource = j.next();
                if (prevBuildStatusResource.getCommitId().equals(buildStatusResource.getCommitId())) {
                    BitbucketBuildStatus prevBuildStatus = this.createBitbucketBuildStatusFromBuild(prevBuild);
                    buildStatus.setKey(prevBuildStatus.getKey());
                    break;
                }
            }

            if (credentials == null) {
                context.onFailure(new Exception("Credentials could not be found!"));
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

            Verifier verifier = null;
            Token token = apiService.getAccessToken(OAuthConstants.EMPTY_TOKEN, verifier);
            apiService.signRequest(token, request);

            Response response = request.send();

            logger.info("This request was sent: " + request.getBodyContents());
            logger.info("This response was received: " + response.getBody());
            listener.getLogger().println("Sending build status " + buildStatus.getState() + " for commit " + buildStatusResource.getCommitId() + " to BitBucket is done!");
        }
    }

    private BitbucketBuildStatus createBitbucketBuildStatusFromBuild(Run<?, ?> build) throws Exception {
        WorkflowJob project = (WorkflowJob)build.getParent();
        String buildState = this.getBuildStatus();
        // bitbucket requires the key to be shorter than 40 chars
        String buildKey = DigestUtils.md5Hex(project.getFullDisplayName() + "#" + build.getNumber());
        String buildUrl = project.getAbsoluteUrl() + build.getNumber() + '/';
        String buildName = project.getFullDisplayName() + " #" + build.getNumber();
        AbstractTestResultAction testResult = build.getAction(AbstractTestResultAction.class);
        String description = "";
        if (testResult != null) {
            int passedCount = testResult.getTotalCount() - testResult.getFailCount();
            description = passedCount + " of " + testResult.getTotalCount() + " tests passed";
        }

        return new BitbucketBuildStatus(buildState, buildKey, buildUrl, buildName, description);
    }

    public static StandardUsernamePasswordCredentials getCredentials(String credentialsId, Job<?,?> owner) {
        if (credentialsId != null) {
            for (StandardUsernamePasswordCredentials c : CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, owner, null, URIRequirementBuilder.fromUri(BitbucketApi.OAUTH_ENDPOINT).build())) {
                if (c.getId().equals(credentialsId)) {
                    return c;
                }
            }
        }

        return null;
    }

    private interface ScmAdapter {
        Map getCommitRepoMap() throws Exception;
    }

    private class GitScmAdapter implements BitbucketBuildStatusNotifierStep.ScmAdapter {

        private final GitSCM gitScm;
        private final Run<?, ?> build;

        public GitScmAdapter(GitSCM scm, Run<?, ?> build) {
            this.gitScm = scm;
            this.build = build;
        }

        public Map getCommitRepoMap() throws Exception {
            List<RemoteConfig> repoList = this.gitScm.getRepositories();
            if (repoList.size() != 1) {
                throw new Exception("None or multiple repos");
            }

            HashMap<String, URIish> commitRepoMap = new HashMap();
            BuildData buildData = build.getAction(BuildData.class);
            if (buildData == null || buildData.getLastBuiltRevision() == null) {
                logger.warning("Build data could not be found");
            } else {
                commitRepoMap.put(buildData.getLastBuiltRevision().getSha1String(), repoList.get(0).getURIs().get(0));
            }

            return commitRepoMap;
        }
    }

    private class MercurialScmAdapter implements BitbucketBuildStatusNotifierStep.ScmAdapter {

        private final MercurialSCM hgSCM;

        public MercurialScmAdapter(MercurialSCM scm) {
            this.hgSCM = scm;
        }

        public Map getCommitRepoMap() throws Exception {
            String source = this.hgSCM.getSource();
            if (source == null || source.isEmpty()) {
                throw new Exception("None or multiple repos");
            }

            HashMap<String, URIish> commitRepoMap = new HashMap();
            commitRepoMap.put(this.hgSCM.getRevision(), new URIish(this.hgSCM.getSource()));

            return commitRepoMap;
        }
    }

    private class MultiScmAdapter implements BitbucketBuildStatusNotifierStep.ScmAdapter {

        private final Run<?, ?> build;

        public MultiScmAdapter(Run<?, ?> build) {
            this.build = build;
        }

        public Map getCommitRepoMap() throws Exception {
            WorkflowJob project = (WorkflowJob)this.build.getParent();
            HashMap<String, URIish> commitRepoMap = new HashMap();
            for(SCM s: project.getSCMs()) {
                MultiSCM multiSCM = (MultiSCM)s;
                List<SCM> scms = multiSCM.getConfiguredSCMs();

                for (Iterator<SCM> i = scms.iterator(); i.hasNext(); ) {
                    SCM scm = i.next();
                    if (scm instanceof GitSCM) {
                        commitRepoMap.putAll(new BitbucketBuildStatusNotifierStep.GitScmAdapter((GitSCM) scm, this.build).getCommitRepoMap());
                    } else if (scm instanceof MercurialSCM) {
                        commitRepoMap.putAll(new BitbucketBuildStatusNotifierStep.MercurialScmAdapter((MercurialSCM) scm).getCommitRepoMap());
                    }
                }
            }

            return commitRepoMap;
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(DescriptorImpl.class);
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        private String globalCredentialsId;

        public String getGlobalCredentialsId() {
            return globalCredentialsId;
        }

        public void setGlobalCredentialsId(String globalCredentialsId) {
            this.globalCredentialsId = globalCredentialsId;
        }

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        protected XmlFile getConfigFile() {
            return new XmlFile(new File(Jenkins.getInstance().getRootDir(), this.getId().replace("Step", "") + ".xml"));
        }

        @Override
        public String getFunctionName() {
            return "bitbucketStatusNotify";
        }

        @Override
        public String getDisplayName() {
            return "Notify a build status to BitBucket.";
        }
    }

    public static class Execution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1L;

        @StepContextParameter
        private transient TaskListener listener;

        @StepContextParameter
        private transient FilePath ws;

        @StepContextParameter
        private transient Run build;

        @StepContextParameter
        private transient Launcher launcher;

        @Inject
        private transient BitbucketBuildStatusNotifierStep step;

        private void readGlobalConfiguration() {
            XmlFile config = step.getDescriptor().getConfigFile();
            BitbucketBuildStatusNotifier.DescriptorImpl cfg = new BitbucketBuildStatusNotifier.DescriptorImpl();
            try {
                config.unmarshal(cfg);
                step.getDescriptor().setGlobalCredentialsId(cfg.getGlobalCredentialsId());
            } catch(IOException e) {
                logger.warning("Unable to read BitbucketBuildStatusNotifier configuration");
            }
        }

        @Override
        public Void run() throws Exception {
            this.readGlobalConfiguration();

            this.step.notifyBuildStatus(this.build, this.listener, getContext());

            if(step.getBuildStatus().equals(BitbucketBuildStatus.FAILED)) {
                throw new Exception("Build failed");
            }

            return null;
        }
    }
}
