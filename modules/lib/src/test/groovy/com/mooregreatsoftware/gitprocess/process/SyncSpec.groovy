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

import com.mooregreatsoftware.gitprocess.lib.Branch
import com.mooregreatsoftware.gitprocess.lib.GitLib
import com.mooregreatsoftware.gitprocess.lib.GitSpecification
import com.mooregreatsoftware.gitprocess.lib.Rebaser
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import javaslang.control.Either
import spock.lang.Subject

import javax.annotation.Nonnull
import java.util.function.Function

import static com.mooregreatsoftware.gitprocess.config.BranchConfig.PARKING_BRANCH_NAME
import static org.eclipse.jgit.lib.Constants.MASTER

@Subject(Sync)
@SuppressWarnings("GroovyPointlessBoolean")
abstract class SyncSpec extends GitSpecification {

    def setup() {
        createFiles(origin, ".gitignore").commit("initial")
        createA
    }

    /*
     * Legend for the symbols below:
     *   i - integration branch (i.e., 'origin/master')
     *   l - local/working feature branch (i.e., 'fb')
     *   r - remote feature branch (i.e., 'origin/fb')
     */

    private Map<String, String> nameToSha = [:]


    @CompileStatic
    String getlSha() {
        localSha("fb")
    }


    @CompileStatic
    String getrSha() {
        remoteSha("fb")
    }


    @CompileStatic
    String getiSha() {
        remoteSha("master")
    }


    @Override
    Object getProperty(String property) {
        if (property.startsWith("create")) {
            def commitName = property.substring("create".length()).toLowerCase()

            String commitSha
            if (currentLib.jgit().status().call().hasUncommittedChanges()) {
                commitSha = commit(commitName).abbreviate(7).name()
            }
            else {
                commitSha = createCommit(commitName).get().abbreviate(7).name()
            }
            nameToSha.put(commitName, commitSha)
            println "${commitName.toUpperCase()} SHA = ${commitSha} > ${parent(commitSha, currentLib)}"
            return true
        }
        if (["rSha", "lSha", "iSha"].contains(property)) return super.getProperty(property)
        if (property.endsWith("Sha")) {
            def commitName = property.substring(0, property.length() - "Sha".length())
            return nameToSha.get(commitName)
        }
        return super.getProperty(property)
    }


    @Override
    void setProperty(String property, Object newValue) {
        if (["rSha", "lSha", "iSha"].contains(property)) {
            super.setProperty(property, newValue)
            return
        }
        if (property.endsWith("Sha")) {
            def commitName = property.substring(0, property.length() - "Sha".length())
            nameToSha.put(commitName, newValue as String)
            println "${commitName.toUpperCase()} SHA = ${newValue} > ${parent(newValue as String, currentLib)}"
            return
        }
        super.setProperty(property, newValue)
    }


    @CompileStatic
    Options createOptions(GitLib gitLib) {
        return new Options(gitLib, false, false)
    }


    static abstract class BasicSpecs extends SyncSpec {
        def "should fail for uncommitted changes"() {
            createCommit "a"
            createFiles "b" // add but don't commit

            when:
            def sync = run(createOptions(currentLib))

            then:
            sync.getLeft() == "You have uncommitted changes"
        }


        def "should fail for being on _parking_"() {
            createAndCheckoutBranch PARKING_BRANCH_NAME, MASTER

            when:
            def sync = run(createOptions(currentLib))

            then:
            sync.getLeft() == "You can not do a sync while on _parking_"
        }


        def "should error for uncommitted changes"() {
            useLocal
            createAndCheckoutBranch "other_branch", MASTER
            createCommit "a"
            createFiles "b" // add but don't commit

            when:
            def sync = run(createOptions(currentLib))

            then:
            sync.getLeft() == "You have uncommitted changes"
        }


        def "should merge/rebase and push"() {
            useLocal
            createAndCheckoutBranch "other_branch", MASTER
            createCommit "a"
            createCommit "b"

            when:
            def sync = run(createOptions(currentLib))

            then:
            sync.isRight()
        }


