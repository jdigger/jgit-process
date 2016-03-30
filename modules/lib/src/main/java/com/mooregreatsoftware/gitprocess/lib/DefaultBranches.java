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
package com.mooregreatsoftware.gitprocess.lib;

import com.mooregreatsoftware.gitprocess.config.BranchConfig;
import javaslang.control.Try;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Optional.empty;
import static org.eclipse.jgit.lib.Constants.HEAD;

public class DefaultBranches implements Branches {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultBranches.class);

    @Nonnull
    private final GitLib gitLib;


    public DefaultBranches(@Nonnull GitLib gitLib) {
        this.gitLib = gitLib;
    }


    @Nonnull
    @Override
    public Optional<Branch> currentBranch() {
        return ref(HEAD).
            map(ref -> ref.getTarget().getName()).
            filter(name -> name.startsWith("refs/")).
            flatMap(this::branch);
    }


    @Nonnull
    private Optional<Ref> ref(String name) {
        return Try.of(() -> gitLib.repository().findRef(name)).map(Optional::of).getOrElse(empty());
    }


    @Nonnull
    @Override
    public BranchConfig config() {
        return gitLib.branchConfig();
    }


    @Nonnull
    @Override
    public Optional<Branch> branch(@Nonnull String branchName) {
        return ref(branchName).map(ref -> Branch.of(gitLib, ref.getName()));
    }


    @Nonnull
    @Override
    // TODO don't allow creating a remote branch "shadow"
    public Branch createBranch(@Nonnull String branchName, @Nonnull Branch baseBranch) throws BranchAlreadyExists {
        if (branch(branchName).isPresent()) throw new BranchAlreadyExists(branchName);

        LOG.info("Creating branch \"{}\" based on \"{}\"", branchName, baseBranch.shortName());
        Try.run(() -> gitLib.jgit().branchCreate().setName(branchName).setStartPoint(baseBranch.name()).call());

        return branch(branchName).get();
    }


    @Nonnull
    @Override
    // TODO Add support for removing a remote branch
    public Branches removeBranch(@Nonnull Branch branch) {
        LOG.info("Removing branch \"{}\"", branch.shortName());
        ExecUtils.v(() -> gitLib.jgit().branchDelete().setBranchNames(branch.name()).setForce(true).call());
        return this;
    }


    @Nonnull
    @Override
    public Iterator<Branch> allBranches() {
        // this may get expensive if there are a LOT of branches; fortunately it can be implemented to not do so
        // without breaking the API
        return ExecUtils.e(() -> gitLib.jgit().branchList().setListMode(ListMode.ALL).call()).
            stream().
            map(ref -> branch(ref.getName()).get()).
            collect(Collectors.toList()).
            listIterator();
    }

}
