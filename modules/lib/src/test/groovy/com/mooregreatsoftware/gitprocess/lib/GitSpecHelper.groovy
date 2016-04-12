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
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CompileStatic
trait GitSpecHelper {
    final Logger logger = LoggerFactory.getLogger(this.class)

    GitLib createFiles(GitLib gitLib, String... fileNames) {
        for (String filename : fileNames) {
            new File(gitLib.workingDirectory(), filename).write("")
            gitLib.addFilepattern(filename)
        }
        return gitLib
    }


    GitLib createFilesNoAdd(GitLib gitLib, String... fileNames) {
        for (String filename : fileNames) {
            new File(gitLib.workingDirectory(), filename).write("")
        }
        return gitLib
    }


    GitLib cloneRepo(GitLib gitLib, String branchName, String remoteName) {
        def uri = new URIish("file://${gitLib.workingDirectory().absolutePath}")
        Git newJGit = Git.cloneRepository().
            setDirectory(createTmpDir()).
            setBranch(branchName).
            setCloneAllBranches(true).
            setURI(uri.toString()).call()
        def gl = GitLib.of(newJGit)
        initBasicConfig(newJGit)

        logger.debug "Cloned '${gitLib.workingDirectory()}' to '${gl.workingDirectory()}'"

        gl.remoteConfig().remoteAdd(remoteName, uri)

        gl.fetch()

        return gl
    }


    GitLib createGitLib(File testDir) {
        if (!testDir.isDirectory()) testDir.delete()
        Git git = Git.init().setDirectory(testDir).call()
        initBasicConfig(git)
        return GitLib.of(git)
    }


    void initBasicConfig(Git jgit) {
        def config = jgit.repository.config
        config.clear()
        config.setString("user", null, "email", 'test.user@test.com')
        config.setString("user", null, "name", 'test user')
        config.save()
    }


    GitLib createDefaultGitLib() {
        createGitLib(createTmpDir())
    }


    File createTmpDir() {
        File tmpDir = File.createTempFile("git-process", "spec")
        tmpDir.delete()
        assert tmpDir.mkdirs()
        return tmpDir
    }


    String branchTip(GitLib gitLib, String branchName) {
        gitLib.branches().branch(branchName).
            objectId().abbreviate(7).name()
    }


    void changeFile(GitLib gitLib, String filename, String contents) {
        String c = contents ?: UUID.randomUUID().toString()
        new File(gitLib.workingDirectory(), filename).write(c)
    }


    void changeFileAndAdd(GitLib gitLib, String filename, String contents) {
        changeFile(gitLib, filename, contents)
        gitLib.addFilepattern(filename)
    }


    void changeFileAndCommit(GitLib gitLib, String filename, String contents) {
        changeFileAndAdd(gitLib, filename, contents)
        gitLib.commit("${filename} - ${contents}")
    }

}
