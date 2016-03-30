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
import groovy.util.logging.Slf4j
import javaslang.control.Either
import org.eclipse.jgit.lib.AnyObjectId
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevWalk
import spock.lang.Specification

import java.util.function.Function

import static com.mooregreatsoftware.gitprocess.config.BranchConfig.PARKING_BRANCH_NAME

@Slf4j
@SuppressWarnings("GroovyPointlessBoolean")
abstract class GitSpecification extends Specification implements GitSpecHelper {

    private GitLib _origin
    private GitLib _local
    private GitLib _current_lib


    @CompileStatic
    def cleanup() {
        if (_origin != null) {
            _origin.workingDirectory().deleteDir()
            _origin.close()
        }
        if (_local != null) {
            _local.workingDirectory().deleteDir()
            _local.close()
        }
    }


    @CompileStatic
    GitLib getOrigin() {
        return _origin ?: origin()
    }


    @CompileStatic
    GitLib getLocal() {
        return _local ?: local(null)
    }


    @CompileStatic
    GitLib getCurrentLib() {
        if (_current_lib == null) {
            currentLib = origin
        }
        return _current_lib
    }


    @CompileStatic
    GitLib getUseOrigin() {
        origin()
        return currentLib
    }


    @CompileStatic
    GitLib getUseLocal() {
        local(null, "origin")
        return currentLib
    }


    @CompileStatic
    GitLib origin() {
        if (_origin == null) {
            _origin = createDefaultGitLib()
            // allow pushing directly into this repo
            def config = _origin.jgit().repository.config
            config.setString("receive", null, "denyCurrentBranch", "ignore")
            config.save()
        }
        if (_current_lib != _origin) currentLib = _origin
        return _origin
    }


    @CompileStatic
    GitLib local(String branchName = null, String remoteName = "origin") {
        if (_local == null) {
            _local = cloneRepo(origin, branchName ?: "master", remoteName)
        }
        if (_current_lib != _local) currentLib = _local
        _local.fetch()
        if (branchName != null) _local.checkout(branchName)
        return _local
    }


    @CompileStatic
    void setCurrentLib(GitLib gitLib) {
        _current_lib = gitLib
        logger.info "Setting currentLib = ${currentRepoName}"
    }


    @CompileStatic
    String getCurrentRepoName() {
        repoName(_current_lib)
    }


    @CompileStatic
    String repoName(GitLib gitLib) {
        (_origin == null || gitLib.workingDirectory() == _origin.workingDirectory()) ? "origin" : "local"
    }


    @CompileStatic
    Branch branch(String branch) {
        currentLib.branches().
            branch(branch).
            orElseThrow({ new IllegalStateException("Could not find ${branch}") })
    }


    @CompileStatic
    Branch checkout(String branch) {
        currentLib.branches().
            branch(branch).
            orElseThrow({ new IllegalStateException("Could not find ${branch}") }).
            checkout().
            getOrElseThrow({ new IllegalStateException(it as String) } as Function)
    }


    @CompileStatic
    Either<String, ObjectId> createCommit(String name) {
        logger.info "Committing in ${currentRepoName}"
        createFiles(currentLib, name).commit(name)
    }


    @CompileStatic
    String integrationBranchName() {
        currentLib.branches().integrationBranch().get().shortName()
    }


    @CompileStatic
    Branch createBranch(String branchName, String baseBranchName = integrationBranchName()) {
        currentLib.branches().createBranch(branchName, baseBranchName)
    }


    @CompileStatic
    Branch createAndCheckoutBranch(String branchName, String baseBranchName = integrationBranchName()) {
        createBranch(branchName, baseBranchName)
        return checkout(branchName)
    }


    @CompileStatic
    GitLib createFiles(String... fileNames) {
        createFiles(currentLib, fileNames)
    }


    @CompileStatic
    GitLib createFilesNoAdd(String... fileNames) {
        createFilesNoAdd(currentLib, fileNames)
    }


    @CompileStatic
    ObjectId commit(String msg) {
        logger.info "Committing in ${currentRepoName}"
        currentLib.commit(msg).getOrElseThrow({ new IllegalStateException(it as String) } as Function)
    }


    @CompileStatic
    void changeFileAndAdd(String filename) {
        changeFileAndAdd(currentLib, filename, null)
    }


    void fileExists(String filename) {
        assert new File(currentLib.workingDirectory() as File, filename).exists()
    }


    def getParkingDoesNotExist() {
        assert currentLib.branches().branch(PARKING_BRANCH_NAME).isPresent() == false
        true
    }


    @CompileStatic
    void resetHard(ref) {
        def resetHard = currentLib.branches().currentBranch().get().resetHard(ref as String)
        if (resetHard.isPresent()) throw new IllegalStateException(resetHard.get())
    }


    void localContainsCommits(List<String> commitNames) {
        final RevWalk walk = new RevWalk(local.jgit().repository)
        try {
            def topOfThisBranch = walk.parseCommit(local.branches().currentBranch().get().objectId())
            walk.markStart(topOfThisBranch)

            def unfoundCommits = new ArrayList<String>(commitNames)
            def iterator = walk.iterator()
            while (iterator.hasNext() && !unfoundCommits.isEmpty()) {
                def revCommit = iterator.next()
                if (unfoundCommits.contains(revCommit.shortMessage)) {
                    unfoundCommits.remove(revCommit.shortMessage)
                }
            }

            assert unfoundCommits.isEmpty()
        }
        finally {
            walk.dispose();
        }
    }


    @CompileStatic
    void localDoesNotContainCommits(List<String> commitNames) {
        final RevWalk walk = new RevWalk(local.jgit().repository)
        try {
            def topOfThisBranch = walk.parseCommit(local.branches().currentBranch().get().objectId())
            walk.markStart(topOfThisBranch)

            def unfoundCommits = new ArrayList<String>(commitNames)
            def iterator = walk.iterator()
            while (iterator.hasNext() && !unfoundCommits.isEmpty()) {
                def revCommit = iterator.next()
                if (unfoundCommits.contains(revCommit.shortMessage)) {
                    unfoundCommits.remove(revCommit.shortMessage)
                }
            }

            assert unfoundCommits == commitNames
        }
        finally {
            walk.dispose();
        }
    }


    @CompileStatic
    String parent(def sha, GitLib lib = local) {
        def resolve = lib.jgit().repository.resolve("${sha}~1")
        if (resolve == null as AnyObjectId) throw new IllegalArgumentException("Could not resolve ${sha}~1 on ${currentRepoName}")
        return resolve.abbreviate(7).name()
    }


    @CompileStatic
    def changeFileAndCommit(String filename) {
        changeFileAndCommit(currentLib, filename, null)
    }

}
