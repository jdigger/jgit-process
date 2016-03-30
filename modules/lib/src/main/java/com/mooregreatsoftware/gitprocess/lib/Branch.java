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
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import static java.util.Optional.empty;
import static org.eclipse.jgit.api.ResetCommand.ResetType.HARD;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_REFS;
import static org.eclipse.jgit.lib.Constants.R_REMOTES;


/**
 * A branch in Git that guarantees that references are valid.
 */
public class Branch {
    private static final Logger LOG = LoggerFactory.getLogger(Branch.class);

    @Nonnull
    private final GitLib gitLib;

    @Nonnull
    private final String refName;

    private final boolean isRemote;


    private Branch(@Nonnull GitLib gitLib, @Nonnull Ref ref) {
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
    @Nonnull
    public static Branch of(@Nonnull GitLib gitLib, @Nonnull String name) {
        final String refName = name.startsWith(R_REFS) ? name : computeRefName(gitLib, name);

        if (!Repository.isValidRefName(refName)) {
            throw new IllegalArgumentException("\"" + name + "\" is not a valid branch name");
        }

        final Ref ref = ExecUtils.e(() -> gitLib.repository().findRef(refName));

        if (ref == null)
            throw new IllegalArgumentException(name + " is not a known reference name in " + gitLib.repository().getAllRefs());

        return new Branch(gitLib, ref);
    }


    @Nonnull
    private static String computeRefName(@Nonnull GitLib gitLib, @Nonnull String name) {
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


    @Nonnull
    private static String computePotentialRemoteRefName(@Nonnull GitLib gitLib, @Nonnull String name) {
        final String shortName = Repository.shortenRefName(name);
        final Optional<String> remoteName = remoteName(gitLib, shortName);
        return remoteName.map(rn -> R_REMOTES + name).orElse(R_HEADS + shortName);
    }


    /**
     * Computes the remote name embedded in the branch ref name. For example, "origin/master" -> "origin"
     *
     * @param gitLib    the library to use to get the list of remote names
     * @param shortName must be the "short name" of the branch. (i.e., no "refs/*")
     * @return empty() if there is not a valid remote name in the branch name
     */
    @Nonnull
    private static Optional<String> remoteName(@Nonnull GitLib gitLib, @Nonnull String shortName) {
        final int idx = shortName.indexOf("/");
        if (idx > 0) { // may be a remote branch
            final String firstPart = shortName.substring(0, idx);
            final boolean matchesARemote = StreamUtils.stream(gitLib.remoteConfig().remoteNames()).
                anyMatch(remote -> remote.equalsIgnoreCase(firstPart));

            if (matchesARemote) {
                return Optional.of(firstPart);
            }
            else {
                return empty();
            }
        }
        else {
            return empty();
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
    @Nonnull
    public String simpleName() {
        if (isRemote()) {
            final String shortName = shortName();
            final String remoteName = remoteName(gitLib, shortName).get();
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
    @Nonnull
    public Optional<String> remoteName() {
        return isRemote() ? remoteName(gitLib, shortName()) : empty();
    }


    /**
     * Is this a remote branch?
     */
    public boolean isRemote() {
        return isRemote;
    }


    /**
     * The "full qualified" name for the branch.
     * <p>
     * For example, instead of "origin/master" this returns "refs/remotes/origin/master"
     */
    @Nonnull
    public String name() {
        return refName;
    }


    /**
     * The "nice" name for the branch.
     * <p>
     * For example, instead of "refs/remotes/origin/master" this returns "origin/master"
     */
    @Nonnull
    public String shortName() {
        return Repository.shortenRefName(name());
    }


    @Nonnull
    public ObjectId objectId() {
        return Try.of(() -> gitLib.jgit().getRepository().resolve(refName)).get();
    }


    @Nonnull
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
    public boolean containsAllOf(@Nonnull Branch otherBranch) {
        LOG.debug("{}.containsAllOf({})", this, otherBranch);
        return contains(otherBranch.objectId());
    }


    public boolean contains(@Nonnull ObjectId oid) {
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


    @Nonnull
    public Either<String, Branch> checkout() {
        return gitLib.checkout(this).map(r -> this);
    }


    @Nonnull
    public Branch upstream(Branch upstream) {
        gitLib.branchConfig().setUpstream(this, upstream);
        return this;
    }


    @Nonnull
    public Optional<Branch> upstream() {
        return gitLib.branchConfig().getUpstream(this);
    }


    /**
     * Write a "control" reference to remember the current OID of the branch.
     *
     * @return the error message, or empty() if it went well
     */
    @Nonnull
    public Optional<String> recordLastSyncedAgainst() {
        LOG.debug("Writing sync control file");
        return Try.of(() -> {
            final RefUpdate refUpdate = gitLib.jgit().getRepository().updateRef("gitProcess/" + shortName());
            refUpdate.setNewObjectId(objectId());
            return refUpdate.forceUpdate();
        }).map(r -> Optional.<String>empty()).getOrElseGet(t -> Optional.of(t.toString()));
    }


    /**
     * Read a "control" reference of what this branch was last synced against.
     *
     * @return Left(error message) Right(the object ID, if it exists)
     */
    @Nonnull
    public Either<String, Optional<ObjectId>> lastSyncedAgainst() {
        // TODO: Support reading the legacy control file
        final Either<String, Optional<ObjectId>> idEither =
            Try.of(() -> gitLib.jgit().getRepository().updateRef("gitProcess/" + shortName()).getOldObjectId()).
                toEither().
                mapLeft(Throwable::toString).
                flatMap(oid -> oid == null ? Either.right(empty()) : Either.right(Optional.of(oid)));
        LOG.debug("Read sync control file for \"{}\": {}", shortName(), idEither.map(oid -> oid.map(id -> id.abbreviate(7).name()).orElse("no record")).getOrElseGet(l -> l));
        return idEither;
    }


    /**
     * Returns the previous remote sha ONLY IF it is not the same as the new remote sha; otherwise empty()
     */
    @Nonnull
    @SuppressWarnings("PointlessBooleanExpression")
    public Optional<ObjectId> previousRemoteOID() {
        if (gitLib.remoteConfig().hasRemotes() == false) return empty();

        final Either<String, Optional<ObjectId>> recordedIdEither = lastSyncedAgainst();
        final ObjectId oldSha = recordedIdEither.map(id -> id.orElseGet(() -> remoteOID().orElse(null))).getOrElse(() -> remoteOID().orElse(null));

        final Either<String, Optional<SimpleFetchResult>> fetch = gitLib.fetch();
        if (fetch.isLeft()) {
            LOG.warn(fetch.getLeft());
            return empty();
        }

        final ObjectId newSha = remoteOID().orElse(null);

        if (Objects.equals(oldSha, newSha)) {
            LOG.debug("The remote branch for \"{}\" has not changed since the last time ({})", shortName(), oldSha);
            return empty();
        }
        else {
            LOG.info("The remote branch for \"{}\" has changed since the last time: {} -> {}", shortName(), oldSha, newSha);
            return Optional.of(oldSha);
        }
    }


    /**
     * Has the remote changed since the last time {@link #recordLastSyncedAgainst()} was run, or the last time a fetch
     * was done if there is no record for last sync.
     */
    @SuppressWarnings("PointlessBooleanExpression")
    public boolean remoteHasChanged() {
        return previousRemoteOID().isPresent();
    }


    @Nonnull
    public Optional<ObjectId> remoteOID() {
        return remoteOIDSupplier().get();
    }


    @Nonnull
    @SuppressWarnings("PointlessBooleanExpression")
    public Optional<String> remoteBranchName() {
        if (gitLib.remoteConfig().hasRemotes() == false) return empty();

        return gitLib.remoteConfig().remoteBranchName(shortName());
    }


    @Nonnull
    @SuppressWarnings("PointlessBooleanExpression")
    private Supplier<Optional<ObjectId>> remoteOIDSupplier() {
        if (gitLib.remoteConfig().hasRemotes() == false) return Optional::empty;

        final Optional<String> optRemoteBranchName = gitLib.remoteConfig().remoteBranchName(shortName());
        if (optRemoteBranchName.isPresent() == false) return Optional::empty;
        final String remoteBranchName = optRemoteBranchName.get();

        return () -> gitLib.branches().branch(remoteBranchName).map(branch -> Optional.of(branch.objectId())).orElse(empty());
    }


    /**
     * Do a hard reset on this branch to the given reference
     *
     * @param ref the reference to reset the working directory and index to
     * @return an error message, or empty() if it worked
     */
    @Nonnull
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
    public boolean equals(Object o) {
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
