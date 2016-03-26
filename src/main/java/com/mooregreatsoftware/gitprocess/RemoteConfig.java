package com.mooregreatsoftware.gitprocess;

import org.eclipse.jgit.transport.URIish;

import javax.annotation.Nonnull;
import java.util.Optional;

public interface RemoteConfig extends Config {
    String REMOTE_NAME_KEY = "remoteName";

    boolean hasRemotes();

    @Nonnull
    Optional<String> remoteName();

    @Nonnull
    RemoteConfig remoteName(@Nonnull String remoteName);

    @Nonnull
    Iterable<String> remoteNames();

    @Nonnull
    RemoteConfig remoteAdd(String remoteName, URIish url);

}
