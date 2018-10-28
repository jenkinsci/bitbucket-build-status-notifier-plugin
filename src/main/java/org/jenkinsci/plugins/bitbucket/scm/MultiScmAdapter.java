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
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.SCM;

import org.eclipse.jgit.transport.URIish;

import org.jenkinsci.plugins.multiplescms.MultiSCM;

import java.util.HashMap;
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

        for (SCM scm : multiScm.getConfiguredSCMs()) {
            if (scm instanceof GitSCM) {
                commitRepoMap.putAll(new GitScmAdapter((GitSCM) scm, this.build).getCommitRepoMap());
            } else if (scm instanceof MercurialSCM) {
                commitRepoMap.putAll(new MercurialScmAdapter((MercurialSCM) scm, this.build).getCommitRepoMap());
            }
        }

        return commitRepoMap;
    }
}
