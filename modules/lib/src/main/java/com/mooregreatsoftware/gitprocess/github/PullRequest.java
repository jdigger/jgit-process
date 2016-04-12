/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mooregreatsoftware.gitprocess.github;

import javax.json.JsonObject;
import java.net.URI;

public class PullRequest {

    private final JsonObject jsonObject;


    public PullRequest(JsonObject jsonObject) {
        this.jsonObject = jsonObject;
    }


    public int id() {
        return jsonObject.getInt("number");
    }


    public URI htmlUrl() {
        return URI.create(jsonObject.getString("html_url"));
    }


    public String title() {
        return jsonObject.getString("title");
    }


    public String headBranchName() {
        return jsonObject.getJsonObject("head").getString("ref");
    }


    public String baseBranchName() {
        return jsonObject.getJsonObject("base").getString("ref");
    }

}
