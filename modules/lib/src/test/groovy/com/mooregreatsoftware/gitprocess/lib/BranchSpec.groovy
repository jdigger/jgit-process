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

import groovy.transform.CompileStatic
import spock.lang.Subject

@Subject(Branch)
@SuppressWarnings("GroovyPointlessBoolean")
class BranchSpec extends GitSpecification {

    def setup() {
        createFiles(origin, ".gitignore").commit("initial")
    }


    def "contains all of"() {
        createAndCheckoutBranch "newBranch", "master"
        createCommit "another_file"

        expect:
        containsAllOf "newBranch", "master"
        doesNotContainAllOf "master", "newBranch"
    }


    def "simpleName"() {
        useLocal

        expect:
        branch("master").simpleName() == "master"
        branch("origin/master").simpleName() == "master"
        createBranch("not_a_remote/master", "master").simpleName() == "not_a_remote/master"
    }


    def "remoteName"() {
        useLocal

        when:
        def master = branch("master")
        def originMaster = branch("origin/master")
        def differentBranch = createBranch("not_a_remote/master", "master")

        then:
        master.remoteName().isPresent() == false
        originMaster.remoteName().isPresent() == true
        originMaster.remoteName().get() == "origin"
        differentBranch.remoteName().isPresent() == false
    }


    def "previous remote SHA"() {
        useLocal

        def sha

        when:
        sha = branch("master").previousRemoteOID()

        then:
        sha == null

        when:
        branch("master").recordLastSyncedAgainst()

        useOrigin
        createCommit "a"

        useLocal
        sha = branch("master").previousRemoteOID()

        then:
        sha != null
    }

    // **********************************************************************
    //
    // HELPERS
    //
    // **********************************************************************

    void containsAllOf(String superBranch, String subBranch) {
        assert currentLib.branches().branch(superBranch).containsAllOf(subBranch) == true
    }


    void doesNotContainAllOf(String superBranch, String subBranch) {
        assert currentLib.branches().branch(superBranch).containsAllOf(subBranch) == false
    }

}
