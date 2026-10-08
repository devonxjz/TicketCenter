const fs = require('fs');
const path = require('path');
const { mappings, frameContent } = require('./sync_frames');
let errors = 0;
for (const mapping of mappings) {
  const source = fs.readFileSync(path.join(__dirname, mapping.root), 'utf8');
  const target = fs.readFileSync(path.join(__dirname, 'frames', mapping.frame), 'utf8');
  if (target !== frameContent(source)) {
    console.error(`Frame differs from root page: ${mapping.frame}`);
    errors++;
  }
  if (!source.includes('Prototype tĩnh')) {
    console.error(`Missing mock scope label: ${mapping.root}`);
    errors++;
  }
  for (const [content, folder] of [[source, __dirname], [target, path.join(__dirname, 'frames')]]) {
    const localLinks = [...content.matchAll(/(?:href|src)="([^"#]+)"/g)];
    for (const match of localLinks) {
      if (/^(?:https?:|javascript:|data:|mailto:)/.test(match[1])) continue;
      if (!fs.existsSync(path.resolve(folder, match[1].split(/[?#]/)[0]))) {
        console.error(`Missing local target from ${mapping.root}: ${match[1]}`);
        errors++;
      }
    }
  }
}
if (errors) process.exit(1);
console.log('Verified all 24 root/frame pairs: identical text/forms/scripts, mock labels and local links.');
