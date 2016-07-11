package org.jenkinsci.plugins.bitbucket.scm;

import hudson.plugins.mercurial.MercurialSCM;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.transport.URIish;

public class MercurialScmAdapter implements ScmAdapter {

    private final MercurialSCM hgSCM;

    public MercurialScmAdapter(MercurialSCM scm) {
        this.hgSCM = scm;
    }

    public Map<String, URIish> getCommitRepoMap() throws Exception {
        String source = this.hgSCM.getSource();
        if (source == null || source.isEmpty()) {
            throw new Exception("None or multiple repos");
        }

        HashMap<String, URIish> commitRepoMap = new HashMap<String, URIish>();
        commitRepoMap.put(this.hgSCM.getRevision(), new URIish(this.hgSCM.getSource()));

        return commitRepoMap;
    }
}
