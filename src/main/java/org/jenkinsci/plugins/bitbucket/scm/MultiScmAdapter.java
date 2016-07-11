package org.jenkinsci.plugins.bitbucket.scm;

import hudson.model.Run;
import hudson.plugins.git.GitSCM;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.SCM;

import org.eclipse.jgit.transport.URIish;

import org.jenkinsci.plugins.multiplescms.MultiSCM;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MultiScmAdapter implements ScmAdapter {

    private final MultiSCM multiScm;
    private final Run<?, ?> build;

    public MultiScmAdapter(MultiSCM multiScm, Run<?, ?> build) {
        this.multiScm = multiScm;
        this.build = build;
    }

    public Map getCommitRepoMap() throws Exception {
        HashMap<String, URIish> commitRepoMap = new HashMap<String, URIish>();
        List<SCM> scms = multiScm.getConfiguredSCMs();

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