        def "should work when pushing with fast-forward"() {
            useOrigin
            createAndCheckoutBranch "fb", "master"
            createCommit "b"

            useLocal
            createCommit "c"

            when:
            syncIsRun()

            then:
            localAndRemoteAreSame()
            localSha("fb") == localSha("origin/fb")
        }


        def "should work with a different remote server name"() {
            useOrigin
            createAndCheckoutBranch "fb", "master"
            createCommit "b"

            local "fb", "a_remote"
            useLocal
            createCommit "c"

            when:
            syncIsRun()

            then:
            localAndRemoteAreSame()
            localSha("fb") == localSha("a_remote/fb")
        }


        def "should work when the branch name contains a slash"() {
            useOrigin
            createAndCheckoutBranch "user/fb"
            createCommit "b"

            local "user/fb"
            useLocal
            createCommit "c"

            when:
            syncIsRun(false)

            then:
            localSha("user/fb") == remoteSha("user/fb")
            localSha("user/fb") == localSha("origin/user/fb")
        }
    }


    static class WithAMerge extends BasicSpecs {
        @Override
        Options createOptions(GitLib gitLib) {
            return new Options(gitLib, true, false)
        }


        def "should work when pushing with non-fast-forward"() {
            useOrigin
            changeFileAndCommit "a"
            createAndCheckoutBranch "fb", "master"

            local "fb"

            useOrigin
            changeFileAndCommit "a"

            when:
            syncIsRun()

            then:
            localAndRemoteAreSame()
            localSha("fb") == localSha("origin/fb")
        }
    }


    static class When_forcing_the_push_with_a_merge extends BasicSpecs {
        @Override
        @CompileStatic
        Options createOptions(GitLib gitLib) {
            return new Options(gitLib, true, false)
        }


        def "should work when pushing with non-fast-forward"() {
            useOrigin
            changeFileAndCommit "a"
            createAndCheckoutBranch "fb", "master"

            local "fb"

            useOrigin
            changeFileAndCommit "a"

            when:
            syncIsRun()

            then:
            localAndRemoteAreSame()
            localSha("fb") == localSha("origin/fb")
        }
    }

    static class RebasingBasicSpecs extends BasicSpecs {
        @Override
        @CompileStatic
        Options createOptions(GitLib gitLib) {
            return new Options(gitLib, false, false)
        }
    }

    abstract static class When_rebasing extends SyncSpec {
        @Override
        @CompileStatic
        Options createOptions(GitLib gitLib) {
            return new Options(gitLib, false, false)
        }


        static class PieceByPiece { // purely for containment

            /**
             * <pre>
             *         i
             *        /
             * - A - C
             *   \
             *   B
             *   \
             *   l,r
             * </pre>
             *
             * Steps to get to this state:
             *   1. Changes have been applied to the integration branch
             *   2. No work has happened on the feature branch since the last `sync`
             */
            static class If_local_and_remote_match extends When_rebasing {

                void verifyStartState() {
                    assert lSha == bSha
                    assert rSha == bSha
                    assert iSha == cSha
                    assert parent(cSha, origin) == aSha
                    assert parent(bSha, origin) == aSha
                }

                /**
                 * <pre>
                 *         i
                 *        /
                 * - A - C - B1
                 *           /
                 *         l,r
                 * </pre>
                 */
                def "should work if no conflict"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"
                    createB

                    useLocal
                    createAndCheckoutBranch("fb", "origin/fb")
                    writeSyncControl()

                    useOrigin
                    checkout("master")
                    createC

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    localAndRemoteAreSame()
                    parent(lSha) == cSha
                }

                /**
                 *         i
                 *        /
                 * - A - C - XX
                 *   \      /
                 *   B     l
                 *   \
                 *   r
                 */
                def "should create an error if there is a conflict"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"
                    changeFileAndAdd "a"
                    createB

                    useLocal
                    createAndCheckoutBranch("fb", "origin/fb")
                    writeSyncControl()

