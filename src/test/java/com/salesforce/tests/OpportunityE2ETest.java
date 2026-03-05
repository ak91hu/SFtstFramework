package com.salesforce.tests;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.salesforce.framework.config.ConfigManager;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * E2E test: creates an Opportunity at Prospecting stage and advances it
 * through every stage until Closed Won, verifying each transition.
 *
 * Retry + wait strategy:
 *  - retryAction()  : wraps flaky clicks/assertions, retries up to MAX_RETRIES
 *  - waitForPageReady() : waits for DOM ready + spinner gone + render buffer
 *  - All navigation timeouts are 60 s (Salesforce can be slow)
 */
@Epic("E2E Workflows")
@Feature("Opportunity Lifecycle")
public class OpportunityE2ETest extends BaseTest {

    // ── Tuning ────────────────────────────────────────────────────────────────
    private static final int    MAX_RETRIES      = 3;
    private static final double RETRY_DELAY_MS   = 2000;
    private static final double ELEMENT_TIMEOUT  = 45000;
    private static final double NAV_TIMEOUT      = 60000;
    private static final double SPINNER_TIMEOUT  = 25000;
    private static final double RENDER_BUFFER_MS = 1500;

    // Standard Salesforce Developer Edition opportunity stages in order
    private static final String[] STAGE_SEQUENCE = {
        "Prospecting",
        "Qualification",
        "Needs Analysis",
        "Value Proposition",
        "Id. Decision Makers",
        "Perception Analysis",
        "Proposal/Price Quote",
        "Negotiation/Review",
        "Closed Won"
    };

    // ── Test ──────────────────────────────────────────────────────────────────

    @Test
    @Story("Closed Won lifecycle")
    @Description("Creates an Opportunity at Prospecting stage and advances it through all 9 standard pipeline stages, verifying each transition, until Closed Won.")
    @Severity(SeverityLevel.BLOCKER)
    public void testOpportunityClosedWonLifecycle() {
        String oppName = "E2E Deal " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        log("═══ START: Opportunity E2E lifecycle ═══");

        // ── STEP 1: Create Opportunity ────────────────────────────────────────
        log("Step 1 — Creating opportunity: " + oppName);
        createOpportunity(oppName);

        // ── STEP 2: Verify initial stage ──────────────────────────────────────
        log("Step 2 — Verifying initial stage");
        String initial = readCurrentStage();
        log("  Initial stage: " + initial);
        Assert.assertEquals(initial, "Prospecting",
            "Opportunity should start at Prospecting");

        // ── STEP 3: Walk through every stage until Closed Won ─────────────────
        for (int i = 1; i < STAGE_SEQUENCE.length; i++) {
            String target = STAGE_SEQUENCE[i];
            log("Step 3." + i + " — Advancing to: " + target);

            advanceToStage(target);

            String actual = readCurrentStage();
            log("  Current stage after advance: " + actual);

            // "Closed Won" may appear as "Closed" in truncated path labels
            boolean matched = actual.equals(target)
                || (target.equals("Closed Won") && actual.contains("Closed"));
            Assert.assertTrue(matched,
                "Expected stage '" + target + "', got: '" + actual + "'");
        }

        log("═══ END: Opportunity successfully progressed to Closed Won! ═══");
    }

    // ── Record creation ───────────────────────────────────────────────────────

    private void createOpportunity(String oppName) {
        navigateTo(ConfigManager.getBaseUrl() + "/lightning/o/Opportunity/new");
        waitForPageReady();

        retryAction("fill Name", () -> {
            page.waitForSelector("input[name='Name']",
                new Page.WaitForSelectorOptions().setTimeout(ELEMENT_TIMEOUT));
            page.fill("input[name='Name']", oppName);
        });

        retryAction("fill CloseDate", () -> {
            page.fill("input[name='CloseDate']", "12/31/2025");
            page.keyboard().press("Tab");   // commit date without closing modal
        });

        retryAction("select Stage = Prospecting", () -> {
            // Open the Stage combobox
            page.locator("lightning-combobox, div.slds-form-element")
                .filter(new Locator.FilterOptions().setHasText("Stage"))
                .first()
                .locator("button")
                .first()
                .click();
            // :visible excludes hidden cached options from previously opened dropdowns
            page.waitForSelector("[role='option']:visible",
                new Page.WaitForSelectorOptions().setTimeout(8000));
            page.locator("[role='option']:visible")
                .filter(new Locator.FilterOptions().setHasText("Prospecting"))
                .first()
                .click();
        });

        retryAction("fill Amount", () ->
            page.fill("input[name='Amount']", "100000"));

        retryAction("click Save", () ->
            page.locator("xpath=//button[normalize-space()='Save']").click());

        // Wait for the record detail page URL (/r/Opportunity/{id}/view)
        log("  Waiting for record detail page...");
        retryAction("waitForURL /r/**/view", () ->
            page.waitForURL("**/r/**/view",
                new Page.WaitForURLOptions().setTimeout(NAV_TIMEOUT)));

        waitForPageReady();
        log("  Created. URL: " + page.url());
    }

