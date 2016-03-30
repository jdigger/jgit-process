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

import com.mooregreatsoftware.gitprocess.config.BranchConfig
import com.mooregreatsoftware.gitprocess.lib.config.StoredBranchConfig
import spock.lang.Subject

@Subject(StoredBranchConfig)
@SuppressWarnings(["GroovyPointlessBoolean", "SpellCheckingInspection"])
class StoredBranchConfigSpec extends GitSpecification {

    def remoteConfig = origin.remoteConfig()


    def "local integration branch"() {
        expect:
        !remoteConfig.hasRemotes()

        when:
        createCommit "fooble"

        then:
        config.integrationBranch().isPresent() == true
        config.integrationBranch().get().shortName() == 'master'

        when:
        createAndCheckoutBranch "another_branch", "master"
        createCommit "fooble2"

        then:
        integrationBranchIs "master"

        when:
        origin.branches().removeBranch branch("master")

        then:
        integrationBranchDoesNotExist

        when: 'set config'
        config.integrationBranch branch("another_branch")

        then:
        integrationBranchIs "another_branch"
    }


    def "remote integration branch"() {
        expect:
        !remoteConfig.hasRemotes()

        when:
        useOrigin

        createCommit "fooble"

        createAndCheckoutBranch "another_branch", "master"
        createCommit "fooble2"

        local "another_branch"
        useLocal

        then: 'cloned from a remote with a "master" branch, no config'
        local.remoteConfig().hasRemotes()
        integrationBranchIs "origin/master"

        when: 'remove the remote "master" branch, no config'
        removeRemoteBranch "master"
        local.fetch()

        then:
        integrationBranchDoesNotExist

        when: 'set config'
        config.integrationBranch branch("origin/another_branch")
        local.fetch()

        then:
        integrationBranchIs "origin/another_branch"
    }


    def "set upstream"() {
        expect:
        remoteConfig.hasRemotes() == false

        when:
        createCommit "fooble"

        and: "checkout a new branch, another_branch, and commit to it"
        createAndCheckoutBranch "another_branch", "master"
        createCommit "fooble2"

        then: "another_branch does not have an upstream"
        upstreamDoesNotExist("another_branch")

        when: "set the upstream on another_branch to local master"
        branch("another_branch").upstream branch("master")

        then: "another_branch's upstream is master"
        upstreamIs "another_branch", "master"

        when:
        useLocal

        then:
        upstreamDoesNotExist("master")

        when:
        branch("master").upstream branch("origin/master")

        then:
        upstreamIs "master", "origin/master"
    }

    // **********************************************************************
    //
    // HELPERS
    //
    // **********************************************************************


    void integrationBranchIs(String branchName) {
        assert config.integrationBranch().isPresent() == true
        assert config.integrationBranch().get().shortName() == branchName
    }


    def getIntegrationBranchDoesNotExist() {
        assert config.integrationBranch().isPresent() == false
        1
    }


    void upstreamDoesNotExist(String branchName) {
        assert branch(branchName).upstream().isPresent() == false
    }


    void upstreamIs(String branchName, String upstreamName) {
        assert branch(branchName).upstream().isPresent() == true
        assert branch(branchName).upstream().get().shortName() == upstreamName
    }


    void removeRemoteBranch(String branchName) {
        origin.branches().removeBranch(origin.branches().branch(branchName).get())
    }


    BranchConfig getConfig() {
        currentLib.branchConfig()
    }

}
