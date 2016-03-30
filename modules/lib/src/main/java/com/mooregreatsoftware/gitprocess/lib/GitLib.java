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
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.e;
import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.v;
import static java.util.Optional.empty;
import static javaslang.control.Either.left;

/**
 * The central launch-point for interacting with Git.
 */
public class GitLib implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(GitLib.class);

    @Nonnull
    private final Git jgit;

    @Nonnull
    private final Branches branches;

    @Nonnull
    private final BranchConfig branchConfig;

    @Nonnull
    private final RemoteConfig remoteConfig;

    @Nonnull
    private final GeneralConfig generalConfig;


    private GitLib(@Nonnull Git jgit) {
        this.jgit = jgit;
        this.branches = new DefaultBranches(this);

        final StoredConfig storedConfig = jgit.getRepository().getConfig();
        v(storedConfig::load);
        this.remoteConfig = new StoredRemoteConfig(storedConfig, (remoteName, uri) -> {
            final RemoteAddCommand remoteAdd = jgit.remoteAdd();
            remoteAdd.setName(remoteName);
            remoteAdd.setUri(uri);
            return remoteAdd.call();
        });
        this.branchConfig = new StoredBranchConfig(storedConfig, remoteConfig, branches);
        this.generalConfig = new StoredGeneralConfig(storedConfig);
    }


    @Nonnull
    @SuppressWarnings("unused")
    public static GitLib of(File workingDir) throws IOException {
        return new GitLib(Git.open(workingDir));
    }


    @Nonnull
    @Deprecated // temporary convenience
    public Git jgit() {
        return this.jgit;
    }


    @Nonnull
    public Branches branches() {
        return branches;
    }


    @Nonnull
    public RemoteConfig remoteConfig() {
        return remoteConfig;
    }


    @Nonnull
    public BranchConfig branchConfig() {
        return branchConfig;
    }


    @Nonnull
    public GeneralConfig generalConfig() {
        return generalConfig;
    }


    @Nonnull
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
    @Nonnull
    public Either<String, ThePushResult> push(@Nonnull Branch localBranch,
                                                 @Nonnull String remoteBranchName,
                                                 boolean forcePush,
                                                 @Nullable Try.CheckedRunnable prePush,
                                                 @Nullable Try.CheckedRunnable postPush) {
        return Pusher.push(this, localBranch, remoteBranchName, forcePush, prePush, postPush);
    }


    /**
     * Fetch the latest changes from the server.
     *
     * @return Left(error message) or Right(fetch results; if no fetch was done, returns this is empty())
     */
    @Nonnull
    public Either<String, Optional<SimpleFetchResult>> fetch() {
        if (remoteConfig().hasRemotes()) {
            return simpleFetchResult().map(Optional::of);
        }
        else {
            LOG.debug("fetch(): no remotes");
            return Either.right(empty());
        }
    }


    @Nonnull
    private Either<String, SimpleFetchResult> simpleFetchResult() {
        final String remoteName = remoteConfig().remoteName().get();
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


    @Deprecated // temporary convenience
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
    @Nonnull
    public Either<String, ObjectId> commit(@Nonnull String msg) {
        LOG.info("Committing \"{}\" using message \"{}\"", branches().currentBranch().map(Branch::shortName).orElse("NONE"), msg);
        return Try.of(() -> jgit.commit().setMessage(msg).call().getId()).toEither().bimap(Throwable::toString, o -> o).peek(o -> LOG.debug("New commit OID: {}", o.abbreviate(7).name()));
    }


    @Nonnull
    public DirCache addFilepattern(@Nonnull String filepattern) {
        LOG.info("Adding \"{}\" into the index for \"{}\"", filepattern, branches().currentBranch().map(Branch::shortName).orElse("NONE"));
        return e(() -> jgit.add().addFilepattern(filepattern).call());
    }


    /**
     * Check out the named branch
     *
     * @param branch the branch to check out
     * @return Left(error message), Right(reference to the branch)
     */
    @Nonnull
    public Either<String, Ref> checkout(@Nonnull Branch branch) {
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
    @Nonnull
    public Either<String, Ref> checkout(@Nonnull String branchName) {
        return branches().branch(branchName).
            map(this::checkout).
            orElseGet(() -> left("\"" + branchName + "\" is not a known branch"));
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GitLib gitLib = (GitLib)o;

        return jgit.getRepository().getWorkTree().equals(gitLib.jgit.getRepository().getWorkTree());
    }


    @Override
    public int hashCode() {
        return jgit.getRepository().getWorkTree().hashCode();
    }


    public boolean hasUncommittedChanges() {
        final Status status = e(() -> jgit.status().call());
        return status.hasUncommittedChanges();
    }

}
