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
package com.mooregreatsoftware.gitprocess.lib;

import com.mooregreatsoftware.gitprocess.config.RemoteConfig;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;

import javax.annotation.Nonnull;
import java.net.URI;
import java.util.Optional;

import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.e;

/**
 * Provides support for https://git-scm.com/docs/git-credential and https://git-scm.com/docs/gitcredentials
 * <p>
 * NOT FINISHED
 * <p>
 * Currently only git-credential-osxkeychain is tested
 */
public class CredentialHelperCredentialsProvider implements CredentialsProvider {
    @Nonnull
    private final RemoteConfig remoteConfig;


    public CredentialHelperCredentialsProvider(@Nonnull RemoteConfig remoteConfig) {
        this.remoteConfig = remoteConfig;
    }


    @Override
    public void setCredentials(AuthScope authscope, Credentials credentials) {

    }


    @SuppressWarnings("PointlessBooleanExpression")
    @Override
    public Credentials getCredentials(AuthScope authscope) {
        if (authscope == null) return null;

        URI uri = e(() -> new URI(authscope.getScheme(), null, authscope.getHost(), authscope.getPort(), null, null, null));
        final Optional<String> credentialHelper = remoteConfig.credentialHelper(uri);

        if (credentialHelper.isPresent() == false) return null;

        final String credHelper = credentialHelper.get();
        final String progName;
        if (credHelper.startsWith("/")) {
            progName = credHelper;
        }
        else {
            progName = "git-credential-" + credHelper;
        }

        return null;
    }


    @Override
    public void clear() {

    }
}
