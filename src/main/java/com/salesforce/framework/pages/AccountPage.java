package com.salesforce.framework.pages;

import com.microsoft.playwright.Page;
import com.salesforce.framework.config.ConfigManager;

public class AccountPage extends BasePage {

    private final String baseUrl = ConfigManager.getBaseUrl();

    public AccountPage(Page page) {
        super(page);
    }

    public void navigateToList() {
        page.navigate(baseUrl + "/lightning/o/Account/list");
        waitForSpinner();
    }

    public void clickNew() {
        page.locator("button[title='New'], a[title='New']").first().click();
        page.waitForSelector("input[name='Name']");
    }

    public void fillName(String name) {
        page.fill("input[name='Name']", name);
    }

    public void fillPhone(String phone) {
        page.fill("input[name='Phone']", phone);
    }

    public void fillWebsite(String website) {
        page.fill("input[name='Website']", website);
    }

    public void setRating(String rating) {
        selectPicklist("Rating", rating);
    }

    public void setType(String type) {
        selectPicklist("Type", type);
    }

    public void save() {
        clickSave();
    }

    public void searchInList(String query) {
        page.fill("input[name='Account-search-input']", query);
        page.keyboard().press("Enter");
        waitForSpinner();
    }

    public boolean isListPageLoaded() {
        return page.url().contains("/o/Account/");
    }

    public boolean isRecordVisible(String name) {
        return page.locator("a").filter(
            new com.microsoft.playwright.Locator.FilterOptions().setHasText(name)
        ).count() > 0;
    }
}
