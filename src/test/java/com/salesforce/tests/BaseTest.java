package com.salesforce.tests;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.salesforce.framework.config.ConfigManager;
import org.testng.annotations.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BaseTest {

    protected static Playwright playwright;
    protected static Browser browser;
    protected BrowserContext context;
    protected Page page;

    @BeforeSuite
    public static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
            new BrowserType.LaunchOptions()
                .setHeadless(ConfigManager.isHeadless())
                .setSlowMo(ConfigManager.getSlowMo())
        );
    }

    @BeforeMethod
    public void createContext() {
        Path sessionFile = Paths.get(ConfigManager.getSessionFile());
        if (Files.exists(sessionFile)) {
            context = browser.newContext(
                new Browser.NewContextOptions()
                    .setStorageStatePath(sessionFile)
            );
        } else {
            context = browser.newContext();
        }
        context.setDefaultTimeout(ConfigManager.getDefaultTimeout());
        page = context.newPage();
        ensureLoggedIn();
    }

    @AfterMethod
    public void closeContext() {
        if (page != null)    page.close();
        if (context != null) context.close();
    }

    @AfterSuite
    public static void closeBrowser() {
        if (browser != null)     browser.close();
        if (playwright != null)  playwright.close();
    }

    private void ensureLoggedIn() {
        page.navigate(ConfigManager.getBaseUrl() + "/lightning/page/home",
            new Page.NavigateOptions().setTimeout(60000));
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);

        // If we ended up on the login page, the session is missing or expired.
        if (page.url().contains("/login") || page.locator("#username").isVisible()) {
            doLogin();
        }
    }

    private void doLogin() {
        long loginTimestamp = System.currentTimeMillis();
        page.fill("#username", ConfigManager.getUsername());
        page.fill("#password", ConfigManager.getPassword());
        page.click("#Login");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Handle email OTP verification (Salesforce sends a code when device is unrecognised).
        if (!page.url().contains("/lightning/")) {
            System.out.println("[BaseTest] OTP verification required.");
            String code = fetchOtpCode(loginTimestamp);
            page.locator("input#emc, input[name='emc'], input[type='text']").first().fill(code);
            Locator remember = page.locator("input#RememberDeviceCheckbox");
            if (remember.count() > 0) remember.check();
            page.locator("input#save, input[type='submit']").first().click();
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        }

        page.waitForURL("**/lightning/**", new Page.WaitForURLOptions().setTimeout(60000));
        System.out.println("[BaseTest] Login successful: " + page.url().split("\\?")[0]);

        // Cache the authenticated session so subsequent tests in this run skip re-login.
        try {
            context.storageState(new BrowserContext.StorageStateOptions()
                .setPath(Paths.get(ConfigManager.getSessionFile())));
            System.out.println("[BaseTest] Session cached to " + ConfigManager.getSessionFile());
        } catch (Exception e) {
            System.out.println("[BaseTest] Warning: could not cache session: " + e.getMessage());
        }
    }

    /**
     * Polls the testmail.app API for a Salesforce OTP email received after
     * {@code afterTimestamp}. Requires the {@code TESTMAIL_API_KEY} env var.
     */
    private String fetchOtpCode(long afterTimestamp) {
        String apiKey = System.getenv("TESTMAIL_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException(
                "TESTMAIL_API_KEY env var is required for Salesforce OTP verification. " +
                "Set it locally or add it as a GitHub Secret.");
        }
        String namespace = ConfigManager.getTestmailNamespace();
        if (namespace.isEmpty()) namespace = "tssj8";
        String tag = ConfigManager.getTestmailTag();
        if (tag.isEmpty()) tag = "test";

        String apiUrl = "https://api.testmail.app/api/json"
            + "?apikey=" + apiKey + "&namespace=" + namespace + "&tag=" + tag;
        Pattern codePattern = Pattern.compile(
            "Verification Code[^0-9]+([0-9]{5,8})", Pattern.CASE_INSENSITIVE);

        long deadline = System.currentTimeMillis() + 90_000;
        System.out.println("[BaseTest] Polling testmail.app for OTP code...");
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(4000);
                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                String body = new String(conn.getInputStream().readAllBytes());
                conn.disconnect();

                // Find the most recent OTP code (last match = last in JSON array).
                Matcher m = codePattern.matcher(body);
                String found = null;
                while (m.find()) found = m.group(1);
                if (found != null) {
                    System.out.println("[BaseTest] OTP code found: " + found);
                    return found;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ignored) {
                // network hiccup — keep polling
            }
        }
        throw new RuntimeException("Timed out waiting for Salesforce OTP email (90s)");
    }
}
