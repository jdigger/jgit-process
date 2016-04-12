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

import javaslang.control.Either;
import joptsimple.OptionParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.PrintStream;
import java.util.Objects;

import static java.util.Arrays.asList;
import static javaslang.control.Either.left;
import static javaslang.control.Either.right;

/**
 * CLI options for {@link PullReqRunner}
 */
public class PullReqOptions extends Options {
    private static final Logger LOG = LoggerFactory.getLogger(PullReqOptions.class);


    @SuppressWarnings("unused")
    protected PullReqOptions(PrintStream printStream) {
        super(printStream);
    }


    /**
     * Try to create an instance of {@link PullReqOptions} but return an error message to print if not successful.
     *
     * @param args        the command line arguments
     * @param printStream where to send logging output
     * @return Left(message to print before exiting) or Right(the options)
     */
    public static Either<String, PullReqOptions> create(String[] args, PrintStream printStream) {
        final PullReqOptions prOptions = new PullReqOptions(printStream);
        final String msgOption = prOptions.parse(args);
        return msgOption != null ? left(msgOption) : right(prOptions);
    }


    /**
     * Try to create an instance of {@link PullReqOptions} but return an error message to print if not successful.
     *
     * @param args the command line arguments
     * @return Left(message to print before exiting) or Right(the options)
     */
    public static Either<String, PullReqOptions> create(String[] args) {
        // TODO Accept an issue id
        return create(args, System.out);
    }


    public String description() {
        return "Creates a new pull request for the current branch.";
    }


    public String usageInfo() {
        return "git pull-req [OPTIONS] \"Pull request title\"";
    }


    /**
     * Override this to customize the OptionParser
     */

    protected OptionParser createOptionParser() {
        final OptionParser optionParser = super.createOptionParser();
        /*
*-d <desc>, --description <desc>*::
    The description of the Pull Request. Usually includes a nice description of what was
    changed to make things easier for the reviewer.

*-u <username>, --user <username>*::
    Your GitHub username. Only needed the first time you connect, and you will be
    prompted for it if needed. Used to generate the value for the 'gitProcess.github.authtoken'
    configuration. See also the 'github.user' configuration item.

*-p <password>, --password <password>*::
    Your GitHub password. Only needed the first time you connect, and you will be
    prompted for it if needed. Used to generate the value for the 'gitProcess.github.authtoken'
    configuration.
         */
        optionParser.accepts("base-branch", "The branch on the server that you want this \"pulled\" into (default: the integration branch)").withRequiredArg();
        optionParser.accepts("head-branch", "The branch that you want reviewed before being \"pulled\" into the base branch (default: the current branch)").withRequiredArg();
        optionParser.acceptsAll(asList("d", "description"), "The description of the Pull Request").withRequiredArg();
        optionParser.accepts("username", "Your GitHub username; only needed the first time you connect, and you will be prompted for it if needed").withRequiredArg();
        optionParser.accepts("password", "Your GitHub password; only needed the first time you connect, and you will be prompted for it if needed").withRequiredArg();

        return optionParser;
    }


    @Override
    public boolean showHelp() {
        if (helpOptionValue()) return true;

        if (nonOptionArgs().size() > 1) {
            LOG.warn("Too many arguments");
            return true;
        }

        final String headBranchName = headBranchName();
        if (headBranchName != null && headBranchName.equals(baseBranchName())) {
            LOG.warn("Head branch name and base branch name can not be the same");
            return true;
        }

        return false;
    }


    /**
     * If an argument was given and it's a number, return it as being either a PR and IssueID
     *
     * @return null if no argument was given or it's not a number
     * @see #prTitle()
     */
    @Nullable
    public Integer issueOrPrID() {
        if (nonOptionArgs().size() < 1)
            return null;
        final String arg = nonOptionArgs().get(0);
        try {
            return Integer.valueOf(arg);
        }
        catch (NumberFormatException e) {
            return null;
        }
    }


    /**
     * If an argument was given and it's not a number, it's the title of the PR
     *
     * @return null if no argument is given or it's a number
     * @see #issueOrPrID()
     */
    @Nullable
    public String prTitle() {
        final Integer issueOrPrID = issueOrPrID();
        if (issueOrPrID != null) return null;
        if (nonOptionArgs().size() == 1)
            return nonOptionArgs().get(0);
        return null;
    }


    /**
     * The name of the branch to base the PR on. Typically the integration branch.
     *
     * @return null if no argument is given
     * @see #headBranchName()
     */
    @Nullable
    public String baseBranchName() {
        return stringValue("base-branch").orElse(null);
    }


    /**
     * The name of the branch to create a PR for. Typically the current branch.
     *
     * @return null if no argument is given
     * @see #baseBranchName()
     */
    @Nullable
    public String headBranchName() {
        return stringValue("head-branch").orElse(null);
    }


    /**
     * The name of the repository to create the PR on. Typically "origin".
     *
     * @return null if no argument is given
     */
    @Nullable
    public String remoteName() {
        return stringValue("remote-name").orElse(null);
    }


    /**
     * The user name to when contacting the server, if the OAuth token has not already been set.
     *
     * @return null if no argument is given
     */
    @Nullable
    public String username() {
        return stringValue("username").orElse(null);
    }


    /**
     * The password to when contacting the server, if the OAuth token has not already been set.
     *
     * @return null if no argument is given
     */
    @Nullable
    public String password() {
        return stringValue("password").orElse(null);
    }

}
