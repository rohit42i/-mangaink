import test from 'node:test';
import assert from 'node:assert/strict';
import { otsu, trace } from '../src/lib/imageProcessing.js';

test('Otsu threshold stays within the grayscale range', () => {
  const pixels = new Uint8Array([0, 0, 0, 0, 255, 255, 255, 255]);
  const threshold = otsu(pixels);
  assert.ok(Number.isInteger(threshold));
  assert.ok(threshold >= 0 && threshold <= 255);
});

test('line tracing creates SVG segments for a clear black line', () => {
  const width = 7;
  const height = 7;
  const rgba = new Uint8ClampedArray(width * height * 4);
  for (let i = 0; i < width * height; i++) {
    rgba[i * 4] = 255;
    rgba[i * 4 + 1] = 255;
    rgba[i * 4 + 2] = 255;
    rgba[i * 4 + 3] = 255;
  }
  for (let x = 2; x <= 4; x++) {
    const offset = (3 * width + x) * 4;
    rgba[offset] = 0;
    rgba[offset + 1] = 0;
    rgba[offset + 2] = 0;
  }

  const result = trace(rgba, width, height, 127, 1);
  assert.ok(result.d.length > 0);
  assert.ok(result.foreground > 0);
  assert.equal(result.w, width);
  assert.equal(result.h, height);
});
