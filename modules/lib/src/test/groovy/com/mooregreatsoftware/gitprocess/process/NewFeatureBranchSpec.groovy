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
package com.mooregreatsoftware.gitprocess.process

import com.mooregreatsoftware.gitprocess.lib.GitSpecification
import spock.lang.Subject

import static com.mooregreatsoftware.gitprocess.config.BranchConfig.PARKING_BRANCH_NAME
import static org.eclipse.jgit.lib.Constants.MASTER

@Subject(NewFeatureBranch)
@SuppressWarnings("GroovyPointlessBoolean")
class NewFeatureBranchSpec extends GitSpecification {

    def setup() {
        createFiles(origin, ".gitignore").commit("initial")
    }


    def "should create the named branch against origin/master"() {
        useLocal

        createAndCheckoutBranch "other_branch", MASTER
        createCommit "a"
        createCommit "b"

        when:
        def newBranch = NewFeatureBranch.newFeatureBranch(local, "test_branch", false).get()

        then:
        newBranch.shortName() == "test_branch"

        and: "the branch was created off of origin/master"
        def integrationBranch = local.branches().integrationBranch().get()
        newBranch.objectId() == integrationBranch.objectId()
        integrationBranch.shortName() == "origin/master"
    }


    def "should bring committed changes on _parking_ over to the new branch"() {
        useLocal

        // change to _parking_ and make some commits there
        createAndCheckoutBranch PARKING_BRANCH_NAME, MASTER
        createCommit "a"
        createCommit "b"

        // remember this since "test_branch" should be created off of _parking_, but _parking_ will be deleted
        def parkingObjId = branch(PARKING_BRANCH_NAME).objectId()

        when:
        def newBranch = NewFeatureBranch.newFeatureBranch(local, "test_branch", false).get()

        then:
        newBranch.shortName() == "test_branch"

        and:
        parkingDoesNotExist

        and: "the branch was created based on _parking_, not origin/master"
        newBranch.objectId() == parkingObjId
        newBranch.objectId() != branch("origin/master").objectId()
    }


    def "should move new branch over to the integration branch"() {
        useLocal
        createAndCheckoutBranch PARKING_BRANCH_NAME, MASTER

        useOrigin
        createCommit "a"
        createCommit "b"

        useLocal
        local.fetch()

        // remember this since _parking_ will be deleted
        def parkingObjId = branch(PARKING_BRANCH_NAME).objectId()

        when:
        def newBranch = NewFeatureBranch.newFeatureBranch(local, "test_branch", false).get()

        then:
        newBranch.shortName() == "test_branch"

        and:
        parkingDoesNotExist

        and: "the branch was created based on origin/master, not _parking_"
        newBranch.objectId() != parkingObjId
        newBranch.objectId() != branch(MASTER).objectId()
        newBranch.objectId() == branch("origin/master").objectId()
    }


    def "should bring new/uncommitted changes on _parking_ over to the new branch"() {
        def branches = origin.branches()

        createAndCheckoutBranch PARKING_BRANCH_NAME, MASTER
        createCommit "a"
        createFiles "b" // added but not committed
        createFilesNoAdd "c" // not added or committed

        when:
        def newBranch = NewFeatureBranch.newFeatureBranch(origin, "test_branch", false).get()

        then:
        newBranch.shortName() == "test_branch"
        branches.currentBranch().get() == newBranch

        and:
        parkingDoesNotExist

        and:
        fileExists "a"
        fileExists "b"
        fileExists "c"
    }

}
