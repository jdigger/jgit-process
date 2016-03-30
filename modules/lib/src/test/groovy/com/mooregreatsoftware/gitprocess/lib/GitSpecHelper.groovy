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

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.slf4j.Logger
import org.slf4j.LoggerFactory

trait GitSpecHelper {
    final Logger logger = LoggerFactory.getLogger(this.class)

    private GitLib _gitLib


    GitLib getGitLib() {
        if (_gitLib == null) {
            this._gitLib = createDefaultGitLib()
        }
        return _gitLib
    }


    GitLib createFiles(String... fileNames) {
        return createFiles(gitLib, fileNames)
    }


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


    GitLib cloneRepo(String branchName, String remoteName) {
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

//    Level logLevel() {
//        return Level.DEBUG
//    }

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
        return createGitLib(createTmpDir())
    }

    File createTmpDir() {
        File tmpDir = File.createTempFile("git-process", "spec")
        tmpDir.delete()
        assert tmpDir.mkdirs()
        return tmpDir
    }

}

/*
  def gitprocess
    if @gitprocess.nil? and respond_to?(:create_process)
      @gitprocess = create_process(gitlib, :log_level => log_level)
    end
    @gitprocess
  end


  def gitlib
    if @gitlib.nil?
      if @gitprocess.nil?
        @gitlib = create_gitlib(Dir.mktmpdir, :log_level => log_level)
      else
        @gitlib = gitprocess.gitlib
      end
    end
    @gitlib
  end


  def config
    gitlib.config
  end


  def remote
    gitlib.remote
  end


  def commit_count
    gitlib.log_count
  end


  def log_level
    Logger::ERROR
  end


  def logger
    gitlib.logger
  end


  def create_files(file_names)
    GitRepoHelper.create_files gitlib, file_names
  end


  def self.create_files(gitlib, file_names)
    Dir.chdir(gitlib.workdir) do |dir|
      file_names.each do |fn|
        gitlib.logger.debug { "Creating #{dir}/#{fn}" }
        FileUtils.touch fn
      end
    end
    gitlib.add(file_names)
  end


  def change_file(filename, contents, lib = gitlib)
    Dir.chdir(lib.workdir) do
      File.open(filename, 'w') { |f| f.puts contents }
    end
  end


  def change_file_and_add(filename, contents, lib = gitlib)
    change_file(filename, contents, lib)
    lib.add(filename)
  end


  def change_file_and_commit(filename, contents, lib = gitlib)
    change_file_and_add(filename, contents, lib)
    lib.commit("#{filename} - #{contents}")
  end


  def create_gitlib(dir, opts)
    git_lib = GitLib.new(dir, opts)
    git_lib.config['user.email'] = 'test.user@test.com'
    git_lib.config['user.name'] = 'test user'
    git_lib
  end


  def clone_repo(branch='master', remote_name = 'origin', &block)
    td = Dir.mktmpdir

    logger.debug { "Cloning '#{gitlib.workdir}' to '#{td}'" }

    gl = create_gitlib(td, :log_level => logger.level)
    gl.remote.add(remote_name, "file://#{gitlib.workdir}")
    gl.fetch(remote_name)

    if branch == 'master'
      gl.reset("#{remote_name}/#{branch}", :hard => true)
    else
      gl.checkout(branch, :new_branch => "#{remote_name}/#{branch}")
    end

    if block_given?
      begin
        block.arity < 1 ? gl.instance_eval(&block) : block.call(gl)
      ensure
        rm_rf(gl.workdir)
      end
      nil
    else
      gl
    end
  end

 */
