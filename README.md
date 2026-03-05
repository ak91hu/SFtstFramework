# Salesforce Test Framework

![CI](https://github.com/YOUR_USERNAME/YOUR_REPO/actions/workflows/ci.yml/badge.svg)

An automated end-to-end test suite for Salesforce Lightning, built with **Java**, **Playwright**, **TestNG**, and **Maven**. Covers login, navigation, and full CRUD workflows for Accounts, Contacts, Leads, and Opportunities — including a multi-stage Opportunity lifecycle E2E test.

---

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Running the Tests](#running-the-tests)
- [Test Coverage](#test-coverage)
- [Architecture](#architecture)
- [Allure Report](#allure-report)
- [CI/CD Pipeline](#cicd-pipeline)
- [Agentic Workflow](#agentic-workflow)
- [Contributing](#contributing)

---

## Features

- **Page Object Model** — clean separation of locators and test logic
- **Session reuse** — Playwright stores authentication state in `session.json` so tests skip the login UI on every run
- **Auto-screenshot on failure** — `ScreenshotListener` captures a timestamped PNG to `screenshots/` for every failing test
- **Environment variable overrides** — all config values can be supplied via `SF_*` env vars for CI/CD pipelines (no secrets in source)
- **Opportunity E2E lifecycle** — drives an opportunity through all nine standard Salesforce pipeline stages to Closed Won, with automatic retry logic and a fallback to the Details tab for stage verification
- **Result parser** — `agents/parse-results.js` turns Surefire XML into structured JSON with matched screenshot paths, ready for automated analysis
- **Allure reporting** — rich HTML reports with test history, categories, severity levels, and failure screenshots embedded directly in the report

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 11+ |
| Browser automation | [Playwright for Java](https://playwright.dev/java/) 1.58 |
| Test runner | [TestNG](https://testng.org/) 7.9 |
| Reporting | [Allure](https://allurereport.org/) 2.33 |
| Build tool | Maven 3.6+ |
| Session exploration | Node.js + Playwright (helper scripts) |

---

## Project Structure

```
SalesforceTestFramework/
├── src/
│   ├── main/java/com/salesforce/framework/
│   │   ├── config/
│   │   │   └── ConfigManager.java        # Reads config.properties; env vars override
│   │   └── pages/
│   │       ├── BasePage.java             # Shared helpers: spinner wait, Save, picklist
│   │       ├── LoginPage.java
│   │       ├── NavigationBar.java
│   │       ├── AccountPage.java
│   │       ├── ContactPage.java
│   │       ├── LeadPage.java
│   │       └── OpportunityPage.java
│   └── test/java/com/salesforce/tests/
│       ├── BaseTest.java                 # Browser lifecycle + session injection
│       ├── ScreenshotListener.java       # Auto-screenshot on failure
│       ├── LoginTest.java
│       ├── NavigationTest.java
│       ├── AccountTest.java
│       ├── ContactTest.java
│       ├── LeadTest.java
│       ├── OpportunityTest.java
│       └── OpportunityE2ETest.java       # Full lifecycle: Prospecting → Closed Won
├── src/test/resources/
│   └── config.properties                 # ← gitignored, see Configuration
├── agents/
│   ├── check-session.js                  # Validates session.json age (8 h limit)
│   └── parse-results.js                  # Surefire XML → structured JSON
├── login_save_session.js                 # Interactive login to generate session.json
├── explore2.js                           # Live UI selector explorer
├── testng.xml                            # Test suite definition
├── pom.xml
└── CLAUDE.md                             # Agentic workflow instructions
```

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 11 or higher |
| Maven | 3.6 or higher |
| Node.js | 18 or higher (for helper scripts only) |

Playwright downloads its own browser binaries automatically on first run — no manual browser installation needed.

---

## Configuration

Copy the provided example file and fill in your org credentials:

```bash
cp src/test/resources/config.properties.example src/test/resources/config.properties
```

`config.properties` is gitignored — never commit it. The example file is safe to commit and shows every key required:

```properties
sf.base.url=https://your-org.develop.my.salesforce.com
sf.username=your@email.com
sf.password=yourPassword

sf.session.file=session.json

browser.headless=true
browser.slowmo=0
browser.timeout=30000
```

### Environment variable overrides

Every property has a corresponding `SF_*` env var that takes precedence over the file. This is the recommended approach for CI/CD:

| Property | Env var |
|---|---|
| `sf.base.url` | `SF_SF_BASE_URL` |
| `sf.username` | `SF_SF_USERNAME` |
| `sf.password` | `SF_SF_PASSWORD` |
| `browser.headless` | `SF_BROWSER_HEADLESS` |

### Session file (optional but recommended)

Running the interactive login script pre-generates `session.json`, which all tests load to skip the login page. Without it, tests fall back to a fresh credential-based login.

```bash
node login_save_session.js
```

---

## Running the Tests

### Full suite + Allure report (recommended)

```bash
mvn verify
```

This runs all tests **and** automatically generates the Allure HTML report into `allure-report/` in one command. Use this for all regular runs.

### Tests only (no report)

```bash
mvn test
```

### Single test class

```bash
mvn test -Dtest=AccountTest
mvn test -Dtest=OpportunityE2ETest
```

### Parse results as JSON

```bash
node agents/parse-results.js
```

Output:

```json
{
  "total": 20,
  "passed": 16,
  "failed": 4,
  "skipped": 0,
  "runStatus": "GREEN",
  "failures": []
}
```

---

## Allure Report

`mvn verify` automatically generates the report. To open it:

```bash
# Open the generated static report with the Allure CLI
allure open target/site/allure-maven-plugin

# Or: regenerate and open in one step
allure serve allure-results
```

The report is structured by **Epic → Feature → Story**:

| Section | What you see |
|---|---|
| Overview | Pass/fail donut, trend chart, suite breakdown |
| Behaviors | Tests grouped by Epic → Feature → Story |
| Suites | Full test tree grouped by class |
| Categories | Failures classified as product bug vs. test defect |
| Timeline | Execution timeline |

Failure screenshots are embedded directly in each failing test — no need to open the `screenshots/` folder separately.

---

## CI/CD Pipeline

The workflow file is at `.github/workflows/ci.yml`. It triggers on every push and pull request to `main`/`master`.

### Required GitHub secrets

| Secret | Description |
|---|---|
| `SF_BASE_URL` | Full org URL, e.g. `https://your-org.develop.my.salesforce.com` |
| `SF_USERNAME` | Salesforce login email |
| `SF_PASSWORD` | Salesforce password |

Add these under **Settings → Secrets and variables → Actions** in your repository.

### What the pipeline does

1. Checks out the code and sets up Java 11 (Temurin) with Maven cache
2. Writes `config.properties` from GitHub secrets at runtime — no credentials in source
3. Installs Playwright's Chromium browser + system dependencies
4. Runs `mvn verify` — executes the full test suite and generates the Allure report
5. Uploads artifacts on every run:
   - `allure-report-{run}` — browsable HTML report (retained 30 days)
   - `allure-results-{run}` — raw JSON results (retained 7 days)
   - `surefire-reports-{run}` — XML reports (retained 7 days)
6. Uploads `screenshots-{run}` only on failure (retained 7 days)

### Viewing the report after a pipeline run

1. Open the Actions tab in GitHub
2. Click the workflow run
3. Download the `allure-report-{run}` artifact
4. Extract and open `index.html`, or serve locally: `allure open allure-report-{run}`

---

## Test Coverage

| Test Class | What it covers |
|---|---|
| `LoginTest` | Valid login, invalid credentials, page element presence |
| `NavigationTest` | Nav bar visibility, direct navigation to each object list |
| `AccountTest` | List loads, create record, search by name |
| `ContactTest` | List loads, create record, search by name |
| `LeadTest` | List loads, create record, search by name |
| `OpportunityTest` | List loads, create record, search by name |
| `OpportunityE2ETest` | Full pipeline: create at Prospecting, advance through 9 stages to Closed Won |

**20 test cases** across 7 test classes.

---

## Architecture

### BaseTest — browser and session lifecycle

```
@BeforeSuite  → Playwright.create() + browser.launch()
@BeforeMethod → newContext(storageState=session.json) + newPage() + ensureLoggedIn()
@AfterMethod  → page.close() + context.close()
@AfterSuite   → browser.close() + playwright.close()
```

Each test method gets its own `BrowserContext`, providing full isolation without paying the cost of launching a new browser process.

### Page Object Model

All page objects extend `BasePage`, which provides:

- `waitForSpinner()` — waits for `.slds-spinner_container` to disappear
- `clickSave()` — exact XPath match on `Save` (avoids "Save & New")
- `selectPicklist(label, value)` — opens a Lightning combobox and picks an option
- `getRecordHeading()` — reads the record title `h1`, skipping the app-nav heading

### ConfigManager

Reads `config.properties` from the classpath. Any property can be overridden at runtime with an environment variable named `SF_` + the uppercased, dot-replaced key:

```java
// sf.username → checks SF_SF_USERNAME first, then config.properties
ConfigManager.getUsername()
```

### ScreenshotListener

Implements TestNG's `ITestListener`. On `onTestFailure`, it captures the current page to `screenshots/{testName}_{timestamp}.png`.

---

## Agentic Workflow

The project includes a CLAUDE.md and two Node.js agent scripts that enable an automated fix loop driven by Claude Code:

| Agent | Role |
|---|---|
| `sf-session` | Validates session.json before any run |
| `sf-runner` | Executes `mvn test` and parses results |
| `sf-analyst` | Classifies failures into categories (selector broken, timing, session expired, etc.) |
| `sf-explorer` | Re-explores live Salesforce UI to refresh selectors |
| `sf-fixer` | Proposes Java edits to fix failures (dry-run, no file writes) |
| `sf-security` | Reviews every proposed change for hardcoded credentials, injection risks, etc. |
| `sf-generator` | Generates new test files and page object methods (text output only) |

**Security gate:** `sf-fixer` and `sf-generator` never write files. All proposals pass through `sf-security` before the orchestrator applies them. A `GATE_DECISION: REJECT` on any `HIGH` finding is a hard stop.

Three built-in workflows: **run-and-fix** (up to 3 fix iterations), **add-test** (generate + security-review + write + verify), and **refresh-selectors** (re-explore + diff + patch).

See [CLAUDE.md](CLAUDE.md) for the full agent definitions, prompt templates, and workflow decision trees.

---

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add tests for any new page objects or flows
4. Ensure `mvn test` passes before opening a pull request
5. Never commit `config.properties` or `session.json`
