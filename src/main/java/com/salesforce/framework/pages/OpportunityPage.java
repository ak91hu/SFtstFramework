package com.salesforce.framework.pages;

import com.microsoft.playwright.Page;
import com.salesforce.framework.config.ConfigManager;

public class OpportunityPage extends BasePage {

    private final String baseUrl = ConfigManager.getBaseUrl();

    public OpportunityPage(Page page) {
        super(page);
    }

    public void navigateToList() {
        page.navigate(baseUrl + "/lightning/o/Opportunity/list");
        waitForSpinner();
    }

    public void clickNew() {
        page.locator("button[title='New'], a[title='New']").first().click();
        page.waitForSelector("input[name='Name']");
    }

    public void fillName(String name) {
        page.fill("input[name='Name']", name);
    }

    public void fillCloseDate(String date) {
        // Expected format: MM/DD/YYYY  — use Tab to commit, not Escape (Escape closes the modal)
        page.fill("input[name='CloseDate']", date);
        page.keyboard().press("Tab");
    }

    public void fillAmount(String amount) {
        page.fill("input[name='Amount']", amount);
    }

    public void fillNextStep(String nextStep) {
        page.fill("input[name='NextStep']", nextStep);
    }

    public void setStage(String stage) {
        selectPicklist("Stage", stage);
    }

    public void setType(String type) {
        selectPicklist("Type", type);
    }

    public void setLeadSource(String source) {
        selectPicklist("Lead Source", source);
    }

    public void save() {
        clickSave();
    }

    public void searchInList(String query) {
        page.fill("input[name='Opportunity-search-input']", query);
        page.keyboard().press("Enter");
        waitForSpinner();
    }

    public boolean isListPageLoaded() {
        return page.url().contains("/o/Opportunity/");
    }

    public boolean isRecordVisible(String name) {
        return page.locator("a").filter(
            new com.microsoft.playwright.Locator.FilterOptions().setHasText(name)
        ).count() > 0;
    }
}
