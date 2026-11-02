import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import { csvCell } from '../src/index.js';

test('a device-reported value cannot become a spreadsheet formula', () => {
  for (const hostile of ['=HYPERLINK("http://x","y")', '+1+1', '-2+3', '@SUM(A1)', '\tcmd', '\rcmd']) {
    assert.ok(csvCell(hostile).startsWith(`"'`), hostile);
  }
});

test('ordinary values and quotes are kept as they are', () => {
  assert.equal(csvCell('Pixel 9'), '"Pixel 9"');
  assert.equal(csvCell('a "b"'), '"a ""b"""');
  assert.equal(csvCell(null), '""');
  assert.equal(csvCell('2026-11-02'), '"2026-11-02"');
});
