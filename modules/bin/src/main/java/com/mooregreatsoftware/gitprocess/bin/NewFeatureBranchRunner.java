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

import com.mooregreatsoftware.gitprocess.lib.GitLib;
import com.mooregreatsoftware.gitprocess.process.NewFeatureBranch;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;

import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.e;

/**
 * Creates a new feature branch.
 *
 * @see NewFeatureBranch#newFeatureBranch(GitLib, String, boolean)
 */
@SuppressWarnings("ConstantConditions")
public class NewFeatureBranchRunner extends AbstractRunner {


    @Nonnull
    public String description() {
        return "Create a new feature branch based on the integration branch.";
    }


    @Nonnull
    public String usageInfo() {
        return "git new-fb [OPTIONS] 'branch_name'";
    }


    protected boolean shouldShowHelp(@Nonnull OptionSet optionSet) {
        return optionSet.nonOptionArguments().size() != 1;
    }


    /**
     * Override this to customize the OptionParser
     */
    @Nonnull
    protected OptionParser createOptionParser() {
        final OptionParser optionParser = super.createOptionParser();
        optionParser.accepts("local", "Don't fetch the latest from remote");
        return optionParser;
    }


    public static void main(String[] args) throws IOException {
        final NewFeatureBranchRunner runner = new NewFeatureBranchRunner();
        final OptionSet optionSet = runner.parse(args);

        final GitLib gitLib = e(() -> GitLib.of(new File(".")));
        NewFeatureBranch.newFeatureBranch(gitLib, (String)optionSet.nonOptionArguments().get(0), optionSet.has("logging"));
    }


}
