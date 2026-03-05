#!/usr/bin/env node
// check-session.js — Validates session.json age and cookie presence
// Exit 0: VALID, Exit 1: EXPIRED or missing

const fs = require('fs');
const path = require('path');

const SESSION_MAX_AGE_HOURS = 8;
const projectRoot = process.argv[2] || path.resolve(__dirname, '..');
const sessionPath = path.join(projectRoot, 'session.json');

function main() {
  if (!fs.existsSync(sessionPath)) {
    console.log('SESSION_STATUS: EXPIRED');
    console.log('REASON: session.json not found');
    console.log('ACTION_REQUIRED: run login_save_session.js');
    process.exit(1);
  }

  const stat = fs.statSync(sessionPath);
  const ageMs = Date.now() - stat.mtimeMs;
  const ageMinutes = Math.floor(ageMs / 60000);
  const ageHours = ageMs / 3600000;

  if (ageHours > SESSION_MAX_AGE_HOURS) {
    console.log('SESSION_STATUS: EXPIRED');
    console.log(`AGE: ${ageMinutes} minutes`);
    console.log(`REASON: session older than ${SESSION_MAX_AGE_HOURS} hours`);
    console.log('ACTION_REQUIRED: run login_save_session.js');
    process.exit(1);
  }

  let session;
  try {
    const raw = fs.readFileSync(sessionPath, 'utf8');
    session = JSON.parse(raw);
  } catch (e) {
    console.log('SESSION_STATUS: EXPIRED');
    console.log('REASON: session.json is invalid JSON');
    console.log('ACTION_REQUIRED: run login_save_session.js');
    process.exit(1);
  }

  const cookies = session.cookies || [];
  if (!Array.isArray(cookies) || cookies.length === 0) {
    console.log('SESSION_STATUS: EXPIRED');
    console.log('REASON: session.json has empty or missing cookies array');
    console.log('ACTION_REQUIRED: run login_save_session.js');
    process.exit(1);
  }

  console.log('SESSION_STATUS: VALID');
  console.log(`AGE: ${ageMinutes} minutes`);
  console.log(`COOKIES: ${cookies.length} cookies present`);
  process.exit(0);
}

main();
