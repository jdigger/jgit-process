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
import com.mooregreatsoftware.gitprocess.process.Sync;
import javaslang.control.Either;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * Syncs local changes with the server.
 *
 * @see Sync#sync(GitLib, boolean, boolean)
 */
@SuppressWarnings("ConstantConditions")
public class SyncRunner extends AbstractRunner {

    public static void main(String[] args) throws IOException {
        final GitLib gitLib = createGitLib();

        int exitValue = run(args,
            a -> SyncOptions.create(a, gitLib.generalConfig()),
            opts -> SyncRunner.<String, Branch>doSync(gitLib, opts)
        );

        System.exit(exitValue);
    }


    @Nonnull
    private static Either<String, Branch> doSync(@Nonnull GitLib gitLib, @Nonnull SyncOptions opts) {
        return Sync.sync(gitLib, opts.merge(), opts.localOnly());
    }

}
