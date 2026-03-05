# Salesforce Test Framework — Project Instructions

## Quick Commands

```bash
# Validate session
node agents/check-session.js

# Run full test suite + generate Allure report
mvn verify -f pom.xml

# Run tests only (no report)
mvn test -f pom.xml

# Parse last test results
node agents/parse-results.js

# Renew session (run manually in browser)
node login_save_session.js

# Run single test class
mvn test -Dtest=AccountTest -f pom.xml

# Run full suite + auto-generate Allure report (recommended)
# Report written to: target/site/allure-maven-plugin/
mvn verify

# Open the generated report
allure open target/site/allure-maven-plugin

# Regenerate and serve in browser
allure serve allure-results
```

## Architecture Rules

- **Pattern:** Page Object Model (POM)
- **BasePage:** All page objects extend `BasePage` — never instantiate directly
- **BaseTest:** `@BeforeClass` creates Playwright + Browser; `@BeforeMethod` creates new `BrowserContext + Page` per test for isolation
- **ScreenshotListener:** Auto-captures screenshot on failure to `screenshots/`
- **ConfigManager:** All credentials via `ConfigManager.getUsername()` / `ConfigManager.getPassword()` — never hardcode
- **Navigation:** Prefer direct Lightning URLs over clicking nav items (more reliable)
- **Locators:** Use `getByLabel()`, `getByRole()`, `getByText()` — these pierce Salesforce Shadow DOM

## Key File Locations

| File | Purpose |
|---|---|
| `src/test/resources/config.properties` | Credentials + org URL (gitignored) |
| `session.json` | Playwright saved session (gitignored) |
| `selectors.json` | Latest explored selectors from live UI |
| `agents/check-session.js` | Session age validator |
| `agents/parse-results.js` | Surefire XML → JSON parser |
| `explore2.js` | Live UI selector explorer (Node.js + Playwright) |
| `testng.xml` | TestNG suite definition |
| `target/surefire-reports/junitreports/` | Per-class XML reports |
| `screenshots/` | Failure screenshots (auto-generated) |

## Known Failure Categories

| Category | Signals | Fix |
|---|---|---|
| `SESSION_EXPIRED` | Redirect to `/login` in error or screenshot | Renew session, rerun |
| `SELECTOR_BROKEN` | `TimeoutError` with selector string in message | Re-explore, update selector |
| `URL_PATTERN` | `waitForURL` timeout | Correct pattern: `**/r/**/view` |
| `TIMING` | Spinner/render timeout, correct page visible in screenshot | Increase wait or add spinner wait |
| `DUPLICATE_RECORD` | Duplicate dialog in screenshot or error | Add unique suffix to test data |
| `LOGIC_ERROR` | `AssertionError` with wrong expected value | Fix assertion or test data |
| `UNKNOWN` | None of the above | Escalate for manual review |

## Key Salesforce Lightning Selector Notes

- Login: `#username`, `#password`, `#Login`, `#error`
- After login: wait for `**/lightning/**` URL
- Spinners: `.slds-spinner_container` — wait for HIDDEN
- Navigation bar: `.slds-context-bar`
- "New" button in list: `[title="New"]`
- Save button: `getByRole(BUTTON, name="Save")`
- Record heading: `getByRole(HEADING, level=1)`

## Lightning URL Patterns

```
Home:         /lightning/page/home
List view:    /lightning/o/{Object}/list
New record:   /lightning/o/{Object}/new
Record view:  /lightning/r/{Object}/{id}/view
```

---

## Agent Definitions

### `sf-session` — Session Validator
**Role:** Check session.json age and cookie validity before any workflow.
**Tools:** Bash
**Input:** projectRoot
**Output:**
```
SESSION_STATUS: VALID|EXPIRED
AGE: N minutes
ACTION_REQUIRED: run login_save_session.js   ← only on EXPIRED
```

**Prompt template:**
```
You are sf-session. Check the Salesforce session at {projectRoot}.
Run: node agents/check-session.js {projectRoot}
Report SESSION_STATUS: VALID or EXPIRED with age in minutes.
If EXPIRED, output ACTION_REQUIRED: run login_save_session.js and stop.
```

---

