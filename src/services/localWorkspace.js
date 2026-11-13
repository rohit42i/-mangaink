const DB_NAME = 'mangainker-workspace';
const DB_VERSION = 1;
const PROJECTS = 'projects';
const HISTORY = 'history';
const SETTINGS = 'settings';

function openDatabase() {
  if (!('indexedDB' in window)) return Promise.reject(new Error('This browser does not support local project storage.'));
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(PROJECTS)) db.createObjectStore(PROJECTS, { keyPath: 'id' });
      if (!db.objectStoreNames.contains(HISTORY)) db.createObjectStore(HISTORY, { keyPath: 'id' });
      if (!db.objectStoreNames.contains(SETTINGS)) db.createObjectStore(SETTINGS, { keyPath: 'id' });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error || new Error('Could not open local project storage.'));
  });
}

async function transact(storeName, mode, action) {
  const db = await openDatabase();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, mode);
    const store = tx.objectStore(storeName);
    let request;
    try { request = action(store); } catch (error) { db.close(); reject(error); return; }
    tx.oncomplete = () => { db.close(); resolve(request?.result); };
    tx.onerror = () => { db.close(); reject(tx.error || request?.error || new Error('Local storage operation failed.')); };
    tx.onabort = () => { db.close(); reject(tx.error || new Error('Local storage operation was cancelled.')); };
  });
}

function makeId() {
  return globalThis.crypto?.randomUUID?.() || Date.now().toString(36) + Math.random().toString(36).slice(2);
}

export async function saveLocalProject(file, svg, projectName) {
  if (!file || !svg) throw new Error('Import and process an image before saving.');
  const now = new Date().toISOString();
  const id = makeId();
  const project = {
    id,
    project_name: String(projectName || file.name || 'Untitled manga page').trim(),
    file_name: file.name || 'manga-page.png',
    file_type: file.type || 'image/png',
    source_blob: file,
    preview_svg: svg,
    created_at: now,
    updated_at: now,
  };
  await transact(PROJECTS, 'readwrite', store => store.put(project));
  await transact(HISTORY, 'readwrite', store => store.put({
    id: makeId(), project_id: id, project_name: project.project_name,
    created_at: now, status: 'saved on this device',
  }));
  return project;
}

export async function listLocalProjects() {
  const rows = await transact(PROJECTS, 'readonly', store => store.getAll());
  return (rows || []).sort((a, b) => Date.parse(b.updated_at) - Date.parse(a.updated_at));
}

export async function listLocalHistory() {
  const rows = await transact(HISTORY, 'readonly', store => store.getAll());
  return (rows || []).sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at));
}

export async function loadLocalProject(id) {
  const project = await transact(PROJECTS, 'readonly', store => store.get(id));
  if (!project) throw new Error('This saved project is no longer available on this device.');
  return {
    file: new File([project.source_blob], project.file_name, { type: project.file_type || 'image/png' }),
    preview: new Blob([project.preview_svg], { type: 'image/svg+xml;charset=utf-8' }),
  };
}

export async function deleteLocalProject(id) {
  await transact(PROJECTS, 'readwrite', store => store.delete(id));
  const history = await listLocalHistory();
  await Promise.all(history.filter(item => item.project_id === id).map(item =>
    transact(HISTORY, 'readwrite', store => store.delete(item.id))
  ));
}

export async function saveLocalSettings(settings) {
  await transact(SETTINGS, 'readwrite', store => store.put({ id: 'studio', value: settings }));
}

export async function loadLocalSettings() {
  const row = await transact(SETTINGS, 'readonly', store => store.get('studio'));
  return row?.value || null;
}
