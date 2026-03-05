package com.salesforce.tests;

import com.salesforce.framework.pages.AccountPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Epic("CRM Objects")
@Feature("Accounts")
public class AccountTest extends BaseTest {

    private static final LocalDateTime NOW          = LocalDateTime.now();
    private static final String        ACCOUNT_NAME = "Test Account " + NOW.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    @Test
    @Story("Accounts list view")
    @Description("Verify that the Accounts list view loads and is accessible.")
    @Severity(SeverityLevel.CRITICAL)
    public void testAccountsListLoads() {
        AccountPage accountPage = new AccountPage(page);
        accountPage.navigateToList();
        Assert.assertTrue(accountPage.isListPageLoaded(), "Accounts list page should load");
    }

    @Test
    @Story("Create account")
    @Description("Create a new Account with name, phone, and website. Verify the record detail page shows the correct name.")
    @Severity(SeverityLevel.BLOCKER)
    public void testCreateAccount() {
        AccountPage accountPage = new AccountPage(page);
        accountPage.navigateToList();
        accountPage.clickNew();
        accountPage.fillName(ACCOUNT_NAME);
        accountPage.fillPhone("555-100-2000");
        accountPage.fillWebsite("https://playwright.dev");
        accountPage.save();

        // After save, Salesforce navigates to the record detail page
        page.waitForURL("**/r/**/view");
        String heading = accountPage.getRecordHeading();
        Assert.assertTrue(heading.contains(ACCOUNT_NAME),
            "Record heading should contain: " + ACCOUNT_NAME + ", got: " + heading);
    }

    @Test(dependsOnMethods = "testCreateAccount")
    @Story("Search account")
    @Description("Search for the previously created Account in the list view and verify it appears in results.")
    @Severity(SeverityLevel.NORMAL)
    public void testSearchAccount() {
        AccountPage accountPage = new AccountPage(page);
        accountPage.navigateToList();
        accountPage.searchInList(ACCOUNT_NAME);
        Assert.assertTrue(accountPage.isRecordVisible(ACCOUNT_NAME),
            "Created account should appear in search results");
    }
}