### `sf-runner` — Test Executor + Result Parser
**Role:** Run mvn test and parse XML results into structured JSON.
**Tools:** Bash
**Input:** projectRoot, suiteFile (default: testng.xml)
**Output:** Full parse-results.js JSON + `RUN_STATUS: GREEN|RED|ERROR`

**Prompt template:**
```
You are sf-runner. Run the Salesforce test suite at {projectRoot}.
1. Run: mvn test -f {projectRoot}/pom.xml (capture stdout/stderr)
2. If mvn exits non-zero with compile errors, output RUN_STATUS: ERROR and paste the compile error. Stop.
3. Run: node agents/parse-results.js {projectRoot}
4. Output the full JSON and end with RUN_STATUS: GREEN or RED.
```

---

### `sf-analyst` — Failure Classifier
**Role:** Classify test failures into categories and recommend fix actions.
**Tools:** Read, Grep
**Input:** failures JSON array (from sf-runner), screenshotPaths, iteration number
**Output:** One block per failure:
```
FAILURE: {testName}
CATEGORY: SESSION_EXPIRED|SELECTOR_BROKEN|URL_PATTERN|TIMING|DUPLICATE_RECORD|LOGIC_ERROR|UNKNOWN
FIX_ACTION: {what to fix}
FIX_TARGET: {file:method or selector name}
```
Plus optional: `SESSION_ABORT: true` (if SESSION_EXPIRED detected) or `ESCALATE: true` (if UNKNOWN and iteration ≥ 2)

**Prompt template:**
```
You are sf-analyst. Classify these test failures (iteration {N}):

{failures_json}

For each failure, output a block:
FAILURE: {testName}
CATEGORY: one of SESSION_EXPIRED|SELECTOR_BROKEN|URL_PATTERN|TIMING|DUPLICATE_RECORD|LOGIC_ERROR|UNKNOWN
FIX_ACTION: specific action to fix it
FIX_TARGET: file path + method, or selector name

If any failure is SESSION_EXPIRED, also output: SESSION_ABORT: true
If category is UNKNOWN and iteration >= 2, also output: ESCALATE: true

Read screenshots if paths provided. Use Grep to check source files if needed.
```

---

### `sf-explorer` — Live UI Selector Explorer
**Role:** Re-explore live Salesforce UI to capture current selectors.
**Tools:** Bash, Read
**Input:** projectRoot, targetObjects (list of Salesforce objects)
**Output:** Per-object selector recommendations:
```
OBJECT: {ObjectName}
FIELD: {fieldLabel} → {recommended locator}
BUTTON: {buttonLabel} → {recommended locator}
LIST_SELECTOR: {css or role selector}
```

**Prompt template:**
```
You are sf-explorer. Explore the Salesforce UI for: {targetObjects}.
Run: node explore2.js (from {projectRoot}) for each target object.
Read the updated selectors.json after exploration.
Output per-object OBJECT/FIELD/BUTTON/LIST_SELECTOR blocks with recommended Playwright locators.
Prefer getByLabel(), getByRole(), getByText() over raw CSS.
```

---

### `sf-fixer` — Change Proposer (DRY-RUN ONLY)
**Role:** Propose targeted Java edits to fix test failures. NEVER writes files.
**Tools:** Read, Grep
**Input:** fixActions (from sf-analyst), selectorRecommendations (from sf-explorer, optional), dryRun: true
**Output:** One block per proposed change:
```
CHANGE: {file path}
LINE_OR_CONTEXT: {exact old string}
REPLACE_WITH: {exact new string}
REASON: {why this fixes the failure category}
```
Followed by: `PROPOSAL_SUMMARY: N changes in M files`

**Critical rule: sf-fixer MUST NOT call the Edit tool. Output proposals only.**

**Prompt template:**
```
You are sf-fixer operating in DRY-RUN mode. NEVER use the Edit tool.
Fix actions to address:
{fix_actions}

Selector recommendations (if provided):
{selector_recs}

Read the relevant source files using Read and Grep tools.
For each change needed, output:
CHANGE: {file path}
LINE_OR_CONTEXT: {the exact old string that must be replaced}
REPLACE_WITH: {the exact new string}
REASON: {why this fixes the failure}

End with: PROPOSAL_SUMMARY: N changes in M files
Do NOT call Edit, Write, or any file-modification tool.
```

