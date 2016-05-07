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

import com.mooregreatsoftware.gitprocess.lib.ExecUtils;
import com.mooregreatsoftware.gitprocess.lib.GitLib;
import javaslang.control.Either;
import javaslang.control.Try;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.File;

/**
 * The base class for the CLI commands to extend from. Provides handling of standard functionality.
 *
 * @param <O> the Options type
 * @param <V> the result type of "mainFunction"
 */
public abstract class AbstractRunner<O extends Options, M extends CharSequence, V> implements Runner {

    private final GitLib gitLib;

    protected O options;


    protected AbstractRunner(GitLib gitLib, O options) {
        this.gitLib = gitLib;
        this.options = options;
    }


    public GitLib gitLib() {
        return gitLib;
    }


    /**
     * Returns in instance of {@link GitLib} set to the current directory (i.e., ".")
     */
    protected static GitLib createCurrentDirGitLib() {
        return Try.of(() -> GitLib.of(new File("."))).
            getOrElseThrow(ExecUtils.exceptionTranslator());
    }


    protected abstract Either<M, V> mainFunc(O options);


    interface B {
        abstract class AbstractBuilder<O extends Options, M extends CharSequence> implements TheBuilder, GitLibSetter, CliArgs {
            protected @MonotonicNonNull GitLib gitLib;
            protected @MonotonicNonNull Either<M, O> eOptions;


            protected abstract Either<M, O> options(String[] args);


            @Override
            @EnsuresNonNull("this.eOptions")
            public TheBuilder cliArgs(String[] args) {
                this.eOptions = options(args);
                return this;
            }


            @Override
            @EnsuresNonNull("this.gitLib")
            public CliArgs gitLib(GitLib gitLib) {
                this.gitLib = gitLib;
                return this;
            }


            @Override
            @SuppressWarnings("RedundantCast")
            public Runner build() {
                final GitLib gl = (@NonNull GitLib)this.gitLib;
                final Either<M, O> eOpts = (@NonNull Either<M, O>)this.eOptions;
                if (eOpts.isLeft()) {
                    return new ErrorRunner<>(eOpts.getLeft());
                }
                return doBuild(gl, eOpts.get());
            }


            protected abstract Runner doBuild(GitLib gitLib, O options);


            protected static class ErrorRunner<M extends CharSequence> implements Runner {
                private final M errorMsg;


                public ErrorRunner(M errorMsg) {
                    this.errorMsg = errorMsg;
                }


                public int run() {
                    System.out.println(errorMsg);
                    return Runner.STOP_ON_OPTIONS_CODE;
                }
            }
        }

        interface TheBuilder {
            Runner build();
        }

        interface GitLibSetter {
            CliArgs gitLib(GitLib gitLib);
        }

        interface CliArgs {
            TheBuilder cliArgs(String[] args);
        }
    }


    @Override
    public int run() {
        if (options == null) return STOP_ON_OPTIONS_CODE;

        final Either<M, V> result = mainFunc(options);
        return valueToExitCode(result);
    }


    private static <M extends CharSequence, V> Integer valueToExitCode(Either<M, V> value) {
        return value.
            map(b -> 0).
            getOrElseGet(cs -> {
                    System.err.println(cs.toString());
                    return STOP_ON_FUNCTION_CODE; // exit value if an issue happens running the function
                }
            );
    }

}
