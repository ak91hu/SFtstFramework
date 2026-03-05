package com.salesforce.tests;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.salesforce.framework.config.ConfigManager;
import org.testng.annotations.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

        // If we ended up on the login page, the session expired — do a fresh login
        if (page.url().contains("/login") || page.locator("#username").isVisible()) {
            doLogin();
        }
    }

    private void doLogin() {
        page.fill("#username", ConfigManager.getUsername());
        page.fill("#password", ConfigManager.getPassword());
        page.click("#Login");
        page.waitForURL("**/lightning/**", new Page.WaitForURLOptions().setTimeout(60000));
    }
}
