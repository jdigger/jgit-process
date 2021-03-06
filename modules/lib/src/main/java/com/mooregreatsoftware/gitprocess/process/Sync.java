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
import static javaslang.control.Either.left;
import static javaslang.control.Either.right;

/**
 * Syncs local changes with the server.
 */
@SuppressWarnings({"ConstantConditions", "RedundantCast"})
public class Sync {
    private static final Logger LOG = LoggerFactory.getLogger(Sync.class);

    /**
     * Simplify Java generics complexity
     */
    interface Combiner<T> extends Function2<GitLib, Branch, Either<String, T>> {
    }

    enum Combiners implements Function2<GitLib, Branch, Either<String, ?>> {
        REBASER(Rebaser::rebase),
        MERGER(Merger::merge);

        private final Function2<GitLib, Branch, Either<String, ?>> function;


        Combiners(Function2<GitLib, Branch, Either<String, ?>> function) {
            this.function = function;
        }


        @Override
        public Either<String, ?> apply(GitLib gitLib, Branch branch) {
            return function.apply(gitLib, branch);
        }


        @SuppressWarnings("unchecked")
        public static <T> Combiner<T> of(Combiners combiner) {
            // work-around for Java generics silliness
            return (g, b) -> (Either<String, T>)combiner.apply(g, b);
        }

    }


    /**
     * Syncs local changes with the server.
     *
     * @return Left(error message) or Right(resulting branch)
     */
    public static Either<String, Branch> sync(GitLib gitLib,
                                              boolean doMerge,
                                              boolean localOnly) {
        if (gitLib == null) throw new IllegalArgumentException("gitLib == null");

        final Branches branches = gitLib.branches();

        if (branches.currentBranch() == null) {
            return left("Not currently on a branch");
        }

        if (branches.integrationBranch() == null) {
            return left("There is no integration branch");
        }

        if (branches.onParking()) {
            return left("You can not do a sync while on _parking_");
        }

        if (gitLib.hasUncommittedChanges()) {
            return left("You have uncommitted changes");
        }

        return doSync(gitLib, doMerge, localOnly);
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
        return combineSync(gitLib, "merge", localOnly, Combiners.of(MERGER));
    }


    private static Either<String, Branch> rebaseSync(GitLib gitLib, boolean localOnly) {
        return combineSync(gitLib, "rebase", localOnly, Combiners.of(REBASER));
    }


    private static <T> Either<String, Branch> combineSync(GitLib gitLib,
                                                          String combineType,
                                                          boolean localOnly,
                                                          Combiner<T> combineWith) {
        LOG.info("Doing {}-based sync", combineType);

        final Branches branches = gitLib.branches();

        final Branch integrationBranch = branches.integrationBranch();
        if (integrationBranch == null) return left("No integration branch is set");

        final Branch currentBranch = branches.currentBranch();
        if (currentBranch == null) return left("No branch is checked out");

        final boolean hasRemotes = gitLib.remoteConfig().hasRemotes();
        if (hasRemotes) {
            final Either<String, @Nullable SimpleFetchResult> fetch = gitLib.fetch();
            if (fetch.isLeft()) return left(fetch.getLeft());
        }

        final String integrationCombineResult = combineWith(gitLib, integrationBranch, combineType, combineWith, currentBranch);
        if (integrationCombineResult != null) return left(integrationCombineResult);

        if (localOnly) {
            LOG.debug("Not pushing to the server because local-only was selected");
            return right(currentBranch);
        }

        if (!hasRemotes) {
            LOG.debug("Not pushing to the server there are no remotes");
            return right(currentBranch);
        }

        final Either<String, Pusher> ePusher = handleRemoteChanged(gitLib, currentBranch, combineWith);
        if (ePusher.isLeft()) return left(ePusher.getLeft());

        final Pusher pusher = ePusher.get();
        return pusher.push().
            flatMap(r -> r.success() ? Either.<String, Branch>right(currentBranch) : left(r.toString()));
    }


    @Nullable
    private static <T> String combineWith(GitLib gitLib,
                                          Branch integrationBranch,
                                          String combineType,
                                          Combiner<T> combiner,
                                          Branch currentBranch) {
        if (LOG.isDebugEnabled())
            LOG.debug("{}{} {} with {}", combineType.substring(0, 1), combineType.substring(1), currentBranch, integrationBranch);

        final Either<String, T> rebaseEither = combiner.apply(gitLib, integrationBranch);

        if (rebaseEither.isLeft()) return rebaseEither.getLeft();

        LOG.debug("Resulting OID from {} with {} is {}", combineType.toLowerCase(), integrationBranch, currentBranch.objectId().abbreviate(7).name());

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
                return right(Pusher.create(gitLib, currentBranch, currentBranch.simpleName()));
            }

            if (remoteOID.equals(lastSyncedOID)) {
                LOG.debug("The last synced OID is the same as the remote OID: {}", remoteOID.abbreviate(7).name());
                if (currentBranch.contains(remoteOID)) {
                    LOG.debug("\"{}\" contains {} so will do a normal fast-forward push", currentBranch.simpleName(), remoteOID.abbreviate(7).name());
                    return right(Pusher.create(gitLib, currentBranch, currentBranch.simpleName()));
                }
                else {
                    LOG.debug("{} does not appear in the history of \"{}\", but since it was not remotely " +
                            "changed going to assume that the local copy is correct and will force push",
                        remoteOID.abbreviate(7).name(), currentBranch.simpleName());
                    return right(Pusher.create(gitLib, currentBranch, currentBranch.simpleName(), true, null, null));
                }
            }
            else {
                LOG.warn("The remote branch has changed since the last time this was " +
                        "synced ({} -> {}) so attempting to reconcile",
                    lastSyncedOID.abbreviate(7).name(), remoteOID.abbreviate(7).name());

                // reconcile against the remote branch
                return reconcileWithRemoteBranch(gitLib, currentBranch, combiner);
            }
        }
        else {
            LOG.debug("This has not been synced before");

            if (remoteOID == null) {
                return right(Pusher.create(gitLib, currentBranch, currentBranch.simpleName()));
            }

            if (currentBranch.contains(remoteOID)) {
                LOG.debug("\"{}\" contains {} so will do a normal fast-forward push", currentBranch.simpleName(), remoteOID.abbreviate(7).name());
                return right(Pusher.create(gitLib, currentBranch, currentBranch.simpleName()));
            }
            else {
                LOG.warn("The remote branch has changed since this branch was " +
                        "created (i.e., it does not contain the remote revision {}) so attempting to reconcile",
                    remoteOID.abbreviate(7).name());

                // reconcile against the remote branch
                return reconcileWithRemoteBranch(gitLib, currentBranch, combiner);
            }
        }
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

}
