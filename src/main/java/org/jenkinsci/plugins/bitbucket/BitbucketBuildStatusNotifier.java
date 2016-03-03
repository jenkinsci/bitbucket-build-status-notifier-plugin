package org.jenkinsci.plugins.bitbucket;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Result;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.SCM;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.bitbucket.api.*;
import org.jenkinsci.plugins.bitbucket.model.BitbucketBuildStatus;
import org.jenkinsci.plugins.bitbucket.model.BitbucketBuildStatusResource;
import org.jenkinsci.plugins.bitbucket.model.BitbucketBuildStatusSerializer;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.scribe.model.*;

public class BitbucketBuildStatusNotifier extends Notifier {

    private static final Logger logger = Logger.getLogger(BitbucketBuildStatusNotifier.class.getName());

    private boolean notifyStart;
    private boolean notifyFinish;
    private String credentialsId;

    @DataBoundConstructor
    public BitbucketBuildStatusNotifier(final boolean notifyStart, final boolean notifyFinish,
                                        final String credentialsId) {
        super();
        this.notifyStart = notifyStart;
        this.notifyFinish = notifyFinish;
        this.credentialsId = credentialsId;
    }

    public boolean getNotifyStart() {
        return this.notifyStart;
    }

    public boolean getNotifyFinish() {
        return this.notifyFinish;
    }

    public String getCredentialsId() {
        return this.credentialsId;
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {

        if (!this.notifyStart) {
            return true;
        }
        logger.info("Bitbucket notify on start");

        try {
            this.notifyBuildStatus(build, listener);
        } catch (Exception e) {
            logger.log(Level.INFO, "Bitbucket notify on start failed: " + e.getMessage(), e);
            listener.getLogger().println("Bitbucket notify on start failed: " + e.getMessage());
            e.printStackTrace(listener.getLogger());
        }

        logger.info("Bitbucket notify on start succeeded");

        return true;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {

        if (!this.notifyFinish) {
            return true;
        }
        logger.info("Bitbucket notify on finish");

        try {
            this.notifyBuildStatus(build, listener);
        } catch (Exception e) {
            logger.log(Level.INFO, "Bitbucket notify on finish failed: " + e.getMessage(), e);
            listener.getLogger().println("Bitbucket notify on finish failed: " + e.getMessage());
            e.printStackTrace(listener.getLogger());
        }

        logger.info("Bitbucket notify on finish succeeded");

        return true;
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

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(DescriptorImpl.class);
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        //This is here to ensure that the reported build status is actually correct. If we were to return false here,
        //other build plugins could still modify the build result, making the sent out HipChat notification incorrect.
        return true;
    }

    private String guessBitbucketBuildState(final Result result) {

        String state;

        // possible statuses SUCCESS, UNSTABLE, FAILURE, NOT_BUILT, ABORTED
        if (result == null) {
            state = BitbucketBuildStatus.INPROGRESS;
        } else if (Result.SUCCESS == result) {
            state = BitbucketBuildStatus.SUCCESSFUL;
        } else if (Result.UNSTABLE == result || Result.FAILURE == result) {
            state = BitbucketBuildStatus.FAILED;
        } else {
            // return empty status for every other result (NOT_BUILT, ABORTED)
            state = null;
        }

        return state;
    }

    private BitbucketBuildStatusResource createBuildStatusResourceFromBuild(final AbstractBuild build) throws Exception {
        SCM scm = build.getProject().getScm();
        if (scm == null) {
            throw new Exception("Bitbucket build notifier only works with SCM");
        }

        ScmAdapter scmAdapter;
        if (scm instanceof GitSCM) {
            scmAdapter = new GitScmAdapter(build);
        } else if (scm instanceof MercurialSCM) {
            scmAdapter = new MercurialScmAdapter((MercurialSCM) scm);
        } else {
            throw new Exception("Bitbucket build notifier requires a git repo or a mercurial repo as SCM");
        }

        URIish urIish = scmAdapter.getRepositoryUri();
        if (!urIish.getHost().equals("bitbucket.org")) {
            throw new Exception("Bitbucket build notifier support only repositories hosted in bitbucket.org");
        }

        // extract bitbucket user name and repository name from repo URI
        String repoUrl = urIish.getPath();
        String repoName = repoUrl.substring(
                repoUrl.lastIndexOf("/") + 1,
                repoUrl.indexOf(".git") > -1 ? repoUrl.indexOf(".git") : repoUrl.length()
        );
        if (repoName.isEmpty()) {
            throw new Exception("Bitbucket build notifier could not extract the repository name from the repository URL");
        }

        String userName = repoUrl.substring(0, repoUrl.indexOf("/" + repoName));
        if (userName.indexOf("/") != -1) {
            userName = userName.substring(userName.indexOf("/") + 1, userName.length());
        }
        if (userName.isEmpty()) {
            throw new Exception("Bitbucket build notifier could not extract the user name from the repository URL");
        }

        String commitId = scmAdapter.findCurrentCommitId();
        if (commitId == null) {
            throw new Exception("Commit ID could not be found!");
        }

        return new BitbucketBuildStatusResource(userName, repoName, commitId);
    }

    private void notifyBuildStatus(final AbstractBuild build, final BuildListener listener) throws Exception {

        UsernamePasswordCredentials credentials = this.getCredentials(this.getCredentialsId(), build.getProject());
        BitbucketBuildStatusResource buildStatusResource = this.createBuildStatusResourceFromBuild(build);
        BitbucketBuildStatus buildStatus = this.createBitbucketBuildStatusFromBuild(build);

        if (credentials == null) {
            Job job = null;
            credentials = this.getCredentials(this.getDescriptor().getGlobalCredentialsId(), job);
        }
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

        Verifier verifier = null;
        Token token = apiService.getAccessToken(OAuthConstants.EMPTY_TOKEN, verifier);
        apiService.signRequest(token, request);

        Response response = request.send();
        logger.info("This response was received:" + response.getBody());
        listener.getLogger().println("Sending build status " + buildStatus.getState() + " for commit " + buildStatusResource.getCommitId() + " to BitBucket is done!");
    }

    private BitbucketBuildStatus createBitbucketBuildStatusFromBuild(AbstractBuild build) {

        String buildState = this.guessBitbucketBuildState(build.getResult());
        // bitbucket requires the key to be shorter than 40 chars
        String buildKey = DigestUtils.md5Hex(build.getProject().getFullDisplayName() + "#" + build.getNumber());
        String buildUrl = build.getProject().getAbsoluteUrl() + build.getNumber() + '/';
        String buildName = build.getProject().getFullDisplayName() + " #" + build.getNumber();

        return new BitbucketBuildStatus(buildState, buildKey, buildUrl, buildName);
    }

    private interface ScmAdapter {
        URIish getRepositoryUri() throws Exception;
        String findCurrentCommitId() throws Exception;
    }

    private class GitScmAdapter implements ScmAdapter {

        private final GitSCM gitSCM;
        private final AbstractBuild build;

        public GitScmAdapter(AbstractBuild build) {
            this.gitSCM = (GitSCM) build.getProject().getScm();
            this.build = build;
        }

        public URIish getRepositoryUri() throws Exception {
            List<RemoteConfig> repoList = gitSCM.getRepositories();
            if (repoList.size() != 1) {
                throw new Exception("None or multiple repos");
            }

            return repoList.get(0).getURIs().get(0);
        }

        public String findCurrentCommitId() throws Exception {
            BuildData buildData = build.getAction(BuildData.class);
            if(buildData == null || buildData.getLastBuiltRevision() == null) {
                throw new Exception("Revision could not be found");
            }

            return buildData.getLastBuiltRevision().getSha1String();
        }
    }

    private class MercurialScmAdapter implements ScmAdapter {

        private final MercurialSCM hgSCM;

        public MercurialScmAdapter(MercurialSCM scm) {
            hgSCM = scm;
        }

        public URIish getRepositoryUri() throws Exception {
            String source = hgSCM.getSource();
            if (source == null || source.isEmpty()) {
                throw new Exception("None or multiple repos");
            }

            return new URIish(source);
        }

        public String findCurrentCommitId() throws Exception {
            return hgSCM.getRevision();
        }
    }

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private String globalCredentialsId;

        public String getGlobalCredentialsId() {
            return globalCredentialsId;
        }

        public void setGlobalCredentialsId(String globalCredentialsId) {
            this.globalCredentialsId = globalCredentialsId;
        }

        @Override
        public String getDisplayName() {
            return "Bitbucket notify build status";
        }

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            req.bindJSON(this, formData.getJSONObject("bitbucket-build-status-notifier"));
            save();

            return true;
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Job<?,?> owner) {
            if (owner == null || !owner.hasPermission(Item.CONFIGURE)) {
                return new ListBoxModel();
            }
            List<DomainRequirement> apiEndpoint = URIRequirementBuilder.fromUri(BitbucketApi.OAUTH_ENDPOINT).build();

            return new StandardUsernameListBoxModel()
                    .withEmptySelection()
                    .withAll(CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, owner, null, apiEndpoint));
        }

