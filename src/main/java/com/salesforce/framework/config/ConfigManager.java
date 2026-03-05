package com.salesforce.framework.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigManager {

    private static final Properties props = new Properties();

    static {
        // Try classpath first (standard Maven)
        try (InputStream is = ConfigManager.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                props.load(is);
            } else {
                // Fallback: try direct filesystem access (useful for some CI environments)
                java.io.File file = new java.io.File("src/test/resources/config.properties");
                if (file.exists()) {
                    try (InputStream fis = new java.io.FileInputStream(file)) {
                        props.load(fis);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[ConfigManager] Warning: Failed to load config.properties: " + e.getMessage());
        }
    }

    public static String get(String key) {
        String base = key.toUpperCase().replace('.', '_');
        String envKey = base.startsWith("SF_") ? base : "SF_" + base;
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isEmpty()) return envVal;
        return props.getProperty(key, "");
    }

    public static String getBaseUrl() {
        String url = get("sf.base.url");
        if (url.isEmpty()) {
            throw new RuntimeException("Base URL is empty! Ensure SF_BASE_URL env var or sf.base.url in config.properties is set.");
        }
        return url;
    }
    public static String getUsername()     { return get("sf.username"); }
    public static String getPassword()     { return get("sf.password"); }
    public static String getSessionFile()  { return get("sf.session.file"); }
    public static String getTestmailNamespace() { return get("testmail.namespace"); }
    public static String getTestmailTag()       { return get("testmail.tag"); }
    public static boolean isHeadless()     { return Boolean.parseBoolean(get("browser.headless")); }
    public static int getSlowMo()          { return Integer.parseInt(get("browser.slowmo")); }
    public static int getDefaultTimeout()  { return Integer.parseInt(get("browser.timeout")); }
}
