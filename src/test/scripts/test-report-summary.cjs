'use strict';
const fs = require('node:fs');
const path = require('node:path');

function summarize(directory) {
  if (!fs.existsSync(directory)) throw new Error('Test report directory is missing.');
  const files = fs.readdirSync(directory).filter(name => /^TEST-.*\.xml$/.test(name));
  if (!files.length) throw new Error('No test result files were produced.');
  const counts = { tests: 0, failures: 0, errors: 0, skipped: 0 };
  const skipped = [];
  const suiteCounts = {};
  for (const file of files) {
    const xml = fs.readFileSync(path.join(directory, file), 'utf8');
    const root = xml.match(/<testsuite\b([^>]*)>/);
    if (!root) throw new Error('Invalid test report.');
    const suite = {};
    for (const key of Object.keys(counts)) {
      const value = root[1].match(new RegExp(`\\b${key}="(\\d+)"`));
      if (!value) throw new Error('Test report counts are missing.');
      counts[key] += Number(value[1]);
      suite[key] = Number(value[1]);
    }
    suiteCounts[file.slice(5, -4)] = suite;
    for (const item of xml.matchAll(/<testcase\b([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g)) {
      if (/<skipped\b/.test(item[2] || '')) {
        const name = item[1].match(/\bname="([^"]*)"/)?.[1] || 'unnamed';
        const className = item[1].match(/\bclassname="([^"]*)"/)?.[1] || 'unknown';
        // Report only Java identifiers, never report XML properties, exception text or captured output.
        if (/^[A-Za-z0-9_.$]+$/.test(className) && /^[A-Za-z0-9_]+$/.test(name)) skipped.push(`${className}.${name}`);
      }
    }
  }
  return { ...counts, executed: counts.tests - counts.skipped, skippedNames: skipped, suiteCounts };
}

if (require.main === module) {
  try {
    const args = process.argv.slice(2);
    const result = summarize(args[0]);
    for (const required of args.filter(arg => arg.startsWith('--require-suite='))) {
      const suite = result.suiteCounts[required.slice('--require-suite='.length)];
      if (!suite || suite.tests - suite.skipped <= 0) throw new Error('A required suite produced no executed test results.');
    }
    const lines = [
      `Tests: ${result.tests}; executed: ${result.executed}; skipped: ${result.skipped}; failures: ${result.failures}; errors: ${result.errors}.`,
      ...result.skippedNames.map(name => `Skipped external case: ${name}`)
    ];
    const summary = args.find(arg => arg.startsWith('--summary='));
    if (summary) fs.appendFileSync(summary.slice('--summary='.length), lines.join('\n') + '\n');
    process.stdout.write(lines.join('\n') + '\n');
    if (result.failures || result.errors || result.executed <= 0
        || (args.includes('--require-no-skips') && result.skipped)) process.exitCode = 1;
  } catch (error) {
    process.stderr.write(error.message + '\n');
    process.exitCode = 1;
  }
}
module.exports = { summarize };
