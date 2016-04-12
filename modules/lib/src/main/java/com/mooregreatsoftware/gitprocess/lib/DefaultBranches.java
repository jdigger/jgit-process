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
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.e;
import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.v;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.R_REFS;

@SuppressWarnings({"RedundantCast", "RedundantTypeArguments"})
public class DefaultBranches implements Branches {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultBranches.class);

    @NonNull
    private final GitLib gitLib;


    public DefaultBranches(@Nonnull GitLib gitLib) {
        this.gitLib = gitLib;
    }


    @Override
    public @Nullable Branch currentBranch() {
        final Ref ref = ref(HEAD);
        if (ref == null) return null;
        final String targetName = ref.getTarget().getName();
        return targetName.startsWith(R_REFS) ? branch(targetName) : null;
    }


    private @Nullable Ref ref(String name) {
        return Try.<@Nullable Ref>of(() -> gitLib.repository().findRef(name)).
            getOrElse((Ref)null);
    }


    @NonNull
    @Override
    public BranchConfig config() {
        return gitLib.branchConfig();
    }


    @Override
    public @Nullable Branch branch(@Nonnull String branchName) {
        final Ref ref = ref(branchName);
        return (ref == null) ? null : Branch.of(gitLib, ref.getName());
    }


    @Nonnull
    @Override
    // TODO don't allow creating a remote branch "shadow"
    public Branch createBranch(@Nonnull String branchName, @Nonnull Branch baseBranch) throws BranchAlreadyExists {
        if (branch(branchName) != null) throw new BranchAlreadyExists(branchName);

        LOG.info("Creating branch \"{}\" based on \"{}\"", branchName, baseBranch.shortName());
        v(() -> gitLib.jgit().branchCreate().setName(branchName).setStartPoint(baseBranch.name()).call());

        return (@NonNull Branch)branch(branchName);
    }


    @Nonnull
    @Override
    // TODO Add support for removing a remote branch
    public Branches removeBranch(@Nonnull Branch branch) {
        LOG.info("Removing branch \"{}\"", branch.shortName());
        v(() -> gitLib.jgit().branchDelete().setBranchNames(branch.name()).setForce(true).call());
        return this;
    }


    @Nonnull
    @Override
    public Iterator<@NonNull Branch> allBranches() {
        // this may get expensive if there are a LOT of branches; fortunately it can be implemented to not do so
        // without breaking the API
        return ((@NonNull List<Ref>)e(() -> gitLib.jgit().branchList().setListMode(ListMode.ALL).call())).
            stream().
            map(ref -> (@NonNull Branch)branch(ref.getName())).
            collect(Collectors.<@NonNull Branch>toList()).<@NonNull Branch>listIterator();
    }

}
