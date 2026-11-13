import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowDownToLine, Check, CircleHelp, FileImage, FolderOpen, Layers, Moon,
  RotateCcw, ScanLine, SlidersHorizontal, Sun, Upload, ZoomIn, ZoomOut,
  ImagePlus, Sparkles, WandSparkles, Eraser, Pencil, Undo2, Redo2, Eye,
} from 'lucide-react';
import CatalystWorkspace from './components/CatalystWorkspace.jsx';
import FloatingTools from './components/FloatingTools.jsx';
import { otsu, trace, segmentsToPath, svgDocument, parseSvgDocument } from './lib/imageProcessing.js';

const clamp = (value, min, max) => Math.max(min, Math.min(max, value));
const initialEdits = [{ removed: [], manual: [] }];

function loadSavedTheme() {
  try { return localStorage.getItem('mangainker-theme') !== 'light'; } catch { return true; }
}

export default function App() {
  const [input, setInput] = useState(null);
  const [result, setResult] = useState(null);
  const [threshold, setThreshold] = useState(145);
  const [speck, setSpeck] = useState(3);
  const [stroke, setStroke] = useState(1.35);
  const [smooth, setSmooth] = useState(true);
  const [view, setView] = useState('ink');
  const [dark, setDark] = useState(loadSavedTheme);
  const [zoom, setZoom] = useState(1);
  const [overlayOpacity, setOverlayOpacity] = useState(65);
  const [whiteBackground, setWhiteBackground] = useState(false);
  const [editMode, setEditMode] = useState('none');
  const [editHistory, setEditHistory] = useState(initialEdits);
  const [editIndex, setEditIndex] = useState(0);
  const [drawing, setDrawing] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const fileInput = useRef(null);
  const imageRef = useRef(null);
  const svgRef = useRef(null);
  const drawingPoints = useRef([]);

  const edits = editHistory[editIndex] || initialEdits[0];
  const removed = useMemo(() => new Set(edits.removed), [edits.removed]);
  const activePath = result ? segmentsToPath(result.segments, removed) : '';

  const run = useCallback(async (image, nextThreshold = threshold, nextSpeck = speck) => {
    if (!image) return;
    setBusy(true);
    setError('');
    await new Promise(resolve => requestAnimationFrame(resolve));
    try {
      const scale = Math.min(1, 2400 / Math.max(image.naturalWidth, image.naturalHeight));
      const width = Math.max(3, Math.round(image.naturalWidth * scale));
      const height = Math.max(3, Math.round(image.naturalHeight * scale));
      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      const context = canvas.getContext('2d', { willReadFrequently: true });
      context.fillStyle = '#fff';
      context.fillRect(0, 0, width, height);
      context.drawImage(image, 0, 0, width, height);
      const pixels = context.getImageData(0, 0, width, height);
      const gray = new Uint8Array(width * height);
      for (let i = 0; i < gray.length; i++) {
        const offset = i * 4;
        gray[i] = Math.round(.299 * pixels.data[offset] + .587 * pixels.data[offset + 1] + .114 * pixels.data[offset + 2]);
      }
      const auto = otsu(gray);
      const output = trace(pixels.data, width, height, nextThreshold, nextSpeck);
      if (!output.d) throw new Error('No usable lines found. Try Auto threshold or reduce speck removal.');
      setResult({ ...output, auto, source: canvas.toDataURL('image/png') });
      setEditHistory(initialEdits);
      setEditIndex(0);
      setView('ink');
    } catch (processingError) {
      setResult(null);
      setError(processingError.message || 'Could not process this image.');
    } finally {
      setBusy(false);
    }
  }, [threshold, speck]);

  const load = useCallback(file => {
    if (!file) return;
    if (!file.type.startsWith('image/')) { setError('Choose a supported image file.'); return; }
    if (file.size > 25 * 1024 * 1024) { setError('Image must be smaller than 25 MB.'); return; }
    const url = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      imageRef.current = image;
      setInput(file);
      setResult(null);
      setZoom(1);
      setError('');
      run(image);
      URL.revokeObjectURL(url);
    };
    image.onerror = () => { setError('Could not open this image.'); URL.revokeObjectURL(url); };
    image.src = url;
  }, [run]);

  const restoreProject = useCallback(async (file, previewBlob) => {
    if (!file || !previewBlob) { load(file); return; }
    try {
      const parsed = parseSvgDocument(await previewBlob.text());
      const url = URL.createObjectURL(file);
      const image = new Image();
      image.onload = () => {
        try {
          const canvas = document.createElement('canvas');
          canvas.width = parsed.w;
          canvas.height = parsed.h;
          const context = canvas.getContext('2d');
          context.fillStyle = '#fff';
          context.fillRect(0, 0, parsed.w, parsed.h);
          context.drawImage(image, 0, 0, parsed.w, parsed.h);
          imageRef.current = image;
          setInput(file);
          setResult({ ...parsed, source: canvas.toDataURL('image/png') });
          setStroke(parsed.strokeWidth);
          setSmooth(parsed.smooth);
          setWhiteBackground(parsed.whiteBackground);
          setEditHistory(initialEdits);
          setEditIndex(0);
          setView('ink');
          setZoom(1);
          setError('');
        } catch (restoreError) {
          setError(restoreError.message || 'Could not restore this saved project.');
        } finally { URL.revokeObjectURL(url); }
      };
      image.onerror = () => { URL.revokeObjectURL(url); setError('Could not open the saved source image.'); };
      image.src = url;
    } catch (restoreError) {
      setError(restoreError.message || 'Could not restore the saved project.');
    }
  }, [load]);

  useEffect(() => {
    if (imageRef.current) run(imageRef.current);
  }, [run]);

  useEffect(() => {
    try { localStorage.setItem('mangainker-theme', dark ? 'dark' : 'light'); } catch { /* storage may be disabled */ }
  }, [dark]);

  const svg = useMemo(() => result ? svgDocument({
    width: result.w,
    height: result.h,
    path: activePath,
    manualPaths: edits.manual,
    strokeWidth: stroke,
    smooth,
    whiteBackground,
  }) : '', [result, activePath, edits.manual, stroke, smooth, whiteBackground]);

  function download(kind) {
    if (!result || !svg) return;
    const blob = new Blob([svg], { type: 'image/svg+xml;charset=utf-8' });
    const baseName = (input?.name || 'manga-page').replace(/\.[^.]+$/, '');
    if (kind === 'svg') {
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = baseName + '-ink.svg';
      anchor.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      return;
    }
    const image = new Image();
    const url = URL.createObjectURL(blob);
    image.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = result.w;
      canvas.height = result.h;
      const context = canvas.getContext('2d');
      if (whiteBackground) {
        context.fillStyle = '#fff';
        context.fillRect(0, 0, canvas.width, canvas.height);
      }
      context.drawImage(image, 0, 0);
      canvas.toBlob(png => {
        if (png) {
          const pngUrl = URL.createObjectURL(png);
          const anchor = document.createElement('a');
          anchor.href = pngUrl;
          anchor.download = baseName + '-ink.png';
          anchor.click();
          setTimeout(() => URL.revokeObjectURL(pngUrl), 1000);
        }
        URL.revokeObjectURL(url);
      }, 'image/png');
    };
    image.onerror = () => { URL.revokeObjectURL(url); setError('Could not create the PNG export.'); };
    image.src = url;
  }

  function commitEdit(next) {
    setEditHistory(previous => [...previous.slice(0, editIndex + 1), next]);
    setEditIndex(index => index + 1);
  }

  function pointFromEvent(event) {
    const bounds = svgRef.current?.getBoundingClientRect();
    if (!bounds || !result) return null;
    return {
      x: ((event.clientX - bounds.left) / bounds.width) * result.w,
      y: ((event.clientY - bounds.top) / bounds.height) * result.h,
    };
  }

  function eraseAt(point) {
    if (!point || !result) return;
    let bestIndex = -1, bestDistance = 14;
    for (let i = 0; i < result.segments.length; i++) {
      if (removed.has(i)) continue;
      const segment = result.segments[i];
      const dx = segment.x2 - segment.x1, dy = segment.y2 - segment.y1;
      const lengthSquared = dx * dx + dy * dy;
      const t = lengthSquared ? clamp(((point.x - segment.x1) * dx + (point.y - segment.y1) * dy) / lengthSquared, 0, 1) : 0;
      const px = segment.x1 + t * dx, py = segment.y1 + t * dy;
      const distance = Math.hypot(point.x - px, point.y - py);
      if (distance < bestDistance) { bestDistance = distance; bestIndex = i; }
    }
    if (bestIndex >= 0) commitEdit({ removed: [...edits.removed, bestIndex], manual: edits.manual });
  }

  function handlePointerDown(event) {
    if (editMode === 'none' || view === 'original') return;
    const point = pointFromEvent(event);
    if (!point) return;
    event.preventDefault();
    if (editMode === 'erase') { eraseAt(point); return; }
    drawingPoints.current = [point];
    event.currentTarget.setPointerCapture?.(event.pointerId);
    setDrawing('M' + point.x.toFixed(1) + ' ' + point.y.toFixed(1));
  }

  function handlePointerMove(event) {
    if (editMode !== 'draw' || !drawingPoints.current.length) return;
    const point = pointFromEvent(event);
    if (!point) return;
    const points = drawingPoints.current;
    const previous = points[points.length - 1];
    if (Math.hypot(point.x - previous.x, point.y - previous.y) < 1.5) return;
    points.push(point);
    setDrawing(value => value + 'L' + point.x.toFixed(1) + ' ' + point.y.toFixed(1));
  }

  function handlePointerUp() {
    if (editMode !== 'draw' || !drawingPoints.current.length) return;
    const points = drawingPoints.current;
    drawingPoints.current = [];
    setDrawing('');
    if (points.length > 1) commitEdit({
      removed: edits.removed,
      manual: [...edits.manual, 'M' + points.map((point, index) => (index ? 'L' : '') + point.x.toFixed(1) + ' ' + point.y.toFixed(1)).join(' ')],
    });
  }

  function undo() { if (editIndex > 0) setEditIndex(index => index - 1); }
  function redo() { if (editIndex < editHistory.length - 1) setEditIndex(index => index + 1); }
  function resetEdits() { setEditHistory(initialEdits); setEditIndex(0); }
  function resetAdjustments() {
    setThreshold(145); setSpeck(3); setStroke(1.35); setSmooth(true);
  }

  const settings = { threshold, speck, stroke, smooth, dark, whiteBackground, overlayOpacity };

  return (
    <div className={'app ' + (dark ? 'dark' : 'light')}>
      <header className="topbar">
        <a className="brand" href="#" onClick={event => event.preventDefault()}>
          <span className="brand-mark"><ScanLine size={21} /></span>
          <span>Manga<span className="accent">Inker</span><small>STUDIO</small></span>
        </a>
        <div className="workspace-name"><i />{input ? input.name : 'Untitled workspace'}</div>
        <div className="top-actions">
          <button className="theme" onClick={() => setDark(value => !value)} aria-label="Toggle theme">{dark ? <Sun size={17} /> : <Moon size={17} />}</button>
          <button className="help" onClick={() => setError('Import a manga page, adjust detection, erase or draw vector paths, then export transparent SVG or PNG.') }><CircleHelp size={16} /> Help</button>
          <button className="export" onClick={() => download('svg')} disabled={!result}><ArrowDownToLine size={16} /> Export SVG</button>
        </div>
      </header>

      <main className="layout">
        <aside className="sidebar">
          <div className="side-label">WORKSPACE</div>
          <button className="side-item active"><Layers size={17} /> Line art studio</button>
          <button className="side-item" onClick={() => fileInput.current?.click()}><FolderOpen size={17} /> Open image</button>
          <div className="side-rule" />
          <div className="side-label">PREVIEW</div>
          <button className={'side-item ' + (view === 'ink' ? 'chosen' : '')} onClick={() => setView('ink')}><WandSparkles size={17} /> Inked result</button>
          <button className={'side-item ' + (view === 'overlay' ? 'chosen' : '')} onClick={() => setView('overlay')}><Eye size={17} /> Trace overlay</button>
          <button className={'side-item ' + (view === 'original' ? 'chosen' : '')} onClick={() => setView('original')}><FileImage size={17} /> Original image</button>
          <div className="sidebar-bottom">
            <div className="note-card"><Sparkles size={17} /><b>Made for manga artists</b><p>Convert sketches into clean, editable line art. Export transparent paths for your drawing workflow.</p></div>
            <div className="profile"><div className="avatar">M</div><span><b>Local workspace</b><small>Cloud sign-in below</small></span></div>
          </div>
        </aside>

        <section className="main-area">
          <div className="heading">
            <div><div className="eyebrow">IMAGE PROCESSING <span>•</span> BROWSER WORKSPACE</div><h1>Line art studio</h1><p>Trace, refine and export your manga artwork.</p></div>
            <div className="heading-actions"><span className="local"><i /> Processed on this device</span><button className="import" onClick={() => fileInput.current?.click()}><Upload size={16} /> Import image</button></div>
          </div>

          <CatalystWorkspace file={input} svg={svg} settings={settings} onLoadFile={load} onLoadProject={restoreProject} onRestoreSettings={saved => {
            if (typeof saved.threshold === 'number') setThreshold(saved.threshold);
            if (typeof saved.speck === 'number') setSpeck(saved.speck);
            if (typeof saved.stroke === 'number') setStroke(saved.stroke);
            if (typeof saved.smooth === 'boolean') setSmooth(saved.smooth);
            if (typeof saved.dark === 'boolean') setDark(saved.dark);
            if (typeof saved.whiteBackground === 'boolean') setWhiteBackground(saved.whiteBackground);
            if (typeof saved.overlayOpacity === 'number') setOverlayOpacity(saved.overlayOpacity);
          }} />

          <div className="studio">
            <section className="canvas-panel">
              <div className="canvas-bar">
                <div className="tabs">
                  <button className={view === 'ink' ? 'active' : ''} onClick={() => setView('ink')}>Inked result</button>
                  <button className={view === 'overlay' ? 'active' : ''} onClick={() => setView('overlay')}>Trace overlay</button>
                  <button className={view === 'original' ? 'active' : ''} onClick={() => setView('original')}>Original</button>
                </div>
                <div className="zoom">
                  <button onClick={() => setZoom(value => clamp(value - .15, .35, 3))} aria-label="Zoom out"><ZoomOut size={16} /></button>
                  <span>{Math.round(zoom * 100)}%</span>
                  <button onClick={() => setZoom(value => clamp(value + .15, .35, 3))} aria-label="Zoom in"><ZoomIn size={16} /></button>
                  <button onClick={() => setZoom(1)} aria-label="Reset zoom"><RotateCcw size={15} /></button>
                </div>
              </div>

              <div className="canvas-area" onDragOver={event => event.preventDefault()} onDrop={event => { event.preventDefault(); load(event.dataTransfer.files?.[0]); }}>
                {!result && !busy ? (
                  <button className="drop" onClick={() => fileInput.current?.click()}>
                    <span className="drop-icon"><ImagePlus size={25} /></span><b>Drop your manga page here</b><span>or click to browse files</span><small>PNG, JPG, WEBP · Up to 25 MB</small>
                  </button>
                ) : busy ? (
                  <div className="processing"><span className="spinner" /><b>Tracing your artwork…</b><small>Detecting, thinning and refining line paths</small></div>
                ) : (
                  <div className={'art ' + (whiteBackground && view !== 'overlay' ? 'art-white' : '')} style={{ transform: 'scale(' + zoom + ')', aspectRatio: result.w + ' / ' + result.h }}>
                    {view === 'original' ? <img src={result.source} alt="Original manga sketch" /> : (
                      <>
                        {view === 'overlay' && <img className="art-underlay" src={result.source} alt="Original sketch under transparent line art" />}
                        <svg ref={svgRef} viewBox={'0 0 ' + result.w + ' ' + result.h} onPointerDown={handlePointerDown} onPointerMove={handlePointerMove} onPointerUp={handlePointerUp} onPointerCancel={handlePointerUp} className={editMode !== 'none' ? 'editable-svg' : ''}>
                          {whiteBackground && view !== 'overlay' && <rect width="100%" height="100%" fill="#fff" />}
                          <g fill="none" stroke="#111" strokeWidth={stroke} strokeLinecap={smooth ? 'round' : 'butt'} strokeLinejoin={smooth ? 'round' : 'miter'} opacity={view === 'overlay' ? overlayOpacity / 100 : 1}>
                            <path d={activePath} />
                            {edits.manual.map((path, index) => <path d={path} key={'manual-' + index} />)}
                            {drawing && <path d={drawing} />}
                          </g>
                        </svg>
                      </>
                    )}
                  </div>
                )}
              </div>

              <div className="canvas-foot">
                <span><i />{result ? 'Ready · ' + result.w + ' × ' + result.h : 'Waiting for an image'}</span>
                <span>{result ? result.foreground.toLocaleString() + ' line pixels · ' + result.segments.length.toLocaleString() + ' editable segments' : 'Your image stays on this device'}</span>
              </div>
              <div className="editor-toolbar">
                <span className="editor-label">PATH EDITOR</span>
                <button className={editMode === 'draw' ? 'selected' : ''} onClick={() => setEditMode(editMode === 'draw' ? 'none' : 'draw')} disabled={!result}><Pencil size={14} /> Draw</button>
                <button className={editMode === 'erase' ? 'selected' : ''} onClick={() => setEditMode(editMode === 'erase' ? 'none' : 'erase')} disabled={!result}><Eraser size={14} /> Erase path</button>
                <button onClick={undo} disabled={editIndex === 0}><Undo2 size={14} /> Undo</button>
                <button onClick={redo} disabled={editIndex >= editHistory.length - 1}><Redo2 size={14} /> Redo</button>
                <button onClick={resetEdits} disabled={!result || editIndex === 0}><RotateCcw size={13} /> Reset edits</button>
                <span className="editor-hint">{editMode === 'draw' ? 'Draw directly over the line art' : editMode === 'erase' ? 'Tap a line segment to remove it' : 'Choose Draw or Erase to edit vector paths'}</span>
              </div>
            </section>

            <aside className="settings">
              <div className="settings-head"><span><SlidersHorizontal size={17} /><b>Adjustments</b></span><button onClick={resetAdjustments}><RotateCcw size={13} /> Reset</button></div>
              <div className="setting">
                <div className="section-title"><em>01</em><b>Line detection</b></div>
                <div className="setting-line"><label>Darkness threshold <button className="auto-btn" onClick={() => result && setThreshold(result.auto)}>Auto</button></label><output>{threshold}</output></div>
                <input type="range" min="20" max="245" value={threshold} onChange={event => setThreshold(+event.target.value)} />
                <div className="range-labels"><span>Fine details</span><span>More lines</span></div>
                <p>Choose which pencil and ink marks become line art.</p>
              </div>
              <div className="setting">
                <div className="section-title"><em>02</em><b>Cleanup</b></div>
                <div className="setting-line"><label>Remove tiny specks</label><output>{speck} px</output></div>
                <input type="range" min="1" max="24" value={speck} onChange={event => setSpeck(+event.target.value)} />
                <div className="switch-row"><span><b>Smooth line joins</b><small>Round connected strokes</small></span><button className={'switch ' + (smooth ? 'on' : '')} onClick={() => setSmooth(value => !value)}><i /></button></div>
              </div>
              <div className="setting">
                <div className="section-title"><em>03</em><b>Output</b></div>
                <div className="setting-line"><label>Stroke width</label><output>{stroke.toFixed(2)}</output></div>
                <input type="range" min=".5" max="5" step=".05" value={stroke} onChange={event => setStroke(+event.target.value)} />
                <div className="switch-row"><span><b>White background</b><small>Off exports transparent line art</small></span><button className={'switch ' + (whiteBackground ? 'on' : '')} onClick={() => setWhiteBackground(value => !value)}><i /></button></div>
                {view === 'overlay' && <><div className="setting-line overlay-line"><label>Overlay opacity</label><output>{overlayOpacity}%</output></div><input type="range" min="10" max="100" value={overlayOpacity} onChange={event => setOverlayOpacity(+event.target.value)} /></>}
                <div className="format"><span>SVG</span><div><b>Editable vector</b><small>Transparent background supported</small></div><Check size={16} /></div>
              </div>
              <div className="settings-bottom">
                <button className="primary" onClick={() => download('svg')} disabled={!result}><ArrowDownToLine size={17} /> Export clean SVG</button>
                <button className="secondary" onClick={() => download('png')} disabled={!result}>Export transparent PNG</button>
                <small><Check size={12} /> Local processing · No upload unless you save</small>
              </div>
            </aside>
          </div>

          <FloatingTools
            onImport={() => fileInput.current?.click()}
            onToggleView={() => setView(value => value === 'overlay' ? 'ink' : 'overlay')}
            onReprocess={() => imageRef.current && run(imageRef.current)}
            onExportSvg={() => download('svg')}
            onExportPng={() => download('png')}
            onDraw={() => { setView('ink'); setEditMode('draw'); }}
            onErase={() => { setView('ink'); setEditMode('erase'); }}
            hasImage={!!input} hasResult={!!result} view={view}
          />
          {error && <div className="error" role="alert">{error}<button onClick={() => setError('')}>Dismiss</button></div>}
          <input ref={fileInput} hidden type="file" accept="image/png,image/jpeg,image/webp" onChange={event => { load(event.target.files?.[0]); event.target.value = ''; }} />
        </section>
      </main>
    </div>
  );
}
