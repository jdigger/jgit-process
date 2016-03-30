package com.mooregreatsoftware.gitprocess;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Configuration around branches.
 */
public interface BranchConfig extends Config {
    String INTEGRATION_BRANCH_KEY = "integrationBranch";
    String PARKING_BRANCH_NAME = "_parking_";

    /**
     * The integration branch. For purely local development, this is typically "master", and for shared development
     * this is typically "origin/master".
     * <p>
     * This is the "implied" branch for many operations.
     *
     * @return empty() only if this is not set in configuration and there is no reasonable default
     * @see #integrationBranch(Branch)
     */
    @Nonnull
    Optional<Branch> integrationBranch();

    /**
     * Sets (and writes to git configuration) the integration branch to use.
     *
     * @param branch the branch to set as integration
     * @return this
     * @see #integrationBranch()
     */
    @Nonnull
    BranchConfig integrationBranch(@Nonnull Branch branch);

    /**
     * Set (and write to git configuration) the upstream/tracking to use for a branch.
     *
     * @param branch   the branch to set the upstream for
     * @param upstream the branch to set the upstream to
     * @return this
     */
    @Nonnull
    BranchConfig setUpstream(@Nonnull Branch branch, @Nonnull Branch upstream);

    /**
     * The upstream/tracking that has been set for a branch.
     *
     * @param branch the branch to get the upstream configuration for
     * @return empty() if nothing has been set
     */
    @Nonnull
    Optional<Branch> getUpstream(Branch branch);
}
