package org.jenkinsci.plugins.bitbucket.model;

import org.scribe.model.Verb;

public class BitbucketBuildStatusResource {

    private static final String API_ENDPOINT = "https://api.bitbucket.org/2.0/";

    private String owner;
    private String repoSlug;
    private String commitId;

    public BitbucketBuildStatusResource(String owner, String repoSlug, String commitId) {
        this.owner = owner;
        this.repoSlug = repoSlug;
        this.commitId = commitId;
    }

    public String generateUrl(Verb verb) throws Exception {
        if (verb.equals(Verb.POST)) {
            return API_ENDPOINT + "repositories/" + this.owner + "/" + this.repoSlug + "/commit/" + this.commitId + "/statuses/build";
        } else {
            throw new Exception("Verb " + verb.toString() + "not allowed or implemented");
        }
    }
}
