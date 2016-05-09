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
import com.mooregreatsoftware.gitprocess.config.RemoteConfig;
import com.mooregreatsoftware.gitprocess.transport.GitTransportConfigCallback;
import javaslang.control.Either;
import javaslang.control.Try;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.jgit.transport.ChainingCredentialsProvider;
import org.eclipse.jgit.transport.NetRCCredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.function.Supplier;

import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;
import static javaslang.control.Either.left;
import static javaslang.control.Either.right;

@SuppressWarnings("RedundantCast")
public class JgitPusher implements Pusher {
    private static final Logger LOG = LoggerFactory.getLogger(JgitPusher.class);

    private final GitLib gitLib;
    private final Branch localBranch;
    private final String remoteBranchName;
    private final boolean forcePush;


    public JgitPusher(GitLib gitLib, Branch localBranch, String remoteBranchName, boolean forcePush) {
        this.gitLib = gitLib;
        this.localBranch = localBranch;
        this.remoteBranchName = remoteBranchName;
        this.forcePush = forcePush;
    }

    // TODO: Create a version that does force-push with lease


    /**
     * Push the local branch to the remote branch on the server.
     * <p>
     * Has protections to prevent things like pushing the local shadow of the integration branch.
     *
     * @return Left(error message), Right(results of the push)
     * @see RemoteConfig#remoteName()
     * @see BranchConfig#integrationBranch()
     */
    @Override
    public Either<String, ThePushResult> push() {
        final RemoteConfig remoteConfig = gitLib.remoteConfig();
        if (remoteConfig.hasRemotes()) {
            final Branch integrationBranch = gitLib.branches().integrationBranch();
            if (integrationBranch != null) {
                final String simpleName = integrationBranch.simpleName();
                if (simpleName.equals(localBranch.simpleName())) {
                    return left("Not pushing to the server because the current branch (" + simpleName + ") is the mainline branch.");
                }
            }

            final String remoteName = remoteConfig.remoteName();
            if (remoteName == null)
                return left("Could not find the remote name (e.g., \"origin\") for this repository");

            LOG.info("Pushing \"{}\" to \"{}\" on \"{}\"", localBranch.shortName(), remoteBranchName, remoteName);
            final Branch remoteBranch = gitLib.branches().branch(remoteName + "/" + remoteBranchName);
            LOG.debug("Expected OID of remote branch is {}", remoteBranch != null ? remoteBranch.objectId().abbreviate(7).name() : "UNKNOWN");

            final ThePushResult thePushResult = doJGitPush(remoteName);

            // TODO: Implement --force-with-lease

            if (thePushResult.success()) {
                LOG.info("Pushed: " + thePushResult);
            }
            else {
                return left(thePushResult.toString());
            }

            if (localBranch.simpleName().equals(remoteBranchName)) {
                LOG.debug("Recording the last synced value");
                final String errorMsg = localBranch.recordLastSyncedAgainst();
                if (errorMsg != null) return left(errorMsg);
            }
            else {
                LOG.debug("Local ({}) and remote ({}) names are not the same, so not recording the last synced value",
                    localBranch.simpleName(), remoteBranchName);
            }

            return right(thePushResult);
        }
        else {
            return left("Not pushing to the server because there is no remote.");
        }
    }


    private ThePushResult doJGitPush(String remoteName) {
        final Iterable<PushResult> pushResults = Try.of(() ->
            ((JgitGitLib)gitLib).jgit().push().
                setRemote(remoteName).
                setRefSpecs(new RefSpec(localBranch.shortName() + ":" + remoteBranchName)).
                setForce(forcePush).
                setCredentialsProvider(new ChainingCredentialsProvider(new NetRCCredentialsProvider())).
                setTransportConfigCallback(new GitTransportConfigCallback()).
                call()
        ).get();
        return new JGitPushResult(pushResults);
    }


    public static class JGitPushResult extends ThePushResult {
        private final Iterable<PushResult> pushResults;


        public JGitPushResult(Iterable<PushResult> pushResults) {
            this.pushResults = pushResults;
            this.success = allPushesSuccessful(pushResults);
        }


        private static boolean allPushesSuccessful(Iterable<PushResult> pushResults) {
            return StreamUtils.stream(pushResults).
                allMatch(JGitPushResult::pushResultSuccessful);
        }


        private static boolean pushResultSuccessful(PushResult res) {
            return res.getRemoteUpdates().stream().
                allMatch(JGitPushResult::goodStatus);
        }


        private static boolean goodStatus(RemoteRefUpdate refUpd) {
            final RemoteRefUpdate.Status status = refUpd.getStatus();
            return status == RemoteRefUpdate.Status.OK || status == RemoteRefUpdate.Status.UP_TO_DATE;
        }


        public String toString() {
            return StreamUtils.stream(pushResults).
                map(pushResult -> "remoteUpdates: [" +
                    toString(pushResult::getRemoteUpdates) + "], " +
                    "messages: " + pushResult.getMessages() + ", " +
                    "advertisedRefs: [" + toString(pushResult::getAdvertisedRefs) + "], " +
                    "trackingRefs: [" + toString(pushResult::getTrackingRefUpdates) + "]").
                collect(joining(lineSeparator()));
        }


        @SuppressWarnings("TypeParameterExplicitlyExtendsObject")
        private static String toString(Supplier<Collection<? extends @NonNull Object>> supplier) {
            return supplier.get().stream().map(Object::toString).collect(joining(", "));
        }
    }


    public static void main(String[] args) throws IOException {
        final GitLib gitLib = JgitGitLib.of(new File("."));
        @SuppressWarnings("RedundantCast")
        final Branch currentBranch = (@NonNull Branch)gitLib.branches().currentBranch();
        final Pusher pusher = Pusher.from(gitLib).localBranch(currentBranch).remoteBranchName("test").force(false).build();
        final Either<String, ThePushResult> simplePushResult = pusher.push();
        LOG.warn("spr: " + simplePushResult);
    }

}
