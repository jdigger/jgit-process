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
package com.mooregreatsoftware.gitprocess.lib.config;

import com.jcraft.jsch.ConfigRepository;
import com.jcraft.jsch.OpenSSHConfig;
import com.mooregreatsoftware.gitprocess.config.RemoteConfig;
import com.mooregreatsoftware.gitprocess.lib.StreamUtils;
import javaslang.control.Try;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.exceptionTranslator;
import static org.eclipse.jgit.lib.Constants.DEFAULT_REMOTE_NAME;

@SuppressWarnings("ConstantConditions")
public class StoredRemoteConfig extends AbstractStoredConfig implements RemoteConfig {
    private static final Logger LOG = LoggerFactory.getLogger(StoredRemoteConfig.class);

    private static final String REMOTE_SECTION_NAME = "remote";

    private final RemoteAdder remoteAdder;

    @SuppressWarnings("MalformedRegex")
    private static final Pattern SSH_URN_PATTERN = Pattern.compile("^(?!http)(?:(?<user>\\S+?)@)?(?<host>\\S+?):(?<path>.*)$");


    public StoredRemoteConfig(StoredConfig storedConfig, RemoteAdder remoteAdder) {
        super(storedConfig);
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
     * @return null if there are no remotes
     * @see #remoteName(String)
     * @see #remoteNames()
     */
    @Nullable
    @Override
    public String remoteName() {
        final String remoteName = getString(GIT_PROCESS_SECTION_NAME, null, REMOTE_NAME_KEY);
        if (remoteName != null) {
            LOG.debug("remoteName(): {}.{} has a value of \"{}\" so using that",
                GIT_PROCESS_SECTION_NAME, REMOTE_NAME_KEY, remoteName);
            return remoteName;
        }
        else {
            final Optional<String> foundRemoteName = StreamUtils.stream(remoteNames()).
                filter(remote -> remote.equals(DEFAULT_REMOTE_NAME)).
                findFirst();

            if (foundRemoteName.isPresent()) {
                LOG.debug("remoteName(): remote \"{}\" found", foundRemoteName.get());
                return foundRemoteName.get();
            }
            else {
                final Optional<String> firstRemote = StreamUtils.stream(remoteNames()).
                    sorted().
                    findFirst();

                logFindRemoteName(firstRemote);
                return firstRemote.orElse(null);
            }
        }
    }


    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private void logFindRemoteName(Optional<String> firstRemote) {
        if (firstRemote.isPresent()) {
            LOG.warn("Do not have a \"{}\" branch so using the first remote " +
                    "it could find: \"{}\".\nTo remove this warning, set `git config {}.{} [remote_name]`",
                DEFAULT_REMOTE_NAME, firstRemote.get(), GIT_PROCESS_SECTION_NAME, REMOTE_NAME_KEY);
        }
        else {
            LOG.debug("remoteName(): there are no remotes so returning null");
        }
    }


    /**
     * Sets (and writes to git configuration) the name of the remote to use (e.g., "origin").
     *
     * @return this
     * @see #remoteName()
     */
    @Override
    public RemoteConfig remoteName(@Nonnull String remoteName) {
        if (remoteName == null || remoteName.trim().isEmpty())
            throw new IllegalArgumentException("remoteName is empty");

        setString(GIT_PROCESS_SECTION_NAME, null, REMOTE_NAME_KEY, remoteName);
        return this;
    }


    /**
     * All the remote names ("origin" etc.) this knows about
     *
     * @return never null; may be empty
     */
    @Override
    @SuppressWarnings("RedundantCast")
    public Iterable<String> remoteNames() {
        return (Iterable<String>)storedConfig.getSections().stream().
            filter(REMOTE_SECTION_NAME::equals).
            flatMap(section -> storedConfig.getSubsections(section).stream()).
            collect(Collectors.toList());
    }


    public RemoteConfig remoteAdd(String remoteName, URIish url) {
        Try.run(() -> remoteAdder.add(remoteName, url)).
            getOrElseThrow(exceptionTranslator());
        return this;
    }


    // TODO Is this needed?
    public interface RemoteAdder {
        org.eclipse.jgit.transport.RemoteConfig add(String remoteName, URIish uri) throws GitAPIException;
    }


    @Nullable
    @Override
    public String credentialHelper(@Nullable URI uri) {
        if (uri != null) {
            LOG.debug("Getting config credential.{}.helper", uri.toString());
            final String uriCredHelper = getString("credential", uri.toString(), "helper");
            if (uriCredHelper != null) {
                LOG.debug("Found credential helper: {}", uriCredHelper);
                return uriCredHelper;
            }
        }

        LOG.debug("Getting config credential.helper");
        final String globalCredHelper = getString("credential", uri != null ? uri.toString() : null, "helper");
        if (globalCredHelper != null) {
            LOG.debug("Found credential helper: {}", globalCredHelper);
            return globalCredHelper;
        }
        LOG.debug("No credential helper found");
        return null;
    }


    @Nullable
    @Override
    public String repositoryName() {
        if (!hasRemotes()) return null;

        final String remoteName = remoteName();
        if (remoteName == null) return null;

        final URI uri = remoteUrl(remoteName);
        if (uri == null) return null;
        LOG.debug("remote URI: {}", uri);
        String path = uri.getPath();

        if (path.endsWith(".git")) path = path.substring(0, path.length() - ".git".length());

        return path.startsWith("/") ? path.substring(1) : path;
    }


    @Nullable
    @Override
    public URI remoteUrl(String remoteName) {
        String urlStr = getString(REMOTE_SECTION_NAME, remoteName, "url");
        if (urlStr == null) {
            LOG.warn("Could not find a repository URL for {}", remoteName);
            return null;
        }

        urlStr = normalizeUrl(urlStr);

        final URI uri = URI.create(urlStr);
        LOG.debug("remote URI: {}", uri);
        return uri;
    }


    protected static String normalizeUrl(String urlStr) {
        File sshDir = new File(System.getenv("user.home"), ".ssh");
        if (sshDir.exists() && sshDir.isDirectory()) {
            File sshConfigFile = new File(sshDir, "config");
            if (sshConfigFile.exists()) {
                try {
                    return normalizeUrl(urlStr, new FileReader(sshConfigFile));
                }
                catch (FileNotFoundException e) {
                    return normalizeUrl(urlStr, null);
                }
            }
        }
        return normalizeUrl(urlStr, null);
    }


    protected static String normalizeUrl(String urlStr, @Nullable Reader sshConfigReader) {
        final Matcher matcher = SSH_URN_PATTERN.matcher(urlStr);
        if (matcher.matches()) {
            String user = matcher.group("user");
            String host = matcher.group("host");
            final String path = matcher.group("path");

            if (sshConfigReader != null) {
                final BufferedReader bufferedReader = new BufferedReader(sshConfigReader);
                final String text = bufferedReader.lines().collect(Collectors.joining(System.lineSeparator()));
                try {
                    final OpenSSHConfig openSSHConfig = OpenSSHConfig.parse(text);
                    final ConfigRepository.Config sshConfig = openSSHConfig.getConfig(host);
                    if (sshConfig.getHostname() != null) {
                        host = sshConfig.getHostname();
                        if (user == null && sshConfig.getUser() != null) {
                            user = sshConfig.getUser();
                        }
                    }
                }
                catch (IOException e) {
                    LOG.warn("Could not parse SSH Config file:\n{}", text);
                }
            }

            urlStr = "ssh://" + (user != null ? user + '@' : "") + host + '/' + path;
        }
        return urlStr;
    }

}
