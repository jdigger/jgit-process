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

import com.mooregreatsoftware.gitprocess.lib.Branch;
import com.mooregreatsoftware.gitprocess.lib.Config;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Configuration around branches.
 */
public interface BranchConfig extends Config {
    String INTEGRATION_BRANCH_KEY = "integrationBranch";
    String PARKING_BRANCH_NAME = "_parking_";

    /**
     * The integration branch. For purely local development, this is typically "master", and for shared development
     * this is typically "origin/master".
     * <p>
     * This is the "implied" branch for many operations.
     *
     * @return empty() only if this is not set in configuration and there is no reasonable default
     * @see #integrationBranch(Branch)
     */
    @Nonnull
    Optional<Branch> integrationBranch();

    /**
     * Sets (and writes to git configuration) the integration branch to use.
     *
     * @param branch the branch to set as integration
     * @return this
     * @see #integrationBranch()
     */
    @Nonnull
    BranchConfig integrationBranch(@Nonnull Branch branch);

    /**
     * Set (and write to git configuration) the upstream/tracking to use for a branch.
     *
     * @param branch   the branch to set the upstream for
     * @param upstream the branch to set the upstream to
     * @return this
     */
    @Nonnull
    BranchConfig setUpstream(@Nonnull Branch branch, @Nonnull Branch upstream);

    /**
     * The upstream/tracking that has been set for a branch.
     *
     * @param branch the branch to get the upstream configuration for
     * @return empty() if nothing has been set
     */
    @Nonnull
    Optional<Branch> getUpstream(Branch branch);
}
