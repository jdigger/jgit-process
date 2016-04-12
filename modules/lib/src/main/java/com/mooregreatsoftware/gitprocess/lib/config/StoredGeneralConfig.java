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
package com.mooregreatsoftware.gitprocess.lib.config;

import com.mooregreatsoftware.gitprocess.config.GeneralConfig;
import org.eclipse.jgit.lib.StoredConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class StoredGeneralConfig extends AbstractStoredConfig implements GeneralConfig {
    private static final Logger LOG = LoggerFactory.getLogger(StoredGeneralConfig.class);


    public StoredGeneralConfig(StoredConfig storedConfig) {
        super(storedConfig);
    }


    @Override
    public boolean defaultRebaseSync() {
        return getBoolean(GIT_PROCESS_SECTION_NAME, null, DEFAULT_REBASE_SYNC_KEY, true);
    }


    @Override
    public GeneralConfig defaultRebaseSync(boolean defaultRebaseSync) {
        LOG.debug("Setting default rebase sync to {}", defaultRebaseSync);
        setBoolean(GIT_PROCESS_SECTION_NAME, null, DEFAULT_REBASE_SYNC_KEY, defaultRebaseSync);
        return this;
    }


    @Override
    public Optional<String> oauthToken() {
        // TODO: Enhance to look in the git-credential-helper
        final String token = getString(GIT_PROCESS_SECTION_NAME, null, OAUTH_TOKEN_KEY);
        return Optional.ofNullable(token);
    }


    @Override
    public GeneralConfig oauthToken(String oauthToken) {
        LOG.info("Saving the OAuth token");
        setString(GIT_PROCESS_SECTION_NAME, null, OAUTH_TOKEN_KEY, oauthToken);
        return this;
    }


    @Override
    public Optional<String> username() {
        // TODO: Enhance to look in the git-credential-helper
        String username = getString(GIT_PROCESS_SECTION_NAME, null, USERNAME_KEY);
        if (username == null) {
            // some programs save the user in this config property
            username = getString("github", null, "user");
        }
        return Optional.ofNullable(username);
    }


    @Override
    public GeneralConfig username(String username) {
        LOG.info("Setting the user name to {}", username);
        setString(GIT_PROCESS_SECTION_NAME, null, USERNAME_KEY, username);
        return this;
    }
}
