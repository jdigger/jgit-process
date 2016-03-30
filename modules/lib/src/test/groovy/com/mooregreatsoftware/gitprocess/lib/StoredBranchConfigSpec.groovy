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
package com.mooregreatsoftware.gitprocess.lib

import spock.lang.Subject

@SuppressWarnings("GroovyPointlessBoolean")
class StoredBranchConfigSpec extends GitSpecification {

    def remoteConfig = gitLib.remoteConfig()

    @Subject
    def config = gitLib.branchConfig()


    def "local integration branch"() {
        expect:
        !remoteConfig.hasRemotes()

        when:
        createFiles("fooble.txt")
        gitLib.commit("initial with fooble")

        then:
        config.integrationBranch().isPresent()
        config.integrationBranch().get().shortName() == 'master'

        when:
        def master = gitLib.branches().branch("master").get()
        def anotherBranch = gitLib.branches().createBranch("another_branch", master)
        gitLib.checkout(anotherBranch)
        createFiles("fooble2.txt")
        gitLib.commit("fooble2")

        then:
        config.integrationBranch().isPresent()
        config.integrationBranch().get().shortName() == 'master'

        when:
        gitLib.branches().removeBranch(master)

        then:
        !config.integrationBranch().isPresent()

        when: 'set config'
        config.integrationBranch(anotherBranch)

        then:
        config.integrationBranch().isPresent()
        config.integrationBranch().get().shortName() == "another_branch"
    }


    def "remote integration branch"() {
        expect:
        !remoteConfig.hasRemotes()

        when:
        createFiles("fooble.txt").commit("initial with fooble")

        def originBranches = gitLib.branches()
        def master = originBranches.branch("master").get()

        originBranches.createBranch("another_branch", master).checkout()
        createFiles("fooble2.txt")
        gitLib.commit("fooble2")

        def gl = cloneRepo('another_branch', 'origin')

        then: 'cloned from a remote with a "master" branch, no config'
        gl.remoteConfig().hasRemotes()
        gl.branchConfig().integrationBranch().get().shortName() == 'origin/master'

        when: 'remove the remote "master" branch, no config'
        originBranches.removeBranch(master)
        gl.fetch()

        then:
        !gl.branchConfig().integrationBranch().isPresent()

        when: 'set config'
        gl.branchConfig().integrationBranch(gl.branches().branch("origin/another_branch").get())
        gl.fetch()

        then:
        gl.branchConfig().integrationBranch().isPresent()
        gl.branchConfig().integrationBranch().get().shortName() == "origin/another_branch"

        cleanup:
        gl?.workingDirectory()?.deleteDir()
        gl?.close()
    }


    def "set upstream"() {
        expect:
        remoteConfig.hasRemotes() == false

        when:
        createFiles("fooble.txt").commit("initial with fooble")

        def master = gitLib.branches().branch("master").get()

        and: "checkout a new branch, another_branch, and commit to it"
        def anotherBranch = gitLib.branches().createBranch("another_branch", master)
        anotherBranch.checkout()
        createFiles("fooble2.txt").commit("fooble2")

        then: "another_branch does not have an upstream"
        anotherBranch.upstream().isPresent() == false

        when: "set the upstream on another_branch to local master"
        anotherBranch.upstream(master)

        then: "another_branch's upstream is master"
        anotherBranch.upstream().isPresent() == true
        anotherBranch.upstream().get().shortName() == "master"

        when:
        def gl = cloneRepo('master', 'origin')

        def clonedMaster = gl.branches().branch("master").get()

        then:
        clonedMaster.upstream().isPresent() == false

        when:
        def originMaster = gl.branches().branch("origin/master").get()
        clonedMaster.upstream(originMaster)

        then:
        clonedMaster.upstream().isPresent()
        clonedMaster.upstream().get().shortName() == "origin/master"
    }

}
