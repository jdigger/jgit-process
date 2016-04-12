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

import com.mooregreatsoftware.gitprocess.bin.AbstractRunner.B.GitLibSetter;
import com.mooregreatsoftware.gitprocess.lib.Branch;
import com.mooregreatsoftware.gitprocess.lib.GitLib;
import com.mooregreatsoftware.gitprocess.process.Sync;
import javaslang.control.Either;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;

/**
 * Syncs local changes with the server.
 *
 * @see #builder()
 * @see Sync#sync(GitLib, boolean, boolean)
 */
public class SyncRunner extends AbstractRunner<SyncOptions, String, Branch> {

    private SyncRunner(GitLib gitLib, SyncOptions options) {
        super(gitLib, options);
    }


    /**
     * Used to create a new instance of {@link SyncRunner}
     */
    public static GitLibSetter builder() {
        return new B.AbstractBuilder<SyncOptions, String>() {
            @Override
            @SuppressWarnings("RedundantCast")
            protected Either<String, SyncOptions> options(String[] args) {
                final GitLib gl = (@NonNull GitLib)this.gitLib;
                return SyncOptions.create(args, gl.generalConfig());
            }


            @Override
            protected Runner doBuild(GitLib gitLib, SyncOptions options) {
                return new SyncRunner(gitLib, options);
            }
        };
    }


    @Override
    protected Either<String, Branch> mainFunc(SyncOptions options) {
        return Sync.sync(gitLib(), options.merge(), options.localOnly());
    }


    public static void main(String[] args) throws IOException {
        System.exit(builder().gitLib(createCurrentDirGitLib()).cliArgs(args).build().run());
    }

}
