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
package com.mooregreatsoftware.gitprocess;

import com.mooregreatsoftware.gitprocess.transport.GitTransportConfigCallback;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.FetchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static com.mooregreatsoftware.gitprocess.ExecUtils.e;
import static com.mooregreatsoftware.gitprocess.ExecUtils.v;
import static java.util.Optional.empty;

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
    public static GitLib of(Git jgit) {
        return new GitLib(jgit);
    }


    @Nonnull
    public Optional<SimpleFetchResult> fetch() {
        if (remoteConfig().hasRemotes()) {
            return e(this::simpleFetchResult);
        }
        else {
            LOG.debug("fetch(): no remotes");
            return empty();
        }
    }


    @Nonnull
    private Optional<SimpleFetchResult> simpleFetchResult() throws GitAPIException {
        final String remoteName = remoteConfig().remoteName().get();
        LOG.info("Fetching latest from \"{}\"", remoteName);
        final FetchResult fetchResult = jgit.fetch().
            setRemote(remoteName).
            setRemoveDeletedRefs(true).
            setTransportConfigCallback(new GitTransportConfigCallback()).
            call();
        final SimpleFetchResult result = new SimpleFetchResult(fetchResult);
        LOG.debug(result.toString());
        return Optional.of(result);
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


    @Nonnull
    public ObjectId commit(@Nonnull String msg) {
        LOG.info("Committing \"{}\" using message \"{}\"", branches().currentBranch().map(Branch::shortName).orElse("NONE"), msg);
        return e(() -> jgit.commit().setMessage(msg).call().getId());
    }


    @Nonnull
    public DirCache addFilepattern(@Nonnull String filepattern) {
        LOG.info("Adding \"{}\" into the index for \"{}\"", filepattern, branches().currentBranch().map(Branch::shortName).orElse("NONE"));
        return e(() -> jgit.add().addFilepattern(filepattern).call());
    }


    @Nonnull
    public Ref checkout(@Nonnull Branch branch) {
        LOG.info("Checking out \"{}\"", branch.shortName());
        return e(() -> jgit.checkout().setName(branch.name()).call());
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

}