---

### `sf-security` — Security Gate
**Role:** Review proposed changes for security issues before any file is written.
**Tools:** Read
**Input:** Proposed changes (from sf-fixer) or proposed file content (from sf-generator)
**Output:**
```
FINDING: HIGH|MEDIUM|LOW | {file} | {method/line} | {issue} | {recommendation}
...
GATE_DECISION: APPROVE | APPROVE_WITH_WARNINGS | REJECT
```

**Gate rules:**
- `APPROVE` — no findings, or LOW findings only
- `APPROVE_WITH_WARNINGS` — MEDIUM findings; changes can proceed but warnings are logged
- `REJECT` — any HIGH finding; orchestrator MUST NOT write any file; includes exact description of what must be fixed

**Checks performed:**
1. `HARDCODED_CREDENTIALS` — passwords/tokens/keys in source, not from ConfigManager
2. `CREDENTIAL_LOGGING` — `System.out.println` / log statements outputting passwords or session tokens
3. `URL_INJECTION` — `page.navigate()` with unvalidated string concatenation
4. `PATH_TRAVERSAL` — file path construction with unvalidated input
5. `SECRET_IN_TEST_DATA` — test strings matching patterns: password, secret, key, token
6. `EVAL_INJECTION` — `page.evaluate()` with external/unvalidated data
7. `CONFIG_BYPASS` — credentials hardcoded instead of using `ConfigManager.getUsername()` etc.

**Prompt template:**
```
You are sf-security. Review the following proposed changes for security issues.
Do NOT apply any changes — read source files for context only using the Read tool.

Proposed changes:
{proposed_changes}

Check for each of these issues:
1. HARDCODED_CREDENTIALS — passwords/tokens/keys in source (not from ConfigManager)
2. CREDENTIAL_LOGGING — System.out.println or log of passwords/tokens
3. URL_INJECTION — page.navigate() with unvalidated string concatenation
4. PATH_TRAVERSAL — file paths built with unvalidated input
5. SECRET_IN_TEST_DATA — test strings matching: password, secret, key, token
6. EVAL_INJECTION — page.evaluate() with external/unvalidated data
7. CONFIG_BYPASS — credentials hardcoded instead of ConfigManager.getUsername() etc.

For each issue found, output:
FINDING: HIGH|MEDIUM|LOW | {file} | {method or line context} | {issue description} | {recommendation}

Then output one of:
GATE_DECISION: APPROVE           ← no findings or LOW only
GATE_DECISION: APPROVE_WITH_WARNINGS  ← MEDIUM findings only
GATE_DECISION: REJECT            ← any HIGH finding; describe exactly what must be fixed
```

---

### `sf-generator` — New Test Generator (TEXT OUTPUT ONLY)
**Role:** Generate new test files and page object methods. NEVER writes files.
**Tools:** Read
**Input:** featureDescription, objectName, selectorData (from sf-explorer)
**Output:**
- Complete new test file content as text
- List of new page object methods to add
- testng.xml `<class>` entry to add

**Critical rule: sf-generator MUST NOT call the Write tool. Orchestrator writes after security approval.**

**Prompt template:**
```
You are sf-generator operating in TEXT OUTPUT ONLY mode. NEVER use the Write tool.
Generate a new test for: {featureDescription}
Salesforce object: {objectName}
Selector data: {selectorData}

Read the existing test files and page objects using Read tool for style consistency.

Output:
1. Complete content of new test file: src/test/java/com/salesforce/tests/{ObjectName}Test.java
2. New methods to add to: src/main/java/com/salesforce/framework/pages/{ObjectName}Page.java
3. testng.xml <class> entry: <class name="com.salesforce.tests.{ObjectName}Test"/>

Follow existing patterns: extend BaseTest, use Page Object methods, no hardcoded credentials.
Do NOT call Write, Edit, or any file-modification tool.
```

---

## Workflows

### Workflow 1: `run-and-fix`

Run the full suite and attempt automated fixes for up to 3 iterations.

