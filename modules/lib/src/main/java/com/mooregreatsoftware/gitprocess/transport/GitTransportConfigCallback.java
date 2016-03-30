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
