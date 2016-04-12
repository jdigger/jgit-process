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
package com.mooregreatsoftware.gitprocess.config;

import com.mooregreatsoftware.gitprocess.lib.Config;
import org.eclipse.jgit.transport.URIish;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URI;

public interface RemoteConfig extends Config {
    String REMOTE_NAME_KEY = "remoteName";

    @Nullable
    default String remoteBranchName(String branchName) {
        final String remoteName = remoteName();
        if (remoteName == null) return null;
        return remoteName + "/" + branchName;
    }

    boolean hasRemotes();

    @Nullable
    String remoteName();

    RemoteConfig remoteName(@Nonnull String remoteName);

    Iterable<String> remoteNames();

    RemoteConfig remoteAdd(String remoteName, URIish url);


    /**
     * Returns the credential helper that has been defined for the given URI, or the global one if "uri" is null or there is not a specific match for the URI
     *
     * @return null if there is no global credential helper defined
     */
    @Nullable
    String credentialHelper(@Nullable URI uri);


    /**
     * Returns the global credential helper
     *
     * @return null if there is no global credential helper defined
     */
    @Nullable
    default String credentialHelper() {
        return credentialHelper(null);
    }


    /**
     * The name of the repository for the current "remote". For example, "jdigger/jgit-process"
     */
    @Nullable
    String repositoryName();

    /**
     * The URL for the named remote
     *
     * @param remoteName the name of the remote (e.g., "origin")
     * @return null if it can not be found
     */
    @Nullable
    URI remoteUrl(String remoteName);

}
