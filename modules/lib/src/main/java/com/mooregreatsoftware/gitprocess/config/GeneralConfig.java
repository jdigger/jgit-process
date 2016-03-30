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

import javax.annotation.Nonnull;

/**
 * General configuration.
 */
public interface GeneralConfig extends Config {
    String DEFAULT_REBASE_SYNC_KEY = "defaultRebaseSync";

    /**
     * Should it default to using rebase instead of merge?
     *
     * @return defaults to true
     * @see #defaultRebaseSync(boolean)
     */
    boolean defaultRebaseSync();

    /**
     * Set if it should default to using rebase instead of merge for a sync.
     *
     * @see #defaultRebaseSync()
     */
    @Nonnull
    @SuppressWarnings("unused")
    GeneralConfig defaultRebaseSync(boolean defaultRebaseSync);

}
