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

import javaslang.control.Either;
import javaslang.control.Try;
import org.eclipse.jgit.api.RebaseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

import static javaslang.control.Either.left;
import static javaslang.control.Either.right;

public class Rebaser {
    private static final Logger LOG = LoggerFactory.getLogger(Rebaser.class);


    public static Either<String, SuccessfulRebase> rebase(GitLib gitLib, Branch baseBranch) {
        final Branch currentBranch = gitLib.branches().currentBranch();

        if (currentBranch == null) return left("No branch is currently checked out");

        LOG.debug("Rebasing {} with {}", currentBranch, baseBranch.shortName());

        final Either<Throwable, RebaseResult> rebaseResults =
            Try.of(() -> gitLib.jgit().rebase().setUpstream(baseBranch.objectId()).call()).toEither();

        if (rebaseResults.isLeft()) return left(rebaseResults.toString());

        final RebaseResult rebaseResult = rebaseResults.get();

        return rebaseResult.getStatus().isSuccessful() ? right(new SuccessfulRebase(rebaseResult)) : left(statusToErrorMessage(rebaseResult));
    }


    /**
     * All this is from the comments. It's accessible in MergeResults.Status, but not here :-(
     */
    protected static String statusToErrorMessage(RebaseResult rebaseResult) {
        final RebaseResult.Status status = rebaseResult.getStatus();
        switch (status) {
            case OK:
                return "OK; Rebase was successful, HEAD points to the new commit";
            case STOPPED:
                return "Stopped due to a conflict; must either abort or resolve or skip";
            case ABORTED:
                return "Aborted; the original HEAD was restored";
            case EDIT:
                return "Stopped for editing in the context of an interactive rebase";
            case FAILED:
                return "Failed; the original HEAD was restored";
            case UNCOMMITTED_CHANGES:
                return "The repository contains uncommitted changes and the rebase is not a fast-forward";
            case CONFLICTS:
                return "Conflicts: checkout of target HEAD failed";
            case UP_TO_DATE:
                return "Already up-to-date";
            case FAST_FORWARD:
                return "Fast-forward, HEAD points to the new commit";
            default:
                return status.toString();
        }
    }


    public static class SuccessfulRebase {
        @Nonnull
        private final RebaseResult rebaseResult;


        public SuccessfulRebase(@Nonnull RebaseResult rebaseResult) {
            this.rebaseResult = rebaseResult;
        }


        public String statusMsg() {
            return statusToErrorMessage(rebaseResult);
        }


        public String toString() {
            return "SuccessfulRebase{" + statusMsg() + '}';
        }

    }

}
