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

package org.jenkinsci.plugins.bitbucket.model;

public class BitbucketBuildStatus {

    // indicates that a build for the commit completed successfully
    public static final String SUCCESSFUL = "SUCCESSFUL";
    // indicates that a build for the commit is in progress but not yet complete
    public static final String INPROGRESS = "INPROGRESS";
    // indicates that a build for the commit failed
    public static final String FAILED = "FAILED";

    private String state;
    private String key;
    private String url;
    private String name;
    private String description;

    public BitbucketBuildStatus(String state, String key, String url) {
        this(state, key, url, "", "");
    }

    public BitbucketBuildStatus(String state, String key, String url, String name) {
        this(state, key, url, name, "");
    }

    public BitbucketBuildStatus(String state, String key, String url, String name, String description) {
        this.state = state;
        this.key = key;
        this.url = url;
        this.name = name;
        this.description = description;
    }

    public String getState() {
        return state;
    }

    public String getKey() {
        return key;
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
