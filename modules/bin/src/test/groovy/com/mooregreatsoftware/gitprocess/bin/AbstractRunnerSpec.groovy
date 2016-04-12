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

import com.mooregreatsoftware.gitprocess.lib.GitLib
import com.mooregreatsoftware.gitprocess.lib.GitSpecification
import groovy.transform.CompileStatic
import javaslang.control.Either
import spock.lang.Unroll

import static com.mooregreatsoftware.gitprocess.bin.AbstractRunner.B.AbstractBuilder
import static com.mooregreatsoftware.gitprocess.bin.AbstractRunner.B.GitLibSetter
import static com.mooregreatsoftware.gitprocess.bin.Runner.STOP_ON_FUNCTION_CODE
import static com.mooregreatsoftware.gitprocess.bin.Runner.STOP_ON_OPTIONS_CODE
import static javaslang.control.Either.left
import static javaslang.control.Either.right

class AbstractRunnerSpec extends GitSpecification {

    def setup() {
        System.setProperty("gitprocess.logging.testing", "true")
    }


    def cleanup() {
        System.clearProperty("gitprocess.logging.testing")
    }


    @Unroll
    def "run with #args -> #result"() {
        def runner = TestingAbstractRunner.builder().gitLib(origin).cliArgs(args as String[]).build()

        when:
        def exitValue = runner.run()

        then:
        exitValue == result

        where:
        args       || result
        []         || 0
        ["-i"]     || 0
        ["-h"]     || STOP_ON_OPTIONS_CODE
        ["--fail"] || STOP_ON_FUNCTION_CODE
    }


    @CompileStatic
    static class TestingAbstractRunner extends AbstractRunner<TestOptions, String, String> {

        protected TestingAbstractRunner(GitLib gitLib, TestOptions options) {
            super(gitLib, options)
        }


        protected Either<String, String> mainFunc(TestOptions options) {
            return options.shouldFail() ? left("something") : right("worked")
        }


        public static GitLibSetter builder() {
            return new Builder()
        }


        @CompileStatic
        public static class Builder extends AbstractBuilder<TestOptions, CharSequence> {

            protected Either<CharSequence, TestOptions> options(String[] args) {
                return TestOptions.createAsEither(args)
            }


            protected Runner doBuild(GitLib gitLib, TestOptions options) {
                return new TestingAbstractRunner(gitLib, options)
            }
        }

    }
}
