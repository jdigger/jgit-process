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
import com.jcabi.github.Repo;
import com.jcabi.http.Request;
import com.jcabi.http.Response;
import com.jcabi.http.response.JsonResponse;
import com.jcabi.http.response.RestResponse;
import com.mooregreatsoftware.gitprocess.lib.Branch;
import com.mooregreatsoftware.gitprocess.lib.GitLib;
import javaslang.control.Either;
import javaslang.control.Try;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.checkerframework.dataflow.qual.Pure;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonStructure;
import java.io.IOException;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static javaslang.control.Either.left;
import static javaslang.control.Either.right;

public abstract class PullReqCreator {
    protected final GitHubRepo gitHubRepo;
    protected final Branch headBranch;
    protected final Branch baseBranch;


    protected PullReqCreator(GitHubRepo gitHubRepo, Branch headBranch, Branch baseBranch) {
        this.gitHubRepo = gitHubRepo;
        this.headBranch = headBranch;
        this.baseBranch = baseBranch;
    }


    public static B.UseGitLib builder() {
        return new B.Builder();
    }


    public Either<String, PullRequest> createPR() {
        final JsonStructure json = createPrJson();

        final Request request = createPrRequest();

        final Try<JsonObject> tPR = Try.of(() ->
            postCreatePR(json, request).
                as(RestResponse.class).
                assertStatus(HTTP_CREATED).
                as(JsonResponse.class).
                json().
                readObject()
        );

        if (tPR.isFailure()) //noinspection ThrowableResultOfMethodCallIgnored
            return left(tPR.getCause().toString());

        final PullRequest pullRequest = new PullRequest(tPR.get());

        return right(pullRequest);
    }


    protected Response postCreatePR(JsonStructure json, Request request) throws IOException {
        return request.method(Request.POST)
            .body().set(json).back()
            .fetch();
    }


    @Pure
    protected Request createPrRequest() {
        final Repo repo = this.gitHubRepo.repo();
        final Coordinates coords = repo.coordinates();
        return repo.
            github().entry().
            uri().
            path("/repos").
            path(coords.user()).
            path(coords.repo()).
            path("/pulls").back();
    }


    @Pure
    protected abstract JsonStructure createPrJson();


    static class StandardPullReqCreator extends PullReqCreator {
        private final String title;
        private final @Nullable String body;


        public StandardPullReqCreator(GitHubRepo gitHubRepo, Branch headBranch, Branch baseBranch, String title, @Nullable String body) {
            super(gitHubRepo, headBranch, baseBranch);
            this.title = title;
            this.body = body;
        }


        @Pure
        protected JsonStructure createPrJson() {
            final JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder().
                add("head", headBranch.simpleName()).
                add("base", baseBranch.simpleName()).
                add("title", title);

            if (body != null) {
                jsonObjectBuilder.add("body", body);
            }

            return jsonObjectBuilder.build();
        }
    }


    static class IssuePullReqCreator extends PullReqCreator {
        private final Integer issueId;


        public IssuePullReqCreator(GitHubRepo gitHubRepo, Branch headBranch, Branch baseBranch, Integer issueId) {
            super(gitHubRepo, headBranch, baseBranch);
            this.issueId = issueId;
        }


        @Pure
        protected JsonStructure createPrJson() {
            return Json.createObjectBuilder().
                add("head", headBranch.shortName()).
                add("base", baseBranch.shortName()).
                add("issue", issueId).build();
        }
    }


    @SuppressWarnings("unused")
    public interface B {
        interface Build {
            Build username(String username);

            Build password(String password);

            Build remoteName(String repoName);

            @RequiresNonNull("this.gitLib")
            PullReqCreator build();
        }

        interface UseGitLib {
            @EnsuresNonNull("this.gitLib")
            HeadBranch gitLib(GitLib gitLib);
        }

        interface HeadBranch {
            BaseBranch headBranch(Branch headBranch);
        }

        interface BaseBranch {
            TitleOrIssueId baseBranch(Branch baseBranch);
        }

        interface Title {
            BodyOrBuild title(String title);
        }

        interface Body {
            Build body(String body);
        }

        interface BodyOrBuild extends Body, Build {
        }

        interface IssueId {
            Build issueId(Integer issueId);
        }

        interface TitleOrIssueId extends Title, IssueId {
        }

        class Builder implements Build, UseGitLib, HeadBranch, BaseBranch, TitleOrIssueId, BodyOrBuild {
            private @MonotonicNonNull GitLib gitLib;
            private @MonotonicNonNull Branch headBranch;
            private @MonotonicNonNull Branch baseBranch;
            private @MonotonicNonNull String title;
            private @MonotonicNonNull String body;
            private @MonotonicNonNull Integer issueId;
            private @MonotonicNonNull String username;
            private @MonotonicNonNull String password;
            private @MonotonicNonNull String remoteName;


            @EnsuresNonNull("this.gitLib")
            public HeadBranch gitLib(GitLib gitLib) {
                this.gitLib = gitLib;
                return this;
            }


            public BaseBranch headBranch(Branch headBranch) {
                this.headBranch = headBranch;
                return this;
            }


            public TitleOrIssueId baseBranch(Branch baseBranch) {
                this.baseBranch = baseBranch;
                return this;
            }


            public BodyOrBuild title(String title) {
                this.title = title;
                return this;
            }


            public Build body(String body) {
                this.body = body;
                return this;
            }


            public Build issueId(Integer issueId) {
                this.issueId = issueId;
                return this;
            }


            public Build username(String username) {
                this.username = username;
                return this;
            }


            public Build password(String password) {
                this.password = password;
                return this;
            }


            public Build remoteName(String repoName) {
                this.remoteName = repoName;
                return this;
            }


            @RequiresNonNull("this.gitLib")
            @SuppressWarnings("RedundantCast")
            public PullReqCreator build() {
                final Branch currentBranch = gitLib.branches().currentBranch();
                if (currentBranch == null) throw new IllegalStateException("No branch is currently checked out");

                final Branch integrationBranch = gitLib.branches().integrationBranch();
                if (integrationBranch == null)
                    throw new IllegalStateException("Could not determine an integration branch");

                final GitHubRepo gitHubRepo = createGitHubRepo();

                return (title != null) ?
                    new StandardPullReqCreator(gitHubRepo, currentBranch, integrationBranch, title, body) :
                    new IssuePullReqCreator(gitHubRepo, currentBranch, integrationBranch, (@NonNull Integer)issueId);
            }


            @RequiresNonNull("this.gitLib")
            protected GitHubRepo createGitHubRepo() {
                final GitHubRepo.B.TheAuthorizerOrBuild theAuthorizerOrBuild = GitHubRepo.builder().gitLib(gitLib);
                if (username != null) {
                    Authorizer authorizer = new Authorizer(gitLib).username(username);
                    if (password != null) {
                        authorizer = authorizer.password(password);
                    }

                    return (remoteName != null) ?
                        theAuthorizerOrBuild.authorizer(authorizer).remoteName(remoteName).build() :
                        theAuthorizerOrBuild.authorizer(authorizer).build();
                }
                else {
                    return (remoteName != null) ?
                        theAuthorizerOrBuild.remoteName(remoteName).build() :
                        theAuthorizerOrBuild.build();
                }
            }
        }

    }

}
