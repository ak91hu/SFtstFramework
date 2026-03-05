package com.salesforce.tests;

import com.salesforce.framework.pages.NavigationBar;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

@Epic("Navigation")
@Feature("Navigation Bar")
public class NavigationTest extends BaseTest {

    @Test
    @Story("Navigation bar visibility")
    @Description("Verify that the Salesforce Lightning navigation bar is visible after login.")
    @Severity(SeverityLevel.CRITICAL)
    public void testNavigationBarVisible() {
        NavigationBar nav = new NavigationBar(page);
        Assert.assertTrue(nav.isVisible(), "Navigation bar should be visible after login");
    }

    @Test
    @Story("Navigate to Accounts")
    @Description("Verify that clicking Accounts in the nav bar navigates to the Accounts list view.")
    @Severity(SeverityLevel.NORMAL)
    public void testNavigateToAccounts() {
        NavigationBar nav = new NavigationBar(page);
        nav.goToAccounts();
        Assert.assertTrue(page.url().contains("Account"), "URL should contain Account");
    }

    @Test
    @Story("Navigate to Contacts")
    @Description("Verify that clicking Contacts in the nav bar navigates to the Contacts list view.")
    @Severity(SeverityLevel.NORMAL)
    public void testNavigateToContacts() {
        NavigationBar nav = new NavigationBar(page);
        nav.goToContacts();
        Assert.assertTrue(page.url().contains("Contact"), "URL should contain Contact");
    }

    @Test
    @Story("Navigate to Leads")
    @Description("Verify that clicking Leads in the nav bar navigates to the Leads list view.")
    @Severity(SeverityLevel.NORMAL)
    public void testNavigateToLeads() {
        NavigationBar nav = new NavigationBar(page);
        nav.goToLeads();
        Assert.assertTrue(page.url().contains("Lead"), "URL should contain Lead");
    }

    @Test
    @Story("Navigate to Opportunities")
    @Description("Verify that clicking Opportunities in the nav bar navigates to the Opportunities list view.")
    @Severity(SeverityLevel.NORMAL)
    public void testNavigateToOpportunities() {
        NavigationBar nav = new NavigationBar(page);
        nav.goToOpportunities();
        Assert.assertTrue(page.url().contains("Opportunity"), "URL should contain Opportunity");
    }
}
