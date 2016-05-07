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

import com.mooregreatsoftware.gitprocess.lib.Branch;
import com.mooregreatsoftware.gitprocess.lib.Branches;
import com.mooregreatsoftware.gitprocess.lib.GitLib;
import com.mooregreatsoftware.gitprocess.lib.Merger;
import com.mooregreatsoftware.gitprocess.lib.Pusher;
import com.mooregreatsoftware.gitprocess.lib.Rebaser;
import com.mooregreatsoftware.gitprocess.lib.SimpleFetchResult;
import javaslang.Function2;
import javaslang.control.Either;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mooregreatsoftware.gitprocess.process.Sync.Combiners.MERGER;
import static com.mooregreatsoftware.gitprocess.process.Sync.Combiners.REBASER;
import static java.util.function.Function.identity;
import static javaslang.control.Either.left;
import static javaslang.control.Either.right;

/**
 * Syncs local changes with the server.
 */
@SuppressWarnings({"ConstantConditions", "RedundantCast"})
public class Sync {
    private static final Logger LOG = LoggerFactory.getLogger(Sync.class);


    /**
     * Syncs local changes with the server.
     *
     * @return Left(error message) or Right(resulting branch)
     */
    public static Either<String, Branch> sync(GitLib gitLib,
                                              boolean doMerge,
                                              boolean localOnly) {
        String preCondErrMsg = verifySyncPreconditions(gitLib);
        if (preCondErrMsg != null) return left(preCondErrMsg);

        return doSync(gitLib, doMerge, localOnly);
    }


    /**
     * @return an error message, or null if there was no error
     */
    private static @Nullable String verifySyncPreconditions(GitLib gitLib) {
        final Branches branches = gitLib.branches();

        if (branches.currentBranch() == null)
            return "Not currently on a branch";

        if (branches.integrationBranch() == null)
            return "There is no integration branch";

        if (branches.onParking())
            return "You can not do a sync while on _parking_";

        if (gitLib.hasUncommittedChanges())
            return "You have uncommitted changes";

        return null;
    }


    /**
     * Syncs local changes with the server.
     *
     * @return Left(error message) or Right(resulting branch)
     */
    private static Either<String, Branch> doSync(GitLib gitLib, boolean doMerge, boolean localOnly) {
        return doMerge ?
            mergeSync(gitLib, localOnly) :
            rebaseSync(gitLib, localOnly);
    }


    /**
     * Merge with the integration branch then push to the server.
     *
     * @return Left(error message), Right(resulting branch)
     */
    private static Either<String, Branch> mergeSync(GitLib gitLib, boolean localOnly) {
        return combineSync(gitLib, localOnly, Combiners.of(MERGER));
    }


    private static Either<String, Branch> rebaseSync(GitLib gitLib, boolean localOnly) {
        return combineSync(gitLib, localOnly, Combiners.of(REBASER));
    }


    private static <T> Either<String, Branch> combineSync(GitLib gitLib,
                                                          boolean localOnly,
                                                          Combiner<T> combiner) {
        LOG.info("Doing {}-based sync", combiner.typeName());

        final Branches branches = gitLib.branches();

        final Branch integrationBranch = branches.integrationBranch();
        if (integrationBranch != null) {
            final Branch currentBranch = branches.currentBranch();
            if (currentBranch != null) {
                String fetchErrMsg = fetch(gitLib, localOnly);
                if (fetchErrMsg != null) return left(fetchErrMsg);

                final String integrationCombineResult = combineWith(gitLib, integrationBranch, combiner, currentBranch);
                if (integrationCombineResult == null) {
                    String pushErrMsg = pushCombinedBranch(gitLib, localOnly, combiner, currentBranch);
                    return pushErrMsg != null ? left(pushErrMsg) : right(currentBranch);
                }
                else {
                    return left(integrationCombineResult);
                }
            }
            else {
                return left("No branch is checked out");
            }
        }
        else {
            return left("No integration branch is set");
        }
    }


    /**
     * @return an error message, or null if there was no error
     */
    private static <T> @Nullable String pushCombinedBranch(GitLib gitLib, boolean localOnly,
                                                           Combiner<T> combiner, Branch currentBranch) {
        if (!localOnly) {
            final boolean hasRemotes = gitLib.remoteConfig().hasRemotes();
            if (hasRemotes) {
                return pushWithConflictResolution(gitLib, combiner, currentBranch);
            }
            else {
                LOG.debug("Not pushing to the server there are no remotes");
                return null;
            }
        }
        else {
            LOG.debug("Not pushing to the server because local-only was selected");
            return null;
        }
    }


    /**
     * @return an error message, or null if there was no error
     */
    private static @Nullable String fetch(GitLib gitLib, boolean localOnly) {
        if (!localOnly) {
            final boolean hasRemotes = gitLib.remoteConfig().hasRemotes();
            if (hasRemotes) {
                final Either<String, @Nullable SimpleFetchResult> fetch = gitLib.fetch();
                if (fetch.isLeft()) return fetch.getLeft();
            }
        }
        return null;
    }


    /**
     * Push to the remote, handling potential conflict caused by the remote changing since the last fetch
     *
     * @return an error message, or null if there was no error
     */
    private static <T> @Nullable String pushWithConflictResolution(GitLib gitLib, Combiner<T> combiner,
                                                                   Branch currentBranch) {
        final Either<String, Pusher> ePusher = handleRemoteChanged(gitLib, currentBranch, combiner);
        if (!ePusher.isLeft()) {
            final Pusher pusher = ePusher.get();
            return pusher.push().
                <@Nullable String>map(r -> r.success() ? null : r.toString()).
                fold(identity(), identity());
        }
        else {
            return ePusher.getLeft();
        }
    }


