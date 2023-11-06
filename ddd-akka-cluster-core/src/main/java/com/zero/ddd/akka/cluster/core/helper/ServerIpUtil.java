package com.zero.ddd.akka.cluster.core.helper;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2018年6月15日 下午4:35:39
 * @Desc 些年若许,不负芳华.
 *
 */
@Slf4j
public class ServerIpUtil {

	public static final String SERVER_IP;
	public static final String LOCAL_SERVER_IP;
	public static final CharSequence SERVER_HOST_NAME;

	static {
		SERVER_IP = getServerIp();
		LOCAL_SERVER_IP = localServerIp();
		SERVER_HOST_NAME = getServerHostName();
	}

	private static String getServerIp() {
		String serverIp = null;
		try {
			Enumeration<NetworkInterface> netInterfaces = 
					NetworkInterface.getNetworkInterfaces();
			InetAddress ip = null;
			while (netInterfaces.hasMoreElements()) {
				NetworkInterface ni = (NetworkInterface) netInterfaces.nextElement();
				ip = ni.getInetAddresses().nextElement();
				serverIp = ip.getHostAddress();
				if (log.isDebugEnabled()) {
					log.debug(
							"serverIp:{}, siteLocal:{}, loopback:{}, linkLocal:{}", 
							serverIp, 
							ip.isSiteLocalAddress(), 
							ip.isLoopbackAddress(), 
							ip.isLinkLocalAddress());
				}
				if (!ip.isSiteLocalAddress() 
						&& !ip.isLoopbackAddress() 
						&& ip.getHostAddress().indexOf(":") == -1) {
					serverIp = ip.getHostAddress();
					break;
				} else {
					ip = null;
				}
			}
		} catch (Exception e) {
			log.error("error while getServerIp:{}", e);
		}
		if (serverIp == null) {
			return "127.0.0.1";
		}
		return serverIp.matches("\\d+.*") ? serverIp : "127.0.0.1";
	}

	private static String localServerIp() {
		try {
			InetAddress localHost = InetAddress.getLocalHost();
			log.info("local host ip:{}", localHost.getHostAddress());
			return localHost.getHostAddress();
		} catch (Exception e) {
			log.error("error while localServerIp:{}", e);
			return null;
		}
	}

	public static String getServerHostName() {
		try {
			InetAddress localHost = InetAddress.getLocalHost();
			log.info("local host name:{}", localHost.getHostName());
			return localHost.getHostName();
		} catch (UnknownHostException e) {
			log.error("error:{}", e);
		}
		return "";
	}

	public static void main(String[] args) throws UnknownHostException {
		System.out.println(System.getenv("HOSTNAME"));
		System.out.println(ServerIpUtil.LOCAL_SERVER_IP);
	}

}
