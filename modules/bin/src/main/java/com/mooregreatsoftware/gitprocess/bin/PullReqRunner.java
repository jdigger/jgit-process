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
package com.mooregreatsoftware.gitprocess.bin;

import com.mooregreatsoftware.gitprocess.github.PullReqCreator;
import com.mooregreatsoftware.gitprocess.github.PullRequest;
import com.mooregreatsoftware.gitprocess.lib.Branch;
import com.mooregreatsoftware.gitprocess.lib.Branches;
import com.mooregreatsoftware.gitprocess.lib.GitLib;
import javaslang.control.Either;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Makes it easier to work with pull requests from the command-line.
 *
 * @see PullReqOptions
 * @see #builder()
 * @see PullReqCreator#createPR()
 */
public class PullReqRunner extends AbstractRunner<PullReqOptions, String, PullRequest> {
    private static final Logger LOG = LoggerFactory.getLogger(PullReqRunner.class);


    private PullReqRunner(GitLib gitLib, PullReqOptions options) {
        super(gitLib, options);
    }


    /**
     * Provides a fluent way of constructing this runner.
     */
    public static AbstractRunner.B.GitLibSetter builder() {
        return new B.AbstractBuilder<PullReqOptions, String>() {
            @Override
            protected Either<String, PullReqOptions> options(String[] args) {
                return PullReqOptions.create(args);
            }


            @Override
            protected Runner doBuild(GitLib gitLib, PullReqOptions options) {
                return new PullReqRunner(gitLib, options);
            }
        };
    }


    @Override
    protected Either<String, PullRequest> mainFunc(PullReqOptions options) {
        // TODO:  Retrieve and check out a pull-request
        // TODO: Create a new pull-request based on a GH-issue

        final PullReqCreator pullReqCreator = pullReqCreator(gitLib(), options);

        final Either<String, PullRequest> pr = pullReqCreator.createPR();
        if (pr.isRight()) {
            final PullRequest pullRequest = pr.get();
            LOG.info("Created \"{}\" at {}", pullRequest.title(), pullRequest.htmlUrl());
        }
        return pr;
    }


    private static PullReqCreator pullReqCreator(GitLib gitLib, PullReqOptions opts) {
        final Branches branches = gitLib.branches();

        final Branch headBranch = headBranch(opts, branches);
        final Branch baseBranch = baseBranch(opts, branches);

        final String title = title(opts, headBranch);
        final @Nullable String description = opts.description();
        final @Nullable String username = opts.username();
        final @Nullable String password = opts.password();

        return prCreatorBuilder(gitLib, headBranch, baseBranch, title, description, username, password).build();
    }


    private static PullReqCreator.B.Build prCreatorBuilder(GitLib gitLib, Branch headBranch, Branch baseBranch,
                                                           String title, @Nullable String description,
                                                           @Nullable String username, @Nullable String password) {
        final PullReqCreator.B.BodyOrBuild bodyOrBuild = PullReqCreator.builder().
            gitLib(gitLib).
            headBranch(headBranch).
            baseBranch(baseBranch).
            title(title);

        PullReqCreator.B.Build builder = (description != null) ?
            bodyOrBuild.body(description) : bodyOrBuild;

        if (username != null)
            builder = builder.username(username);

        if (password != null)
            builder = builder.password(password);

        return builder;
    }


    private static String title(PullReqOptions opts, Branch headBranch) {
        final String titleOption = opts.prTitle();
        return (titleOption != null) ? titleOption : headBranch.shortName();
    }


    private static Branch baseBranch(PullReqOptions opts, Branches branches) {
        final String baseBranchOption = opts.baseBranchName();
        if (baseBranchOption != null) {
            final Branch baseBranch = branches.branch(baseBranchOption);
            if (baseBranch == null)
                throw new IllegalStateException("Could not find branch named \"" + baseBranchOption + "\"");
            return baseBranch;
        }
        else {
            final Branch baseBranch = branches.integrationBranch();
            if (baseBranch == null)
                throw new IllegalStateException("No integration branch has been set or can be derived, and no base branch was passed in");
            return baseBranch;
        }
    }


    private static Branch headBranch(PullReqOptions opts, Branches branches) {
        final String headBranchOption = opts.headBranchName();
        if (headBranchOption != null) {
            final Branch headBranch = branches.branch(headBranchOption);
            if (headBranch == null)
                throw new IllegalStateException("Could not find branch named \"" + headBranchOption + "\"");
            return headBranch;
        }
        else {
            final Branch headBranch = branches.currentBranch();
            if (headBranch == null)
                throw new IllegalStateException("Not checked out on a branch, and no head branch was passed in");
            return headBranch;
        }
    }


    public static void main(String[] args) throws IOException {
        final Runner runner = builder().gitLib(createCurrentDirGitLib()).cliArgs(args).build();
        System.exit(runner.run());
    }

}
