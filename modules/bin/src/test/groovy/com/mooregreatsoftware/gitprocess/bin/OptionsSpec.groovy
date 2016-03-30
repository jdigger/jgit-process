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

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(Options)
@SuppressWarnings(["GroovyPointlessBoolean", "SpellCheckingInspection"])
abstract class OptionsSpec extends Specification {

    def setup() {
        System.setProperty("gitprocess.logging.testing", "true")
    }


    def cleanup() {
        System.clearProperty("gitprocess.logging.testing")
    }


    def "logging options"() {
        def options

        when:
        options = TestOptions.create(["-i"])._2()

        then:
        options.useInfoLogging() == true
        options.useQuietLogging() == false
        options.useVerboseLogging() == false

        when:
        options = TestOptions.create(["-q"])._2()

        then:
        options.useInfoLogging() == false
        options.useQuietLogging() == true
        options.useVerboseLogging() == false

        when:
        options = TestOptions.create(["-v"])._2()

        then:
        options.useInfoLogging() == false
        options.useQuietLogging() == false
        options.useVerboseLogging() == true

        when: // no logging options are given
        options = TestOptions.create([])._2()

        then:
        options.useInfoLogging() == true
        options.useQuietLogging() == false
        options.useVerboseLogging() == false
    }


    @Unroll
    def "logging options errors: #args"() {
        when:
        def options = TestOptions.create(args)._1()

        then:
        options.contains("are mutually exclusive")

        where:
        args << [["-v", "-i"], ["-v", "-q"], ["-i", "-q"]]
    }


    def "version option"() {
        when:
        def options = TestOptions.create(["--version"])._1()

        then:
        options.contains("version: ")
    }


    def "unrecognized option"() {
        when:
        def options = TestOptions.create(["--bogus"])._1()

        then:
        options.contains("bogus is not a recognized option")
    }


    def "help option"() {
        when:
        def options = TestOptions.create(["-?"])._1()

        then:
        options.contains("USAGE: feature usage")
        options.contains("feature descr")
        options.toString().matches(/(?s).*-\?, -h, --help\s+show help.*/)
        options.contains("version: ")
    }

}
