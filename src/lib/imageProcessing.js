export function otsu(values) {
  if (!values?.length) return 127;
  const histogram = new Uint32Array(256);
  for (const value of values) histogram[value]++;
  let total = 0;
  for (let i = 0; i < 256; i++) total += i * histogram[i];
  let backgroundWeight = 0, backgroundSum = 0, bestVariance = -1, threshold = 127;
  for (let i = 0; i < 256; i++) {
    backgroundWeight += histogram[i];
    if (!backgroundWeight) continue;
    const foregroundWeight = values.length - backgroundWeight;
    if (!foregroundWeight) break;
    backgroundSum += i * histogram[i];
    const meanBackground = backgroundSum / backgroundWeight;
    const meanForeground = (total - backgroundSum) / foregroundWeight;
    const variance = backgroundWeight * foregroundWeight * (meanBackground - meanForeground) ** 2;
    if (variance > bestVariance) { bestVariance = variance; threshold = i; }
  }
  return threshold;
}

function skeletonize(mask, width, height) {
  const neighbours = new Uint8Array(8);
  let changed = true, rounds = 0;
  while (changed && rounds++ < 64) {
    changed = false;
    for (let phase = 0; phase < 2; phase++) {
      const remove = [];
      for (let y = 1; y < height - 1; y++) {
        for (let x = 1; x < width - 1; x++) {
          const index = y * width + x;
          if (!mask[index]) continue;
          neighbours[0] = mask[index - width];
          neighbours[1] = mask[index - width + 1];
          neighbours[2] = mask[index + 1];
          neighbours[3] = mask[index + width + 1];
          neighbours[4] = mask[index + width];
          neighbours[5] = mask[index + width - 1];
          neighbours[6] = mask[index - 1];
          neighbours[7] = mask[index - width - 1];
          let count = 0, transitions = 0;
          for (let k = 0; k < 8; k++) {
            count += neighbours[k];
            if (!neighbours[k] && neighbours[(k + 1) % 8]) transitions++;
          }
          if (count < 2 || count > 6 || transitions !== 1) continue;
          const [n, ne, e, se, s, sw, w, nw] = neighbours;
          const keep = phase === 0
            ? n * e * s === 0 && e * s * w === 0
            : n * e * w === 0 && n * s * w === 0;
          if (keep) remove.push(index);
        }
      }
      if (remove.length) {
        for (const index of remove) mask[index] = 0;
        changed = true;
      }
    }
  }
}

function removeSmallComponents(mask, width, height, minimumSize) {
  if (minimumSize <= 1) return;
  const seen = new Uint8Array(mask.length);
  const stack = [];
  for (let index = 0; index < mask.length; index++) {
    if (!mask[index] || seen[index]) continue;
    stack.length = 0;
    stack.push(index);
    seen[index] = 1;
    for (let cursor = 0; cursor < stack.length; cursor++) {
      const point = stack[cursor], x = point % width, y = Math.floor(point / width);
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
        if (!dx && !dy) continue;
        const nx = x + dx, ny = y + dy;
        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
        const next = ny * width + nx;
        if (mask[next] && !seen[next]) { seen[next] = 1; stack.push(next); }
      }
    }
    if (stack.length < minimumSize) for (const point of stack) mask[point] = 0;
  }
}

