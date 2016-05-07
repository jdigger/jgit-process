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

import javaslang.control.Try;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.jgit.lib.StoredConfig;

import javax.annotation.Nullable;

import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.exceptionTranslator;

public abstract class AbstractStoredConfig {
    protected final StoredConfig storedConfig;


    public AbstractStoredConfig(StoredConfig storedConfig) {
        this.storedConfig = storedConfig;
    }


    @SuppressWarnings("RedundantCast")
    protected String getString(String section, @Nullable String subsection, String key) {
        return storedConfig.getString(section, (@NonNull String)subsection, key);
    }


    @SuppressWarnings("RedundantCast")
    protected void setString(String section, @Nullable String subsection, String key, String value) {
        storedConfig.setString(section, (@NonNull String)subsection, key, value);
        save();
    }


    @SuppressWarnings("RedundantCast")
    protected boolean getBoolean(String section, @Nullable String subsection, String key, boolean defaultValue) {
        return storedConfig.getBoolean(section, (@NonNull String)subsection, key, defaultValue);
    }


    @SuppressWarnings("RedundantCast")
    protected void setBoolean(String section, @Nullable String subsection, String key, boolean value) {
        storedConfig.setBoolean(section, (@NonNull String)subsection, key, value);
        save();
    }


    protected void save() {
        Try.run(storedConfig::save).getOrElseThrow(exceptionTranslator());
    }

}
