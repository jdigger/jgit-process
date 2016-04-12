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

import com.mooregreatsoftware.gitprocess.config.RemoteConfig;
import javaslang.control.Either;
import javaslang.control.Try;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import static org.eclipse.jgit.api.ResetCommand.ResetType.HARD;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_REFS;
import static org.eclipse.jgit.lib.Constants.R_REMOTES;

//import javax.annotation.Nonnull;


/**
 * A branch in Git that guarantees that references are valid.
 */
@SuppressWarnings("RedundantCast")
public class Branch {
    private static final Logger LOG = LoggerFactory.getLogger(Branch.class);

    private final GitLib gitLib;
    private final String refName;
    private final boolean isRemote;


    private Branch(GitLib gitLib, Ref ref) {
        this.gitLib = gitLib;
        this.refName = ref.getName();

        isRemote = ref.getName().startsWith(R_REMOTES);
    }


    /**
     * Creates a representation of an existing branch.
     *
     * @param gitLib the GitLib to use for commands
     * @param name   the name of the existing branch; if not fully-qualified (e.g., "refs/heads/master") it will do
     *               the translation
     * @throws IllegalArgumentException if it can't find the branch name
     */
    public static Branch of(GitLib gitLib, String name) {
        final String refName = name.startsWith(R_REFS) ? name : computeRefName(gitLib, name);

        if (!Repository.isValidRefName(refName)) {
            throw new IllegalArgumentException("\"" + name + "\" is not a valid branch name");
        }

        final Ref ref = ExecUtils.<@Nullable Ref>e(() -> gitLib.repository().findRef(refName));

        if (ref == null)
            throw new IllegalArgumentException(name + " is not a known reference name in " + gitLib.repository().getAllRefs());

        return new Branch(gitLib, ref);
    }


    @NonNull
    private static String computeRefName(@NonNull GitLib gitLib, @NonNull String name) {
        final String refName;
        if (gitLib.remoteConfig().hasRemotes()) {
            refName = computePotentialRemoteRefName(gitLib, name);
        }
        else {
            // assuming it's a local name since there are no remotes
            refName = R_HEADS + name;
        }
        return refName;
    }


    @Pure
    @NonNull
    private static String computePotentialRemoteRefName(@NonNull GitLib gitLib, @NonNull String name) {
        final String shortName = Repository.shortenRefName(name);
        final String remoteName = remoteName(gitLib, shortName);
        return remoteName != null ? R_REMOTES + name : R_HEADS + shortName;
    }


    /**
     * Computes the remote name embedded in the branch ref name. For example, "origin/master" -> "origin"
     *
     * @param gitLib    the library to use to get the list of remote names
     * @param shortName must be the "short name" of the branch. (i.e., no "refs/*")
     * @return null if there is not a valid remote name in the branch name
     */
    @Pure
    private static @Nullable String remoteName(@NonNull GitLib gitLib, @NonNull String shortName) {
        final int idx = shortName.indexOf("/");
        if (idx > 0) { // may be a remote branch
            final String firstPart = shortName.substring(0, idx);
            final boolean matchesARemote = StreamUtils.stream(gitLib.remoteConfig().remoteNames()).
                anyMatch(remote -> remote.equalsIgnoreCase(firstPart));

            if (matchesARemote) {
                return firstPart;
            }
            else {
                return null;
            }
        }
        else {
            return null;
        }
    }


    /**
     * Return just the "branch" part of the short name.
     * <p>
     * Examples:
     * <ul>
     * <li>"origin/master" -> "master"</li>
     * <li>"master" -> "master"</li>
     * <li>"not_a_remote/master" -> "not_a_remote/master"</li>
     * </ul>
     * <p>
     * This is the inverse of {@link #remoteName()}
     *
     * @see #remoteName()
     */
    @Pure
    @NonNull
    public String simpleName() {
        if (isRemote()) {
            final String shortName = shortName();
            final String remoteName = (@NonNull String)remoteName(gitLib, shortName);
            return shortName.substring(remoteName.length() + 1);
        }
        else {
            return shortName();
        }
    }


