package com.mooregreatsoftware.gitprocess.lib;

import com.mooregreatsoftware.gitprocess.config.RemoteConfig;
import javaslang.control.Either;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_REMOTES;

@SuppressWarnings({"RedundantCast", "RedundantTypeArguments"})
public abstract class AbstractBranch implements Branch {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractBranch.class);

    protected final GitLib gitLib;
    protected final String refName;
    protected final boolean isRemote;


    public AbstractBranch(GitLib gitLib, Ref ref) {
        this.gitLib = gitLib;
        this.refName = ref.getName();
        this.isRemote = ref.getName().startsWith(R_REMOTES);
    }


    protected static String computeRefName(GitLib gitLib, String name) {
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
    private static String computePotentialRemoteRefName(GitLib gitLib, String name) {
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
    private static @Nullable String remoteName(GitLib gitLib, String shortName) {
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
    @Override
    public String simpleName() {
        if (isRemote()) {
            final String remoteName = remoteName(gitLib, shortName());
            if (remoteName == null) throw new IllegalStateException("remoteName == null");
            return shortName().substring(remoteName.length() + 1);
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
     * @return null if there's no remote part to the name matching an existing remote name
     * @see RemoteConfig#remoteNames()
     */
    @Override
    public @Nullable String remoteName() {
        return isRemote() ? remoteName(gitLib, shortName()) : null;
    }


    /**
     * Is this a remote branch?
     */
    @Pure
    @Override
    public boolean isRemote() {
        return isRemote;
    }


    /**
     * The "full qualified" name for the branch.
     * <p>
     * For example, instead of "origin/master" this returns "refs/remotes/origin/master"
     */
    @Override
    public String name() {
        return refName;
    }


    /**
     * The "nice" name for the branch.
     * <p>
     * For example, instead of "refs/remotes/origin/master" this returns "origin/master"
     */
    @Override
    public String shortName() {
        return Repository.shortenRefName(name());
    }


    /**
     * The 7-digit SHA-1 for the head of the branch.
     */
    @Override
    @SuppressWarnings("unused")
    public String sha() {
        return objectId().abbreviate(7).name();
    }


    /**
     * Does this branch contain every commit in "otherBranch"?
     *
     * @param otherBranch the name of the other branch
     * @return does the tip of the other branch must appear somewhere in the history of this branch?
     */
    @Override
    public boolean containsAllOf(Branch otherBranch) {
        LOG.debug("{}.containsAllOf({})", this, otherBranch);
        return contains(otherBranch.objectId());
    }


    @Override
    public Either<String, Branch> checkout() {
        return gitLib.checkout(this).map(r -> this);
    }


    @Override
    public Branch upstream(Branch upstream) {
        gitLib.branchConfig().setUpstream(this, upstream);
        return this;
    }


    @Override
    @Nullable
    public Branch upstream() {
        return gitLib.branchConfig().getUpstream(this);
    }


    /**
     * Returns the previous remote sha ONLY IF it is not the same as the new remote sha; otherwise null
     */
    @Override
    public @Nullable ObjectId previousRemoteOID() {
        if (!gitLib.remoteConfig().hasRemotes()) return null;

        final Either<String, @Nullable ObjectId> recordedIdEither = lastSyncedAgainst();
        final ObjectId oldSha = recordedIdEither.<@Nullable ObjectId>map(id -> id != null ? id : remoteOID()).getOrElse(this::remoteOID);

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


    @Override
    public @Nullable ObjectId remoteOID() {
        if (!gitLib.remoteConfig().hasRemotes()) return null;

        final String remoteBranchName = gitLib.remoteConfig().remoteBranchName(shortName());
        if (remoteBranchName == null) return null;

        final Branch branch = gitLib.branches().branch((@NonNull String)remoteBranchName);
        return branch != null ? branch.objectId() : null;
    }


    @Override
    public @Nullable String remoteBranchName() {
        if (!gitLib.remoteConfig().hasRemotes()) return null;

        return gitLib.remoteConfig().remoteBranchName(shortName());
    }

}
