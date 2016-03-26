package com.mooregreatsoftware.gitprocess;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static com.mooregreatsoftware.gitprocess.ExecUtils.e;
import static com.mooregreatsoftware.gitprocess.StreamUtils.stream;
import static java.util.Optional.empty;
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
    private final Ref ref;

    private final boolean isRemote;


    private Branch(@Nonnull GitLib gitLib, @Nonnull Ref ref) {
        this.gitLib = gitLib;
        this.ref = ref;

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

        final Ref ref = e(() -> gitLib.repository().findRef(refName));

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
            final boolean matchesARemote = stream(gitLib.remoteConfig().remoteNames()).
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


    @Nonnull
    public Optional<String> remoteName() {
        return isRemote() ? remoteName(gitLib, shortName()) : empty();
    }


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
        return ref().getName();
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
    public Ref ref() {
        return ref;
    }


    @Nonnull
    public ObjectId objectId() {
        return ref().getTarget().getLeaf().getObjectId();
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
        return e(() -> {
            final RevWalk walk = new RevWalk(gitLib.repository());
            try {
                walk.setRetainBody(false);
                final RevCommit topOfThisBranch = walk.parseCommit(objectId());
                final ObjectId topOfOtherBranch = walk.parseCommit(otherBranch.objectId()).getId();
                walk.markStart(topOfThisBranch);
                return StreamSupport.stream(walk.spliterator(), false).
                    anyMatch(tcommit -> topOfOtherBranch.equals(tcommit.getId()));
            }
            finally {
                walk.dispose();
            }
        });
    }


    @Nonnull
    public Branch checkout() {
        gitLib.checkout(this);
        return this;
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


    @Override
    public String toString() {
        return "Branch{" + Repository.shortenRefName(name()) + '}';
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Branch branch = (Branch)o;

        return gitLib.equals(branch.gitLib) && ref.equals(branch.ref);
    }


    @Override
    public int hashCode() {
        int result = gitLib.hashCode();
        result = 31 * result + ref.hashCode();
        return result;
    }

}
