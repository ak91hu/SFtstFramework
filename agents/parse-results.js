#!/usr/bin/env node
// parse-results.js — Parses TestNG Surefire XML report into structured JSON
// Reads TEST-TestSuite.xml (comprehensive) with screenshots from <system-out>
// Output: {total, passed, failed, skipped, runStatus, failures:[{testName,className,errorMessage,screenshotPath}]}

const fs = require('fs');
const path = require('path');

const projectRoot = process.argv[2] || path.resolve(__dirname, '..');
const suiteXml = path.join(projectRoot, 'target', 'surefire-reports', 'TEST-TestSuite.xml');
const junitDir = path.join(projectRoot, 'target', 'surefire-reports', 'junitreports');

// Require whitespace or start before attribute name to avoid matching inside e.g. classname= when looking for name=
function parseXmlAttr(xml, attr) {
  const m = xml.match(new RegExp(`(?:^|\\s)${attr}="([^"]*)"`));
  return m ? m[1] : '';
}

function parseCdata(xml) {
  const m = xml.match(/<!\[CDATA\[([\s\S]*?)\]\]>/);
  return m ? m[1].trim() : '';
}

function unescapeXml(str) {
  return str
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#039;/g, "'")
    .replace(/&apos;/g, "'")
    .replace(/&#10;/g, '\n');
}

function extractScreenshotFromSystemOut(body) {
  const m = body.match(/Screenshot saved:\s*([^\n<]+\.png)/);
  return m ? m[1].trim() : null;
}

function findScreenshotByPrefix(testName, screenshotsDir) {
  if (!fs.existsSync(screenshotsDir)) return null;
  const prefix = testName + '_';
  const files = fs.readdirSync(screenshotsDir);
  const match = files.find(f => f.startsWith(prefix) && f.endsWith('.png'));
  return match ? path.join(screenshotsDir, match) : null;
}

function parseXmlFile(xmlPath) {
  const xml = fs.readFileSync(xmlPath, 'utf8');
  const screenshotsDir = path.join(projectRoot, 'screenshots');
  const results = [];

  // Match block testcase elements — negative lookbehind prevents matching self-closing />
  // Self-closing <testcase ... /> is handled by the second alternative
  const testcaseRegex = /<testcase([^>]*?)(?<!\/)>([\s\S]*?)<\/testcase>|<testcase([^>]*?)\/>/g;
  let match;

  while ((match = testcaseRegex.exec(xml)) !== null) {
    const attrs = match[1] || match[3];
    const body = match[2] || '';

    const testName = parseXmlAttr(attrs, 'name');
    const className = parseXmlAttr(attrs, 'classname');

    // Skipped test
    if (/<skipped[\s/>]/.test(body)) {
      results.push({ testName, className, status: 'skipped', errorMessage: null, screenshotPath: null });
      continue;
    }

    // Failure or error element
    const failMatch = body.match(/<(failure|error)([^>]*?)>([\s\S]*?)<\/\1>/);
    if (failMatch) {
      const failAttrs = failMatch[2];
      const failBody = failMatch[3];
      const rawMessage = parseXmlAttr(failAttrs, 'message');
      let message = unescapeXml(rawMessage).split('\n')[0].trim();
      // Playwright wraps some errors as "Error {\n  message='...'" — extract inner message
      if (message === 'Error {') {
        const inner = unescapeXml(rawMessage).match(/message='([^\n']+)/);
        if (inner) message = inner[1].trim();
      }
      const cdata = parseCdata(failBody);
      const errorMessage = message || (cdata.split('\n')[0] || '').trim();

      // Prefer screenshot path from system-out (contains actual timestamp filename)
      let screenshotPath = extractScreenshotFromSystemOut(body);
      if (!screenshotPath) {
        screenshotPath = findScreenshotByPrefix(testName, screenshotsDir);
      }

      results.push({ testName, className, status: 'failed', errorMessage, screenshotPath });
      continue;
    }

    results.push({ testName, className, status: 'passed', errorMessage: null, screenshotPath: null });
  }

  return results;
}

function main() {
  let allResults;

  // Prefer single comprehensive suite file; fall back to per-class junitreports
  if (fs.existsSync(suiteXml)) {
    allResults = parseXmlFile(suiteXml);
  } else if (fs.existsSync(junitDir)) {
    const xmlFiles = fs.readdirSync(junitDir).filter(f => f.startsWith('TEST-') && f.endsWith('.xml'));
    if (xmlFiles.length === 0) {
      console.error(`ERROR: No TEST-*.xml files found in ${junitDir}`);
      process.exit(1);
    }
    allResults = [];
    for (const file of xmlFiles) {
      allResults.push(...parseXmlFile(path.join(junitDir, file)));
    }
  } else {
    console.error(`ERROR: No test reports found. Run "mvn test" first.`);
    console.error(`  Expected: ${suiteXml}`);
    console.error(`  Or:       ${junitDir}/TEST-*.xml`);
    process.exit(1);
  }

  const total = allResults.length;
  const passed = allResults.filter(r => r.status === 'passed').length;
  const failed = allResults.filter(r => r.status === 'failed').length;
  const skipped = allResults.filter(r => r.status === 'skipped').length;
  const failures = allResults
    .filter(r => r.status === 'failed')
    .map(({ testName, className, errorMessage, screenshotPath }) => ({
      testName,
      className,
      errorMessage,
      screenshotPath,
    }));

  const runStatus = failed > 0 ? 'RED' : 'GREEN';
  const output = { total, passed, failed, skipped, runStatus, failures };

  console.log(JSON.stringify(output, null, 2));
  console.log(`\nRUN_STATUS: ${runStatus}`);
}

main();
