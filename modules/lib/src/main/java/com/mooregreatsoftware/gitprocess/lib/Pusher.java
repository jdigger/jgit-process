package com.mooregreatsoftware.gitprocess.lib;

import javaslang.control.Either;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

public interface Pusher {
    Either<String, ThePushResult> push();

    static B.LocalBranch from(GitLib gitLib) {
        return new B.PusherBuilder().gitLib(gitLib);
    }

    interface B {
        class PusherBuilder implements Builder, TheGitLib, LocalBranch, RemoteBranchName, ForcePush {
            private @MonotonicNonNull GitLib gitLib;
            private @MonotonicNonNull Branch localBranch;
            private @MonotonicNonNull String remoteBranchName;
            private boolean forcePush;


            @Override
            public Pusher build() {
                if (gitLib instanceof JgitGitLib) {
                    return new JgitPusher(gitLib, localBranch, remoteBranchName, forcePush);
                }
                throw new IllegalStateException("Don't know how to create a Pusher without JGit");
            }


            @Override
            public ForcePush remoteBranchName(String branchName) {
                this.remoteBranchName = branchName;
                return this;
            }


            @Override
            public LocalBranch gitLib(GitLib gitLib) {
                this.gitLib = gitLib;
                return this;
            }


            @Override
            public RemoteBranchName localBranch(Branch branch) {
                this.localBranch = branch;
                return this;
            }


            @Override
            public RemoteBranchName localBranchName(String branchName) {
                if (gitLib == null) throw new IllegalStateException("gitLib == null"); // should be impossible
                final Branch branch = gitLib.branches().branch(branchName);
                if (branch == null) throw new IllegalArgumentException("Unknown branch: " + branchName);
                return this;
            }


            @Override
            public Builder force(boolean force) {
                this.forcePush = force;
                return this;
            }
        }

        interface Builder {
            Pusher build();
        }

        interface TheGitLib {
            LocalBranch gitLib(GitLib gitLib);
        }

        @SuppressWarnings("unused")
        interface LocalBranch {
            RemoteBranchName localBranch(Branch branch);

            RemoteBranchName localBranchName(String branchName);
        }

        interface RemoteBranchName {
            ForcePush remoteBranchName(String branchName);
        }

        interface ForcePush {
            Builder force(boolean force);
        }
    }

    class ThePushResult {
        protected boolean success;


        public ThePushResult() {
        }


        public boolean success() {
            return success;
        }
    }

}
