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
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.LogTaskListener;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.bitbucket.api.BitbucketApi;
import org.jenkinsci.plugins.bitbucket.api.BitbucketApiService;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.scribe.model.*;

public class BitbucketBuildStatusNotifier extends Notifier {

    private static final Logger logger = Logger.getLogger(BitbucketBuildStatusNotifier.class.getName());

    private final boolean notifyStart;
    private final boolean notifyFinish;
    private final boolean overrideLatestBuild;
    private final String credentialsId;
    private final String overrideCommitIdVar;

    @DataBoundConstructor
    public BitbucketBuildStatusNotifier(final boolean notifyStart, final boolean notifyFinish,
                                        final boolean overrideLatestBuild, final String credentialsId,
                                        final String overrideCommitIdVar) {
        super();
        this.notifyStart = notifyStart;
        this.notifyFinish = notifyFinish;
        this.overrideLatestBuild = overrideLatestBuild;
        this.credentialsId = credentialsId;
        this.overrideCommitIdVar = overrideCommitIdVar;
    }

    public boolean getNotifyStart() {
        return this.notifyStart;
    }

    public boolean getNotifyFinish() {
        return this.notifyFinish;
    }

    public boolean getOverrideLatestBuild() {
        return this.overrideLatestBuild;
    }

    public String getCredentialsId() {
        return this.credentialsId != null ? this.credentialsId : this.getDescriptor().getGlobalCredentialsId();
    }

    public String getOverrideCommitIdVar() {
        return overrideCommitIdVar;
    }

    private StandardUsernamePasswordCredentials getCredentials(AbstractBuild<?,?> build) {
        StandardUsernamePasswordCredentials credentials = BitbucketBuildStatusHelper
                .getCredentials(getCredentialsId(), build.getProject());
        if (credentials == null) {
            credentials = BitbucketBuildStatusHelper
                    .getCredentials(this.getDescriptor().getGlobalCredentialsId(), null);
        }
        return credentials;
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        if (!this.notifyStart) {
            return true;
        }
        logger.info("Bitbucket notify on start");


        try {
            BitbucketBuildStatusHelper.notifyBuildStatus(this.getCredentials(build), this.getOverrideLatestBuild(), build, listener, getCommitId(build));
        } catch (Exception e) {
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
            BitbucketBuildStatusHelper.notifyBuildStatus(this.getCredentials(build), this.getOverrideLatestBuild(), build, listener, getCommitId(build));
        } catch (Exception e) {
            logger.log(Level.INFO, "Bitbucket notify on finish failed: " + e.getMessage(), e);
            listener.getLogger().println("Bitbucket notify on finish failed: " + e.getMessage());
            e.printStackTrace(listener.getLogger());
        }

        logger.info("Bitbucket notify on finish succeeded");

        return true;
    }

    private String getCommitId(AbstractBuild<?, ?> build) {
        if (StringUtils.isBlank(overrideCommitIdVar)) {
            return null;
        } else {
            try {
                String commitId = build.getEnvironment(new LogTaskListener(logger, Level.INFO)).get(overrideCommitIdVar);
                logger.info("Overriding commitId with " + commitId);
                return commitId;
            } catch (IOException e) {
                logger.warning(e.getMessage());
            } catch (InterruptedException e) {
                logger.warning(e.getMessage());
            }
            return null;
        }
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

            UsernamePasswordCredentials credentials = BitbucketBuildStatusHelper.getCredentials(credentialsId, owner);

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
            UsernamePasswordCredentials credentials = BitbucketBuildStatusHelper.getCredentials(globalCredentialsId, owner);

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
