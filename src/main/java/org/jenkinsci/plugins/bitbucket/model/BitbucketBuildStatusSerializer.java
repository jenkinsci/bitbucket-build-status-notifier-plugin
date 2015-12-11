package org.jenkinsci.plugins.bitbucket.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class BitbucketBuildStatusSerializer implements JsonSerializer<BitbucketBuildStatus> {

    public JsonElement serialize(final BitbucketBuildStatus buildStatus, final Type type,
                                 final JsonSerializationContext jsonSerializationContext) {

        final JsonObject jsonObject = new JsonObject();

        // required
        jsonObject.addProperty("state", buildStatus.getState());
        jsonObject.addProperty("key", buildStatus.getKey());
        jsonObject.addProperty("url", buildStatus.getUrl());

        // optionals
        if (!buildStatus.getName().isEmpty()) {
            jsonObject.addProperty("name", buildStatus.getName());
        }
        if (!buildStatus.getDescription().isEmpty()) {
            jsonObject.addProperty("description", buildStatus.getDescription());
        }

        return jsonObject;
    }
}
