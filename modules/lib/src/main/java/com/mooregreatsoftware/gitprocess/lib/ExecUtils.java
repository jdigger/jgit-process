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

import org.checkerframework.dataflow.qual.Pure;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Function;

/**
 * Functions to make it more convenient to execute functions, such as wrapping lambdas.
 */
public final class ExecUtils {

    /**
     * Returns the Throwable as a String, including its stack trace
     */
    @Pure
    public static String toString(Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        pw.println(throwable.toString());
        throwable.printStackTrace(pw);
        return sw.toString();
    }


    /**
     * A function that will wrap a non-RuntimeException with an IllegalStateException. If it's already a
     * RuntimeException, leave it alone.
     */
    @Pure
    public static Function<Throwable, RuntimeException> exceptionTranslator() {
        return exp -> (exp instanceof RuntimeException) ? (RuntimeException)exp : new IllegalStateException(exp);
    }

}
