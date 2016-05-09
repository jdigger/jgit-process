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

import com.mooregreatsoftware.gitprocess.config.BranchConfig
import com.mooregreatsoftware.gitprocess.config.RemoteConfig
import com.mooregreatsoftware.gitprocess.github.JettySupport
import com.mooregreatsoftware.gitprocess.lib.AbstractBranches
import com.mooregreatsoftware.gitprocess.lib.Branch
import com.mooregreatsoftware.gitprocess.lib.GitLib
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Subject

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static java.net.HttpURLConnection.HTTP_CREATED
import static java.net.HttpURLConnection.HTTP_NOT_FOUND

@Subject(ToMaster)
@SuppressWarnings("GroovyPointlessBoolean")
class ToMasterSpec extends Specification {
//class ToMasterSpec extends GitSpecification {

    @AutoCleanup
    JettySupport jetty = new JettySupport()


    def setup() {
//        createFiles(origin, ".gitignore").commit("initial")
    }

    @Ignore
    def "should create the named branch against origin/master"() {
        def prUrl = { "http://localhost:${jetty.serverPort}/jdigger/testproj/pull/123" }
        jetty.addPostHandler({ HttpServletRequest request, HttpServletResponse response ->
            if (request.pathInfo == "/repos/jdigger/testproj/pulls") {
                def slurper = new JsonSlurper()
                def json = slurper.parse(request.getInputStream())
                response.status = HTTP_CREATED
                response.writer.println JsonOutput.toJson(
                    [
                        number  : 123,
                        html_url: prUrl.call(),
                        title   : json.title,
                        head    : [ref: json.head],
                        base    : [ref: json.base],
                    ]
                )
                return
            }
            response.status = HTTP_NOT_FOUND
        } as JettySupport.SimpleHandler)
        // http://localhost:54536/jdigger/testproj: http://localhost:54536/jdigger/testproj/info/refs?service=git-upload-pack
        jetty.addGetHandler({ HttpServletRequest request, HttpServletResponse response ->
            if (request.pathInfo == "/jdigger/testproj/info/refs") {
                response.setContentType("application/x-git-upload-pack-advertisement")

            }
            return
        } as JettySupport.SimpleHandler)

        jetty.start()

//        createCommit("a")
//        origin.branches().createBranch("new_branch", "master").checkout()
//        createCommit("b")
//        origin.generalConfig().oauthToken("93abcd234234")
//        origin.remoteConfig().remoteAdd("testRemote", new URIish("http://localhost:${jetty.serverPort}/jdigger/testproj"))
//        createFakeRemoteBranch("testRemote/master")
//
//        createAndCheckoutBranch "other_branch", MASTER
//        createCommit "a"
//        createCommit "b"
//        def gitLib = Mock(GitLib) {
//            branches() >> Mock(Branches) {
//                currentBranch() >> Mock(Branch)
//                    return "Not currently on a branch";
//
//                if (branches.integrationBranch() == null)
//                    return "There is no integration branch";
//
//                if (branches.onParking())
//
//            }
//                return "You can not do a sync while on _parking_";
//
//            if (gitLib.hasUncommittedChanges())
//        }

        def gitLib = Mock(GitLib) {
            hasUncommittedChanges() >> false
        }

//        def sha = "bacacaacacacacac1234234234234234234232423423422".bytes
//        def refFactory = new JgitBranches.RefFactory() {
//            @Override
//            Ref lookup(String refName) {
//                if (refName == "HEAD") return new SymbolicRef(refName, lookup("refs/heads/master"))
//                else if (refName == "refs/heads/master") return new ObjectIdRef(null, refName, RevCommit.parse(sha)) {
//                    @Override
//                    ObjectId getPeeledObjectId() {
//                        return RevCommit.parse(sha)
//                    }
//
//
//                    @Override
//                    boolean isPeeled() {
//                        return false
//                    }
//                }
//                else if (refName == "refs/remotes/origin/master") return new ObjectIdRef(null, refName, RevCommit.parse(sha)) {
//                    @Override
//                    ObjectId getPeeledObjectId() {
//                        return RevCommit.parse(sha)
//                    }
//
//
//                    @Override
//                    boolean isPeeled() {
//                        return false
//                    }
//                }
//                else throw new IllegalArgumentException("Unknown $refName")
//            }
//        }
//
//        def branchFactory = new JgitBranches.BranchFactory() {
//            @Override
//            Branch lookup(String branchName) {
//                return new AbstractBranch(gitLib, refFactory.lookup(branchName)) {
//                    @Override
//                    boolean containsAllOf(String otherBranchName) {
//                        return false
//                    }
//
//
//                    @Override
//                    boolean contains(ObjectId oid) {
//                        return false
//                    }
//                }
//            }
//        }

        def branchConfig = Mock(BranchConfig) {
            integrationBranch() >> branchFactory.lookup("refs/remotes/origin/master")
        }

        gitLib.branches() >> new AbstractBranches() {
            @Override
            protected BranchConfig config() {
                return branchConfig
            }


            @Override
            protected void doCreateBranch(String branchName, Branch baseBranch) throws Exception {

            }


            @Override
            protected void doRemoveBranch(Branch branch) throws Exception {

            }


            @Override
            Branch currentBranch() {
                return Mock(Branch) {
                    name() >> "master"
                }
            }


            @Override
            Branch branch(String branchName) {
                return Mock(Branch) {
                    name() >> branchName
                }
            }


            @Override
            Iterator<Branch> allBranches() {
                return null
            }
        }

        gitLib.branchConfig() >> branchConfig
        gitLib.remoteConfig() >> Mock(RemoteConfig) {
        }

        0 * gitLib.(_)

        when:
        ToMaster toMaster = new ToMaster(gitLib)
        def errMsg = toMaster.toMaster(false, false)

        then:
        errMsg == null
    }

//    def "should bring committed changes on _parking_ over to the new branch"() {
//        useLocal
//
//        // change to _parking_ and make some commits there
//        createAndCheckoutBranch PARKING_BRANCH_NAME, MASTER
//        createCommit "a"
//        createCommit "b"
//
//        // remember this since "test_branch" should be created off of _parking_, but _parking_ will be deleted
//        def parkingObjId = branch(PARKING_BRANCH_NAME).objectId()
//
//        when:
//        def newBranch = NewFeatureBranch.newFeatureBranch(local, "test_branch", false).get()
//
//        then:
//        newBranch.shortName() == "test_branch"
//
//        and:
//        parkingDoesNotExist
//
//        and: "the branch was created based on _parking_, not origin/master"
//        newBranch.objectId() == parkingObjId
//        newBranch.objectId() != branch("origin/master").objectId()
//    }
//
//
//    def "should move new branch over to the integration branch"() {
//        useLocal
//        createAndCheckoutBranch PARKING_BRANCH_NAME, MASTER
//
//        useOrigin
//        createCommit "a"
//        createCommit "b"
//
//        useLocal
//        local.fetch()
//
//        // remember this since _parking_ will be deleted
//        def parkingObjId = branch(PARKING_BRANCH_NAME).objectId()
//
//        when:
//        def newBranch = NewFeatureBranch.newFeatureBranch(local, "test_branch", false).get()
//
//        then:
//        newBranch.shortName() == "test_branch"
//
//        and:
//        parkingDoesNotExist
//
//        and: "the branch was created based on origin/master, not _parking_"
//        newBranch.objectId() != parkingObjId
//        newBranch.objectId() != branch(MASTER).objectId()
//        newBranch.objectId() == branch("origin/master").objectId()
//    }
//
//
//    def "should bring new/uncommitted changes on _parking_ over to the new branch"() {
//        def branches = origin.branches()
//
//        createAndCheckoutBranch PARKING_BRANCH_NAME, MASTER
//        createCommit "a"
//        createFiles "b" // added but not committed
//        createFilesNoAdd "c" // not added or committed
//
//        when:
//        def newBranch = NewFeatureBranch.newFeatureBranch(origin, "test_branch", false).get()
//
//        then:
//        newBranch.shortName() == "test_branch"
//        branches.currentBranch() == newBranch
//
//        and:
//        parkingDoesNotExist
//
//        and:
//        fileExists "a"
//        fileExists "b"
//        fileExists "c"
//    }

}
