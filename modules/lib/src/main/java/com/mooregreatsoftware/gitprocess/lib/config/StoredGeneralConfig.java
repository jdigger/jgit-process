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

import javax.annotation.Nonnull;

import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.v;

public class StoredGeneralConfig implements GeneralConfig {
    private static final Logger LOG = LoggerFactory.getLogger(StoredGeneralConfig.class);

    @Nonnull
    private final StoredConfig storedConfig;


    public StoredGeneralConfig(@Nonnull StoredConfig storedConfig) {
        this.storedConfig = storedConfig;
    }


    @Override
    public boolean defaultRebaseSync() {
        return storedConfig.getBoolean(GIT_PROCESS_SECTION_NAME, null, DEFAULT_REBASE_SYNC_KEY, true);
    }


    @Nonnull
    @Override
    public GeneralConfig defaultRebaseSync(boolean defaultRebaseSync) {
        LOG.debug("Setting default rebase sync to {}", defaultRebaseSync);
        storedConfig.setBoolean(GIT_PROCESS_SECTION_NAME, null, DEFAULT_REBASE_SYNC_KEY, defaultRebaseSync);
        v(storedConfig::save);
        return this;
    }

}
