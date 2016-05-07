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
import javaslang.control.Try.CheckedRunnable;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.output.WriterOutputStream;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jgit.transport.ChainingCredentialsProvider;
import org.eclipse.jgit.transport.NetRCCredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.function.Supplier;

import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;
import static javaslang.control.Either.left;
import static javaslang.control.Either.right;

public class Pusher {
    private static final Logger LOG = LoggerFactory.getLogger(Pusher.class);


    public static class PusherOptions {
        public final GitLib gitLib;
        public final Branch localBranch;
        public final String remoteBranchName;
        public final @Nullable CheckedRunnable prePush;
        public final @Nullable CheckedRunnable postPush;


        public PusherOptions(GitLib gitLib, Branch localBranch, String remoteBranchName,
                             @Nullable CheckedRunnable prePush, @Nullable CheckedRunnable postPush) {
            this.gitLib = gitLib;
            this.localBranch = localBranch;
            this.remoteBranchName = remoteBranchName;
            this.prePush = prePush;
            this.postPush = postPush;
        }


        public static Builder from(GitLib gitLib) {
            return new Builder(gitLib);
        }


        @SuppressWarnings({"unused", "RedundantCast"})
        public static class Builder {
            private GitLib gitLib;
            @MonotonicNonNull
            private Branch localBranch = null;
            @MonotonicNonNull
            private String remoteBranchName = null;
            @Nullable
            private CheckedRunnable prePush = null;
            @Nullable
            private CheckedRunnable postPush = null;


            public Builder(@NonNull GitLib gitLib) {
                if (gitLib == null) throw new IllegalStateException("gitLib == null");
                this.gitLib = gitLib;
            }


            public PusherOptions build() throws IllegalStateException {
                if (localBranch == null) throw new IllegalStateException("localBranch == null");
                if (remoteBranchName == null) throw new IllegalStateException("remoteBranchName == null");
                return new PusherOptions(gitLib, localBranch, remoteBranchName, prePush, postPush);
            }


            public Builder localBranch(@NonNull Branch localBranch) {
                this.localBranch = localBranch;
                return (@NonNull Builder)this;
            }


            public Builder localBranch(@NonNull String localBranchName) throws IllegalArgumentException {
                final Branch branch = gitLib.branches().branch(localBranchName);
                if (branch == null)
                    throw new IllegalArgumentException("\"" + localBranchName + "\" is not a known branch");
                this.localBranch = branch;
                return (@NonNull Builder)this;
            }


            public Builder remoteBranchName(@NonNull String remoteBranchName) {
                this.remoteBranchName = remoteBranchName;
                return (@NonNull Builder)this;
            }


            public Builder prePush(@Nullable CheckedRunnable prePush) {
                this.prePush = prePush;
                return (@NonNull Builder)this;
            }


            public Builder postPush(@Nullable CheckedRunnable postPush) {
                this.postPush = postPush;
                return (@NonNull Builder)this;
            }
        }
    }

    private PusherOptions options;
    private boolean forcePush;


    public Pusher(PusherOptions options, boolean forcePush) {
        this.options = options;
        this.forcePush = forcePush;
    }


    public static Pusher create(GitLib gitLib,
                                Branch localBranch,
                                String remoteBranchName,
                                boolean forcePush,
                                @Nullable CheckedRunnable prePush,
                                @Nullable CheckedRunnable postPush) {
        PusherOptions options = PusherOptions.from(gitLib).
            localBranch(localBranch).
            remoteBranchName(remoteBranchName).
            prePush(prePush).
            postPush(postPush).
            build();
        return new Pusher(options, forcePush);
    }


    // TODO: Create a version that does force-push with lease


    public Either<String, ThePushResult> push() {
        return push(options.gitLib, options.localBranch, options.remoteBranchName, forcePush, options.prePush, options.postPush);
    }


