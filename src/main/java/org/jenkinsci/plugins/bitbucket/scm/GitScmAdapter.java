package org.jenkinsci.plugins.bitbucket.scm;

import hudson.model.Run;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.util.BuildData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

public class GitScmAdapter implements ScmAdapter {
    private static final Logger logger = Logger.getLogger(GitScmAdapter.class.getName());

    private final GitSCM gitScm;
    private final Run<?, ?> build;

    public GitScmAdapter(GitSCM scm, Run<?, ?> build) {
        this.gitScm = scm;
        this.build = build;
    }

    public Map<String, URIish> getCommitRepoMap() throws Exception {
        List<RemoteConfig> repoList = this.gitScm.getRepositories();
        if (repoList.size() != 1) {
            throw new Exception("None or multiple repos");
        }

        HashMap<String, URIish> commitRepoMap = new HashMap<String, URIish>();
        BuildData buildData = build.getAction(BuildData.class);
        if (buildData == null || buildData.getLastBuiltRevision() == null) {
            logger.warning("Build data could not be found");
        } else {
            commitRepoMap.put(buildData.getLastBuiltRevision().getSha1String(), repoList.get(0).getURIs().get(0));
        }

        return commitRepoMap;
    }
}