    /**
     * Return just the "remote" part of the short name. e.g., "origin/master" -> "origin".
     * If the part that is in the "remote" position of the short name is not *actually* a remote name,
     * it is not returned. e.g., "not_a_remote/master" -> empty()
     *
     * @return empty() if there's no remote part to the name matching an existing remote name
     * @see RemoteConfig#remoteNames()
     */
    @NonNull
    public Optional<@NonNull String> remoteName() {
        return isRemote() ? Optional.ofNullable(remoteName(gitLib, shortName())) : Optional.<String>empty();
    }


    /**
     * Is this a remote branch?
     */
    @Pure
    public boolean isRemote() {
        return isRemote;
    }


    /**
     * The "full qualified" name for the branch.
     * <p>
     * For example, instead of "origin/master" this returns "refs/remotes/origin/master"
     */
    @NonNull
    public String name() {
        return refName;
    }


    /**
     * The "nice" name for the branch.
     * <p>
     * For example, instead of "refs/remotes/origin/master" this returns "origin/master"
     */
    @NonNull
    public String shortName() {
        return Repository.shortenRefName(name());
    }


    public @NonNull ObjectId objectId() {
        return Try.of(() -> ((@NonNull ObjectId)gitLib.jgit().getRepository().resolve(refName))).get();
    }


    @NonNull
    public String sha() {
        return objectId().abbreviate(7).name();
    }


    /**
     * Does this branch contain every commit in "otherBranchName"?
     *
     * @param otherBranchName the name of the other branch
     * @return does the tip of the other branch must appear somewhere in the history of this branch?
     */
    public boolean containsAllOf(String otherBranchName) {
        return containsAllOf(Branch.of(gitLib, otherBranchName));
    }


    /**
     * Does this branch contain every commit in "otherBranch"?
     *
     * @param otherBranch the name of the other branch
     * @return does the tip of the other branch must appear somewhere in the history of this branch?
     */
    public boolean containsAllOf(@NonNull Branch otherBranch) {
        LOG.debug("{}.containsAllOf({})", this, otherBranch);
        return contains(otherBranch.objectId());
    }


    public boolean contains(@NonNull ObjectId oid) {
        LOG.debug("{}.contains({})", this, oid.abbreviate(7).name());
        return Try.of(() -> {
            final RevWalk walk = new RevWalk(gitLib.repository());
            try {
                walk.setRetainBody(false);
                final RevCommit topOfThisBranch = walk.parseCommit(objectId());
                walk.markStart(topOfThisBranch);
                return StreamSupport.stream(walk.spliterator(), false).
                    anyMatch(commit -> oid.equals(commit.getId()));
            }
            finally {
                walk.dispose();
            }
        }).getOrElseThrow((Function<Throwable, IllegalStateException>)IllegalStateException::new);
    }


    @NonNull
    public Either<String, Branch> checkout() {
        return gitLib.checkout(this).map(r -> this);
    }


    @NonNull
    public Branch upstream(Branch upstream) {
        gitLib.branchConfig().setUpstream(this, upstream);
        return this;
    }


    @Nullable
    public Branch upstream() {
        return gitLib.branchConfig().getUpstream(this);
    }


    /**
     * Write a "control" reference to remember the current OID of the branch.
     *
     * @return the error message, or null if it went well
     */

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    public @Nullable String recordLastSyncedAgainst() {
        LOG.debug("Writing sync control file");
        final Try<RefUpdate.Result> refUpdateRes = Try.of(() -> {
            final RefUpdate refUpdate = gitLib.jgit().getRepository().updateRef("gitProcess/" + shortName());
            refUpdate.setNewObjectId(objectId());
            return refUpdate.forceUpdate();
        });
        return refUpdateRes.isFailure() ? refUpdateRes.getCause().toString() : null;
    }


