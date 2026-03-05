package com.salesforce.tests;

import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Paths;

public class ScreenshotListener implements ITestListener {

    @Override
    public void onTestFailure(ITestResult result) {
        Object instance = result.getInstance();
        if (!(instance instanceof BaseTest)) return;

        Page page = ((BaseTest) instance).page;
        if (page == null) return;

        try {
            byte[] bytes = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));

            // Save to screenshots/ for local inspection
            new File("screenshots").mkdirs();
            String fileName = "screenshots/" + result.getName() + "_" + System.currentTimeMillis() + ".png";
            try (FileOutputStream fos = new FileOutputStream(fileName)) {
                fos.write(bytes);
            }
            System.out.println("Screenshot saved: " + fileName);

            // Attach to Allure report
            Allure.addAttachment("Screenshot on failure", "image/png",
                    new ByteArrayInputStream(bytes), ".png");
        } catch (Exception e) {
            System.err.println("Failed to capture screenshot: " + e.getMessage());
        }
    }
}
