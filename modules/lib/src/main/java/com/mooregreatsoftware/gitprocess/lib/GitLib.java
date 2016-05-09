package com.mooregreatsoftware.gitprocess.lib;

import com.mooregreatsoftware.gitprocess.config.BranchConfig;
import com.mooregreatsoftware.gitprocess.config.GeneralConfig;
import com.mooregreatsoftware.gitprocess.config.RemoteConfig;
import javaslang.control.Either;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

import javax.annotation.Nonnull;
import java.io.File;

public interface GitLib extends AutoCloseable {
    //    @Deprecated // temporary convenience
    Git jgit();

    @EnsuresNonNull("branches")
    Branches branches();

    RemoteConfig remoteConfig();

    @EnsuresNonNull("branchConfig")
    BranchConfig branchConfig();

    GeneralConfig generalConfig();

    // TODO remove direct invocation of Pusher.push
    @SuppressWarnings("unused")
    Either<String, Pusher.ThePushResult> push(Branch localBranch,
                                              String remoteBranchName,
                                              boolean forcePush);

    Either<String, @Nullable SimpleFetchResult> fetch();

    File workingDirectory();

    Either<String, ObjectId> commit(@Nonnull String msg);

    DirCache addFilepattern(@NonNull String filepattern);

    Either<String, Ref> checkout(Branch branch);

    Either<String, Branch> checkout(String branchName);

    boolean hasUncommittedChanges();
}
