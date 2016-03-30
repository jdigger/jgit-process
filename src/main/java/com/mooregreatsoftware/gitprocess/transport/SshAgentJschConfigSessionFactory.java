package com.mooregreatsoftware.gitprocess.transport;

import com.jcraft.jsch.IdentityRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.agentproxy.AgentProxyException;
import com.jcraft.jsch.agentproxy.Connector;
import com.jcraft.jsch.agentproxy.RemoteIdentityRepository;
import com.jcraft.jsch.agentproxy.USocketFactory;
import com.jcraft.jsch.agentproxy.connector.SSHAgentConnector;
import com.jcraft.jsch.agentproxy.usocket.JNAUSocketFactory;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An extension of {@link JschConfigSessionFactory} that will use ssh-agent if it is available.
 */
public class SshAgentJschConfigSessionFactory extends JschConfigSessionFactory {
    private static final Logger LOG = LoggerFactory.getLogger(SshAgentJschConfigSessionFactory.class);

    static {
        JSch.setLogger(new com.jcraft.jsch.Logger() {
            @Override
            public boolean isEnabled(int level) {
                return true;
            }


            @Override
            public void log(int level, String message) {
                LOG.debug(message);
            }
        });
    }

    @Override
    protected void configure(OpenSshConfig.Host host, Session session) {
        // empty
    }


    @Override
    protected JSch createDefaultJSch(FS fs) throws JSchException {
        final JSch jsch = super.createDefaultJSch(fs);

        try {
            if (SSHAgentConnector.isConnectorAvailable()) {
                USocketFactory usf = new JNAUSocketFactory();
                Connector conn = new SSHAgentConnector(usf);
                IdentityRepository identRepo = new RemoteIdentityRepository(conn);
                jsch.setIdentityRepository(identRepo);
            }
        }
        catch (AgentProxyException e) {
            LOG.error("Could not establish a connection to ssh-agent: {}", e.getMessage());
        }

        return jsch;
    }
}
