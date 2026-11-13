import React, { useRef, useState } from 'react';
import {
  ArrowDownToLine, Eraser, Eye, FileImage, FolderOpen, Grip, Pencil,
  RefreshCw, ScanLine, X,
} from 'lucide-react';

export default function FloatingTools({
  onImport, onToggleView, onReprocess, onExportSvg, onExportPng,
  onDraw, onErase, hasImage, hasResult, view,
}) {
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState({ right: 22, bottom: 22 });
  const drag = useRef(null);
  const moved = useRef(false);

  function pointerDown(event) {
    if (event.button !== undefined && event.button !== 0) return;
    const rect = event.currentTarget.getBoundingClientRect();
    drag.current = { x: event.clientX, y: event.clientY, left: rect.left, top: rect.top };
    moved.current = false;
    event.currentTarget.setPointerCapture?.(event.pointerId);
  }
  function pointerMove(event) {
    if (!drag.current) return;
    const dx = event.clientX - drag.current.x;
    const dy = event.clientY - drag.current.y;
    if (Math.abs(dx) + Math.abs(dy) > 5) moved.current = true;
    if (!moved.current) return;
    const left = Math.max(8, Math.min(window.innerWidth - 64, drag.current.left + dx));
    const top = Math.max(8, Math.min(window.innerHeight - 64, drag.current.top + dy));
    setPosition({ left, top });
  }
  function pointerUp() {
    if (drag.current && !moved.current) setOpen(value => !value);
    drag.current = null;
  }
  const positioned = position.left !== undefined
    ? { left: position.left, top: position.top }
    : { right: position.right, bottom: position.bottom };

  return (
    <div className="mi-floating-tools" style={positioned}>
      {open && (
        <div className="mi-floating-panel" role="dialog" aria-label="Floating MangaInker tools">
          <div className="mi-floating-heading">
            <span><ScanLine size={15} /> Quick tools</span>
            <button type="button" onClick={() => setOpen(false)} aria-label="Close floating tools"><X size={15} /></button>
          </div>
          <button type="button" onClick={onImport}><FolderOpen size={15} /> Import manga page</button>
          <button type="button" onClick={onToggleView} disabled={!hasImage}><Eye size={15} /> {view === 'overlay' ? 'Show inked result' : 'Show trace overlay'}</button>
          <button type="button" onClick={onDraw} disabled={!hasResult}><Pencil size={15} /> Draw vector path</button>
          <button type="button" onClick={onErase} disabled={!hasResult}><Eraser size={15} /> Erase vector path</button>
          <button type="button" onClick={onReprocess} disabled={!hasImage}><RefreshCw size={15} /> Process again</button>
          <div className="mi-floating-divider" />
          <button type="button" onClick={onExportSvg} disabled={!hasResult}><ArrowDownToLine size={15} /> Export SVG</button>
          <button type="button" onClick={onExportPng} disabled={!hasResult}><FileImage size={15} /> Export transparent PNG</button>
          <p>Floating website controls stay outside the artwork and are never included in exports.</p>
        </div>
      )}
      <button
        type="button"
        className={'mi-floating-trigger ' + (open ? 'is-open' : '')}
        aria-label={open ? 'Close floating tools' : 'Open floating tools'}
        aria-expanded={open}
        onPointerDown={pointerDown}
        onPointerMove={pointerMove}
        onPointerUp={pointerUp}
        onPointerCancel={pointerUp}
        title="Drag to move · Tap for quick tools"
      >
        <Grip className="mi-floating-grip" size={13} />
        <ScanLine size={22} />
      </button>
    </div>
  );
}
