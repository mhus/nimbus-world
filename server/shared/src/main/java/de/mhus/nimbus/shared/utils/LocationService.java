package de.mhus.nimbus.shared.utils;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static org.apache.logging.log4j.util.Strings.isBlank;
import static org.apache.logging.log4j.util.Strings.isEmpty;

/*
 * Service to determine server location details such as IP address and port.
 *
 * I'm not fully happy with this implementation, but it works for now.
 *
 */
@Service
@Slf4j
public class LocationService {

    public enum SERVER {
        UNKNOWN,
        PLAYER,
        LIFE,
        CONTROL,
        GENERATOR,
        UNIVERSE,
        REGION
    }


    @Value("${server.port:4092}")
    private int applicationServerPort;

    @Value("${spring.application.host:}")
    private String applicationServerHost;

    @Value("${spring.application.name:}")
    private String applicationServiceName;

    private String serverIp;
    private Integer serverPort;
    private String externalAddress;
    private SERVER meServer;

    /**
     * Get application service name from spring.application.name property.
     */
    public String getApplicationServiceName() {
        return applicationServiceName;
    }

    /**
     * Get server IP address for originIp in CommandContext.
     * Uses environment variable NIMBUS_SERVER_IP or tries to detect real IP address.
     */
    public String getServerIp() {
        if (serverIp == null) {
            serverIp = System.getenv("NIMBUS_SERVER_IP");
            if (isBlank(serverIp) && !isBlank(applicationServerHost)) {
                serverIp = applicationServerHost;
            }
            if (serverIp == null || serverIp.isBlank()) {
                serverIp = detectRealIpAddress();
            }
            log.info("Using server IP for CommandContext: {}", serverIp);
        }
        return serverIp;
    }

    /**
     * Get server port for originPort in CommandContext.
     * Uses environment variable NIMBUS_SERVER_PORT or defaults to 9042 (world-player default).
     */
    public int getServerPort() {
        if (serverPort == null) {
            String portStr = System.getenv("NIMBUS_SERVER_PORT");
            if (portStr != null && !portStr.isBlank()) {
                try {
                    serverPort = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    log.warn("Invalid NIMBUS_SERVER_PORT: {}, using default 9042", portStr);
                    serverPort = applicationServerPort;
                }
            } else {
                serverPort = applicationServerPort; // Default world-player REST port
            }
            log.info("Using server port for CommandContext: {}", serverPort);
        }
        return serverPort;
    }

    public String getInternalServerUrl() {
        return "http://" + getServerIp() + ":" + getServerPort();
    }

    public String getExternalServerUrl() {
        if (externalAddress == null) {
            externalAddress = System.getenv("NIMBUS_EXTERNAL_URL");
            if (isEmpty(externalAddress)) {
                externalAddress = "http://" + getServerIp() + ":" + getServerPort();
            }
        }
        return getServerIp() + ":" + getServerPort();
    }

    /**
     * Detect the real IP address of the server.
     * Tries multiple approaches to find the actual IP, especially in containerized environments.
     */
    private String detectRealIpAddress() {
        try {
            // Try Kubernetes pod IP first (common environment variable)
            String podIp = System.getenv("POD_IP");
            if (podIp != null && !podIp.isBlank() && !podIp.equals("127.0.0.1")) {
                log.info("Using Kubernetes POD_IP: {}", podIp);
                return podIp;
            }

            // Try Kubernetes hostname (often the pod name with IP)
            String hostname = System.getenv("HOSTNAME");
            if (hostname != null && !hostname.isBlank()) {
                try {
                    java.net.InetAddress hostAddr = java.net.InetAddress.getByName(hostname);
                    String hostIp = hostAddr.getHostAddress();
                    if (!hostIp.equals("127.0.0.1") && !hostIp.startsWith("127.")) {
                        log.info("Using hostname IP: {} ({})", hostIp, hostname);
                        return hostIp;
                    }
                } catch (Exception e) {
                    log.debug("Could not resolve hostname {}: {}", hostname, e.getMessage());
                }
            }

            // Try to find non-loopback network interface
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = interfaces.nextElement();

                // Skip loopback and inactive interfaces
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();

                    // Skip loopback, link-local, and IPv6 addresses
                    if (address.isLoopbackAddress() || address.isLinkLocalAddress() ||
                            address instanceof java.net.Inet6Address) {
                        continue;
                    }

                    String ip = address.getHostAddress();
                    log.info("Detected network interface IP: {} ({})", ip, networkInterface.getName());
                    return ip;
                }
            }

            log.warn("Could not detect real IP address, falling back to localhost");
            return "localhost";

        } catch (Exception e) {
            log.error("Error detecting IP address, using localhost", e);
            return "localhost";
        }
    }

    public SERVER getMeServer() {
        if (meServer == null) {
            meServer = toServer(System.getenv("NIMBUS_SERVICE_NAME"));
            if (meServer == SERVER.UNKNOWN) {
                meServer = toServer(applicationServiceName);
            }
        }
        return meServer;
    }

    public SERVER toServer(String name) {
        switch (name != null ? name.toLowerCase() : "") {
            case "player":
            case "world-player":
            case "nimbus-world-player":
                return SERVER.PLAYER;
            case "life":
            case "world-life":
            case "nimbus-world-life":
                return SERVER.LIFE;
            case "control":
            case "world-control":
            case "nimbus-world-control":
                return SERVER.CONTROL;
            case "universe":
            case "nimbus-universe":
                return SERVER.UNIVERSE;
            case "region":
            case "nimbus-region":
                return SERVER.REGION;
            default:
                return SERVER.UNKNOWN;
        }
    }

    /**
     * Check if this service is running as world-control.
     *
     * @return true if running as world-control, false otherwise
     */
    public boolean isWorldControl() {
        return getMeServer() == SERVER.CONTROL;
    }

    /**
     * Check if this service is running as world-player.
     *
     * @return true if running as world-player, false otherwise
     */
    public boolean isWorldPlayer() {
        return getMeServer() == SERVER.PLAYER;
    }

    /**
     * Check if this service is running as world-life.
     *
     * @return true if running as world-life, false otherwise
     */
    public boolean isWorldLife() {
        return getMeServer() == SERVER.LIFE;
    }

    /**
     * Check if this service is running as universe.
     *
     * @return true if running as universe, false otherwise
     */
    public boolean isUniverse() {
        return getMeServer() == SERVER.UNIVERSE;
    }

    /**
     * Check if this service is running as region.
     *
     * @return true if running as region, false otherwise
     */
    public boolean isRegion() {
        return getMeServer() == SERVER.REGION;
    }

}