                    useOrigin
                    checkout("master")
                    changeFileAndAdd "a" // force the conflict
                    createC

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    def exp = thrown(IllegalStateException)
                    exp.getMessage().contains("conflict")
                }
            }

            /**
             * <pre>
             *         i
             *        /
             * - A - C
             *   \
             *   B - D
             *   \   \
             *   r    l
             * </pre>
             *
             * Steps to get to this state:
             *   1. Changes have been applied to the integration branch
             *   2. Work has happened locally only on the feature branch since the last `sync`
             */
            static class If_local_is_fast_forward_of_remote extends When_rebasing {

                void verifyStartState() {
                    assert lSha == dSha
                    assert rSha == bSha
                    assert iSha == cSha
                    assert parent(cSha, origin) == aSha
                    assert parent(bSha, origin) == aSha
                    assert parent(dSha, local) == bSha
                }

                /**
                 * <pre>
                 *         i
                 *        /
                 * - A - C - B1 - D1
                 *                /
                 *              l,r
                 * </pre>
                 */
                def "should work if no conflict"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"
                    createB

                    useLocal
                    createAndCheckoutBranch("fb", "origin/fb")
                    writeSyncControl()
                    createD

                    useOrigin
                    checkout("master")
                    createC

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    localAndRemoteAreSame()
                    parent(parent(lSha)) == cSha
                }

                /**
                 *         i
                 *        /
                 * - A - C - XX
                 *   \       \
                 *   B - D    l
                 *   \   \
                 *   r   ??
                 */
                def "should create an error if there is a conflict"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"
                    changeFileAndAdd "a"
                    createB

                    useLocal
                    createAndCheckoutBranch("fb", "origin/fb")
                    writeSyncControl()
                    createD

