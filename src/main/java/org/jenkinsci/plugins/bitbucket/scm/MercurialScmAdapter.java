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
