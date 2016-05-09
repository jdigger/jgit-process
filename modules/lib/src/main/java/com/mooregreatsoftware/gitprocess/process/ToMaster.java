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

import com.mooregreatsoftware.gitprocess.github.GitHubRepo;
import com.mooregreatsoftware.gitprocess.github.PullRequests;
import com.mooregreatsoftware.gitprocess.lib.Branch;
import com.mooregreatsoftware.gitprocess.lib.Branches;
import com.mooregreatsoftware.gitprocess.lib.GitLib;
import com.mooregreatsoftware.gitprocess.lib.JgitPusher;
import javaslang.control.Either;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javaslang.control.Either.left;

/**
 * Brings the feature branch into the integration branch.
 *
 * @see #toMaster(boolean, boolean)
 */
@SuppressWarnings("ConstantConditions")
public class ToMaster {
    private static final Logger LOG = LoggerFactory.getLogger(ToMaster.class);

    private final GitLib gitLib;


    public ToMaster(GitLib gitLib) {
        this.gitLib = gitLib;
    }


    public @Nullable String toMaster(boolean doMerge,
                                     boolean localOnly) {
        final Either<String, Branch> eSync = Sync.sync(gitLib, doMerge, localOnly);
        if (eSync.isLeft()) return eSync.getLeft();

        final Either<String, JgitPusher.ThePushResult> ePushRes = pushToIntegration();
        if (ePushRes.isLeft()) return ePushRes.getLeft();

        final GitHubRepo gitHubRepo = GitHubRepo.builder().gitLib(gitLib).build();
        final PullRequests pullRequests = gitHubRepo.pullRequests();

        return null;
    }


    private Either<String, JgitPusher.ThePushResult> pushToIntegration() {
        final Branches branches = gitLib.branches();
        final Branch currentBranch = branches.currentBranch();
        if (currentBranch == null) return left("No branch is currently checked out");

        final Branch integrationBranch = branches.integrationBranch();
        if (integrationBranch == null) return left("No integration branch has been set");
        final String integrationBranchName = integrationBranch.simpleName();

        return gitLib.push(currentBranch, integrationBranchName, false);
    }

}
