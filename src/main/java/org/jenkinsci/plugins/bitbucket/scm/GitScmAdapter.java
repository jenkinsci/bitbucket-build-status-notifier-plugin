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
