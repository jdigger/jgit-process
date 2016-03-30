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

import javaslang.control.Either
import spock.lang.Specification
import spock.lang.Unroll

import static com.mooregreatsoftware.gitprocess.bin.AbstractRunner.STOP_ON_FUNCTION_CODE
import static com.mooregreatsoftware.gitprocess.bin.AbstractRunner.STOP_ON_OPTIONS_CODE
import static com.mooregreatsoftware.gitprocess.bin.AbstractRunner.run

class AbstractRunnerSpec extends Specification {

    def setup() {
        System.setProperty("gitprocess.logging.testing", "true")
    }


    def cleanup() {
        System.clearProperty("gitprocess.logging.testing")
    }


    @Unroll
    def "run with #args -> #result"() {
        given:
        def exitValue

        when:
        exitValue = run(
            args as String[],
            { args -> TestOptions.createAsEither(args) },
            { it.shouldFail() ? Either.left("something") : Either.right("worked") }
        )

        then:
        exitValue == result

        where:
        args       || result
        []         || 0
        ["-i"]     || 0
        ["-h"]     || STOP_ON_OPTIONS_CODE
        ["--fail"] || STOP_ON_FUNCTION_CODE
    }

}