    /**
     * Push the local branch to the remote branch on the server.
     * <p>
     * Has protections to prevent things like pushing the local shadow of the integration branch.
     *
     * @param localBranch      the name of the local branch to push
     * @param remoteBranchName the name of the branch to push to on the server
     * @param forcePush        should it force the push even if it can not fast-forward?
     * @param prePush          called before doing the push (may be null)
     * @param postPush         called after doing the push (may be null); if null will run
     *                         {@link Branch#recordLastSyncedAgainst()} if the remote branch is the same name as
     *                         the local branch
     * @return Left(error message), Right(results of the push)
     * @see RemoteConfig#remoteName()
     * @see BranchConfig#integrationBranch()
     */
    // TODO: See if explicit pre- and post-Push handlers are needed
    public static Either<String, ThePushResult> push(GitLib gitLib,
                                                     Branch localBranch,
                                                     String remoteBranchName,
                                                     boolean forcePush,
                                                     @Nullable CheckedRunnable prePush,
                                                     @Nullable CheckedRunnable postPush) {
        final RemoteConfig remoteConfig = gitLib.remoteConfig();
        if (remoteConfig.hasRemotes()) {
            final Branch integrationBranch = gitLib.branches().integrationBranch();
            if (integrationBranch != null) {
                final String simpleName = integrationBranch.simpleName();
                if (simpleName.equals(localBranch.simpleName())) {
                    return left("Not pushing to the server because the current branch (" + simpleName + ") is the mainline branch.");
                }
            }

            if (prePush != null) {
                LOG.debug("Running pre-push function");
                final Try<Void> prePushTry = Try.run(prePush);
                if (prePushTry.isFailure()) return left(ExecUtils.toString(prePushTry.getCause()));
                LOG.debug("Ran pre-push function");
            }

            @SuppressWarnings("RedundantCast")
            final String remoteName = remoteConfig.remoteName();
            if (remoteName == null)
                return left("Could not find the remote name (e.g., \"origin\") for this repository");

            LOG.info("Pushing \"{}\" to \"{}\" on \"{}\"", localBranch.shortName(), remoteBranchName, remoteName);
            final Branch remoteBranch = gitLib.branches().branch(remoteName + "/" + remoteBranchName);
            LOG.debug("Expected OID of remote branch is {}", remoteBranch != null ? remoteBranch.objectId().abbreviate(7).name() : "UNKNOWN");

//            final ThePushResult thePushResult = doGitProgPush(gitLib, localBranch, remoteBranchName, forcePush, remoteName);
            final ThePushResult thePushResult = doJGitPush(gitLib, localBranch, remoteBranchName, forcePush, remoteName);

            // TODO: Implement --force-with-lease

            if (thePushResult.success()) {
                LOG.info("Pushed: " + thePushResult);
            }
            else {
                return left(thePushResult.toString());
            }

            if (postPush != null) {
                LOG.debug("Running post-push function");
                final Try<Void> postPushTry = Try.run(postPush);
                if (postPushTry.isFailure()) {
                    return left(ExecUtils.toString(postPushTry.getCause()));
                }
                LOG.debug("Ran post-push function");
            }
            else {
                if (localBranch.simpleName().equals(remoteBranchName)) {
                    LOG.debug("Recording the last synced value");
                    final String errorMsg = localBranch.recordLastSyncedAgainst();
                    if (errorMsg != null) return left(errorMsg);
                }
                else {
                    LOG.debug("Local ({}) and remote ({}) names are not the same, so not recording the last synced value", localBranch.simpleName(), remoteBranchName);
                }
            }

            return right(thePushResult);
        }
        else {
            return left("Not pushing to the server because there is no remote.");
        }
    }


    @SuppressWarnings("unused")
    private static ThePushResult doGitProgPush(GitLib gitLib, Branch localBranch, String remoteBranchName, boolean forcePush, String remoteName) {
        String cmd = String.format("git push --porcelain %s %s %s:%s", remoteName, forcePush ? "--force" : "", localBranch.shortName(), remoteBranchName);
        CommandLine commandLine = CommandLine.parse(cmd);
        DefaultExecutor executor = new DefaultExecutor();
        executor.setWorkingDirectory(gitLib.workingDirectory());
        final StringWriter stdOutWriter = new StringWriter();
        final StringWriter stdErrWriter = new StringWriter();
        executor.setStreamHandler(new PumpStreamHandler(new WriterOutputStream(stdOutWriter), new WriterOutputStream(stdErrWriter)));
        final int exitCode = Try.of(() -> {
            try {
                return executor.execute(commandLine);
            }
            catch (ExecuteException e) {
                return e.getExitValue();
            }
            catch (IOException e) {
                final String message = e.getMessage();
                if (message != null && message.contains("No such file or directory")) {
                    return 1;
                }
                e.printStackTrace();
                return -1;
            }
        }).get();

        return new ProcPushResult(stdOutWriter, stdErrWriter, exitCode);
    }


    private static ThePushResult doJGitPush(GitLib gitLib, Branch localBranch, String remoteBranchName, boolean forcePush, String remoteName) {
        final Iterable<PushResult> pushResults = Try.of(() ->
            gitLib.jgit().push().
                setRemote(remoteName).
                setRefSpecs(new RefSpec(localBranch.shortName() + ":" + remoteBranchName)).
                setForce(forcePush).
                setCredentialsProvider(new ChainingCredentialsProvider(new NetRCCredentialsProvider())).
                setTransportConfigCallback(new GitTransportConfigCallback()).
                call()
        ).get();
        return new JGitPushResult(pushResults);
    }


    public static class ThePushResult {
        protected boolean success;


        public ThePushResult() {
        }


        public boolean success() {
            return success;
        }
    }


    public static class ProcPushResult extends ThePushResult {

        final StringWriter stdOutWriter;

        final StringWriter stdErrWriter;


        public ProcPushResult(StringWriter stdOutWriter, StringWriter stdErrWriter, int exitCode) {
            this.stdOutWriter = stdOutWriter;
            this.stdErrWriter = stdErrWriter;
            success = (exitCode == 0);
        }


        public String toString() {
            final String stderr = stdErrWriter.toString().trim();
            return stderr.isEmpty() ? stdOutWriter.toString() : stdOutWriter.toString() + "\n" + stderr;
        }
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
        final GitLib gitLib = GitLib.of(new File("."));
        @SuppressWarnings("RedundantCast")
        final Branch currentBranch = (@NonNull Branch)gitLib.branches().currentBranch();
        final Either<String, ThePushResult> simplePushResult = Pusher.push(gitLib, currentBranch, "test", false, null, null);
        LOG.warn("spr: " + simplePushResult);
    }

}
