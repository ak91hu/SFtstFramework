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
        // Prefer visible button in the modal or at the end of the DOM to avoid background clicks.
        Locator saveBtn = page.locator("button:visible").filter(
            new Locator.FilterOptions().setHasText("Save")).last();
        saveBtn.click();
        waitForSpinner();
        
        // Handle "Similar Records Exist" duplicate warning
        try {
            page.waitForSelector(":has-text('Similar Records Exist')",
                new Page.WaitForSelectorOptions().setTimeout(3000));
            saveBtn.click();
            waitForSpinner();
        } catch (Exception ignored) {}
    }

    protected void clickCancelForm() {
        page.locator("button:visible[name='CancelEdit']").first().click();
    }

    // Select a picklist value by the field's label text and the desired option text.
    protected void selectPicklist(String fieldLabel, String optionValue) {
        // Match a container (combobox or form-element) that contains a label with exact text match.
        Locator container = page.locator("lightning-combobox, div.slds-form-element")
            .filter(new Locator.FilterOptions().setHas(page.locator("label").filter(
                new Locator.FilterOptions().setHasText(fieldLabel)))).first();
        
        container.locator("button").first().click();
        
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
        
        // 1. Wait for tab title to change from generic states
        try {
            page.waitForFunction(
                "() => { const t = document.title; return t.includes(' | ') && " +
                "!t.startsWith('Lightning') && !t.startsWith('New ') && " +
                "!t.startsWith('Home') && !t.startsWith('Developer') && " +
                "!t.startsWith('Loading') && !t.startsWith('Salesforce'); }",
                null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignored) {}

        // 2. Try several specific selectors for the header title
        String[] selectors = {
            "slot[name='primaryField'] .slds-page-header__title",
            "lightning-formatted-text.slds-page-header__title",
            ".slds-page-header__title",
            "h1.slds-page-header__title",
            ".records-record-layout-item[field-label='Name'] .slds-form-element__static"
        };

        for (int i = 0; i < 3; i++) {
            for (String selector : selectors) {
                try {
                    Locator loc = page.locator(selector).first();
                    if (loc.isVisible()) {
                        String text = loc.innerText().trim();
                        if (!text.isEmpty() && !text.equalsIgnoreCase("Lightning Experience") 
                            && !text.equalsIgnoreCase("Developer Edition")) {
                            return text;
                        }
                    }
                } catch (Exception ignored) {}
            }
            
            // Fallback to title parsing if selectors haven't rendered yet
            String title = page.title();
            int barIdx = title.indexOf(" | ");
            if (barIdx > 0) {
                String candidate = title.substring(0, barIdx).trim();
                if (!candidate.equalsIgnoreCase("Lightning Experience") 
                    && !candidate.equalsIgnoreCase("Developer Edition")) {
                    return candidate;
                }
            }
            
            page.waitForTimeout(2000);
        }

        return page.title();
    }
}
