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

import com.jcabi.github.Coordinates;
import com.jcabi.github.Github;
import com.jcabi.github.Repo;
import com.jcabi.github.RtGithub;
import com.jcabi.http.Request;
import com.jcabi.http.wire.RetryWire;
import com.mooregreatsoftware.gitprocess.lib.ExecUtils;
import com.mooregreatsoftware.gitprocess.lib.GitLib;
import com.mooregreatsoftware.gitprocess.lib.JgitGitLib;
import javaslang.control.Try;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

import java.io.File;
import java.net.URI;

public final class GitHubRepoBuilder implements GitHubRepo.B.TheAuthorizerOrBuild, GitHubRepo.B.ProjectName,
    GitHubRepo.B.Password, GitHubRepo.B.GitLibOrRepo {

    private @Nullable GitLib gitLib;
    private @Nullable String oauth2Token;
    private @Nullable String username;
    private @Nullable String password;
    private @MonotonicNonNull String repoUser;
    private @MonotonicNonNull String projectName;
    private @Nullable URI serverApiUri;
    private @Nullable String remoteName;


    protected GitHubRepoBuilder() {
    }


    public GitHubRepo.B.ServerUriOrRepoNameOrBuild oauth2Token(String oauth2Token) {
        this.oauth2Token = oauth2Token;
        return this;
    }


    public GitHubRepo.B.Password username(String username) {
        this.username = username;
        return this;
    }


    public GitHubRepo.B.Build password(String password) {
        this.password = password;
        return this;
    }


    public GitHubRepo.B.ProjectName repoUser(String repoUser) {
        this.repoUser = repoUser;
        return this;
    }


    public GitHubRepo.B.TheAuthorizer projectName(String projectName) {
        this.projectName = projectName;
        return this;
    }


    public GitHubRepo.B.Build serverApiUri(URI serverApiUri) {
        this.serverApiUri = serverApiUri;
        return this;
    }


    public GitHubRepo.B.Build remoteName(String remoteName) {
        this.remoteName = remoteName;
        return this;
    }


    public GitHubRepo.B.TheAuthorizer repoUserAndProject(String repoUserAndProject) {
        final String[] strings = repoUserAndProject.split("/");
        if (strings.length != 2) {
            throw new IllegalArgumentException("There should be exactly one slash in the complete repository name (e.g., \"jdigger/jgit-process\"): " + repoUserAndProject);
        }
        return repoUser(strings[0]).projectName(strings[1]);
    }


    public GitHubRepo.B.TheAuthorizerOrBuild gitLib(GitLib gitLib) {
        final String repositoryName = gitLib.remoteConfig().repositoryName();
        if (repositoryName == null)
            throw new IllegalStateException("Can not find a repository name for " + gitLib.workingDirectory());

        this.gitLib = gitLib;
        repoUserAndProject(repositoryName);
        return this;
    }


    public GitHubRepo.B.ServerUriOrRepoNameOrBuild authorizer(Authorizer authorizer) {
        return oauth2Token(authorizer.getOauthToken());
    }


    @RequiresNonNull({"this.repoUser", "this.projectName"})
    public GitHubRepo build() {
        if (gitLib != null && oauth2Token == null) {
            final Authorizer authorizer = new Authorizer(gitLib);
            this.oauth2Token = authorizer.getOauthToken();
        }

        final Github github = createGithub();

        final Repo repo = github.repos().get(new Coordinates.Simple(repoUser, projectName));
        return new GitHubRepo(repo);
    }


    @SuppressWarnings("RedundantCast")
    protected Github createGithub() {
        final URI serverApiUri;
        if (this.serverApiUri != null) {
            serverApiUri = this.serverApiUri;
        }
        else {
            final GitLib gitLib = getGitLib();
            final String remoteName = getRemoteName(this.remoteName, gitLib);
            serverApiUri = GitHubRepo.getServerApiUri(remoteName, gitLib);
        }

        final Request baseRequest = (oauth2Token == null) ?
            new RtGithub((@NonNull String)username, (@NonNull String)password).entry() :
            new RtGithub(oauth2Token).entry();

        return new RtGithub(baseRequest.uri().set(serverApiUri).back().through(RetryWire.class));
    }


    private static String getRemoteName(@Nullable String remoteName, GitLib gitLib) {
        return (remoteName != null) ? remoteName : remoteNameFromGitLib(gitLib);
    }


    private static String remoteNameFromGitLib(GitLib gitLib) {
        final String remoteName = gitLib.remoteConfig().remoteName();
        if (remoteName == null)
            throw new IllegalStateException("Could not find a remote");
        return remoteName;
    }


    private GitLib getGitLib() {
        return this.gitLib != null ?
            this.gitLib :
            Try.of(() -> JgitGitLib.of(new File("."))).
                getOrElseThrow(ExecUtils.exceptionTranslator());
    }

}