```
1. sf-session → [EXPIRED: stop, tell user to run login_save_session.js]

2. sf-runner  → [GREEN: done, "All tests pass"]
               [ERROR: stop, show compile error to user]

3. LOOP (max 3 iterations):
   a. sf-analyst(failures, iteration=N)
      → [SESSION_ABORT: stop immediately]
      → [ESCALATE: stop, show unresolved failures]
      → [SELECTOR_BROKEN in any failure: sf-explorer(affected objects)]

   b. sf-fixer(fix_actions, selector_recs if available, dryRun=true)
      → outputs proposed changes

   c. sf-security(proposed changes)
      → [REJECT: abort iteration, show security issue to user, stop]
      → [APPROVE / APPROVE_WITH_WARNINGS: orchestrator applies via Edit tool]

   d. sf-runner(testng.xml)
      → [GREEN: done, "Fixed in N iterations"]
      → [RED + iter < 3: loop]
      → [RED + iter = 3: stop, show remaining failures]
```

**Spawn prompt:**
```
Run workflow run-and-fix for the Salesforce test framework at C:/testingClaude/SalesforceTestFramework.
Follow the run-and-fix workflow defined in CLAUDE.md exactly:
1. Spawn sf-session agent to check session validity.
2. Spawn sf-runner agent to execute mvn test and parse results.
3. If RED, spawn sf-analyst → sf-explorer (if needed) → sf-fixer → sf-security → apply approved changes → loop.
Stop at GREEN, ESCALATE, SESSION_ABORT, security REJECT, or 3 iterations.
Report final status with iteration count and any remaining failures.
```

---

### Workflow 2: `add-test`

Add a new test for a Salesforce feature.

```
1. sf-session → [EXPIRED: stop]

2. sf-explorer(objectName)

3. sf-generator(featureDescription, objectName, selectors)
   → outputs proposed test file + page object additions

4. sf-security(proposed file content)
   → [REJECT: sf-generator retry with security feedback, max 1 retry]
   → [APPROVE / APPROVE_WITH_WARNINGS: orchestrator writes new test file, edits page object, edits testng.xml]

5. sf-runner(testng.xml)
   → [GREEN: done, "New test added and passing"]
   → [RED, only new test failing: sf-analyst + sf-fixer + sf-security + apply, 1 iteration]
   → [RED, pre-existing failures: report separately, ask user about run-and-fix]
```

**Spawn prompt:**
```
Run workflow add-test for the Salesforce test framework at C:/testingClaude/SalesforceTestFramework.
Feature to test: {featureDescription}
Salesforce object: {objectName}
Follow the add-test workflow defined in CLAUDE.md:
1. sf-session, 2. sf-explorer, 3. sf-generator, 4. sf-security gate, 5. write files, 6. sf-runner.
```

---

### Workflow 3: `refresh-selectors`

Refresh all selectors from the live Salesforce UI.

```
1. sf-session → [EXPIRED: stop]

2. sf-explorer(Account, Contact, Lead, Opportunity)

3. Orchestrator compares selectors.json to current page objects (read files directly)
   → [no changes needed: done, "All selectors valid"]
   → [changes found:]
      a. sf-fixer(selector updates, dryRun=true)
      b. sf-security(proposed changes)
         → [REJECT: stop, show security issue to user]
         → [APPROVE: orchestrator applies via Edit]
      c. sf-runner(testng.xml)
         → [GREEN: done]
         → [RED: sf-analyst + sf-fixer + sf-security, 1 iteration]
```

**Spawn prompt:**
```
Run workflow refresh-selectors for the Salesforce test framework at C:/testingClaude/SalesforceTestFramework.
Follow the refresh-selectors workflow defined in CLAUDE.md:
1. sf-session, 2. sf-explorer all objects, 3. compare to page objects, 4. sf-fixer + sf-security + apply if changes, 5. sf-runner.
```

---

## Security Policy

- **sf-security MUST run before any file is written** in all three workflows.
- `GATE_DECISION: REJECT` is a hard stop — orchestrator MUST NOT write any file.
- `GATE_DECISION: APPROVE_WITH_WARNINGS` — log warnings but proceed.
- All credentials must flow through `ConfigManager` — never hardcoded.
- `config.properties` and `session.json` are gitignored — never commit them.
