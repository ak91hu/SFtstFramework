package com.salesforce.tests;

import com.salesforce.framework.pages.OpportunityPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Epic("CRM Objects")
@Feature("Opportunities")
public class OpportunityTest extends BaseTest {

    private static final String OPP_NAME   = "PwDeal " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    private static final String CLOSE_DATE = "12/31/2025";
    private static final String STAGE      = "Prospecting";

    @Test
    @Story("Opportunities list view")
    @Description("Verify that the Opportunities list view loads and is accessible.")
    @Severity(SeverityLevel.CRITICAL)
    public void testOpportunitiesListLoads() {
        OpportunityPage oppPage = new OpportunityPage(page);
        oppPage.navigateToList();
        Assert.assertTrue(oppPage.isListPageLoaded(), "Opportunities list page should load");
    }

    @Test
    @Story("Create opportunity")
    @Description("Create a new Opportunity with name, close date, stage, and amount. Verify the record detail page shows the correct name.")
    @Severity(SeverityLevel.BLOCKER)
    public void testCreateOpportunity() {
        OpportunityPage oppPage = new OpportunityPage(page);
        oppPage.navigateToList();
        oppPage.clickNew();
        oppPage.fillName(OPP_NAME);
        oppPage.fillCloseDate(CLOSE_DATE);
        oppPage.setStage(STAGE);
        oppPage.fillAmount("50000");
        oppPage.save();

        page.waitForURL("**/r/**/view");
        
        // Retry record heading check up to 3 times as Salesforce navigation is flaky
        String heading = "";
        for (int i = 0; i < 3; i++) {
            heading = oppPage.getRecordHeading();
            if (heading.contains(OPP_NAME)) break;
            page.waitForTimeout(3000);
        }
        
        Assert.assertTrue(heading.contains(OPP_NAME),
            "Record heading should contain: " + OPP_NAME + ", got: " + heading);
    }

    @Test(dependsOnMethods = "testCreateOpportunity")
    @Story("Search opportunity")
    @Description("Search for the previously created Opportunity in the list view and verify it appears in results.")
    @Severity(SeverityLevel.NORMAL)
    public void testSearchOpportunity() {
        OpportunityPage oppPage = new OpportunityPage(page);
        oppPage.navigateToList();
        oppPage.searchInList(OPP_NAME);
        Assert.assertTrue(oppPage.isRecordVisible(OPP_NAME),
            "Created opportunity should appear in search results");
    }
}
