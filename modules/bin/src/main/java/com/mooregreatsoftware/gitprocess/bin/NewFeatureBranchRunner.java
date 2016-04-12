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

import com.mooregreatsoftware.gitprocess.lib.Branch;
import com.mooregreatsoftware.gitprocess.lib.GitLib;
import com.mooregreatsoftware.gitprocess.process.NewFeatureBranch;
import javaslang.control.Either;

import java.io.IOException;

/**
 * Creates a new feature branch.
 *
 * @see NewFeatureBranch#newFeatureBranch(GitLib, String, boolean)
 */
public class NewFeatureBranchRunner extends AbstractRunner<NewFeatureBranchOptions, String, Branch> {

    private NewFeatureBranchRunner(GitLib gitLib, NewFeatureBranchOptions options) {
        super(gitLib, options);
    }


    public static AbstractRunner.B.GitLibSetter builder() {
        return new B.AbstractBuilder<NewFeatureBranchOptions, String>() {
            @Override
            protected Either<String, NewFeatureBranchOptions> options(String[] args) {
                return NewFeatureBranchOptions.create(args);
            }


            @Override
            protected Runner doBuild(GitLib gitLib, NewFeatureBranchOptions options) {
                return new NewFeatureBranchRunner(gitLib, options);
            }
        };
    }


    @Override
    protected Either<String, Branch> mainFunc(NewFeatureBranchOptions options) {
        return NewFeatureBranch.newFeatureBranch(gitLib(), options.branchName(), options.localOnly());
    }


    public static void main(String[] args) throws IOException {
        System.exit(builder().gitLib(createCurrentDirGitLib()).cliArgs(args).build().run());
    }

}
