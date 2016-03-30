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
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.output.WriterOutputStream;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.ChainingCredentialsProvider;
import org.eclipse.jgit.transport.NetRCCredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Optional;

import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.e;
import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.joining;
import static javaslang.control.Either.left;
import static javaslang.control.Either.right;

public class Pusher {
    private static final Logger LOG = LoggerFactory.getLogger(Pusher.class);


    public static class PusherOptions {
        @Nonnull
        public final GitLib gitLib;
        @Nonnull
        public final Branch localBranch;
        @Nonnull
        public final String remoteBranchName;
        @Nullable
        public final Try.CheckedRunnable prePush;
        @Nullable
        public final Try.CheckedRunnable postPush;


        public PusherOptions(@Nonnull GitLib gitLib, @Nonnull Branch localBranch, @Nonnull String remoteBranchName, @Nullable Try.CheckedRunnable prePush, @Nullable Try.CheckedRunnable postPush) {
            this.gitLib = gitLib;
            this.localBranch = localBranch;
            this.remoteBranchName = remoteBranchName;
            this.prePush = prePush;
            this.postPush = postPush;
        }


        @Nonnull
        public static Builder from(@Nonnull GitLib gitLib) {
            return new Builder(gitLib);
        }


        @SuppressWarnings("unused")
        public static class Builder {
            private GitLib gitLib;
            private Branch localBranch;
            private String remoteBranchName;
            private Try.CheckedRunnable prePush;
            private Try.CheckedRunnable postPush;


            public Builder(GitLib gitLib) {
                if (gitLib == null) throw new IllegalStateException("gitLib == null");
                this.gitLib = gitLib;
            }


            @Nonnull
            public PusherOptions build() throws IllegalStateException {
                if (localBranch == null) throw new IllegalStateException("localBranch == null");
                if (remoteBranchName == null) throw new IllegalStateException("remoteBranchName == null");
                return new PusherOptions(gitLib, localBranch, remoteBranchName, prePush, postPush);
            }


            @Nonnull
            public Builder localBranch(@Nonnull Branch localBranch) {
                this.localBranch = localBranch;
                return this;
            }


            @Nonnull
            public Builder localBranch(@Nonnull String localBranchName) throws IllegalArgumentException {
                this.localBranch = gitLib.branches().
                    branch(localBranchName).
                    orElseThrow(() -> new IllegalArgumentException("\"" + localBranchName + "\" is not a known branch"));
                return this;
            }


            @Nonnull
            public Builder remoteBranchName(@Nonnull String remoteBranchName) {
                this.remoteBranchName = remoteBranchName;
                return this;
            }


            @Nonnull
            public Builder prePush(Try.CheckedRunnable prePush) {
                this.prePush = prePush;
                return this;
            }


            @Nonnull
            public Builder postPush(Try.CheckedRunnable postPush) {
                this.postPush = postPush;
                return this;
            }
        }
    }

    @Nonnull
    private PusherOptions options;
    private boolean forcePush;


    public Pusher(@Nonnull PusherOptions options, boolean forcePush) {
        this.options = options;
        this.forcePush = forcePush;
    }


    public static Pusher create(@Nonnull GitLib gitLib,
                                @Nonnull Branch localBranch,
                                @Nonnull String remoteBranchName,
                                boolean forcePush,
                                @Nullable Try.CheckedRunnable prePush,
                                @Nullable Try.CheckedRunnable postPush) {
        return new Pusher(PusherOptions.from(gitLib).localBranch(localBranch).remoteBranchName(remoteBranchName).prePush(prePush).postPush(postPush).build(), forcePush);
    }


    // TODO: Create a version that does force-push with lease


