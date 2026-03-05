package com.salesforce.tests;

import com.salesforce.framework.pages.ContactPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Epic("CRM Objects")
@Feature("Contacts")
public class ContactTest extends BaseTest {

    private static final LocalDateTime NOW        = LocalDateTime.now();
    private static final String        DT         = NOW.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    private static final String        DTC        = NOW.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    private static final String        LAST_NAME  = "Automation " + DT;
    private static final String        FIRST_NAME = "Bot " + DT;
    private static final String        EMAIL      = "bot" + DTC + "@sfpwtest.io";
    private static final String        PHONE      = "700-" + DTC.substring(8, 11) + "-" + DTC.substring(10, 14);

    @Test
    @Story("Contacts list view")
    @Description("Verify that the Contacts list view loads and is accessible.")
    @Severity(SeverityLevel.CRITICAL)
    public void testContactsListLoads() {
        ContactPage contactPage = new ContactPage(page);
        contactPage.navigateToList();
        Assert.assertTrue(contactPage.isListPageLoaded(), "Contacts list page should load");
    }

    @Test
    @Story("Create contact")
    @Description("Create a new Contact with first name, last name, email, phone, and title. Verify the record detail page shows the correct name.")
    @Severity(SeverityLevel.BLOCKER)
    public void testCreateContact() {
        ContactPage contactPage = new ContactPage(page);
        contactPage.navigateToList();
        contactPage.clickNew();
        contactPage.fillFirstName(FIRST_NAME);
        contactPage.fillLastName(LAST_NAME);
        contactPage.fillEmail(EMAIL);
        contactPage.fillPhone(PHONE);
        contactPage.fillTitle("QA Automation");
        contactPage.save();

        page.waitForURL("**/r/**/view");
        
        // Retry record heading check up to 3 times as Salesforce navigation is flaky
        String heading = "";
        for (int i = 0; i < 3; i++) {
            heading = contactPage.getRecordHeading();
            if (heading.contains(LAST_NAME)) break;
            page.waitForTimeout(3000);
        }
        
        Assert.assertTrue(heading.contains(LAST_NAME),
            "Record heading should contain: " + LAST_NAME + ", got: " + heading);
    }

    @Test(dependsOnMethods = "testCreateContact")
    @Story("Search contact")
    @Description("Search for the previously created Contact in the list view and verify it appears in results.")
    @Severity(SeverityLevel.NORMAL)
    public void testSearchContact() {
        ContactPage contactPage = new ContactPage(page);
        contactPage.navigateToList();
        contactPage.searchInList(LAST_NAME);
        Assert.assertTrue(contactPage.isRecordVisible(LAST_NAME),
            "Created contact should appear in search results");
    }
}
