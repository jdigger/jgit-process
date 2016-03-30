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

import com.mooregreatsoftware.gitprocess.config.GeneralConfig
import spock.lang.Unroll

@SuppressWarnings("GroovyPointlessBoolean")
class SyncOptionsSpec extends OptionsSpec {

    @Unroll
    def "rebase/merge #args - #defaultValue => #rebaseResult"() {
        when:
        def options = SyncOptions.create(args as String[], generalConfig).get()

        then:
        options.rebase() == rebaseResult
        options.merge() == !rebaseResult

        where:
        args        | defaultValue || rebaseResult
        ["-r"]      | false        || true
        ["--merge"] | false        || false
        ["-r"]      | true         || true
        ["--merge"] | true         || false
        []          | true         || true
        []          | false        || false

        generalConfig = { defaultValue } as GeneralConfig
    }


    @Unroll
    def "--rebase --merge"() {
        expect:
        SyncOptions.create(["-r", "--merge"] as String[], { false } as GeneralConfig).isLeft()
        SyncOptions.create(["-r", "--merge"] as String[], { false } as GeneralConfig).getLeft().
            toString().contains "USAGE: git sync [OPTIONS]"
    }

}
