package com.salesforce.framework.pages;

import com.microsoft.playwright.Page;
import com.salesforce.framework.config.ConfigManager;

public class ContactPage extends BasePage {

    private final String baseUrl = ConfigManager.getBaseUrl();

    public ContactPage(Page page) {
        super(page);
    }

    public void navigateToList() {
        page.navigate(baseUrl + "/lightning/o/Contact/list");
        waitForSpinner();
    }

    public void clickNew() {
        page.locator("button[title='New'], a[title='New']").first().click();
        page.waitForSelector("input[name='lastName']");
    }

    public void fillFirstName(String firstName) {
        page.fill("input[name='firstName']", firstName);
    }

    public void fillLastName(String lastName) {
        page.fill("input[name='lastName']", lastName);
    }

    public void fillEmail(String email) {
        page.fill("input[name='Email']", email);
    }

    public void fillPhone(String phone) {
        page.fill("input[name='Phone']", phone);
    }

    public void fillTitle(String title) {
        page.fill("input[name='Title']", title);
    }

    public void fillDepartment(String department) {
        page.fill("input[name='Department']", department);
    }

    public void setSalutation(String salutation) {
        selectPicklist("Salutation", salutation);
    }

    public void save() {
        clickSave();
    }

    public void searchInList(String query) {
        page.fill("input[name='Contact-search-input']", query);
        page.keyboard().press("Enter");
        waitForSpinner();
    }

    public boolean isListPageLoaded() {
        return page.url().contains("/o/Contact/");
    }

    public boolean isRecordVisible(String name) {
        return page.locator("a").filter(
            new com.microsoft.playwright.Locator.FilterOptions().setHasText(name)
        ).count() > 0;
    }
}
