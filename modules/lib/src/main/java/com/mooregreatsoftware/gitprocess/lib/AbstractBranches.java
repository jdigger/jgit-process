package com.mooregreatsoftware.gitprocess.lib;

import com.mooregreatsoftware.gitprocess.config.BranchConfig;
import javaslang.control.Try;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.exceptionTranslator;

@SuppressWarnings({"RedundantCast", "RedundantTypeArguments"})
public abstract class AbstractBranches implements Branches {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractBranches.class);


    protected abstract BranchConfig config();


    /**
     * The integration branch. For purely local development, this is typically "master", and for shared development
     * this is typically "origin/master".
     * <p>
     * This is the "implied" branch for many operations.
     *
     * @return null only if this is not set in configuration and there is no reasonable default
     * @see #integrationBranch(Branch)
     * @see BranchConfig#integrationBranch(Branch)
     */
    @Override
    public @Nullable Branch integrationBranch() {
        return config().integrationBranch();
    }


    /**
     * Sets (and writes to git configuration) the integration branch to use.
     *
     * @param branch the branch to set as integration
     * @return this
     * @see #integrationBranch()
     * @see BranchConfig#integrationBranch()
     */
    @Override
    public Branches integrationBranch(Branch branch) {
        config().integrationBranch(branch);
        return this;
    }


    @Override
    // TODO don't allow creating a remote branch "shadow"
    public Branch createBranch(String branchName, Branch baseBranch) throws BranchAlreadyExists {
        if (branch(branchName) != null) throw new BranchAlreadyExists(branchName);

        LOG.info("Creating branch \"{}\" based on \"{}\"", branchName, baseBranch.shortName());
        Try.run(() -> doCreateBranch(branchName, baseBranch)).
            getOrElseThrow(exceptionTranslator());

        return (@NonNull Branch)branch(branchName);
    }


    protected abstract void doCreateBranch(String branchName, Branch baseBranch) throws Exception;

    protected abstract void doRemoveBranch(Branch branch) throws Exception;


    @Override
    // TODO Add support for removing a remote branch
    public Branches removeBranch(Branch branch) {
        LOG.info("Removing branch \"{}\"", branch.shortName());
        Try.run(() -> doRemoveBranch(branch)).
            getOrElseThrow(exceptionTranslator());
        return this;
    }

}
