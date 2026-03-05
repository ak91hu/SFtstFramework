package com.salesforce.tests;

import com.microsoft.playwright.*;
import com.salesforce.framework.config.ConfigManager;
import com.salesforce.framework.pages.LoginPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.*;

// LoginTest does not extend BaseTest — it tests the login flow directly
// with a fresh browser context (no saved session).
@Epic("Authentication")
@Feature("Login")
public class LoginTest {

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    protected Page page;

    @BeforeClass
    public void setup() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
            new BrowserType.LaunchOptions()
                .setHeadless(ConfigManager.isHeadless())
        );
    }

    @BeforeMethod
    public void openPage() {
        context = browser.newContext();
        context.setDefaultTimeout(30000);
        page = context.newPage();
    }

    @AfterMethod
    public void closePage() {
        if (page != null)    page.close();
        if (context != null) context.close();
    }

    @AfterClass
    public void teardown() {
        if (browser != null)    browser.close();
        if (playwright != null) playwright.close();
    }

    @Test
    @Story("Login form elements")
    @Description("Verify that the username field, password field, and Login button are visible on the login page.")
    @Severity(SeverityLevel.NORMAL)
    public void testLoginPageElementsPresent() {
        LoginPage loginPage = new LoginPage(page);
        loginPage.navigate(ConfigManager.getBaseUrl());

        Assert.assertTrue(page.locator("#username").isVisible(), "Username field not visible");
        Assert.assertTrue(page.locator("#password").isVisible(), "Password field not visible");
        Assert.assertTrue(page.locator("#Login").isVisible(), "Login button not visible");
    }

    @Test
    @Story("Invalid credentials")
    @Description("Verify that submitting a wrong password keeps the user on the login page and does not grant access to Lightning.")
    @Severity(SeverityLevel.CRITICAL)
    public void testLoginWithInvalidCredentials() {
        LoginPage loginPage = new LoginPage(page);
        loginPage.navigate(ConfigManager.getBaseUrl());
        loginPage.login(ConfigManager.getUsername(), "WrongPassword123!");

        // After bad credentials Salesforce stays on the login page (no lightning redirect)
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        String url = page.url();
        Assert.assertFalse(url.contains("/lightning/"),
            "Should NOT reach Lightning after invalid credentials, but got: " + url);
        // Also verify the login form is still present (still on login page)
        Assert.assertTrue(loginPage.isLoginPageDisplayed(),
            "Login page should still be displayed after failed login");
    }

    @Test
    @Story("Successful login")
    @Description("Verify that valid credentials redirect the user to Salesforce Lightning (or MFA if enabled).")
    @Severity(SeverityLevel.BLOCKER)
    public void testLoginWithValidCredentials() {
        LoginPage loginPage = new LoginPage(page);
        loginPage.navigate(ConfigManager.getBaseUrl());
        loginPage.login(ConfigManager.getUsername(), ConfigManager.getPassword());

        // Salesforce briefly lands on the root URL before redirecting to Lightning.
        // Wait for that redirect; if it times out, we may be on MFA/verification instead.
        try {
            page.waitForURL("**lightning**",
                new Page.WaitForURLOptions().setTimeout(20000));
        } catch (Exception ignored) {}
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
        String url = page.url();

        boolean reachedLightning = url.contains("/lightning/");
        boolean reachedMfa = url.contains("verification") || url.contains("identity");

        Assert.assertTrue(reachedLightning || reachedMfa,
            "Expected redirect to Lightning or MFA page, but got: " + url);
    }
}
