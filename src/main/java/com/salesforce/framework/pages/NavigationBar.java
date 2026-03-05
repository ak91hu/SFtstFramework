package com.salesforce.framework.pages;

import com.microsoft.playwright.Page;
import com.salesforce.framework.config.ConfigManager;

public class NavigationBar extends BasePage {

    private final String baseUrl = ConfigManager.getBaseUrl();

    public NavigationBar(Page page) {
        super(page);
    }

    public boolean isVisible() {
        return page.locator(".slds-context-bar").isVisible();
    }

    public void goToHome() {
        page.locator(".slds-context-bar a[href='/lightning/page/home']").click();
        waitForSpinner();
    }

    public void goToAccounts() {
        page.navigate(baseUrl + "/lightning/o/Account/list");
        waitForSpinner();
    }

    public void goToContacts() {
        page.navigate(baseUrl + "/lightning/o/Contact/list");
        waitForSpinner();
    }

    public void goToLeads() {
        page.navigate(baseUrl + "/lightning/o/Lead/list");
        waitForSpinner();
    }

    public void goToOpportunities() {
        page.navigate(baseUrl + "/lightning/o/Opportunity/list");
        waitForSpinner();
    }
}