    /**
     * Uses "combiner" to combine the current branch with the base branch.
     *
     * @return an error message, or null if there was no error
     */
    private static <T> @Nullable String combineWith(GitLib gitLib,
                                                    Branch baseBranch,
                                                    Combiner<T> combiner,
                                                    Branch currentBranch) {
        final String combineType = combiner.typeName();
        if (LOG.isDebugEnabled()) {
            LOG.debug("{}{} {} with {}", combineType.substring(0, 1), combineType.substring(1),
                currentBranch, baseBranch);
        }

        final Either<String, T> eCombined = combiner.apply(gitLib, baseBranch);

        if (eCombined.isLeft()) return eCombined.getLeft();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Resulting OID from {} with {} is {}", combineType.toLowerCase(), baseBranch, char7(currentBranch.objectId()));
        }

        return null;
    }


    /**
     * @return Left(error message), Right(the Pusher to use)
     */
    @SuppressWarnings("PointlessBooleanExpression")
    private static <T> Either<String, Pusher> handleRemoteChanged(GitLib gitLib,
                                                                  Branch currentBranch,
                                                                  Combiner<T> combiner) {
        LOG.debug("Handle potential remote changed");

        final ObjectId remoteOID = currentBranch.remoteOID();

        final Either<String, @Nullable ObjectId> eLastSynced = currentBranch.lastSyncedAgainst();
        if (eLastSynced.isLeft()) return left(eLastSynced.getLeft());

        final ObjectId lastSyncedOID = eLastSynced.get();
        if (lastSyncedOID != null) { // deal with possible change
            if (remoteOID == null) {
                LOG.warn("The remote version of this branch has disappeared");
                return right(Pusher.create(gitLib, currentBranch, currentBranch.simpleName(), false, null, null));
            }

            if (remoteOID.equals(lastSyncedOID)) {
                LOG.debug("The last synced OID is the same as the remote OID: {}", char7(remoteOID));
                if (currentBranch.contains(remoteOID)) {
                    LOG.debug("\"{}\" contains {} so will do a normal fast-forward push", currentBranch.simpleName(), char7(remoteOID));
                    return right(Pusher.create(gitLib, currentBranch, currentBranch.simpleName(), false, null, null));
                }
                else {
                    LOG.debug("{} does not appear in the history of \"{}\", but since it was not remotely " +
                            "changed going to assume that the local copy is correct and will force push",
                        char7(remoteOID), currentBranch.simpleName());
                    return right(Pusher.create(gitLib, currentBranch, currentBranch.simpleName(), true, null, null));
                }
            }
            else {
                LOG.warn("The remote branch has changed since the last time this was " +
                        "synced ({} -> {}) so attempting to reconcile",
                    char7(lastSyncedOID), char7(remoteOID));

                // reconcile against the remote branch
                return reconcileWithRemoteBranch(gitLib, currentBranch, combiner);
            }
        }
        else {
            LOG.debug("This has not been synced before");

            if (remoteOID == null) {
                return right(Pusher.create(gitLib, currentBranch, currentBranch.simpleName(), false, null, null));
            }

            if (currentBranch.contains(remoteOID)) {
                LOG.debug("\"{}\" contains {} so will do a normal fast-forward push", currentBranch.simpleName(), char7(remoteOID));
                return right(Pusher.create(gitLib, currentBranch, currentBranch.simpleName(), false, null, null));
            }
            else {
                LOG.warn("The remote branch has changed since this branch was " +
                        "created (i.e., it does not contain the remote revision {}) so attempting to reconcile",
                    char7(remoteOID));

                // reconcile against the remote branch
                return reconcileWithRemoteBranch(gitLib, currentBranch, combiner);
            }
        }
    }


    private static String char7(ObjectId objectId) {
        return objectId.abbreviate(7).name();
    }


    private static <T> Either<String, Pusher> reconcileWithRemoteBranch(GitLib gitLib, Branch currentBranch, Combiner<T> combiner) {
        final String remoteBranchName = currentBranch.remoteBranchName();
        if (remoteBranchName == null) return left("Could not determine a remote branch name for " + currentBranch);

        final Branch remoteBranch = gitLib.branches().branch(remoteBranchName);
        final Either<String, T> eRemote = combiner.apply(gitLib, remoteBranch);
        if (eRemote.isLeft()) return left(eRemote.getLeft());

        // reapply to integration so that it can fast-forward with it
        final Either<String, T> eIntegration = combiner.apply(gitLib, gitLib.branches().integrationBranch());
        if (eIntegration.isLeft()) return left(eIntegration.getLeft());

        // if it had to "reconcile" with remote, that means that this may not be a simple fast-forward on the remote
        // branch, so use force-push
        return right(Pusher.create(gitLib, currentBranch, currentBranch.simpleName(), true, null, null));
    }


    // **********************************************************************
    //
    // HELPER CLASSES
    //
    // **********************************************************************

    interface Combiner<T> extends Function2<GitLib, Branch, Either<String, T>> {
        String typeName();
    }

    enum Combiners {
        REBASER(Rebaser::rebase, "rebase"),
        MERGER(Merger::merge, "merge");

        private final Function2<GitLib, Branch, Either<String, ?>> function;
        private final String typeName;


        Combiners(Function2<GitLib, Branch, Either<String, ?>> function, String typeName) {
            this.function = function;
            this.typeName = typeName;
        }


        @SuppressWarnings("unchecked")
        public static <T> Combiner<T> of(Combiners combiner) {
            // work-around for Java generics silliness
            return new Combiner<T>() {
                @Override
                public String typeName() {
                    return combiner.typeName;
                }


                @Override
                public Either<String, T> apply(GitLib gitLib, Branch branch) {
                    return (Either<String, T>)combiner.function.apply(gitLib, branch);
                }
            };
        }

    }

}
