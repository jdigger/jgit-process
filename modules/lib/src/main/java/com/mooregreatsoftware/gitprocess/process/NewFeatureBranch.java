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
package com.mooregreatsoftware.gitprocess.process;

import com.mooregreatsoftware.gitprocess.config.BranchConfig;
import com.mooregreatsoftware.gitprocess.lib.Branch;
import com.mooregreatsoftware.gitprocess.lib.Branches;
import com.mooregreatsoftware.gitprocess.lib.GitLib;
import javaslang.control.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

import static javaslang.control.Either.left;

/**
 * Creates a new feature branch.
 *
 * @see #newFeatureBranch(GitLib, String, boolean)
 */
@SuppressWarnings("ConstantConditions")
public class NewFeatureBranch {
    private static final Logger LOG = LoggerFactory.getLogger(NewFeatureBranch.class);


    /**
     * Creates a new feature branch based on (generally) the integration branch.
     *
     * @param gitLib     the git library to use
     * @param branchName the name of the branch to create
     * @param localOnly  do not try to fetch before creating the branch
     * @return Left(error message), Right(the newly created branch)
     * @see BranchConfig#integrationBranch()
     */
    public static Either<String, Branch> newFeatureBranch(@Nonnull GitLib gitLib, @Nonnull String branchName, boolean localOnly) {
        final Branches branches = gitLib.branches();
        final boolean onParking = branches.onParking();

        final BranchConfig branchConfig = gitLib.branchConfig();
        final Branch integrationBranch = branchConfig.integrationBranch().orElseThrow(() -> new IllegalStateException("No integration branch"));

        final Branch baseBranch = baseBranch(gitLib, branches, integrationBranch);

        if (!localOnly) gitLib.fetch();

        LOG.info("Creating \"{}\" off of \"{}\"", branchName, baseBranch.shortName());

        final Branch newBranch = branches.createBranch(branchName, baseBranch);
        final Either<String, Branch> checkoutRes = newBranch.checkout();
        if (checkoutRes.isLeft()) return left(checkoutRes.getLeft());

        newBranch.upstream(integrationBranch);

        if (onParking) {
            branches.removeBranch(branches.parking());
        }

        return Either.right(newBranch);
    }


    /**
     * If we are not on the parking branch, or the integration branch is fully rebased/merged with parking,
     * then we want to base the new branch off of the integration branch.
     * <p>
     * However, if we are on the parking branch AND integration is not fully merged with parking, then we want to
     * make sure to base the new branch off of the parking branch so that all the changes get picked up
     *
     * @param gitLib            the git library to use
     * @param branches          the branch container
     * @param integrationBranch the integration branch
     * @return the branch to base the feature branch on
     * @throws IllegalStateException if there is not integration branch
     */
    @Nonnull
    private static Branch baseBranch(@Nonnull GitLib gitLib, @Nonnull Branches branches, @Nonnull Branch integrationBranch) {
        if (!branches.onParking() || integrationBranchContainsAllOfParking(gitLib, integrationBranch)) {
            return integrationBranch;
        }
        else {
            return branches.parking();
        }
    }


    private static boolean integrationBranchContainsAllOfParking(@Nonnull GitLib gitLib, @Nonnull Branch integrationBranch) {
        final Branch parking = gitLib.branches().parking();
        return integrationBranch.containsAllOf(parking);
    }

}
