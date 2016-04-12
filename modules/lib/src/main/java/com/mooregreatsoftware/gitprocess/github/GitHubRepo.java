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
package com.mooregreatsoftware.gitprocess.github;

import com.jcabi.github.Repo;
import com.mooregreatsoftware.gitprocess.lib.GitLib;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * A representation of the GitHub repository and its API
 *
 * @see #builder()
 */
public class GitHubRepo {
    private static final Logger LOG = LoggerFactory.getLogger(GitHubRepo.class);
    public static final URI DEFAULT_GITHUB_URI = URI.create("https://api.github.com");

    private final Repo repo;


    /**
     * Only allow the builder to create
     *
     * @see #builder()
     */
    protected GitHubRepo(Repo repo) {
        this.repo = repo;
    }


    public static URI getServerApiUri(String remoteName, GitLib gitLib) {
        final URI remoteUrl = getRemoteUrl(gitLib, remoteName);

        final URI serverApiUri = remoteUrl.getHost().toLowerCase().contains("github.com") ?
            DEFAULT_GITHUB_URI :
            serverApiFromRemoteUrl(remoteUrl);

        LOG.debug("Using GitHub API URL of {}", serverApiUri);

        return serverApiUri;
    }


    protected static URI serverApiFromRemoteUrl(URI remoteUrl) {
        final String scheme = remoteUrl.getScheme();
        final int port = remoteUrl.getPort();
        if (port == 80 && scheme.equalsIgnoreCase("http")) {
            return URI.create("http://" + remoteUrl.getHost());
        }
        else if (port == 443 && scheme.equalsIgnoreCase("https")) {
            return URI.create("https://" + remoteUrl.getHost());
        }
        else {
            return URI.create(scheme + "://" + remoteUrl.getHost() + ":" + port);
        }
    }


    public static URI getRemoteUrl(GitLib gitLib, String remoteName) {
        final URI remoteUrl = gitLib.remoteConfig().remoteUrl(remoteName);
        if (remoteUrl == null)
            throw new IllegalStateException("Can not find a URL for remote named \"" + remoteName + "\"");
        return remoteUrl;
    }


    public Repo repo() {
        return this.repo;
    }


    /**
     * Builds a new instance of {@link GitHubRepo}
     */
    public static B.GitLibOrRepo builder() {
        return new GitHubRepoBuilder();
    }


    public PullRequests pullRequests() {
        return new PullRequests(repo.pulls());
    }


    // **********************************************************************
    //
    // HELPER CLASSES
    //
    // **********************************************************************


    /**
     * Namespacing class for builder
     *
     * @see GitHubRepo#builder()
     */
    @SuppressWarnings("unused")
    public static final class B {

        public interface Build {
            @RequiresNonNull({"this.repoUser", "this.projectName"})
            GitHubRepo build();
        }

        public interface ServerUriOrRepoName {
            Build serverApiUri(URI serverApiUri);

            Build remoteName(String repoName);
        }

        public interface ServerUriOrRepoNameOrBuild extends ServerUriOrRepoName, Build {
        }

        public interface RepoUser {
            ProjectName repoUser(String repoUser);
        }

        public interface ProjectName {
            TheAuthorizer projectName(String projectName);
        }

        public interface RepoUserAndProject {
            TheAuthorizer repoUserAndProject(String repoUserAndProject);
        }

        public interface RepoUserOrRepoUserAndProject extends RepoUser, RepoUserAndProject {
        }

        public interface TheGitLib {
            TheAuthorizerOrBuild gitLib(GitLib gitLib);
        }

        public interface GitLibOrRepo extends TheGitLib, RepoUserOrRepoUserAndProject {
        }

        public interface TheAuthorizer {
            ServerUriOrRepoNameOrBuild authorizer(Authorizer authorizer);

            ServerUriOrRepoNameOrBuild oauth2Token(String oauth2Token);

            Password username(String username);
        }

        public interface Password {
            Build password(String password);
        }

        public interface TheAuthorizerOrBuild extends TheAuthorizer, ServerUriOrRepoNameOrBuild {
        }
    }

}