                    useOrigin
                    checkout("master")
                    changeFileAndAdd "a" // force the conflict
                    createC

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    def exp = thrown(IllegalStateException)
                    exp.getMessage().contains("conflict")
                }
            }

            /**
             * <pre>
             *         i     l
             *        /       \
             * - A - C - B1 - D
             *   \
             *   B
             *   \
             *   r
             * </pre>
             *
             * Steps to get to this state:
             *   1. Changes have been applied to the integration branch
             *   2. The local feature branch is manually rebased with integration
             *   3. Work has happened locally only on the feature branch since the last `sync`
             */
            static class If_local_has_been_rebased_onto_integration extends When_rebasing {

                void verifyStartState() {
                    assert lSha == dSha
                    assert rSha == bSha
                    assert iSha == cSha
                    assert parent(cSha, origin) == aSha
                    assert parent(bSha, origin) == aSha
                    assert parent(b1Sha, local) == cSha
                    assert parent(parent(dSha, local)) == cSha
                }

                /**
                 * <pre>
                 *         i
                 *        /
                 * - A - C - B1 - D1
                 *                /
                 *              l,r
                 * </pre>
                 */
                def "should work"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"
                    createB
                    checkout("master")
                    createC

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")
                    Rebaser.rebase(local, branch("origin/master"))
                    b1Sha = branchTip(local, "fb")
                    createD

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    localAndRemoteAreSame()
                    localContainsCommits(["c", "b", "d"])
                }
            }

            /**
             * <pre>
             *         i   r
             *        /   /
             * - A - C - B1
             *   \
             *   B - D
             *       \
             *       l
             * </pre>
             *
             * Steps to get to this state:
             *   1. Changes have been applied to the integration branch
             *   2. The remote feature branch is rebased with integration, but no new work
             *   2. Work has happened locally on the feature branch
             */
            static class If_remote_has_already_been_rebased_onto_integration extends When_rebasing {

                void verifyStartState() {
                    assert lSha == dSha
                    assert rSha == b1Sha
                    assert iSha == cSha
                    assert parent(cSha, origin) == aSha
                    assert parent(b1Sha, origin) == cSha
                    assert parent(bSha, local) == aSha
                    assert parent(dSha, local) == bSha
                }

                /**
                 * <pre>
                 *     i
                 *    /
                 * - A - C - B1 - D1
                 *               /
                 *             l,r
                 * </pre>
                 */
                def "should work"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"
                    createB
                    checkout("master")
                    createC

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")
                    createD

                    useOrigin
                    checkout("fb")
                    Rebaser.rebase(origin, branch("master"))
                    b1Sha = branchTip(origin, "fb")

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    localAndRemoteAreSame()
                    lSha != dSha
                    parent(lSha) == b1Sha
                    parent(parent(lSha)) == cSha
                }

                /**
                 * <pre>
                 *         i   r
                 *        /   /
                 * - A - C - B1 - XX
                 *   \            /
                 *   B - D       l
                 *       \
                 *       ??
                 * </pre>
                 */
                def "should create an error if there is a conflict"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"
                    createB
                    checkout("master")
                    changeFileAndAdd "a"
                    createC

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")
                    changeFileAndAdd "a" // force the conflict
                    createD

                    useOrigin
                    checkout("fb")
                    Rebaser.rebase(origin, branch("master"))
                    b1Sha = branchTip(origin, "fb")

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    def exp = thrown(IllegalStateException)
                    exp.getMessage().contains("conflict")
                }
            }

            /**
             * <pre>
             *         i      r
             *        /       \
             * - A - C - B1 - D
             *   \
             *   B
             *   \
             *   l
             * </pre>
             *
             * Steps to get to this state:
             *   1. Changes have been applied to the integration branch
             *   2. The remote feature branch is rebased with integration, but with new work
             *   3. Work has not happened locally on the feature branch
             */
            static class If_remote_is_ahead_of_local extends When_rebasing {

                void verifyStartState() {
                    assert lSha == bSha
                    assert rSha == dSha
                    assert iSha == cSha
                    assert parent(cSha, origin) == aSha
                    assert parent(b1Sha, origin) == cSha
                    assert parent(bSha, local) == aSha
                    assert parent(dSha, origin) == b1Sha
                }

                /**
                 * <pre>
                 *     i
                 *    /
                 * - A - C - B1 - D1
                 *               /
                 *             l,r
                 * </pre>
                 */
                def "should work with control file"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"
                    createB
                    checkout("master")
                    createC

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")
                    writeSyncControl()

                    useOrigin
                    checkout("fb")
                    Rebaser.rebase(origin, branch("master"))
                    b1Sha = branchTip(origin, "fb")
                    createD

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    localAndRemoteAreSame()
                    parent(lSha) == b1Sha
                }

                /**
                 * <pre>
                 *     i
                 *    /
                 * - A - C - B1 - D1
                 *               /
                 *             l,r
                 * </pre>
                 */
                def "should work without control file"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"
                    createB
                    checkout("master")
                    createC

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")

                    useOrigin
                    checkout("fb")
                    Rebaser.rebase(origin, branch("master"))
                    b1Sha = branchTip(origin, "fb")
                    createD

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    localAndRemoteAreSame()
                    parent(lSha) == b1Sha
                }
            }

            /**
             * <pre>
             *         i
             *        /
             * - A - C
             *   \
             *   B - D
             *   \   \
             *   E   l
             *   \
             *   r
             * </pre>
             *
             * Steps to get to this state:
             *   1. Changes have been applied to the integration branch
             *   2. Work has happened locally
             *   3. Work has happened remotely
             */
            static class Remote_and_local_both_have_work_and_remote_is_not_rebased extends When_rebasing {

                void verifyStartState() {
                    assert lSha == dSha
                    assert rSha == eSha
                    assert iSha == cSha
                    assert parent(cSha, origin) == aSha
                    assert parent(eSha, origin) == bSha
                    assert parent(bSha, origin) == aSha
                    assert parent(dSha, local) == bSha
                }

                /**
                 * <pre>
                 *         i          l,r
                 *        /            \
                 * - A - C - B1 - E1 - D1
                 * </pre>
                 */
                def "should work if no conflict"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"
                    createB
                    checkout("master")
                    createC

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")
                    writeSyncControl()
                    createD

                    useOrigin
                    checkout("fb")
                    createE

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    localAndRemoteAreSame()
                    localContainsCommits(["c", "b", "e", "d"])
                }

                /**
                 * <pre>
                 *         i   l
                 *        /   /
                 * - A - C - XX
                 *   \
                 *   B - D
                 *   \   \
                 *   \   ??
                 *   E
                 *    \
                 *    r
                 * </pre>
                 */
                def "should work if conflict applying B"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"
                    changeFileAndAdd "a"
                    createB
                    checkout("master")
                    changeFileAndAdd "a" // conflict between B and C
                    createC

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")
                    writeSyncControl()
                    createD

                    useOrigin
                    checkout("fb")
                    createE

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    def exp = thrown(IllegalStateException)
                    exp.getMessage().contains("conflict")
                }

                /**
                 * <pre>
                 *         i      l
                 *        /       \
                 * - A - C - B1 - XX
                 *   \
                 *   B - D
                 *   \   \
                 *   E   ??
                 *   \
                 *   r
                 * </pre>
                 */
                def "should work if conflict applying remote"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"
                    createB
                    checkout("master")
                    changeFileAndAdd "a"
                    createC

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")
                    writeSyncControl()
                    createD

                    useOrigin
                    checkout("fb")
                    changeFileAndAdd "a" // conflict between C and E
                    createE

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    def exp = thrown(IllegalStateException)
                    exp.getMessage().contains("conflict")
                }

                /**
                 * <pre>
                 *         i           l
                 *        /            \
                 * - A - C - B1 - E1 - XX
                 *   \
                 *   B - D
                 *   \   \
                 *   E   ??
                 *   \
                 *   r
                 * </pre>
                 */
                def "should work if conflict applying local"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"
                    createB
                    checkout("master")
                    changeFileAndAdd "a"
                    createC

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")
                    writeSyncControl()
                    changeFileAndAdd "a" // conflict between C and D
                    createD

                    useOrigin
                    checkout("fb")
                    createE

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    def exp = thrown(IllegalStateException)
                    exp.getMessage().contains("conflict")
                }
            }

            /**
             * <pre>
             *         i   l
             *        /   /
             * - A - C - D
             *   \
             *   B
             *   \
             *   r
             * </pre>
             *
             * Steps to get to this state:
             *   1. Changes have been applied to the integration branch
             *   2. Nothing has changed on the remote since the last sync
             *   3. Work has happened locally on the feature branch, and it is no longer a "simple" addition to the remote
             */
            static class Local_is_based_on_integration_but_not_a_simple_version_of_remote extends When_rebasing {

                void verifyStartState() {
                    assert lSha == dSha
                    assert rSha == bSha
                    assert iSha == cSha
                    assert parent(cSha, origin) == aSha
                    assert parent(bSha, origin) == aSha
                    assert parent(dSha, local) == cSha
                }

                /**
                 * <pre>
                 *     i
                 *    /
                 * - A - C - D
                 *          /
                 *        l,r
                 * </pre>
                 */
                def "should work with control file pointing to remote"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"
                    createB
                    checkout("master")
                    createC

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")
                    writeSyncControl()

                    resetHard(cSha)
                    createD

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    localAndRemoteAreSame()
                    parent(rSha) == cSha
                    localContainsCommits(["c", "d"])

                    // because the remote did not change since being recorded, assuming that the local version supersedes it
                    localDoesNotContainCommits(["b"])
                }

                /**
                 * <pre>
                 *     i
                 *    /
                 * - A - C - B1 - D1
                 *               /
                 *             l,r
                 * </pre>
                 */
                def "should work without control file"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"
                    createB
                    checkout("master")
                    createC

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")

                    resetHard(cSha)
                    createD

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    localAndRemoteAreSame()
                    localContainsCommits(["c", "b", "d"])
                }
            }

            /**
             * <pre>
             *           l
             *          /
             *         D
             *        /
             * - A - C - E
             *   \      /
             *   B     i
             *   \
             *   r
             * </pre>
             *
             * Steps to get to this state:
             *   1. Changes have been applied to the integration branch
             *   2. Nothing has changed on the remote since the last sync
             *   3. Work has happened locally based on a newer version of integration, and it is no longer a "simple" addition to the remote
             */
            static class Local_is_not_based_on_integration_and_not_a_simple_version_of_remote extends When_rebasing {

                void verifyStartState() {
                    assert lSha == dSha
                    assert rSha == bSha
                    assert iSha == eSha
                    assert parent(cSha, origin) == aSha
                    assert parent(bSha, origin) == aSha
                    assert parent(eSha, origin) == cSha
                    assert parent(dSha, local) == cSha
                }

                /**
                 * <pre>
                 * - A - C - E - D1
                 *          /    /
                 *         i   l,r
                 * </pre>
                 */
                def "should work with control file pointing to remote"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"
                    createB
                    checkout("master")
                    createC

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")
                    writeSyncControl()

                    resetHard(cSha)
                    createD

                    useOrigin
                    checkout("master")
                    createE

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    localAndRemoteAreSame()
                    parent(rSha) == eSha
                    localContainsCommits(["c", "d", "e"])

                    // because the remote did not change since being recorded, assuming that the local version supersedes it
                    localDoesNotContainCommits(["b"])
                }

                /**
                 * <pre>
                 * - A - C - E - B1 - D1
                 *          /        /
                 *         i       l,r
                 * </pre>
                 */
                def "should work without control file"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"
                    createB
                    checkout("master")
                    createC

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")

                    resetHard(cSha)
                    createD

                    useOrigin
                    checkout("master")
                    createE

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    localAndRemoteAreSame()
                    localContainsCommits(["c", "b", "d", "e"])
                }

                /**
                 * <pre>
                 *           ??
                 *          /
                 *         D
                 *        /
                 * - A - C - E - XX
                 *   \      /    /
                 *   B     i    l
                 *   \
                 *   r
                 * </pre>
                 */
                def "should have an error with conflict on local content"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"
                    createB
                    checkout("master")
                    createC

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")
                    writeSyncControl()

                    resetHard(cSha)
                    changeFileAndAdd "a"
                    createD

                    useOrigin
                    checkout("master")
                    changeFileAndAdd "a" // conflict between D and E
                    createE

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    def exp = thrown(IllegalStateException)
                    exp.getMessage().contains("conflict")
                }
            }

            /**
             * <pre>
             *         i      r
             *        /       \
             * - A - C - B1 - E
             *   \
             *   B - D
             *       \
             *       l
             * </pre>
             *
             * Steps to get to this state:
             *   1. Changes have been applied to the integration branch
             *   2. Work has happened locally based on an older version of integration
             *   3. Work has happened remotely based on rebasing against integration
             */
            static class Local_and_remote_both_have_changes_and_remote_was_rebased_with_integration extends When_rebasing {

                void verifyStartState() {
                    assert lSha == dSha
                    assert rSha == eSha
                    assert iSha == cSha
                    assert parent(cSha, origin) == aSha
                    assert parent(bSha, local) == aSha
                    assert parent(parent(eSha, origin), origin) == cSha
                    assert parent(dSha, local) == bSha
                }

                /**
                 * <pre>
                 * - A - C - B1 - E - D1
                 *      /            /
                 *     i           l,r
                 * </pre>
                 */
                def "should work with control file pointing to remote"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")
                    writeSyncControl()
                    createB
                    createD

                    useOrigin
                    checkout("master")
                    createC

                    checkout("fb")
                    resetHard(cSha)
                    createFiles "b"
                    createB1
                    createE

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    localAndRemoteAreSame()
                    parent(rSha) == eSha
                    localContainsCommits(["c", "d", "b1", "e"])

                    // the "b" ("b1") on the integration branch had the changes made in "b", so it does not get reapplied
                    localDoesNotContainCommits(["b"])
                }

                /**
                 * <pre>
                 * - A - C - E - B1 - D1
                 *          /        /
                 *         i       l,r
                 * </pre>
                 */
                def "should work without control file"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")
                    createB
                    createD

                    useOrigin
                    checkout("master")
                    createC

                    checkout("fb")
                    resetHard(cSha)
                    createFiles "b"
                    createB1
                    createE

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    localAndRemoteAreSame()
                    parent(rSha) == eSha
                    localContainsCommits(["c", "d", "b1", "e"])

                    // the "b" ("b1") on the integration branch had the changes made in "b", so it does not get reapplied
                    localDoesNotContainCommits(["b"])
                }

                /**
                 * <pre>
                 *         i      r   l
                 *        /       \   \
                 * - A - C - B1 - E - XX
                 *   \
                 *   B - D
                 *       \
                 *       ??
                 * </pre>
                 */
                def "should have an error with conflict on local content"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")
                    createB
                    changeFileAndAdd "a"
                    createD

                    useOrigin
                    checkout("master")
                    createC

                    checkout("fb")
                    resetHard(cSha)
                    changeFileAndAdd "b"
                    createB1
                    changeFileAndAdd "a" // conflict between D and E
                    createE

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    def exp = thrown(IllegalStateException)
                    exp.getMessage().contains("conflict")
                }
            }

            /**
             * <pre>
             *              r
             *              \
             *         B1 - E
             *        /
             * - A - C - F
             *   \       \
             *   B - D   i
             *       \
             *       l
             * </pre>
             *
             * Steps to get to this state:
             *   1. Changes have been applied to the integration branch
             *   2. Work has happened locally based on an older version of integration
             *   3. Work has happened remotely based on rebasing against integration
             *   4. More work happened on integration
             */
            static class Local_and_remote_both_changed_and_remote_was_rebased_with_integration_then_more_changes_on_integration extends When_rebasing {

                void verifyStartState() {
                    assert lSha == dSha
                    assert rSha == eSha
                    assert iSha == fSha
                    assert parent(cSha, origin) == aSha
                    assert parent(fSha, origin) == cSha
                    assert parent(bSha, local) == aSha
                    assert parent(parent(eSha, origin), origin) == cSha
                    assert parent(dSha, local) == bSha
                }

                /**
                 * <pre>
                 * - A - C - F - B2 - E1 - D1
                 *          /              /
                 *         i             l,r
                 * </pre>
                 */
                def "should work with control file pointing to remote"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")
                    writeSyncControl()
                    createB
                    createD

                    useOrigin
                    checkout("master")
                    createC

                    checkout("fb")
                    resetHard(cSha)
                    createFiles "b"
                    createB1
                    createE

                    checkout("master")
                    createF

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    localAndRemoteAreSame()
                    localContainsCommits(["c", "d", "b1", "e", "f"])

                    // the "b" ("b1") on the integration branch had the changes made in "b", so it does not get reapplied
                    localDoesNotContainCommits(["b"])
                }

                /**
                 * <pre>
                 * - A - C - F - B2 - E1 - D1
                 *          /              /
                 *         i             l,r
                 * </pre>
                 */
                def "should work without control file"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")
                    createB
                    createD

                    useOrigin
                    checkout("master")
                    createC

                    checkout("fb")
                    resetHard(cSha)
                    createFiles "b"
                    createB1
                    createE

                    checkout("master")
                    createF

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    localAndRemoteAreSame()
                    localContainsCommits(["c", "d", "b1", "e", "f"])

                    // the "b" ("b1") on the integration branch had the changes made in "b", so it does not get reapplied
                    localDoesNotContainCommits(["b"])
                }

                /**
                 * <pre>
                 *              r
                 *              \
                 *         B1 - E
                 *        /
                 * - A - C - F - XX
                 *   \       \    \
                 *   B - D   i    l
                 *       \
                 *       ??
                 * </pre>
                 */
                def "should have an error with conflict on remote content"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")
                    writeSyncControl()
                    createB
                    createD

                    useOrigin
                    checkout("master")
                    createC

                    checkout("fb")
                    resetHard(cSha)
                    changeFileAndAdd "a"
                    createB1
                    createE

                    checkout("master")
                    changeFileAndAdd "a" // conflict between B1 and F
                    createF

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    def exp = thrown(IllegalStateException)
                    exp.getMessage().contains("conflict")
                }

                /**
                 * <pre>
                 *              r
                 *              \
                 *         B1 - E
                 *        /
                 * - A - C - F - B2 - E1 - XX
                 *   \       \             /
                 *   B - D   i            l
                 *       \
                 *       ??
                 * </pre>
                 */
                def "should have an error with conflict on local content"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")
                    writeSyncControl()
                    createB
                    changeFileAndAdd "a"
                    createD

                    useOrigin
                    checkout("master")
                    createC

                    checkout("fb")
                    resetHard(cSha)
                    createFiles "b"
                    createB1
                    changeFileAndAdd "a" // conflict between D and E
                    createE

                    checkout("master")
                    createF

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    def exp = thrown(IllegalStateException)
                    exp.getMessage().contains("conflict")
                }
            }

            /**
             * <pre>
             *         i
             *        /
             * - A - C
             *   \
             *   B - l
             *   \
             *   D - r
             * </pre>
             *
             * Steps to get to this state:
             *   1. There is a remote feature branch ("fb")
             *   2. The local repo has a feature branch by the same name that is fully within the remote's history
             *   3. The integration branch has moved on since the feature branches were last branched
             */
            static class Has_local_branch_by_same_name_subsumed_by_remote extends When_rebasing {

                void verifyStartState() {
                    assert lSha == bSha
                    assert rSha == dSha
                    assert iSha == cSha
                    assert parent(cSha, origin) == aSha
                    assert parent(bSha, local) == aSha
                    assert parent(dSha, origin) == bSha
                }

                /**
                 * <pre>
                 * - A - C - B1 - D1
                 *      /        /
                 *     i       l,r
                 * </pre>
                 */
                def "change to the remote and rebases with integration if it is subsumed by the remote"() {
                    useOrigin
                    createAndCheckoutBranch "fb", "master"
                    createB
                    createD

                    useLocal
                    local.fetch()
                    createAndCheckoutBranch("fb", "origin/fb")
                    resetHard(bSha)

                    useOrigin
                    checkout("master")
                    createC

                    verifyStartState()

                    when:
                    syncIsRun()

                    then:
                    localAndRemoteAreSame()
                    parent(parent(lSha)) == cSha
                    localContainsCommits(["c", "d", "b"])
                }
            }
        }
    }

    // **********************************************************************
    //
    // HELPER METHODS
    //
    // **********************************************************************

    @Canonical
    static class Options {
        @Nonnull
        GitLib gitLib
        boolean doMerge
        boolean localOnly
    }


    @CompileStatic
    Either<String, Branch> run(GitLib gitLib) {
        Options options = createOptions(gitLib)
        return run(options)
    }


    @CompileStatic
    Either<String, Branch> run(Options options) {
        Sync.sync(options.gitLib, options.doMerge, options.localOnly)
    }


    @CompileStatic
    Branch syncIsRun(boolean doCheckout = true) {
        if (doCheckout) {
            def fb = local.branches().branch("fb") ?: local.branches().createBranch("fb", "master")
            fb.checkout().getOrElseThrow({ new IllegalStateException(it as String) } as Function)
        }

        run(local).getOrElseThrow({ logger.error it.toString(); new IllegalStateException(it.toString()) } as Function)
    }


    @CompileStatic
    String localSha(String branchName) {
        branchTip(local, branchName)
    }


    @CompileStatic
    String remoteSha(String branchName) {
        branchTip(origin, branchName)
    }


    void localAndRemoteAreSame() {
        assert localSha("fb") == remoteSha("fb")
    }


    @CompileStatic
    def writeSyncControl() {
        currentLib.branches().currentBranch().recordLastSyncedAgainst()
    }

}
