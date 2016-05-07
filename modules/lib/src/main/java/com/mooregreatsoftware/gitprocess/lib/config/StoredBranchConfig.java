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
package com.mooregreatsoftware.gitprocess.lib.config;

import com.mooregreatsoftware.gitprocess.config.BranchConfig;
import com.mooregreatsoftware.gitprocess.config.RemoteConfig;
import com.mooregreatsoftware.gitprocess.lib.Branch;
import com.mooregreatsoftware.gitprocess.lib.Branches;
import com.mooregreatsoftware.gitprocess.lib.StreamUtils;
import javaslang.control.Try;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.stream.Collectors;

import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.exceptionTranslator;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_BRANCH_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_MERGE;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_REMOTE;
import static org.eclipse.jgit.lib.Constants.MASTER;

@SuppressWarnings({"ConstantConditions", "RedundantTypeArguments"})
public class StoredBranchConfig extends AbstractStoredConfig implements BranchConfig {
    private static final Logger LOG = LoggerFactory.getLogger(StoredBranchConfig.class);

    private final RemoteConfig remoteConfig;
    private final Branches branches;


    public StoredBranchConfig(StoredConfig storedConfig, RemoteConfig remoteConfig, Branches branches) {
        super(storedConfig);
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
     * @return null if it can't compute a reasonable value
     * @see RemoteConfig#remoteName()
     */
    @Override
    @Nullable
    public Branch integrationBranch() {
        final Branch configIntegrationBranch = integrationBranchFromGitConfig();
        if (configIntegrationBranch != null) return configIntegrationBranch;

        return remoteConfig.hasRemotes() ? integrationBranchFromRemoteBranches() : integrationBranchFromLocalBranches();
    }


    /**
     * Set (and writes to git configuration) the integration branch to use
     */
    @Override
    public BranchConfig integrationBranch(Branch branch) {
        if (branch == null) throw new IllegalArgumentException("branch == null");
        LOG.debug("Setting integration branch to {}", branch.name());
        setString(GIT_PROCESS_SECTION_NAME, null, INTEGRATION_BRANCH_KEY, branch.name());
        return this;
    }


    private @Nullable Branch integrationBranchFromRemoteBranches() {
        // TODO: Figure out how to get the remote HEAD.  FETCH_HEAD?
        // Appears to be indirectly in FetchResult.getAdvertisedRefs()
        return Try.<@Nullable Branch>of(() -> {
            // if remote has "master", assume that's the integration branch.
            // otherwise give up and return empty()
            final String remoteName = remoteConfig.remoteName();
            if (remoteName == null) return null;
            final String lookFor = remoteName + "/" + MASTER;
            final Branch branch = searchForBranch(branches.remoteBranches(), lookFor);

            logIntegrationBranch(lookFor, branch, branches.remoteBranches());
            return branch;
        }).getOrElseThrow(exceptionTranslator());
    }


    private @Nullable Branch integrationBranchFromLocalBranches() {
        return Try.<@Nullable Branch>of(() -> {
            // if have "master", assume that's the integration branch.
            // otherwise give up and return empty()
            final String lookFor = MASTER;
            final Branch branch = searchForBranch(branches.localBranches(), lookFor);

            logIntegrationBranch(lookFor, branch, branches.localBranches());
            return branch;
        }).getOrElseThrow(exceptionTranslator());
    }


    private @Nullable Branch searchForBranch(Iterator<Branch> branches, String branchName) {
        return StreamUtils.stream(branches).
            filter(branch -> branch.shortName().equals(branchName)).
            findFirst().orElse(null);
    }


    private static void logIntegrationBranch(String branchName, @Nullable Branch branch, Iterator<Branch> branches) {
        if (branch != null) {
            LOG.debug("integrationBranch(): have a \"{}\" branch", branch.shortName());
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


    private @Nullable Branch integrationBranchFromGitConfig() {
        final String branchName = getString(GIT_PROCESS_SECTION_NAME, null, INTEGRATION_BRANCH_KEY);
        if (branchName != null) {
            LOG.debug("integrationBranch(): {}.{} has a value of \"{}\" so using that",
                GIT_PROCESS_SECTION_NAME, INTEGRATION_BRANCH_KEY, branchName);
            return branches.branch(branchName);
        }
        return null;
    }


    @Override
    public BranchConfig setUpstream(Branch branch, Branch upstream) {
        final String branchShortName = branch.shortName();

        String remoteName = upstream.remoteName();
        if (remoteName != null) {
            String upstreamBranchName = upstream.shortName();
            setString(CONFIG_BRANCH_SECTION,
                branchShortName, CONFIG_KEY_REMOTE,
                remoteName);
            final String upstreamNameWithoutRemote = upstreamBranchName.substring(remoteName.length() + 1);
            setString(CONFIG_BRANCH_SECTION,
                branchShortName, CONFIG_KEY_MERGE,
                Constants.R_HEADS + upstreamNameWithoutRemote);
            LOG.info("Setting upstream for \"{}\" to remote \"{}\" on \"{}\"", branch.shortName(), upstreamNameWithoutRemote, remoteName);
        }
        else {
            setString(CONFIG_BRANCH_SECTION,
                branchShortName, CONFIG_KEY_REMOTE, ".");
            setString(CONFIG_BRANCH_SECTION,
                branchShortName, CONFIG_KEY_MERGE, upstream.shortName());
            LOG.info("Setting upstream for \"{}\" to local \"{}\"", branch.shortName(), upstream.shortName());
        }
        return this;
    }


    @Override
    @SuppressWarnings("RedundantCast")
    public @Nullable Branch getUpstream(Branch branch) {
        final String remoteName = getString(CONFIG_BRANCH_SECTION, branch.shortName(), CONFIG_KEY_REMOTE);

        if (remoteName == null) {
            LOG.debug("There is no upstream set for \"{}\"", branch.shortName());
            return null;
        }

        final String remotePath = (remoteName.equals(".") ? "" : remoteName + "/");

        final String upstreamBranchName = getString(CONFIG_BRANCH_SECTION, branch.shortName(), CONFIG_KEY_MERGE);

        if (upstreamBranchName == null) {
            throw new IllegalStateException("There is no configuration value for \"" + CONFIG_BRANCH_SECTION + '.' + branch.shortName() + '.' + CONFIG_KEY_MERGE + "\", which is an invalid Git state");
        }

        final String fullRemotePath = remotePath + Repository.shortenRefName(upstreamBranchName);

        final Branch upstream = branches.branch(fullRemotePath);

        if (upstream == null) {
            throw new IllegalArgumentException("The upstream for \"" + branch.shortName() + "\" (\"" + fullRemotePath + "\") does not exist");
        }

        return upstream;
    }

}