    /**
     * Read a "control" reference of what this branch was last synced against.
     *
     * @return Left(error message) Right(the object ID, if it exists)
     */
    @NonNull
    public Either<String, @Nullable ObjectId> lastSyncedAgainst() {
        // TODO: Support reading the legacy control file
        final Either<String, @Nullable ObjectId> idEither =
            Try.of(() -> gitLib.jgit().getRepository().updateRef("gitProcess/" + shortName()).getOldObjectId()).
                toEither().
                mapLeft(Throwable::toString).
                flatMap(oid -> oid == null ? Either.right(null) : Either.right(oid));
        LOG.debug("Read sync control file for \"{}\": {}", shortName(), idEither.map(oid -> oid != null ? oid.abbreviate(7).name() : "no record").getOrElseGet(l -> l));
        return idEither;
    }


    /**
     * Returns the previous remote sha ONLY IF it is not the same as the new remote sha; otherwise null
     */
    @SuppressWarnings("PointlessBooleanExpression")
    @javax.annotation.Nullable
    public ObjectId previousRemoteOID() {
        if (gitLib.remoteConfig().hasRemotes() == false) return null;

        final Either<String, @Nullable ObjectId> recordedIdEither = lastSyncedAgainst();
        final @Nullable ObjectId oldSha = recordedIdEither.<@Nullable ObjectId>map(id -> id != null ? id : remoteOID()).getOrElse(this::remoteOID);

        final Either<String, @Nullable SimpleFetchResult> fetch = gitLib.fetch();
        if (fetch.isLeft()) {
            LOG.warn(fetch.getLeft());
            return null;
        }

        final ObjectId newSha = remoteOID();

        if (Objects.equals(oldSha, newSha)) {
            if (oldSha == null)
                LOG.debug("The remote branch was never set");
            else
                LOG.debug("The remote branch for \"{}\" has not changed since the last time ({})", shortName(), oldSha);
            return null;
        }
        else {
            if (oldSha == null)
                LOG.info("The remote branch for \"{}\" has been created and this was never synced", shortName());
            else if (newSha == null)
                LOG.info("The remote branch for \"{}\" has disappeared since the last time: {}", shortName(), oldSha);
            else
                LOG.info("The remote branch for \"{}\" has changed since the last time: {} -> {}", shortName(), oldSha, newSha);
            return oldSha;
        }
    }


    /**
     * Has the remote changed since the last time {@link #recordLastSyncedAgainst()} was run, or the last time a fetch
     * was done if there is no record for last sync.
     */
    public boolean remoteHasChanged() {
        return previousRemoteOID() != null;
    }


    @Nullable
    public ObjectId remoteOID() {
        return remoteOIDSupplier().get();
    }


    @Nullable
    @SuppressWarnings("PointlessBooleanExpression")
    public String remoteBranchName() {
        if (gitLib.remoteConfig().hasRemotes() == false) return null;

        return gitLib.remoteConfig().remoteBranchName(shortName());
    }


    @NonNull
    @SuppressWarnings("PointlessBooleanExpression")
    private Supplier<@Nullable ObjectId> remoteOIDSupplier() {
        if (gitLib.remoteConfig().hasRemotes() == false) return () -> null;

        final String remoteBranchName = gitLib.remoteConfig().remoteBranchName(shortName());
        if (remoteBranchName == null) return () -> null;

        return () -> {
            final Branch branch = gitLib.branches().branch((@NonNull String)remoteBranchName);
            return branch != null ? branch.objectId() : null;
        };
    }


    /**
     * Do a hard reset on this branch to the given reference
     *
     * @param ref the reference to reset the working directory and index to
     * @return an error message, or empty() if it worked
     */
    @NonNull
    public Optional<String> resetHard(String ref) {
        return Try.of(() ->
                gitLib.jgit().reset().setMode(HARD).setRef(ref).call()
        ).map(r -> Optional.<String>empty()).getOrElseGet(t -> Optional.of(t.toString()));
    }


    @Override
    public String toString() {
        return "Branch{" + Repository.shortenRefName(name()) + "(" + objectId().abbreviate(7).name() + ")}";
    }


    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Branch branch = (Branch)o;

        return gitLib.equals(branch.gitLib) && refName.equals(branch.refName);
    }


    @Override
    public int hashCode() {
        int result = gitLib.hashCode();
        result = 31 * result + refName.hashCode();
        return result;
    }

}
