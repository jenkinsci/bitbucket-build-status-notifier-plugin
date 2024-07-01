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
import hudson.plugins.git.Revision;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

public class GitScmAdapter implements ScmAdapter {
    private static final Logger logger = Logger.getLogger(GitScmAdapter.class.getName());

    private final BuildData buildData;
    private final List<RemoteConfig> repoList;

    public GitScmAdapter(GitSCM scm, Run<?, ?> build) {
        this.repoList = scm.getRepositories();
        this.buildData = build.getAction(BuildData.class);
    }

    public Map<String, URIish> getCommitRepoMap() throws Exception {
        if (repoList.size() < 1) {
            throw new Exception("No repos found");
        }

        HashMap<String, URIish> commitRepoMap = new HashMap<String, URIish>();
        if (buildData == null) {
            logger.warning("Build data could not be found");

            return commitRepoMap;
        }

        Revision lastBuiltRevision = buildData.getLastBuiltRevision();
        if (lastBuiltRevision == null) {
            logger.warning("Last build revision could not be found");

            return commitRepoMap;
        }

        RemoteConfig remoteConfig = repoList.get(0);
        if (remoteConfig == null || remoteConfig.getURIs().isEmpty()) {
            logger.warning("No URIs found in the remote config");

            return commitRepoMap;
        }

        URIish uri = remoteConfig.getURIs().get(0);
        String sha1String = lastBuiltRevision.getSha1String();
        if (uri != null && sha1String != null) {
            commitRepoMap.put(sha1String, uri);
        } else {
            logger.warning("URI or SHA1 string is null");
        }

        return commitRepoMap;
    }
}
