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
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

import static com.mooregreatsoftware.gitprocess.process.Sync.Combiners.MERGER;
import static com.mooregreatsoftware.gitprocess.process.Sync.Combiners.REBASER;
import static javaslang.control.Either.left;
import static javaslang.control.Either.right;

/**
 * Syncs local changes with the server.
 */
@SuppressWarnings("ConstantConditions")
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
    @Nonnull
    public static Either<String, Branch> sync(@Nonnull GitLib gitLib,
                                              boolean doMerge,
                                              boolean localOnly) {
        if (gitLib == null) throw new IllegalArgumentException("gitLib == null");

        final Branches branches = gitLib.branches();

        if (!branches.currentBranch().isPresent()) {
            return left("Not currently on a branch");
        }

        if (!branches.integrationBranch().isPresent()) {
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
    @Nonnull
    private static Either<String, Branch> doSync(@Nonnull GitLib gitLib, boolean doMerge, boolean localOnly) {
        return doMerge ?
            mergeSync(gitLib, localOnly) :
            rebaseSync(gitLib, localOnly);
    }


    /**
     * Merge with the integration branch then push to the server.
     *
     * @return Left(error message) or Right(resulting branch)
     */
    @Nonnull
    private static Either<String, Branch> mergeSync(@Nonnull GitLib gitLib, boolean localOnly) {
        return combineSync(gitLib, "merge", localOnly, Combiners.of(MERGER));
    }


    @Nonnull
    private static Either<String, Branch> rebaseSync(@Nonnull GitLib gitLib, boolean localOnly) {
        return combineSync(gitLib, "rebase", localOnly, Combiners.of(REBASER));
    }


    @Nonnull
    private static <T> Either<String, Branch> combineSync(@Nonnull GitLib gitLib,
                                                          @Nonnull String combineType,
                                                          boolean localOnly,
                                                          Combiner<T> combineWith) {
        LOG.info("Doing {}-based sync", combineType);

        final Branches branches = gitLib.branches();
        final Branch integrationBranch = branches.integrationBranch().get();
        final Branch currentBranch = branches.currentBranch().get();

        final boolean hasRemotes = gitLib.remoteConfig().hasRemotes();
        if (hasRemotes) {
            final Either<String, Optional<SimpleFetchResult>> fetch = gitLib.fetch();
            if (fetch.isLeft()) return left(fetch.getLeft());
        }

        final String integrationCombineResult = combineWith(gitLib, integrationBranch, combineType, combineWith);
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

        return ePusher.get().
            push().
            flatMap(r -> r.success() ? right(currentBranch) : left(r.toString()));
    }


    @Nullable
    private static <T> String combineWith(@Nonnull GitLib gitLib,
                                          @Nonnull Branch integrationBranch,
                                          @Nonnull String combineType,
                                          @Nonnull Combiner<T> combiner) {
        final Branch currentBranch = gitLib.branches().currentBranch().get();
        if (LOG.isDebugEnabled())
            LOG.debug("{}{} {} with {}", combineType.substring(0, 1), combineType.substring(1), currentBranch, integrationBranch);
        final Either<String, T> rebaseEither = combiner.apply(gitLib, integrationBranch);

        if (rebaseEither.isLeft()) return rebaseEither.getLeft();

        LOG.debug("Resulting OID from {} with {} is {}", combineType.toLowerCase(), integrationBranch, currentBranch.objectId().abbreviate(7).name());

        return null;
    }


    /**
     * @return error message if there are problems
     */
    @Nonnull
    @SuppressWarnings("PointlessBooleanExpression")
    private static <T> Either<String, Pusher> handleRemoteChanged(@Nonnull GitLib gitLib,
                                                                  @Nonnull Branch currentBranch,
                                                                  @Nonnull Combiner<T> combiner) {
        LOG.debug("Handle potential remote changed");

        final Optional<ObjectId> oRemoteOID = currentBranch.remoteOID();

        final Either<String, Optional<ObjectId>> eLastSynced = currentBranch.lastSyncedAgainst();
        if (eLastSynced.isLeft()) return left(eLastSynced.getLeft());

        final Optional<ObjectId> oLastSynced = eLastSynced.get();
        if (oLastSynced.isPresent()) { // deal with possible change
            if (oRemoteOID.isPresent() == false) {
                LOG.warn("The remote version of this branch has disappeared");
                return right(Pusher.create(gitLib, currentBranch, currentBranch.simpleName()));
            }

            final ObjectId remoteOID = oRemoteOID.get();
            final ObjectId lastSyncedOID = oLastSynced.get();

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

            if (oRemoteOID.isPresent() == false) {
                return right(Pusher.create(gitLib, currentBranch, currentBranch.simpleName()));
            }

            final ObjectId remoteOID = oRemoteOID.get();

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


    @Nonnull
    private static <T> Either<String, Pusher> reconcileWithRemoteBranch(@Nonnull GitLib gitLib, @Nonnull Branch currentBranch, @Nonnull Combiner<T> combiner) {
        final String remoteBranchName = currentBranch.remoteBranchName().get();
        final Branch remoteBranch = gitLib.branches().branch(remoteBranchName).get();
        final Either<String, T> eRemote = combiner.apply(gitLib, remoteBranch);
        if (eRemote.isLeft()) return left(eRemote.getLeft());

        // reapply to integration so that it can fast-forward with it
        final Either<String, T> eIntegration = combiner.apply(gitLib, gitLib.branches().integrationBranch().get());
        if (eIntegration.isLeft()) return left(eIntegration.getLeft());

        // if it had to "reconcile" with remote, that means that this may not be a simple fast-forward on the remote
        // branch, so use force-push
        return right(Pusher.create(gitLib, currentBranch, currentBranch.simpleName(), true, null, null));
    }

}
