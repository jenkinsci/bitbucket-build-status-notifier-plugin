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
