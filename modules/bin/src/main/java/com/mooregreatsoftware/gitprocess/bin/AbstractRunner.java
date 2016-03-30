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
import javaslang.control.Either;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.function.Function;

import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.e;

/**
 * The base class for the CLI commands to extend from. Provides handling of standard functionality.
 */
public abstract class AbstractRunner {


    public static final int STOP_ON_OPTIONS_CODE = 1;
    public static final int STOP_ON_FUNCTION_CODE = 2;


    /**
     * Returns in instance of {@link GitLib} set to the current directory (i.e., ".")
     */
    @Nonnull
    protected static GitLib createGitLib() {
        return e(() -> GitLib.of(new File(".")));
    }


    /**
     * Gather the {@link Options} for the command, run it, and return the appropriate exit code for the program.
     * <p>
     * If there's a problem while gathering the options, print them to STDOUT and return 1.
     *
     * @param <O>             the Options type
     * @param <V>             the result type of "mainFunction"
     * @param args            the CLI arguments
     * @param optionsFunction given the CLI args, returns either Left(message to print) or Right(options to pass to the main function)
     * @param mainFunction    the main function to run
     * @return 0 if successful; anything else if not
     */
    protected static <O extends Options, M extends CharSequence, V>
    int run(@Nonnull String[] args,
            @Nonnull Function<String[], Either<M, O>> optionsFunction,
            @Nonnull Function<O, Either<M, V>> mainFunction) {

        //noinspection UnnecessaryUnboxing
        return optionsFunction.apply(args).
            left(). // are the options finished?
            map(CharSequence::toString).
            peek(System.out::println). // print the message
            map(m -> STOP_ON_OPTIONS_CODE). // exit value if options were consumed
            toEither().
            right(). // the options still need to be passed on
            map(mainFunction).
            map(AbstractRunner::valueToExitCode). // exit value for function
            toEither().
            getOrElseGet(l -> l).  // extract exit code
            intValue();
    }


    @Nonnull
    private static <M extends CharSequence, V> Integer valueToExitCode(@Nonnull Either<M, V> value) {
        return value.
            map(b -> 0).
            getOrElseGet(cs -> {
                    System.err.println(cs.toString());
                    return STOP_ON_FUNCTION_CODE; // exit value if an issue happen running the function
                }
            );
    }

}