    // ── Stage navigation ──────────────────────────────────────────────────────

    /**
     * Advances the opportunity to the target stage by:
     * 1. Clicking the stage name in the path bar
     * 2. Clicking "Mark Stage as Complete" to save
     */
    private void advanceToStage(String targetStage) {
        retryAction("click stage '" + targetStage + "' in path", () -> {
            dismissGuidancePanel();
            // Try exact title match first
            Locator stageLink = page.locator("a.slds-path__link[title='" + targetStage + "']");
            if (stageLink.count() == 0) {
                // Truncated labels (e.g. "Needs Analy...") and "Closed Won"/"Closed Lost"
                // which render as just "Closed" in the path bar — try first word of stage name
                String prefix = targetStage.contains(" ")
                    ? targetStage.split(" ")[0]
                    : targetStage.substring(0, Math.min(10, targetStage.length()));
                stageLink = page.locator("a.slds-path__link")
                    .filter(new Locator.FilterOptions().setHasText(prefix));
            }
            stageLink.first().waitFor(
                new Locator.WaitForOptions().setTimeout(ELEMENT_TIMEOUT));
            stageLink.first().click();
            waitForRender(); // let the path selection visually update
        });

        retryAction("click 'Mark Stage as Complete'", () -> {
            dismissGuidancePanel();

            // Detect if "Close This Opportunity" modal is already open (opened
            // automatically when the Closed path step is clicked, or by a previous retry).
            boolean modalOpen = false;
            Locator modalEl = page.locator(".slds-modal__container");
            if (modalEl.count() > 0) {
                modalOpen = modalEl.first().isVisible();
            }

            if (!modalOpen) {
                // "Closed Won"/"Closed Lost" stages show a "Select Closed Stage" button.
                Locator selectClosedBtn = page.locator("button")
                    .filter(new Locator.FilterOptions().setHasText("Select Closed Stage"));
                try {
                    selectClosedBtn.first().waitFor(
                        new Locator.WaitForOptions().setTimeout(6000));
                    selectClosedBtn.first().click();
                    waitForRender();
                    modalOpen = true;
                } catch (Exception ignored) {}
            }

            if (modalOpen) {
                // "Close This Opportunity" is an Aura modal — Stage field is a native <select>.
                Locator stageSelect = page.locator(".slds-modal__container select");
                if (stageSelect.count() > 0) {
                    // Native select: match by label text
                    stageSelect.first().selectOption(
                        new com.microsoft.playwright.options.SelectOption().setLabel("Closed Won"));
                } else {
                    // Fallback: Lightning combobox (click trigger, then pick option)
                    page.locator(".slds-modal__container button[aria-haspopup='listbox'], " +
                                 ".slds-modal__container [role='combobox']")
                        .first().click();
                    page.waitForSelector("[role='option']:visible",
                        new Page.WaitForSelectorOptions().setTimeout(8000));
                    page.locator("[role='option']:visible")
                        .filter(new Locator.FilterOptions().setHasText("Closed Won"))
                        .first().click();
                }
                page.locator(".slds-modal__container")
                    .locator("button")
                    .filter(new Locator.FilterOptions().setHasText("Save"))
                    .first().click();
                return;
            }

            // Standard flow: "Mark Stage as Complete" button
            Locator btn = page.locator("button[title='Mark Stage as Complete']");
            if (btn.count() == 0) {
                btn = page.locator("button")
                    .filter(new Locator.FilterOptions()
                        .setHasText("Mark Stage as Complete"));
            }
            if (btn.count() == 0) {
                btn = page.locator("button")
                    .filter(new Locator.FilterOptions()
                        .setHasText("Mark"));
            }
            btn.first().waitFor(
                new Locator.WaitForOptions().setTimeout(ELEMENT_TIMEOUT));
            btn.first().click();
        });

        waitForPageReady();
    }

