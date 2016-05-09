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
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.StreamSupport;

import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.exceptionTranslator;
import static org.eclipse.jgit.api.ResetCommand.ResetType.HARD;
import static org.eclipse.jgit.lib.Constants.R_REFS;


/**
 * A branch in Git that guarantees that references are valid.
 */
@SuppressWarnings({"RedundantCast", "RedundantTypeArguments"})
public class JgitBranch extends AbstractBranch {
    private static final Logger LOG = LoggerFactory.getLogger(JgitBranch.class);


    private JgitBranch(JgitGitLib gitLib, Ref ref) {
        super(gitLib, ref);
    }


    /**
     * Creates a representation of an existing branch.
     *
     * @param gitLib the GitLib to use for commands
     * @param name   the name of the existing branch; if not fully-qualified (e.g., "refs/heads/master") it will do
     *               the translation
     * @throws IllegalArgumentException if it can't find the branch name
     */
    public static Branch of(JgitGitLib gitLib, String name) throws IllegalArgumentException {
        final String refName = name.startsWith(R_REFS) ? name : computeRefName(gitLib, name);

        if (!Repository.isValidRefName(refName)) {
            throw new IllegalArgumentException("\"" + name + "\" is not a valid branch name");
        }

        final Ref ref = Try.<@Nullable Ref>of(() -> gitLib.repository().findRef(refName)).
            getOrElseThrow(exceptionTranslator());

        if (ref == null)
            throw new IllegalArgumentException(name + " is not a known reference name in " + gitLib.repository().getAllRefs());

        return new JgitBranch(gitLib, ref);
    }


    /**
     * Does this branch contain every commit in "otherBranchName"?
     *
     * @param otherBranchName the name of the other branch
     * @return does the tip of the other branch must appear somewhere in the history of this branch?
     */
    @Override
    public boolean containsAllOf(String otherBranchName) {
        return containsAllOf(JgitBranch.of((JgitGitLib)gitLib, otherBranchName));
    }


    protected Git jgit() {
        return ((JgitGitLib)gitLib).jgit();
    }


    @Override
    public boolean contains(ObjectId oid) {
        LOG.debug("{}.contains({})", this, oid.abbreviate(7).name());
        return Try.of(() -> {
            final RevWalk walk = new RevWalk(((JgitGitLib)gitLib).repository());
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
        }).getOrElseThrow(ExecUtils.exceptionTranslator());
    }


    /**
     * Write a "control" reference to remember the current OID of the branch.
     *
     * @return the error message, or null if it went well
     */
    @Override
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    public @Nullable String recordLastSyncedAgainst() {
        LOG.debug("Writing sync control file");
        final Try<RefUpdate.Result> refUpdateRes = Try.of(() -> {
            final RefUpdate refUpdate = jgit().getRepository().updateRef("gitProcess/" + shortName());
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
    @Override
    public Either<String, @Nullable ObjectId> lastSyncedAgainst() {
        final Either<String, @Nullable ObjectId> idEither =
            Try.of(() -> jgit().getRepository().updateRef("gitProcess/" + shortName()).getOldObjectId()).
                toEither().
                mapLeft(Throwable::toString).
                flatMap(oid -> oid == null ? Either.right(null) : Either.right(oid));
        LOG.debug("Read sync control file for \"{}\": {}", shortName(), idEither.map(oid -> oid != null ? oid.abbreviate(7).name() : "no record").getOrElseGet(l -> l));
        return idEither;
    }


    /**
     * Do a hard reset on this branch to the given reference
     *
     * @param ref the reference to reset the working directory and index to
     * @return an error message, or null if it worked
     */
    @Override
    public @Nullable String resetHard(String ref) {
        return Try.of(() -> jgit().reset().setMode(HARD).setRef(ref).call()).
            <@Nullable String>map(r -> null).
            getOrElseGet(Throwable::toString);
    }


    @Override
    public ObjectId objectId() {
        return Try.of(() -> ((@NonNull ObjectId)jgit().getRepository().resolve(refName))).get();
    }


    @Override
    public String toString() {
        return "Branch{" + Repository.shortenRefName(name()) + "(" + objectId().abbreviate(7).name() + ")}";
    }


    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractBranch branch = (AbstractBranch)o;

        return gitLib.equals(branch.gitLib) && refName.equals(branch.refName);
    }


    @Override
    public int hashCode() {
        int result = gitLib.hashCode();
        result = 31 * result + refName.hashCode();
        return result;
    }

}
