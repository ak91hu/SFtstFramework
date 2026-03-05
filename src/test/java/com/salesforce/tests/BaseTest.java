package com.salesforce.tests;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
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
        
        // Capture browser console logs
        page.onConsoleMessage(msg -> {
            System.out.println("[BROWSER] [" + msg.type() + "] " + msg.text());
        });

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
        String targetUrl = ConfigManager.getBaseUrl();
        if (targetUrl.endsWith("/")) targetUrl = targetUrl.substring(0, targetUrl.length() - 1);
        targetUrl += "/lightning/page/home";

        log("Navigating to: " + targetUrl);
        try {
            page.navigate(targetUrl, new Page.NavigateOptions().setTimeout(90000));
        } catch (Exception e) {
            log("Navigation failed: " + e.getMessage());
            saveDebugScreenshot("nav_failure");
            throw e;
        }
        
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(15000));
        } catch (Exception ignored) {}

        log("Current URL: " + page.url());
        saveDebugScreenshot("initial_load");

        // If we ended up on the login page, the session is missing or expired.
        if (page.url().contains("/login") || page.locator("#username").isVisible()) {
            log("Session expired or not found. Logging in...");
            doLogin();
        } else {
            log("Already logged in or on an intermediate page.");
            handleIntermediatePages();
        }
    }

    private void doLogin() {
        long loginTimestamp = System.currentTimeMillis();
        log("Filling credentials for: " + ConfigManager.getUsername());
        
        page.fill("#username", ConfigManager.getUsername());
        page.fill("#password", ConfigManager.getPassword());
        saveDebugScreenshot("before_login_click");
        
        // Wait for the URL to change after clicking Login.
        String currentUrl = page.url();
        page.click("#Login");
        
        log("Waiting for post-login redirect...");
        try {
            page.waitForURL(url -> !url.equals(currentUrl), 
                new Page.WaitForURLOptions().setTimeout(30000));
        } catch (Exception e) {
            log("URL did not change after 30s. Current URL: " + page.url());
            saveDebugScreenshot("login_no_redirect");
        }
        
        try {
            page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(30000));
        } catch (Exception ignored) {}
        
        log("Post-login URL: " + page.url());
        saveDebugScreenshot("post_login_click");

        // Handle email OTP verification (MFA / Computer Activation)
        if (page.url().contains("verification") || page.url().contains("identity") || !page.url().contains("/lightning/")) {
            // Case 1: On Start page (need to click "Send" or "Continue" button)
            if (page.url().contains("StartUi")) {
                log("On identity/verification START page. Looking for Send/Continue button...");
                saveDebugScreenshot("verification_start");
                
                // Broad selector: Salesforce often uses input[name='save'] or value containing "Email", "Send", or "Continue"
                Locator sendBtn = page.locator("input#save, input#verify, input[name='save'], input[type='submit'], " +
                                              "button:has-text('Send'), button:has-text('Continue'), " +
                                              "input[value*='Email'], input[value*='Send'], input[value*='Continue']").first();
                try {
                    sendBtn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
                    String btnLabel = sendBtn.getAttribute("value");
                    if (btnLabel == null) btnLabel = sendBtn.innerText();
                    log("Action button found: " + btnLabel);
                    sendBtn.click();
                    log("Clicked action button. Waiting for Finish page...");
                    try {
                        page.waitForURL(url -> url.contains("FinishUi") || url.contains("verification") || url.contains("/lightning/"), 
                            new Page.WaitForURLOptions().setTimeout(25000));
                        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                    } catch (Exception e) {
                        log("URL did not change to FinishUi after clicking Send. Current: " + page.url());
                    }
                } catch (Exception e) {
                    log("Send/Continue button not visible after 10s. Listing all visible buttons...");
                    Locator allButtons = page.locator("input[type='submit'], button:visible");
                    for (int i = 0; i < allButtons.count(); i++) {
                        log("  Button " + i + ": " + allButtons.nth(i).getAttribute("value") + " | " + allButtons.nth(i).innerText());
                    }
                }
            }

            // Case 2: On Finish page (need to enter code)
            if (page.locator("input#emc, input[name='emc'], input#otp").count() > 0) {
                log("On verification FINISH page. OTP required.");
                String code = fetchOtpCode(loginTimestamp);
                page.locator("input#emc, input[name='emc'], input#otp, input[type='text']").first().fill(code);
                Locator remember = page.locator("input#RememberDeviceCheckbox");
                if (remember.count() > 0) remember.check();
                saveDebugScreenshot("before_otp_submit");
                
                String postOtpUrl = page.url();
                page.locator("input#save, input#verify, input[type='submit'], button:has-text('Verify')").first().click();
                
                log("Waiting for post-OTP redirect...");
                try {
                    page.waitForURL(url -> !url.equals(postOtpUrl), 
                        new Page.WaitForURLOptions().setTimeout(30000));
                    page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(20000));
                } catch (Exception e) {
                    log("URL did not change after OTP submission. Current: " + page.url());
                }
                saveDebugScreenshot("after_otp_submit");
            }
        }

        handleIntermediatePages();

        log("Waiting for Lightning URL...");
        try {
            page.waitForURL("**/lightning/**", new Page.WaitForURLOptions().setTimeout(90000));
        } catch (Exception e) {
            log("Timed out waiting for Lightning URL. Current URL: " + page.url());
            saveDebugScreenshot("lightning_timeout");
            throw e;
        }
        log("Login successful: " + page.url().split("\\?")[0]);

        // Cache the authenticated session
        try {
            context.storageState(new BrowserContext.StorageStateOptions()
                .setPath(Paths.get(ConfigManager.getSessionFile())));
            log("Session cached to " + ConfigManager.getSessionFile());
        } catch (Exception e) {
            log("Warning: could not cache session: " + e.getMessage());
        }
    }

    /** Handles common Salesforce splash pages that block navigation to Lightning. */
    private void handleIntermediatePages() {
        String url = page.url();
        log("Checking for intermediate pages at: " + url);
        
        // "Register Your Mobile Phone" / "Verify Your Identity" (with 'Not Now' option)
        Locator notNow = page.locator("a:has-text('Not Now'), a:has-text('Remind Me Later')");
        if (notNow.count() > 0 && notNow.first().isVisible()) {
            log("Clicking 'Not Now' / 'Remind Me Later'...");
            notNow.first().click();
            try { page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(20000)); } catch (Exception ignored) {}
            saveDebugScreenshot("after_not_now");
        }

        // Lightning Experience transition splash
        if (page.locator(".slds-button:has-text('Switch to Lightning Experience')").count() > 0) {
            log("Clicking 'Switch to Lightning Experience'...");
            page.locator(".slds-button:has-text('Switch to Lightning Experience')").click();
            try { page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(20000)); } catch (Exception ignored) {}
            saveDebugScreenshot("after_switch_to_lightning");
        }
    }

    private void log(String message) {
        String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        System.out.println("[BaseTest] [" + timestamp + "] " + message);
    }

    private void saveDebugScreenshot(String name) {
        try {
            new java.io.File("screenshots/debug").mkdirs();
            String path = "screenshots/debug/" + name + "_" + System.currentTimeMillis() + ".png";
            page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(path)));
            log("Debug screenshot saved: " + path);
        } catch (Exception e) {
            log("Failed to save debug screenshot: " + e.getMessage());
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
        log("Polling testmail.app for OTP code...");
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
                    log("OTP code found: " + found);
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