    public static Pusher create(@Nonnull GitLib gitLib,
                                @Nonnull Branch localBranch,
                                @Nonnull String remoteBranchName) {
        return new Pusher(PusherOptions.from(gitLib).localBranch(localBranch).remoteBranchName(remoteBranchName).build(), false);
    }


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
    @Nonnull
    public static Either<String, ThePushResult> push(@Nonnull GitLib gitLib,
                                                     @Nonnull Branch localBranch,
                                                     @Nonnull String remoteBranchName,
                                                     boolean forcePush,
                                                     @Nullable Try.CheckedRunnable prePush,
                                                     @Nullable Try.CheckedRunnable postPush) {
        if (gitLib.remoteConfig().hasRemotes()) {
            final Optional<Branch> integrationBranch = gitLib.branches().integrationBranch();
            if (integrationBranch.isPresent()) {
                final String simpleName = integrationBranch.get().simpleName();
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

            final String remoteName = gitLib.remoteConfig().remoteName().get();
            LOG.info("Pushing \"{}\" to \"{}\" on \"{}\"", localBranch.shortName(), remoteBranchName, remoteName);
            LOG.debug("Expected OID of remote branch is {}",
                gitLib.branches().
                    branch(remoteName + "/" + remoteBranchName).
                    map(b -> b.objectId().abbreviate(7).name()).
                    orElse("UNKNOWN")
            );

            final ThePushResult thePushResult = doGitProgPush(gitLib, localBranch, remoteBranchName, forcePush, remoteName);
//            final SimplePushResult simplePushResult = doJGitPush(gitLib, localBranch, remoteBranchName, forcePush, remoteName);

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
                if (postPushTry.isFailure()) return left(ExecUtils.toString(postPushTry.getCause()));
                LOG.debug("Ran post-push function");
            }
            else {
                if (localBranch.simpleName().equals(remoteBranchName)) {
                    LOG.debug("Recording the last synced value");
                    final Optional<String> oErrorMsg = localBranch.recordLastSyncedAgainst();
                    if (oErrorMsg.isPresent()) return left(oErrorMsg.get());
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


    @Nonnull
    private static ThePushResult doGitProgPush(@Nonnull GitLib gitLib, @Nonnull Branch localBranch, @Nonnull String remoteBranchName, boolean forcePush, @Nonnull String remoteName) {
        String cmd = String.format("git push --porcelain %s %s %s:%s", remoteName, forcePush ? "--force" : "", localBranch.shortName(), remoteBranchName);
        CommandLine commandLine = CommandLine.parse(cmd);
        DefaultExecutor executor = new DefaultExecutor();
        executor.setWorkingDirectory(gitLib.workingDirectory());
        final StringWriter stdOutWriter = new StringWriter();
        final StringWriter stdErrWriter = new StringWriter();
        executor.setStreamHandler(new PumpStreamHandler(new WriterOutputStream(stdOutWriter), new WriterOutputStream(stdErrWriter)));
        final int exitCode = e(() -> {
            try {
                return executor.execute(commandLine);
            }
            catch (ExecuteException e) {
                return e.getExitValue();
            }
            catch (IOException e) {
                if (e.getMessage().contains("No such file or directory")) {
                    return 1;
                }
                e.printStackTrace();
                return -1;
            }
        });

        return new ProcPushResult(stdOutWriter, stdErrWriter, exitCode);
    }


    @Nonnull
    private static ThePushResult doJGitPush(@Nonnull GitLib gitLib, @Nonnull Branch localBranch, @Nonnull String remoteBranchName, boolean forcePush, String remoteName) {
        final Iterable<PushResult> pushResults = e(() ->
                gitLib.jgit().push().
                    setRemote(remoteName).
                    setRefSpecs(new RefSpec(localBranch.shortName() + ":" + remoteBranchName)).
                    setForce(forcePush).
                    setCredentialsProvider(new ChainingCredentialsProvider(new NetRCCredentialsProvider())).
                    setTransportConfigCallback(new GitTransportConfigCallback()).
                    call()
        );
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
        }


        public String toString() {
            return StreamUtils.stream(pushResults).
                map(pushResult ->
                    "remoteUpdates: [" +
                        pushResult.getRemoteUpdates().stream().map(RemoteRefUpdate::toString).collect(joining(", ")) + "], " +
                        "messages: " + pushResult.getMessages() + ", " +
                        "advertisedRefs: [" + pushResult.getAdvertisedRefs().stream().map(Ref::getName).collect(joining(", ")) + "], " +
                        "trackingRefs: [" + pushResult.getTrackingRefUpdates().stream().map(TrackingRefUpdate::toString).collect(joining(", ")) + "]").
                collect(joining(lineSeparator()));
        }
    }


    public static void main(String[] args) throws IOException {
        GitLib gitLib = GitLib.of(new File("."));
        final Either<String, ThePushResult> simplePushResult = Pusher.push(gitLib, gitLib.branches().currentBranch().get(), "test", false, null, null);
        LOG.warn("spr: " + simplePushResult);
    }

}
