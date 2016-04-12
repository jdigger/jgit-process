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

@SuppressWarnings("GroovyPointlessBoolean")
class PullReqOptionsSpec extends OptionsSpec {

    def "some options"() {
        def options

        when:
        options = PullReqOptions.create([] as String[]).get()

        then:
        options.prTitle() == null
        options.issueOrPrID() == null

        when:
        options = PullReqOptions.create(["frooble"] as String[]).get()

        then:
        options.prTitle() == "frooble"
        options.issueOrPrID() == null

        when:
        options = PullReqOptions.create(["34"] as String[]).get()

        then:
        options.prTitle() == null
        options.issueOrPrID() == 34
        options.baseBranchName() == null
        options.headBranchName() == null
        options.remoteName() == null

        when:
        options = PullReqOptions.create(["--base-branch", "the_base", "--head-branch", "a_head"] as String[]).get()

        then:
        options.prTitle() == null
        options.issueOrPrID() == null
        options.baseBranchName() == "the_base"
        options.headBranchName() == "a_head"

        expect:
        // head and base branches can not be equal
        PullReqOptions.create(["--base-branch", "the_base", "--head-branch", "the_base"] as String[]).isLeft()
    }

}
