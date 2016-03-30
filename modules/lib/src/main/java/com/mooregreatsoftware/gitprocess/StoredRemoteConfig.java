package com.mooregreatsoftware.gitprocess;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.mooregreatsoftware.gitprocess.ExecUtils.v;
import static com.mooregreatsoftware.gitprocess.StreamUtils.stream;
import static java.util.Optional.of;
import static org.eclipse.jgit.lib.Constants.DEFAULT_REMOTE_NAME;

@SuppressWarnings("ConstantConditions")
public class StoredRemoteConfig implements RemoteConfig {
    private static final Logger LOG = LoggerFactory.getLogger(StoredRemoteConfig.class);

    private static final String REMOTE_SECTION_NAME = "remote";

    @Nonnull
    private final StoredConfig storedConfig;

    @Nonnull
    RemoteAdder remoteAdder;


    public StoredRemoteConfig(@Nonnull StoredConfig storedConfig, @Nonnull RemoteAdder remoteAdder) {
        this.storedConfig = storedConfig;
        this.remoteAdder = remoteAdder;
    }


    /**
     * Are there any remotes (e.g., "origin") defined for this repository?
     */
    @Override
    public boolean hasRemotes() {
        return storedConfig.getSections().contains(REMOTE_SECTION_NAME);
    }


    /**
     * The name of the remote to use (e.g., "origin").
     * <p>
     * If the "gitProcess.remoteName" git config has been set, use that. Otherwise assume "origin" if it exists.
     * If there are no remotes, returns empty(). If no config has been set and "origin" doesn't exist, this will
     * return the "first remote name"; which is the first one it finds after sorting hem alphabetically.
     *
     * @return empty() if there are no remotes
     * @see #remoteName(String)
     * @see #remoteNames()
     */
    @Nonnull
    @Override
    public Optional<String> remoteName() {
        final String remoteName = storedConfig.getString(GIT_PROCESS_SECTION_NAME, null, REMOTE_NAME_KEY);
        if (remoteName != null) {
            LOG.debug("remoteName(): {}.{} has a value of \"{}\" so using that",
                GIT_PROCESS_SECTION_NAME, REMOTE_NAME_KEY, remoteName);
            return of(remoteName);
        }
        else {
            final Optional<String> foundRemoteName = stream(remoteNames()).
                filter(remote -> remote.equals(DEFAULT_REMOTE_NAME)).
                findFirst();

            if (foundRemoteName.isPresent()) {
                LOG.debug("remoteName(): remote \"{}\" found", foundRemoteName.get());
                return foundRemoteName;
            }
            else {
                final Optional<String> firstRemote = stream(remoteNames()).
                    sorted().
                    findFirst();

                logFindRemoteName(firstRemote);
                return firstRemote;
            }
        }
    }


    private void logFindRemoteName(Optional<String> firstRemote) {
        if (firstRemote.isPresent()) {
            LOG.warn("Do not have a \"{}\" branch so using the first remote " +
                    "it could find: \"{}\".\nTo remove this warning, set `git config {}.{} [remote_name]`",
                DEFAULT_REMOTE_NAME, firstRemote.get(), GIT_PROCESS_SECTION_NAME, REMOTE_NAME_KEY);
        }
        else {
            LOG.debug("remoteName(): there are no remotes so returning empty()");
        }
    }


    /**
     * Sets (and writes to git configuration) the name of the remote to use (e.g., "origin").
     *
     * @return this
     * @see #remoteName()
     */
    @Nonnull
    @Override
    public RemoteConfig remoteName(@Nonnull String remoteName) {
        if (remoteName == null || remoteName.trim().isEmpty())
            throw new IllegalArgumentException("remoteName is empty");

        storedConfig.setString(GIT_PROCESS_SECTION_NAME, null, REMOTE_NAME_KEY, remoteName);
        v(storedConfig::save);
        return this;
    }


    /**
     * All the remote names ("origin" etc.) this knows about
     *
     * @return never null; may be empty
     */
    @Nonnull
    @Override
    public Iterable<String> remoteNames() {
        return storedConfig.getSections().stream().
            filter(REMOTE_SECTION_NAME::equals).
            flatMap(section -> storedConfig.getSubsections(section).stream()).
            collect(Collectors.toList());
    }


    @Nonnull
    public RemoteConfig remoteAdd(String remoteName, URIish url) {
        v(() -> remoteAdder.add(remoteName, url));
        return this;
    }


    public interface RemoteAdder {
        org.eclipse.jgit.transport.RemoteConfig add(String remoteName, URIish uri) throws GitAPIException;
    }

}
