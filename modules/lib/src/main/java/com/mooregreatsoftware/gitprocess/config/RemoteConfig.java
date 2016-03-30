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
import java.util.Optional;

public interface RemoteConfig extends Config {
    String REMOTE_NAME_KEY = "remoteName";

    default Optional<String> remoteBranchName(@Nonnull String branchName) {
        return remoteName().map(rn -> rn + "/" + branchName);
    }

    boolean hasRemotes();

    @Nonnull
    Optional<String> remoteName();

    @Nonnull
    RemoteConfig remoteName(@Nonnull String remoteName);

    @Nonnull
    Iterable<String> remoteNames();

    @Nonnull
    RemoteConfig remoteAdd(String remoteName, URIish url);


    /**
     * Returns the credential helper that has been defined for the given URI, or the global one if "uri" is null or there is not a specific match for the URI
     *
     * @return empty() if there is no global credential helper defined
     */
    @Nonnull
    Optional<String> credentialHelper(@Nullable URI uri);


    /**
     * Returns the global credential helper
     *
     * @return empty() if there is no global credential helper defined
     */
    @Nonnull
    default Optional<String> credentialHelper() {
        return credentialHelper(null);
    }

}
