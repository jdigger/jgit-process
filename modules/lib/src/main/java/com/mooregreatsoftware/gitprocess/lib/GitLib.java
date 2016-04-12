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

import com.mooregreatsoftware.gitprocess.config.BranchConfig;
import com.mooregreatsoftware.gitprocess.config.GeneralConfig;
import com.mooregreatsoftware.gitprocess.config.RemoteConfig;
import com.mooregreatsoftware.gitprocess.lib.Pusher.ThePushResult;
import com.mooregreatsoftware.gitprocess.lib.config.StoredBranchConfig;
import com.mooregreatsoftware.gitprocess.lib.config.StoredGeneralConfig;
import com.mooregreatsoftware.gitprocess.lib.config.StoredRemoteConfig;
import com.mooregreatsoftware.gitprocess.transport.GitTransportConfigCallback;
import javaslang.control.Either;
import javaslang.control.Try;
import javaslang.control.Try.CheckedRunnable;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;

import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.e;
import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.v;
import static javaslang.control.Either.left;

/**
 * The central launch-point for interacting with Git.
 */
public class GitLib implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(GitLib.class);

    private final Git jgit;

    @MonotonicNonNull
    private Branches branches;

    @MonotonicNonNull
    private BranchConfig branchConfig;

    private final RemoteConfig remoteConfig;
    private final GeneralConfig generalConfig;
    private final StoredConfig storedConfig;


    private GitLib(Git jgit) {
        this.jgit = jgit;

        storedConfig = jgit.getRepository().getConfig();
        v(storedConfig::load);
        this.remoteConfig = new StoredRemoteConfig(storedConfig, (remoteName, uri) -> {
            final RemoteAddCommand remoteAdd = jgit.remoteAdd();
            remoteAdd.setName(remoteName);
            remoteAdd.setUri(uri);
            return remoteAdd.call();
        });
        this.generalConfig = new StoredGeneralConfig(storedConfig);
    }


    @SuppressWarnings("unused")
    public static GitLib of(File workingDir) throws IOException {
        return new GitLib(Git.open(workingDir));
    }


//    @Deprecated // temporary convenience
    public Git jgit() {
        return this.jgit;
    }


    @EnsuresNonNull("branches")
    public Branches branches() {
        if (this.branches == null) {
            this.branches = new DefaultBranches(this);
        }
        return branches;
    }


    @Nonnull
    public RemoteConfig remoteConfig() {
        return remoteConfig;
    }


    @EnsuresNonNull("branchConfig")
    public BranchConfig branchConfig() {
        if (this.branchConfig == null) {
            this.branchConfig = new StoredBranchConfig(storedConfig, remoteConfig, branches());
        }
        return branchConfig;
    }


    public GeneralConfig generalConfig() {
        return generalConfig;
    }


    public static GitLib of(Git jgit) {
        return new GitLib(jgit);
    }


    /**
     * Push the local branch to the remote branch on the server.
     * <p>
     * Has protections to prevent things like pushing the local shadow of the integration branch.
     *
     * @param localBranch      the name of the local branch to push
     * @param remoteBranchName the name of the branch to push to on the server
     * @param forcePush        should it force the push even if it can not fast-forward?
     * @param prePush          called before doing the push (may be null)
     * @param postPush         called after doing the push (may be null)
     * @see RemoteConfig#remoteName()
     * @see BranchConfig#integrationBranch()
     */
    /**
     * Merge with the integration branch then push to the server.
     *
     * @return Left(error message) or Right(resulting branch)
     */
    public Either<String, ThePushResult> push(Branch localBranch,
                                              String remoteBranchName,
                                              boolean forcePush,
                                              @Nullable CheckedRunnable prePush,
                                              @Nullable CheckedRunnable postPush) {
        return Pusher.push(this, localBranch, remoteBranchName, forcePush, prePush, postPush);
    }


    /**
     * Fetch the latest changes from the server.
     *
     * @return Left(error message) or Right(fetch results; if no fetch was done, this is null)
     */
    public Either<String, @Nullable SimpleFetchResult> fetch() {
        if (remoteConfig().hasRemotes()) {
            return simpleFetchResult();
        }
        else {
            LOG.debug("fetch(): no remotes");
            return Either.right(null);
        }
    }


    @Nonnull
    @SuppressWarnings("RedundantCast")
    private Either<String, @Nullable SimpleFetchResult> simpleFetchResult() {
        final String remoteName = (@NonNull String)remoteConfig().remoteName();
        LOG.info("Fetching latest from \"{}\"", remoteName);
        return Try.of(() ->
                jgit.fetch().
                    setRemote(remoteName).
                    setRemoveDeletedRefs(true).
                    setTransportConfigCallback(new GitTransportConfigCallback()).
                    call()
        ).
            toEither().
            bimap(Throwable::toString, SimpleFetchResult::new).
            peek(sfr -> LOG.debug(sfr.toString()));
    }


    @Nonnull
    public File workingDirectory() {
        return jgit.getRepository().getWorkTree();
    }


//    @Deprecated // temporary convenience
    protected Repository repository() {
        return jgit.getRepository();
    }


    @Override
    public void close() throws Exception {
        jgit.close();
    }


    /**
     * Commit the current index/branch
     *
     * @param msg the commit message
     * @return Left(error message), Right(the new OID)
     */
    public Either<String, ObjectId> commit(@Nonnull String msg) {
        final Branch currentBranch = branches().currentBranch();
        final String currentBranchName = currentBranch != null ? currentBranch.shortName() : "NONE";
        LOG.info("Committing \"{}\" using message \"{}\"", currentBranchName, msg);
        return Try.of(() -> jgit.commit().setMessage(msg).call().getId()).
            toEither().
            bimap(Throwable::toString, o -> o).
            peek(o -> LOG.debug("New commit OID: {}", o.abbreviate(7).name()));
    }


    public DirCache addFilepattern(@NonNull String filepattern) {
        final Branch currentBranch = branches().currentBranch();
        final String currentBranchName = currentBranch != null ? currentBranch.shortName() : "NONE";
        LOG.info("Adding \"{}\" into the index for \"{}\"", filepattern, currentBranchName);
        return (@NonNull DirCache)e(() -> jgit.add().addFilepattern(filepattern).call());
    }


    /**
     * Check out the named branch
     *
     * @param branch the branch to check out
     * @return Left(error message), Right(reference to the branch)
     */
    public Either<String, Ref> checkout(Branch branch) {
        LOG.info("Checking out \"{}\"", branch.shortName());
        return Try.of(() -> jgit.checkout().setName(branch.name()).call()).
            toEither().
            bimap(ExecUtils::toString, r -> r).peek(r -> LOG.debug("Checked out {}", branch));
    }


    /**
     * Check out the named branch.
     * <p>
     * If the branch does not exist, an error is returned.
     *
     * @param branchName the name of the branch to check out
     * @return Left(error message), Right(reference to the branch)
     */
    public Either<String, Branch> checkout(String branchName) {
        final Branch branch = branches().branch(branchName);
        if (branch == null) return left("\"" + branchName + "\" is not a known branch");
        return branch.checkout();
    }


    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GitLib gitLib = (GitLib)o;

        return jgit.getRepository().getWorkTree().equals(gitLib.jgit.getRepository().getWorkTree());
    }


    @Override
    public int hashCode() {
        return jgit.getRepository().getWorkTree().hashCode();
    }


    @SuppressWarnings("RedundantCast")
    public boolean hasUncommittedChanges() {
        final Status status = (@NonNull Status)e(() -> jgit.status().call());
        return status.hasUncommittedChanges();
    }

}
