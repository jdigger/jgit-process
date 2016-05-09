package com.mooregreatsoftware.gitprocess.lib;

import javaslang.control.Either;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.eclipse.jgit.lib.ObjectId;

public interface Branch {
    @Pure
    String simpleName();

    @Nullable String remoteName();

    @Pure
    boolean isRemote();

    String name();

    String shortName();

    ObjectId objectId();

    @SuppressWarnings("unused")
    String sha();

    boolean containsAllOf(String otherBranchName);

    boolean containsAllOf(Branch otherBranch);

    boolean contains(ObjectId oid);

    Either<String, Branch> checkout();

    Branch upstream(Branch upstream);

    @Nullable Branch upstream();

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    @Nullable String recordLastSyncedAgainst();

    Either<String, @Nullable ObjectId> lastSyncedAgainst();

    @Nullable ObjectId previousRemoteOID();

    @Nullable ObjectId remoteOID();

    @Nullable String remoteBranchName();

    @Nullable String resetHard(String ref);
}
