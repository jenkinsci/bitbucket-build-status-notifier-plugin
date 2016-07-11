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
