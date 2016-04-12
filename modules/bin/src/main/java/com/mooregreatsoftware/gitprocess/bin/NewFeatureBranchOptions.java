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

import javax.annotation.Nonnull;
import java.io.PrintStream;

import static javaslang.control.Either.left;
import static javaslang.control.Either.right;

/**
 * CLI options for {@link NewFeatureBranchRunner}
 */
public class NewFeatureBranchOptions extends Options {

    protected NewFeatureBranchOptions(@Nonnull PrintStream printStream) {
        super(printStream);
    }


    /**
     * Try to create an instance of {@link NewFeatureBranchOptions} but return an error message to
     * print if not successful.
     *
     * @param args        the command line arguments
     * @param printStream where to send logging output
     * @return Left(message to print before exiting) or Right(the options)
     */
    public static Either<String, NewFeatureBranchOptions> create(@Nonnull String[] args, @Nonnull PrintStream printStream) {
        NewFeatureBranchOptions newFeatureBranchOptions = new NewFeatureBranchOptions(printStream);
        final String msgOption = newFeatureBranchOptions.parse(args);
        return msgOption != null ? left(msgOption) : right(newFeatureBranchOptions);
    }


    /**
     * Try to create an instance of {@link NewFeatureBranchOptions} but return an error message to
     * print if not successful.
     *
     * @param args the command line arguments
     * @return Left(message to print before exiting) or Right(the options)
     */
    public static Either<String, NewFeatureBranchOptions> create(@Nonnull String[] args) {
        return create(args, System.out);
    }


    @Nonnull
    protected OptionParser createOptionParser() {
        final OptionParser optionParser = super.createOptionParser();
        optionParser.accepts("local", "Don't fetch the latest from remote");
        return optionParser;
    }


    @Nonnull
    public String description() {
        return "Create a new feature branch based on the integration branch.";
    }


    @Nonnull
    public String usageInfo() {
        return "git new-fb [OPTIONS] 'branch_name'";
    }


    @Override
    public boolean showHelp() {
        // need exactly one arg for branch name
        return helpOptionValue() || nonOptionArgs().size() != 1;
    }


    public String branchName() {
        return nonOptionArgs().get(0);
    }


    public boolean localOnly() {
        return booleanValue("local");
    }

}
