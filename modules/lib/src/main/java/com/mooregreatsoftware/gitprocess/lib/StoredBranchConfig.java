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

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.e;
import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.v;
import static java.util.Optional.empty;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_BRANCH_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_MERGE;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_REMOTE;
import static org.eclipse.jgit.lib.Constants.MASTER;

@SuppressWarnings("ConstantConditions")
public class StoredBranchConfig implements BranchConfig {
    private static final Logger LOG = LoggerFactory.getLogger(StoredBranchConfig.class);

    @Nonnull
    private final StoredConfig storedConfig;

    @Nonnull
    private final RemoteConfig remoteConfig;

    @Nonnull
    private final Branches branches;


    public StoredBranchConfig(@Nonnull StoredConfig storedConfig, @Nonnull RemoteConfig remoteConfig, @Nonnull Branches branches) {
        this.storedConfig = storedConfig;
        this.remoteConfig = remoteConfig;
        this.branches = branches;
    }


    /**
     * Returns the integration branch to use.
     * <p>
     * If one is explicitly defined in "gitProcess.integrationBranch" then always use that.
     * If there are remotes defined, then gets the "master" branch from the default remote.
     * Otherwise checks to see if "master" exists locally.
     *
     * @return empty() if it can't compute a reasonable value
     * @see RemoteConfig#remoteName()
     */
    @Nonnull
    @Override
    public Optional<Branch> integrationBranch() {
        final Optional<Branch> configIntegrationBranch = integrationBranchFromGitConfig();
        if (configIntegrationBranch.isPresent()) return configIntegrationBranch;

        return remoteConfig.hasRemotes() ? integrationBranchFromRemoteBranches() : integrationBranchFromLocalBranches();
    }


    /**
     * Set (and writes to git configuration) the integration branch to use
     */
    @Nonnull
    @Override
    public BranchConfig integrationBranch(@Nonnull Branch branch) {
        if (branch == null) throw new IllegalArgumentException("branch == null");
        LOG.debug("Setting integration branch to {}", branch.name());
        storedConfig.setString(GIT_PROCESS_SECTION_NAME, null, INTEGRATION_BRANCH_KEY, branch.name());
        v(storedConfig::save);
        return this;
    }


    @Nonnull
    private Optional<Branch> integrationBranchFromRemoteBranches() {
        // TODO: Figure out how to get the remote HEAD.  FETCH_HEAD?
        // Appears to be indirectly in FetchResult.getAdvertisedRefs()
        return e(() -> {
            // if remote has "master", assume that's the integration branch.
            // otherwise give up and return empty()
            final String lookFor = remoteConfig.remoteName().orElse("ERROR") + "/" + MASTER;
            final Optional<Branch> branch = searchForBranch(branches.remoteBranches(), lookFor);

            logIntegrationBranch(lookFor, branch, branches.remoteBranches());
            return branch;
        });
    }


    @Nonnull
    private Optional<Branch> integrationBranchFromLocalBranches() {
        return e(() -> {
            // if have "master", assume that's the integration branch.
            // otherwise give up and return empty()
            final String lookFor = MASTER;
            final Optional<Branch> branch = searchForBranch(branches.localBranches(), lookFor);

            logIntegrationBranch(lookFor, branch, branches.localBranches());
            return branch;
        });
    }


    @Nonnull
    private Optional<Branch> searchForBranch(Iterator<Branch> branches, String branchName) {
        return StreamUtils.stream(branches).
            filter(branch -> branch.shortName().equals(branchName)).
            findFirst();
    }


    private static void logIntegrationBranch(String branchName, Optional<Branch> branch, Iterator<Branch> branches) {
        if (branch.isPresent()) {
            LOG.debug("integrationBranch(): have a \"{}\" branch", branch.get().shortName());
        }
        else {
            LOG.warn("Do not have a \"{}\" branch: [{}]\n" +
                    "Fix with `git config {}.{} [branch_name]`",
                branchName,
                StreamUtils.stream(branches).
                    map(Branch::shortName).
                    collect(Collectors.joining(", ")),
                GIT_PROCESS_SECTION_NAME, INTEGRATION_BRANCH_KEY
            );
        }
    }


    @Nonnull
    private Optional<Branch> integrationBranchFromGitConfig() {
        final String branchName = storedConfig.getString(GIT_PROCESS_SECTION_NAME, null, INTEGRATION_BRANCH_KEY);
        if (branchName != null) {
            LOG.debug("integrationBranch(): {}.{} has a value of \"{}\" so using that",
                GIT_PROCESS_SECTION_NAME, INTEGRATION_BRANCH_KEY, branchName);
            return branches.branch(branchName);
        }
        return empty();
    }


    @Nonnull
    @Override
    public BranchConfig setUpstream(@Nonnull Branch branch, @Nonnull Branch upstream) {
        final String branchShortName = branch.shortName();

        Optional<String> remoteName = upstream.remoteName();
        if (remoteName.isPresent()) {
            String upstreamBranchName = upstream.shortName();
            storedConfig.setString(CONFIG_BRANCH_SECTION,
                branchShortName, CONFIG_KEY_REMOTE,
                remoteName.get());
            final String upstreamNameWithoutRemote = upstreamBranchName.substring(remoteName.get().length() + 1);
            storedConfig.setString(CONFIG_BRANCH_SECTION,
                branchShortName, CONFIG_KEY_MERGE,
                Constants.R_HEADS + upstreamNameWithoutRemote);
            LOG.info("Setting upstream for \"{}\" to remote \"{}\" on \"{}\"", branch.shortName(), upstreamNameWithoutRemote, remoteName.get());
        }
        else {
            storedConfig.setString(CONFIG_BRANCH_SECTION,
                branchShortName, CONFIG_KEY_REMOTE, ".");
            storedConfig.setString(CONFIG_BRANCH_SECTION,
                branchShortName, CONFIG_KEY_MERGE, upstream.shortName());
            LOG.info("Setting upstream for \"{}\" to local \"{}\"", branch.shortName(), upstream.shortName());
        }
        v(storedConfig::save);
        return this;
    }


    @Nonnull
    @Override
    public Optional<Branch> getUpstream(Branch branch) {
        Optional<Branch> upstream = Optional.ofNullable(
            storedConfig.getString(
                CONFIG_BRANCH_SECTION,
                branch.shortName(),
                CONFIG_KEY_REMOTE
            )
        ).map(remoteName ->
            remoteName.equals(".") ? "" : remoteName + "/").
            flatMap(remotePath ->
                    Optional.ofNullable(storedConfig.getString(
                            CONFIG_BRANCH_SECTION,
                            branch.shortName(),
                            CONFIG_KEY_MERGE
                        )
                    ).map(Repository::shortenRefName).
                        map(upstreamBranchName ->
                                remotePath + upstreamBranchName
                        )
            ).flatMap(branches::branch);

        if (upstream.isPresent()) {
            LOG.debug("getUpstream({}): {}", branch.shortName(), upstream.get().shortName());
        }
        else {
            LOG.debug("getUpstream({}): empty", branch.shortName());
        }

        return upstream;
    }

}
