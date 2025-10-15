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
}