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
package com.mooregreatsoftware.gitprocess;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.mooregreatsoftware.gitprocess.StreamUtils.stream;

/**
 * The "container" for branches, this gives easy access to the most important branches (current, integration,
 * parking, etc.) as well as all the other branches.
 */
public interface Branches {

    @Nonnull
    Optional<Branch> currentBranch();


    /**
     * Is the current branch the parking branch?
     *
     * @see #currentBranch()
     */
    default boolean onParking() {
        return currentBranch().map(bn -> bn.shortName().equals(BranchConfig.PARKING_BRANCH_NAME)).orElse(false);
    }

    /**
     * The "parking" branch
     */
    @Nonnull
    default Branch parking() {
        // return the parking branch if it exists, otherwise create it based on the integration branch
        return branch(
            BranchConfig.PARKING_BRANCH_NAME).
            orElseGet(
                () -> createBranch(
                    BranchConfig.PARKING_BRANCH_NAME,
                    integrationBranch().
                        orElseThrow(() -> new IllegalStateException("No integration branch"))
                )
            );
    }

    /**
     * The configuration for branches
     */
    @Nonnull
    BranchConfig config();

    /**
     * The integration branch. For purely local development, this is typically "master", and for shared development
     * this is typically "origin/master".
     * <p>
     * This is the "implied" branch for many operations.
     *
     * @return empty() only if this is not set in configuration and there is no reasonable default
     * @see #integrationBranch(Branch)
     * @see BranchConfig#integrationBranch(Branch)
     */
    @Nonnull
    default Optional<Branch> integrationBranch() {
        return config().integrationBranch();
    }

    /**
     * Sets (and writes to git configuration) the integration branch to use.
     *
     * @param branch the branch to set as integration
     * @return this
     * @see #integrationBranch()
     * @see BranchConfig#integrationBranch()
     */
    @Nonnull
    default Branches integrationBranch(@Nonnull Branch branch) {
        config().integrationBranch(branch);
        return this;
    }

    /**
     * Creates the given branch based on "baseBranch".
     *
     * @param branchName the name of the branch to create
     * @param baseBranch if branch to create this off of
     * @return the new branch
     * @throws BranchAlreadyExists if the branch already exists
     */
    @Nonnull
    Branch createBranch(@Nonnull String branchName, @Nonnull Branch baseBranch) throws BranchAlreadyExists;


    /**
     * Returns the given branch.
     *
     * @param branchName the name of the branch to return, or empty() if it doesn't exist
     */
    @Nonnull
    Optional<Branch> branch(@Nonnull String branchName);


    @Nonnull
    Iterator<Branch> allBranches();


    @Nonnull
    default Iterator<Branch> remoteBranches() {
        return stream(allBranches()).filter(Branch::isRemote).collect(Collectors.toList()).listIterator();
    }


    @Nonnull
    default Iterator<Branch> localBranches() {
        return stream(allBranches()).filter(branch -> !branch.isRemote()).collect(Collectors.toList()).listIterator();
    }

    Branches removeBranch(@Nonnull Branch baseBranch);

    final class BranchAlreadyExists extends RuntimeException {
        public BranchAlreadyExists(String branchName) {
            super("\"" + branchName + "\" already exists");
        }
    }

}
