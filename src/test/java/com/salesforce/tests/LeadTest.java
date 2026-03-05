package com.salesforce.tests;

import com.salesforce.framework.pages.LeadPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Epic("CRM Objects")
@Feature("Leads")
public class LeadTest extends BaseTest {

    private static final LocalDateTime NOW       = LocalDateTime.now();
    private static final String        DT        = NOW.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    private static final String        DTC       = NOW.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    private static final String        LAST_NAME = "AutoLead " + DT;
    private static final String        COMPANY   = "SfTest Corp " + DT;
    private static final String        EMAIL     = "lead" + DTC + "@sfpwtest.io";
    private static final String        PHONE     = "700-" + DTC.substring(8, 11) + "-" + DTC.substring(10, 14);

    @Test
    @Story("Leads list view")
    @Description("Verify that the Leads list view loads and is accessible.")
    @Severity(SeverityLevel.CRITICAL)
    public void testLeadsListLoads() {
        LeadPage leadPage = new LeadPage(page);
        leadPage.navigateToList();
        Assert.assertTrue(leadPage.isListPageLoaded(), "Leads list page should load");
    }

    @Test
    @Story("Create lead")
    @Description("Create a new Lead with name, company, email, phone, and status. Verify the record detail page shows the correct name.")
    @Severity(SeverityLevel.BLOCKER)
    public void testCreateLead() {
        LeadPage leadPage = new LeadPage(page);
        leadPage.navigateToList();
        leadPage.clickNew();
        leadPage.fillFirstName("Bot " + DT);
        leadPage.fillLastName(LAST_NAME);
        leadPage.fillCompany(COMPANY);
        leadPage.fillEmail(EMAIL);
        leadPage.fillPhone(PHONE);
        // Lead Status is required — "Open - Not Contacted" is the default in this org
        // but we explicitly set it to be safe
        leadPage.setLeadStatus("Open - Not Contacted");
        leadPage.save();

        page.waitForURL("**/r/**/view");
        
        // Retry record heading check up to 3 times as Salesforce navigation is flaky
        String heading = "";
        for (int i = 0; i < 3; i++) {
            heading = leadPage.getRecordHeading();
            if (heading.contains(LAST_NAME)) break;
            page.waitForTimeout(3000);
        }
        
        Assert.assertTrue(heading.contains(LAST_NAME),
            "Record heading should contain: " + LAST_NAME + ", got: " + heading);
    }

    @Test(dependsOnMethods = "testCreateLead")
    @Story("Search lead")
    @Description("Search for the previously created Lead in the list view and verify it appears in results.")
    @Severity(SeverityLevel.NORMAL)
    public void testSearchLead() {
        LeadPage leadPage = new LeadPage(page);
        leadPage.navigateToList();
        leadPage.searchInList(LAST_NAME);
        Assert.assertTrue(leadPage.isRecordVisible(LAST_NAME),
            "Created lead should appear in search results");
    }
}