        public FormValidation doCheckCredentialsId(@QueryParameter final String credentialsId,
                                                   @AncestorInPath final Job<?,?> owner) {
            String globalCredentialsId = this.getGlobalCredentialsId();

            if (credentialsId == null || credentialsId.isEmpty()) {
                if (globalCredentialsId == null || globalCredentialsId.isEmpty()) {
                    return FormValidation.error("Please enter Bitbucket OAuth credentials");
                } else {
                    return this.doCheckGlobalCredentialsId(this.getGlobalCredentialsId());
                }
            }

            UsernamePasswordCredentials credentials = BitbucketBuildStatusNotifier.getCredentials(credentialsId, owner);

            return this.checkCredentials(credentials);
        }

        public ListBoxModel doFillGlobalCredentialsIdItems() {
            Job owner = null;
            List<DomainRequirement> apiEndpoint = URIRequirementBuilder.fromUri(BitbucketApi.OAUTH_ENDPOINT).build();

            return new StandardUsernameListBoxModel()
                    .withEmptySelection()
                    .withAll(CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, owner, null, apiEndpoint));
        }

        public FormValidation doCheckGlobalCredentialsId(@QueryParameter final String globalCredentialsId) {
            if (globalCredentialsId.isEmpty()) {
                return FormValidation.ok();
            }

            Job owner = null;
            UsernamePasswordCredentials credentials = BitbucketBuildStatusNotifier.getCredentials(globalCredentialsId, owner);

            return this.checkCredentials(credentials);
        }

        private FormValidation checkCredentials(UsernamePasswordCredentials credentials) {

            try {
                OAuthConfig config = new OAuthConfig(credentials.getUsername(), credentials.getPassword().getPlainText());
                BitbucketApiService apiService = (BitbucketApiService) new BitbucketApi().createService(config);
                Verifier verifier = null;
                Token token = apiService.getAccessToken(OAuthConstants.EMPTY_TOKEN, verifier);

                if (token.isEmpty()) {
                    return FormValidation.error("Invalid Bitbucket OAuth credentials");
                }
            } catch (Exception e) {
                return FormValidation.error(e.getClass() + e.getMessage());
            }

            return FormValidation.ok();
        }
    }
}
