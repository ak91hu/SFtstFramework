package com.salesforce.framework.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigManager {

    private static final Properties props = new Properties();

    static {
        try (InputStream is = ConfigManager.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }
    }

    public static String get(String key) {
        String envKey = "SF_" + key.toUpperCase().replace('.', '_');
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isEmpty()) return envVal;
        return props.getProperty(key, "");
    }

    public static String getBaseUrl()      { return get("sf.base.url"); }
    public static String getUsername()     { return get("sf.username"); }
    public static String getPassword()     { return get("sf.password"); }
    public static String getSessionFile()  { return get("sf.session.file"); }
    public static boolean isHeadless()     { return Boolean.parseBoolean(get("browser.headless")); }
    public static int getSlowMo()          { return Integer.parseInt(get("browser.slowmo")); }
    public static int getDefaultTimeout()  { return Integer.parseInt(get("browser.timeout")); }
}
