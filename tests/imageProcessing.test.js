import test from 'node:test';
import assert from 'node:assert/strict';
import { otsu, trace, segmentsToPath, svgDocument, parseSvgDocument } from '../src/lib/imageProcessing.js';

function whitePixels(width, height) {
  const pixels = new Uint8ClampedArray(width * height * 4);
  for (let i = 0; i < width * height; i++) {
    pixels[i * 4] = pixels[i * 4 + 1] = pixels[i * 4 + 2] = 255;
    pixels[i * 4 + 3] = 255;
  }
  return pixels;
}

test('Otsu threshold stays within grayscale range', () => {
  const threshold = otsu(new Uint8Array([0, 0, 0, 0, 255, 255, 255, 255]));
  assert.ok(Number.isInteger(threshold) && threshold >= 0 && threshold <= 255);
});

test('traces a clean line into editable vector segments', () => {
  const width = 9, height = 9, pixels = whitePixels(width, height);
  for (let x = 2; x <= 6; x++) {
    const i = (4 * width + x) * 4;
    pixels[i] = pixels[i + 1] = pixels[i + 2] = 0;
  }
  const result = trace(pixels, width, height, 127, 1);
  assert.ok(result.d.length > 0);
  assert.ok(result.segments.length > 0);
  assert.ok(result.foreground > 0);
  assert.equal(result.w, width);
  assert.equal(result.h, height);
});

test('transparent SVG export omits an opaque background', () => {
  const svg = svgDocument({ width: 10, height: 10, path: 'M1 1L9 9' });
  assert.match(svg, /<svg/);
  assert.doesNotMatch(svg, /<rect/);
});

test('white background export is optional', () => {
  const svg = svgDocument({ width: 10, height: 10, path: '', whiteBackground: true });
  assert.match(svg, /<rect width="100%" height="100%" fill="#fff"/);
});

test('vector path editing removes selected segments', () => {
  const segments = [{ x1: 1, y1: 1, x2: 2, y2: 2 }, { x1: 3, y1: 3, x2: 4, y2: 4 }];
  assert.equal(segmentsToPath(segments, [0]), 'M3 3L4 4');
});

test('saved SVG projects restore editable segments and output settings', () => {
  const svg = svgDocument({
    width: 20, height: 15, path: 'M1 2L3 4',
    manualPaths: ['M5 6L7 8L9 10'], strokeWidth: 2.25, whiteBackground: true,
  });
  const restored = parseSvgDocument(svg);
  assert.equal(restored.w, 20);
  assert.equal(restored.h, 15);
  assert.equal(restored.strokeWidth, 2.25);
  assert.equal(restored.whiteBackground, true);
  assert.equal(restored.segments.length, 3);
});
