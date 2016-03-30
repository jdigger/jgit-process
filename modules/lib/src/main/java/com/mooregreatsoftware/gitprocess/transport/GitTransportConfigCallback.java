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
package com.mooregreatsoftware.gitprocess.transport;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;

/**
 * Transport configuration callback for the "remoting" Git commands (e.g., clone, fetch, push, etc.) that
 * will use {@link }SshAgentJschConfigSessionFactory} when the transport is SSH.
 */
public class GitTransportConfigCallback implements TransportConfigCallback {
    @Override
    public void configure(Transport transport) {
        if (transport instanceof SshTransport) {
            SshTransport sshTransport = (SshTransport)transport;
            sshTransport.setSshSessionFactory(new SshAgentJschConfigSessionFactory());
        }
    }
}