export function trace(rgba, width, height, threshold = 145, speck = 3) {
  if (!rgba || rgba.length !== width * height * 4) throw new Error('Image pixel data is incomplete.');
  const mask = new Uint8Array(width * height);
  const gray = new Uint8Array(width * height);
  for (let i = 0; i < mask.length; i++) {
    const offset = i * 4;
    const alpha = rgba[offset + 3] / 255;
    const luminance = Math.round(0.299 * rgba[offset] + 0.587 * rgba[offset + 1] + 0.114 * rgba[offset + 2]);
    gray[i] = Math.round(255 * (1 - alpha) + luminance * alpha);
    if (gray[i] <= threshold) mask[i] = 1;
  }
  removeSmallComponents(mask, width, height, Math.max(1, Math.round(speck)));
  skeletonize(mask, width, height);

  const directions = [[-1,-1],[0,-1],[1,-1],[-1,0],[1,0],[-1,1],[0,1],[1,1]];
  const segments = [];
  let path = '';
  for (let index = 0; index < mask.length; index++) {
    if (!mask[index]) continue;
    const x = index % width, y = Math.floor(index / width);
    for (const [dx, dy] of directions) {
      const nx = x + dx, ny = y + dy;
      if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
      const next = ny * width + nx;
      if (mask[next] && next > index) {
        const segment = { x1: x + 0.5, y1: y + 0.5, x2: nx + 0.5, y2: ny + 0.5 };
        segments.push(segment);
        path += 'M' + segment.x1 + ' ' + segment.y1 + 'L' + segment.x2 + ' ' + segment.y2;
      }
    }
  }
  return {
    d: path, segments, w: width, h: height,
    foreground: mask.reduce((sum, value) => sum + value, 0),
    auto: otsu(gray),
  };
}

export function segmentsToPath(segments, removed = []) {
  const hidden = removed instanceof Set ? removed : new Set(removed);
  let path = '';
  for (let i = 0; i < segments.length; i++) {
    if (hidden.has(i)) continue;
    const line = segments[i];
    path += 'M' + line.x1 + ' ' + line.y1 + 'L' + line.x2 + ' ' + line.y2;
  }
  return path;
}

export function svgDocument({ width, height, path, manualPaths = [], strokeWidth = 1.35, smooth = true, whiteBackground = false }) {
  const background = whiteBackground ? '<rect width="100%" height="100%" fill="#fff"/>' : '';
  const cap = smooth ? 'round' : 'butt';
  const joins = smooth ? 'round' : 'miter';
  const manual = manualPaths.map(value => '<path d="' + value + '"/>').join('');
  return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ' + width + ' ' + height + '" width="' + width + '" height="' + height + '">' +
    background + '<g fill="none" stroke="#111" stroke-width="' + strokeWidth + '" stroke-linecap="' + cap + '" stroke-linejoin="' + joins + '">' +
    '<path d="' + path + '"/>' + manual + '</g></svg>';
}

export function parseSvgDocument(svgText) {
  const viewBox = String(svgText).match(/viewBox=["']0\s+0\s+(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)/i);
  if (!viewBox) throw new Error('This saved project has an unsupported SVG preview.');
  const width = Math.max(1, Math.round(Number(viewBox[1])));
  const height = Math.max(1, Math.round(Number(viewBox[2])));
  const strokeMatch = String(svgText).match(/stroke-width=["']([\d.]+)/i);
  const strokeWidth = strokeMatch ? Number(strokeMatch[1]) : 1.35;
  const whiteBackground = /<rect\b[^>]*fill=["']#fff["']/i.test(svgText);
  const smooth = /stroke-linecap=["']round["']/i.test(svgText);
  const segments = [];
  const pathPattern = /<path\b[^>]*\bd=["']([^"']*)["'][^>]*>/gi;
  let pathMatch;
  while ((pathMatch = pathPattern.exec(svgText))) {
    const tokens = pathMatch[1].match(/[ML]|-?\d*\.?\d+(?:e[-+]?\d+)?/gi) || [];
    let command = '', current = null;
    for (let i = 0; i < tokens.length;) {
      if (/^[ML]$/i.test(tokens[i])) command = tokens[i++].toUpperCase();
      if (!command || i + 1 >= tokens.length) break;
      const x = Number(tokens[i++]), y = Number(tokens[i++]);
      if (!Number.isFinite(x) || !Number.isFinite(y)) continue;
      if (command === 'M' || !current) {
        current = { x, y };
        command = 'L';
      } else {
        segments.push({ x1: current.x, y1: current.y, x2: x, y2: y });
        current = { x, y };
      }
    }
  }
  if (!segments.length) throw new Error('The saved project does not contain editable line paths.');
  return {
    w: width, h: height, segments,
    d: segmentsToPath(segments),
    foreground: segments.length,
    auto: 127,
    strokeWidth,
    smooth,
    whiteBackground,
  };
}
