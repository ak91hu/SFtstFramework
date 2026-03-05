package com.salesforce.framework.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

public abstract class BasePage {

    protected final Page page;
    private static final String SPINNER = ".slds-spinner_container";

    protected BasePage(Page page) {
        this.page = page;
    }

    protected void waitForSpinner() {
        try {
            page.waitForSelector(SPINNER,
                new Page.WaitForSelectorOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(20000));
        } catch (Exception ignored) {
            // spinner may never appear or may have already gone
        }
    }

    protected void clickSave() {
        // XPath exact text match avoids matching "Save & New"
        page.locator("xpath=//button[normalize-space()='Save']").click();
        waitForSpinner();
        // Handle "Similar Records Exist" duplicate warning (org uses "Warn" rule).
        // "View Duplicates" is an <a> link, so detect the popup heading text instead.
        try {
            page.waitForSelector(":has-text('Similar Records Exist')",
                new Page.WaitForSelectorOptions().setTimeout(3000));
            page.locator("xpath=//button[normalize-space()='Save']").click();
            waitForSpinner();
        } catch (Exception ignored) {}
    }

    protected void clickCancelForm() {
        page.locator("button[name='CancelEdit']").click();
    }

    // Select a picklist value by the field's label text and the desired option text.
    protected void selectPicklist(String fieldLabel, String optionValue) {
        // Find the lightning-combobox (or fallback div) that contains the label text,
        // open it by clicking its trigger button, then click the matching option.
        page.locator("lightning-combobox, div.slds-form-element")
            .filter(new Locator.FilterOptions().setHasText(fieldLabel))
            .first()
            .locator("button")
            .first()
            .click();
        // :visible excludes hidden option elements from closed/cached dropdowns.
        // This avoids picking up invisible option elements from previously opened dropdowns.
        page.waitForSelector("[role='option']:visible",
            new Page.WaitForSelectorOptions().setTimeout(5000));
        page.locator("[role='option']:visible")
            .filter(new Locator.FilterOptions().setHasText(optionValue))
            .first()
            .click();
    }

    // Returns the record name from the Salesforce record detail page.
    // Salesforce sets the browser tab title to "{RecordName} | {Object} | Salesforce".
    // Waits for the title to reflect the record rather than a loading/transition state.
    public String getRecordHeading() {
        waitForSpinner();
        // Wait until the tab title is no longer a generic loading/nav title
        try {
            page.waitForFunction(
                "() => { const t = document.title; return t.includes(' | ') && " +
                "!t.startsWith('Lightning') && !t.startsWith('New ') && !t.startsWith('Home'); }",
                null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignored) {}
        String title = page.title();
        int barIdx = title.indexOf(" | ");
        if (barIdx > 0) {
            return title.substring(0, barIdx).trim();
        }
        // Fallback: first visible h1 on the page
        Locator allH1 = page.locator("h1");
        int count = allH1.count();
        for (int i = 0; i < count; i++) {
            Locator h = allH1.nth(i);
            if (h.isVisible()) {
                return h.innerText().trim();
            }
        }
        allH1.first().waitFor();
        return allH1.first().innerText().trim();
    }
}
