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
package com.mooregreatsoftware.gitprocess.lib;

import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * Functions to make it more convenient to execute functions, such as wrapping lambdas.
 */
public final class ExecUtils {

    /**
     * Executes the {@link Callable}, wrapping any checked exceptions in either a {@link IllegalStateException} or
     * {@link RuntimeException}.
     * <p>
     * Acts like {@link #v(ExceptionAction)} but returns a value.
     *
     * @param callable what to run
     * @param <T>      what to return
     */
    public static <T> T e(Callable<T> callable) {
        try {
            return callable.call();
        }
        catch (GitAPIException | IOException e) {
            throw new IllegalStateException(e);
        }
        catch (Exception e) {
            if (e instanceof RuntimeException) throw (RuntimeException)e;
            else throw new RuntimeException(e);
        }
    }


    /**
     * Executes the {@link Callable}, wrapping any checked exceptions in either a {@link IllegalStateException} or
     * {@link RuntimeException}.
     * <p>
     * Acts like {@link #e(Callable)} but does not return a value.
     *
     * @param callable what to run
     * @see #e(Callable)
     */
    public static void v(ExceptionAction callable) {
        try {
            callable.call();
        }
        catch (GitAPIException | IOException e) {
            throw new IllegalStateException(e);
        }
        catch (Exception e) {
            if (e instanceof RuntimeException) throw (RuntimeException)e;
            else throw new RuntimeException(e);
        }
    }


    /**
     * Similar interface as {@link Callable} but does not return anything. Used for {@link GitLib#v(ExceptionAction)}
     */
    public interface ExceptionAction {
        void call() throws Exception;
    }

}
