package com.mooregreatsoftware.gitprocess

import spock.lang.Subject

@Subject(NewFeatureBranch)
@SuppressWarnings("GroovyPointlessBoolean")
class NewFeatureBranchSpec extends GitSpecification {

    def setup() {
        createFiles(gitLib, ".gitignore").commit("initial")
    }


    def "smoke"() {
        createFiles(".gitignore")
        gitLib.commit("initial")
        def featureBranch = NewFeatureBranch.newFeatureBranch(gitLib, 'new_branch')

        expect:
        featureBranch
    }


    def "should create the named branch against origin/master"() {
        def gl = cloneRepo("master", "origin")
        def branches = gl.branches()
        def master = branches.branch("master").get()
        branches.createBranch("other_branch", master).checkout()
        createFiles(gl, "a").commit("a")
        createFiles(gl, "b").commit("b")

        when:
        def newBranch = NewFeatureBranch.newFeatureBranch(gl, "test_branch")

        then:
        newBranch.shortName() == "test_branch"

        and: "the branch was created off of origin/master"
        def integrationBranch = branches.integrationBranch().get()
        newBranch.objectId() == integrationBranch.objectId()
        integrationBranch.shortName() == "origin/master"
    }


    def "should bring committed changes on _parking_ over to the new branch"() {
        def gl = cloneRepo("master", "origin")
        def branches = gl.branches()

        // change to _parking_ and make some commits there
        branches.createBranch("_parking_", branches.branch("master").get()).checkout()
        createFiles(gl, "a").commit("a")
        createFiles(gl, "b").commit("b")

        // remember this since "test_branch" should be created off of _parking_, but _parking_ will be deleted
        def parkingObjId = branches.branch("_parking_").get().objectId()

        when:
        def newBranch = NewFeatureBranch.newFeatureBranch(gl, "test_branch")

        then:
        newBranch.shortName() == "test_branch"

        and: "_parking_ was deleted"
        branches.branch("_parking_").isPresent() == false

        and: "the branch was created based on _parking_, not origin/master"
        newBranch.objectId() == parkingObjId
        newBranch.objectId() != branches.integrationBranch().get().objectId()
    }


    def "should move new branch over to the integration branch"() {
        def gl = cloneRepo("master", "origin")
        def branches = gl.branches()

        // change to _parking_
        branches.createBranch("_parking_", branches.branch("master").get()).checkout()

        // make some changes on "origin/master"
        createFiles(gitLib, "a").commit("a")
        createFiles(gitLib, "b").commit("b")
        gl.fetch()

        // remember this since _parking_ will be deleted
        def parkingObjId = branches.branch("_parking_").get().objectId()

        when:
        def newBranch = NewFeatureBranch.newFeatureBranch(gl, "test_branch")

        then:
        newBranch.shortName() == "test_branch"

        and: "_parking_ was deleted"
        branches.branch("_parking_").isPresent() == false

        and: "the branch was created based on origin/master, not _parking_"
        newBranch.objectId() != parkingObjId
        newBranch.objectId() != branches.branch("master").get().objectId()
        newBranch.objectId() == branches.integrationBranch().get().objectId()
        branches.integrationBranch().get().shortName() == "origin/master"
    }


    def "should bring new/uncommitted changes on _parking_ over to the new branch"() {
        def branches = gitLib.branches()

        branches.createBranch("_parking_", branches.branch("master").get()).checkout()
        createFiles(gitLib, "a").commit("some files")
        createFiles(gitLib, "b") // added but not committed
        createFilesNoAdd(gitLib, "c") // not added or committed

        when:
        def newBranch = NewFeatureBranch.newFeatureBranch(gitLib, "test_branch")

        then:
        newBranch.shortName() == "test_branch"
        branches.currentBranch().get() == newBranch

        and: "_parking_ was deleted"
        branches.branch("_parking_").isPresent() == false

        and:
        new File(gitLib.workingDirectory(), "a").exists()
        new File(gitLib.workingDirectory(), "b").exists()
        new File(gitLib.workingDirectory(), "c").exists()
    }

}
