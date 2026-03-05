package com.salesforce.framework.pages;

import com.microsoft.playwright.Page;

public class LoginPage extends BasePage {

    public LoginPage(Page page) {
        super(page);
    }

    public void navigate(String baseUrl) {
        page.navigate(baseUrl);
        page.waitForSelector("#username");
    }

    public void login(String username, String password) {
        page.fill("#username", username);
        page.fill("#password", password);
        page.click("#Login");
    }

    public boolean isErrorDisplayed() {
        return page.locator("#error").isVisible();
    }

    public String getErrorText() {
        return page.locator("#error").innerText().trim();
    }

    public boolean isLoginPageDisplayed() {
        return page.locator("#username").isVisible();
    }
}
