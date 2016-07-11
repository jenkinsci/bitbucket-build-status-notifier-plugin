package org.jenkinsci.plugins.bitbucket.scm;

import org.eclipse.jgit.transport.URIish;

import java.util.Map;

public interface ScmAdapter {
    Map<String, URIish> getCommitRepoMap() throws Exception;
}