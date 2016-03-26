package com.mooregreatsoftware.gitprocess

import spock.lang.Subject

@Subject(Branch)
class BranchSpec extends GitSpecification {

    def "ContainsAllOf"() {
        createFiles(".gitignore")
        gitLib.commit("initial")
        gitLib.branches().createBranch("newBranch", gitLib.branches().integrationBranch().get()).checkout()
        createFiles("another_file.txt")
        gitLib.commit("added another file")

        expect:
        Branch.of(gitLib, "newBranch").containsAllOf('master')
        !Branch.of(gitLib, "master").containsAllOf('newBranch')
    }

}
