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
import hudson.model.*;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.SCM;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.validator.UrlValidator;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.bitbucket.api.BitbucketApi;
import org.jenkinsci.plugins.bitbucket.api.BitbucketApiService;
import org.jenkinsci.plugins.bitbucket.model.BitbucketBuildStatus;
import org.jenkinsci.plugins.bitbucket.model.BitbucketBuildStatusResource;
import org.jenkinsci.plugins.bitbucket.model.BitbucketBuildStatusSerializer;
import org.jenkinsci.plugins.bitbucket.validator.BitbucketHostValidator;
import org.jenkinsci.plugins.multiplescms.MultiSCM;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.scribe.model.*;

import java.net.MalformedURLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BitbucketBuildStatusNotifier extends Notifier {

    private static final Logger logger = Logger.getLogger(BitbucketBuildStatusNotifier.class.getName());
    private static final BitbucketHostValidator hostValidator = new BitbucketHostValidator();

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
            logger.info("Bitbucket notify on start succeeded");
        } catch (Exception e) {
            logger.log(Level.INFO, "Bitbucket notify on start failed: " + e.getMessage(), e);
            listener.getLogger().println("Bitbucket notify on start failed: " + e.getMessage());
        }

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
            logger.info("Bitbucket notify on finish succeeded");
        } catch (Exception e) {
            logger.log(Level.INFO, "Bitbucket notify on finish failed: " + e.getMessage(), e);
            listener.getLogger().println("Bitbucket notify on finish failed: " + e.getMessage());
        }

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
        } else if (Result.UNSTABLE == result || Result.FAILURE == result || Result.ABORTED == result) {
            state = BitbucketBuildStatus.FAILED;
        } else {
            // return empty status for every other result (NOT_BUILT, ABORTED)
            state = null;
        }

        return state;
    }

    private List<BitbucketBuildStatusResource> createBuildStatusResources(final AbstractBuild build) throws Exception {
        SCM scm = build.getProject().getScm();
        if (scm == null) {
            throw new Exception("Bitbucket build notifier only works with SCM");
        }

        ScmAdapter scmAdapter;
        if (scm instanceof GitSCM) {
            scmAdapter = new GitScmAdapter((GitSCM) scm, build);
        } else if (scm instanceof MercurialSCM) {
            scmAdapter = new MercurialScmAdapter((MercurialSCM) scm);
        } else if (scm instanceof MultiSCM){
            scmAdapter = new MultiScmAdapter(build);
        } else {
            throw new Exception("Bitbucket build notifier requires a git repo or a mercurial repo as SCM");
        }

        Map<String, URIish> commitRepoMap = scmAdapter.getCommitRepoMap();
        List<BitbucketBuildStatusResource> buildStatusResources = new ArrayList<BitbucketBuildStatusResource>();
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

        return buildStatusResources;
    }

    private void notifyBuildStatus(final AbstractBuild build, final BuildListener listener) throws Exception {

        UsernamePasswordCredentials credentials = BitbucketBuildStatusNotifier.getCredentials(this.getCredentialsId(), build.getProject());
        List<BitbucketBuildStatusResource> buildStatusResources = this.createBuildStatusResources(build);

        AbstractBuild prevBuild = build.getPreviousBuild();
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
                Job job = null;
                credentials = BitbucketBuildStatusNotifier.getCredentials(this.getDescriptor().getGlobalCredentialsId(), job);
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

            logger.info("This request was sent: " + request.getBodyContents());
            logger.info("This response was received: " + response.getBody());
            listener.getLogger().println("Sending build status " + buildStatus.getState() + " for commit " + buildStatusResource.getCommitId() + " to BitBucket is done!");
        }
    }

    private BitbucketBuildStatus createBitbucketBuildStatusFromBuild(AbstractBuild build) throws Exception {

        UrlValidator validator = new UrlValidator();
        String buildState = this.guessBitbucketBuildState(build.getResult());
        // bitbucket requires the key to be shorter than 40 chars
        String buildKey = DigestUtils.md5Hex(build.getProject().getFullDisplayName() + "#" + build.getNumber());
        URIish buildUrl = new URIish(build.getProject().getAbsoluteUrl() + build.getNumber() + '/');
        String buildName = build.getProject().getFullDisplayName() + " #" + build.getNumber();
        AbstractTestResultAction testResult = build.getAction(AbstractTestResultAction.class);
        String description = "";
        if (testResult != null) {
            int passedCount = testResult.getTotalCount() - testResult.getFailCount();
            description = passedCount + " of " + testResult.getTotalCount() + " tests passed";
        }

        // validate URL to avoid Bitbucket bad requests
        if (!validator.isValid(buildUrl.toString())) {
            throw new MalformedURLException("Job URL " + buildUrl.toString() + " is not valid. Please ensure your Jenkins host has a valid URL wihtin its configuration.");
        }

        return new BitbucketBuildStatus(buildState, buildKey, buildUrl.toString(), buildName, description);
    }

    private interface ScmAdapter {
        Map getCommitRepoMap() throws Exception;
    }

    private class GitScmAdapter implements ScmAdapter {

        private final GitSCM gitScm;
        private final AbstractBuild build;

        public GitScmAdapter(GitSCM scm, AbstractBuild build) {
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

    private class MercurialScmAdapter implements ScmAdapter {

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

    private class MultiScmAdapter implements ScmAdapter {

        private final AbstractBuild build;

        public MultiScmAdapter(AbstractBuild build) {
            this.build = build;
        }

        public Map getCommitRepoMap() throws Exception {
            MultiSCM multiSCM = (MultiSCM) this.build.getProject().getScm();
            List<SCM> scms = multiSCM.getConfiguredSCMs();

            HashMap<String, URIish> commitRepoMap = new HashMap();
            for (Iterator<SCM> i = scms.iterator(); i.hasNext(); ) {
                SCM scm = i.next();
                if (scm instanceof GitSCM) {
                    commitRepoMap.putAll(new GitScmAdapter((GitSCM) scm, this.build).getCommitRepoMap());
                } else if (scm instanceof MercurialSCM) {
                    commitRepoMap.putAll(new MercurialScmAdapter((MercurialSCM) scm).getCommitRepoMap());
                }
            }

            return commitRepoMap;
        }
    }

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private String globalCredentialsId;
        
        public DescriptorImpl() {
            load();
        }

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
