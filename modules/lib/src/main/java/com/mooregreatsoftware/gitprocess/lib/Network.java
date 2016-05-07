package com.mooregreatsoftware.gitprocess.lib;

import javaslang.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;

public class Network {
    private static final Logger LOG = LoggerFactory.getLogger(Network.class);


    public static String macAddress() {
        return Try.of(() -> {
            final InetAddress ip = localHost();
            LOG.debug("Current IP address : {}", ip.getHostAddress());

            final NetworkInterface network = NetworkInterface.getByInetAddress(ip);

            final byte[] mac = network.getHardwareAddress();

            return mac != null ? toHex(mac, true) : ip.getHostName();
        }).getOrElseThrow(ExecUtils.exceptionTranslator());
    }


    public static InetAddress localHost() throws UnknownHostException {
        final long startLocalhost = System.currentTimeMillis();
        final InetAddress ip = InetAddress.getLocalHost();
        final long endLocalhost = System.currentTimeMillis();
        if ((endLocalhost - startLocalhost) > 1_000L) {
            LOG.warn("It took a long time to get the localhost entry.\n" +
                "Your /etc/hosts likely needs to be updated to something like\n" +
                "127.0.0.1  localhost  {}\n" +
                "::1 localhost  {}", ip.getHostName(), ip.getHostName());
        }
        return ip;
    }


    public static String toHex(byte[] mac, boolean dashDelim) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            sb.append(String.format("%02X%s", mac[i], (dashDelim && i < mac.length - 1) ? "-" : ""));
        }
        return sb.toString();
    }

}
