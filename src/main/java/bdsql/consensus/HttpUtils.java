package bdsql.consensus;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class HttpUtils {

    public static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    public static String sanitizeForFilename(String s) {
        if (s == null || s.isBlank()) {
            return "node";
        }
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public static String leaderHttpUrl(String leaderHostPort, String path) {
        System.out.println("Constructing leader HTTP URL from leaderHostPort: " + leaderHostPort + " and path: " + path);
        if (leaderHostPort == null || leaderHostPort.isBlank())
            return null;
        String[] parts = leaderHostPort.split(":");
        if (parts.length != 2)
            return null;
        String host = parts[0].trim();
        int port;
        try {
            port = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        int httpPort = port + 1000;
        if (!path.startsWith("/"))
            path = "/" + path;
        return "http://" + host + ":" + httpPort + path;
    }

    public static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return s;
        }
    }
}