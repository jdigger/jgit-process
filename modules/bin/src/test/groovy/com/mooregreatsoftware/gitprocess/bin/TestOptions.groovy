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
package com.mooregreatsoftware.gitprocess.bin

import javaslang.Tuple2
import javaslang.control.Either
import joptsimple.OptionParser

import javax.annotation.Nonnull

class TestOptions extends Options {


    TestOptions(PrintStream output) {
        super(output)
    }


    @Nonnull
    protected OptionParser createOptionParser() {
        final OptionParser optionParser = super.createOptionParser();
        optionParser.accepts("fail", "Fail the function");
        return optionParser;
    }

    /**
     * Should the main function fail?
     */
    boolean shouldFail() {
        booleanValue("fail")
    }


    static Tuple2<CharSequence, TestOptions> create(List<String> args) {
        return create(args as String[])
    }


    static Either<CharSequence, TestOptions> createAsEither(String[] args) {
        def tuple2 = create(args as String[])
        return tuple2._1() != null ? Either.left(tuple2._1()) : Either.right(tuple2._2())
    }


    static Tuple2<CharSequence, TestOptions> create(String[] args) {
        def testOptions = new TestOptions(System.out)
        final String msgOption = testOptions.parse(args as String[]);
        return Tuple2.of(msgOption, testOptions);
    }


    @Nonnull
    String description() {
        return "feature descr"
    }


    @Nonnull
    String usageInfo() {
        return "feature usage"
    }


    boolean showHelp() {
        return helpOptionValue()
    }

}