    // ── Stage reading ─────────────────────────────────────────────────────────

    /**
     * Reads the currently active stage from the path bar.
     * Falls back to the Details tab Stage field if the path isn't found.
     */
    private String readCurrentStage() {
        waitForPageReady();

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                // The active path item has class slds-is-current (and/or slds-is-active)
                Locator current = page.locator(
                    ".slds-path__item.slds-is-current a.slds-path__link, " +
                    ".slds-path__item.slds-is-active a.slds-path__link"
                ).last();
                current.waitFor(new Locator.WaitForOptions().setTimeout(10000));

                String title = current.getAttribute("title");
                if (title != null && !title.isBlank()) return title.trim();

                String text = current.innerText().trim();
                if (!text.isBlank()) return text;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES - 1) {
                    log("  Path selector failed, falling back to Details tab: " + e.getMessage().lines().findFirst().orElse(""));
                }
                waitForRender();
            }
        }

        return readStageFromDetails();
    }

    /** Fallback: read Stage field value from the Details tab. */
    private String readStageFromDetails() {
        try {
            // Click the Details tab if it exists and isn't already active
            Locator detailsTab = page.locator("a[title='Details'], a[data-label='Details']").first();
            if (detailsTab.count() > 0) {
                detailsTab.click();
                waitForPageReady();
            }
            // Stage field in the details section
            Locator stageValue = page.locator(
                "[data-field='StageName'] .slds-form-element__static, " +
                "records-record-layout-item[field-label='Stage'] lightning-formatted-text"
            ).first();
            stageValue.waitFor(new Locator.WaitForOptions().setTimeout(10000));
            return stageValue.innerText().trim();
        } catch (Exception e) {
            log("  Could not read stage from details: " + e.getMessage().lines().findFirst().orElse(""));
            return "UNKNOWN";
        }
    }

    // ── Guidance panel dismissal ──────────────────────────────────────────────

    /**
     * Dismisses the Salesforce Trailhead in-app learning side panel when it
     * intercepts pointer events over action buttons.
     */
    private void dismissGuidancePanel() {
        Locator panel = page.locator("runtime_thp_learning-side-panel");
        if (panel.count() == 0 || !panel.isVisible()) return;
        log("  Dismissing guidance side panel...");
        Locator closeBtn = panel.locator(
            "button[title='Close'], button[aria-label='Close'], button.closeButton");
        if (closeBtn.count() > 0) {
            closeBtn.first().click(new Locator.ClickOptions().setForce(true));
        } else {
            page.keyboard().press("Escape");
        }
        try {
            panel.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(5000));
        } catch (Exception ignored) {}
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void navigateTo(String url) {
        retryAction("navigate to " + url, () ->
            page.navigate(url,
                new Page.NavigateOptions()
                    .setTimeout(NAV_TIMEOUT)
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)));
    }

    private void waitForPageReady() {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        // Wait for Lightning spinner to disappear
        try {
            page.waitForSelector(".slds-spinner_container",
                new Page.WaitForSelectorOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(SPINNER_TIMEOUT));
        } catch (Exception ignored) {}
        waitForRender();
    }

    private void waitForRender() {
        page.waitForTimeout(RENDER_BUFFER_MS);
    }

    /**
     * Retries the given action up to MAX_RETRIES times.
     * Logs each retry with the step name for easy debugging.
     */
    private void retryAction(String stepName, Runnable action) {
        Throwable last = null;
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                action.run();
                return;
            } catch (Exception e) {
                last = e;
                String msg = e.getMessage() == null ? e.getClass().getSimpleName()
                    : e.getMessage().lines().findFirst().orElse(e.getClass().getSimpleName());
                log("  Retry " + (i + 1) + "/" + MAX_RETRIES
                    + " [" + stepName + "]: " + msg);
                page.waitForTimeout(RETRY_DELAY_MS);
            }
        }
        throw new RuntimeException("Step [" + stepName + "] failed after "
            + MAX_RETRIES + " retries", last);
    }

    private void log(String msg) {
        System.out.println("[E2E] " + msg);
    }
}
