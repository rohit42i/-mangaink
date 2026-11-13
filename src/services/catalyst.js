import { zcAuth } from '@zcatalyst/auth/web';
import { Datastore } from '@zcatalyst/datastore';
import { Stratus } from '@zcatalyst/stratus';

const BUCKET_NAME = '1manga';
const PROJECT_TABLE = 'MI_Projects';
const PROFILE_TABLE = 'MI_UserProfiles';
const SETTINGS_TABLE = 'MI_UserSettings';
const JOB_TABLE = 'MI_ProcessingJobs';

let initPromise;
let datastore;
let bucket;

function isCatalystHosted() {
  const host = window.location.hostname.toLowerCase();
  return host.endsWith('.catalystserverless.in') || host.endsWith('.catalystapp.in');
}

async function getSession() {
  const authenticatedUser = await zcAuth.isUserAuthenticated();
  if (!authenticatedUser) return { authenticated: false, user: null };
  const user = await zcAuth.getProjectUserDetails();
  return { authenticated: true, user };
}

export async function initializeCatalyst() {
  if (!isCatalystHosted()) {
    return { available: false, authenticated: false, user: null };
  }
  initPromise ??= getSession();
  try {
    return { available: true, ...(await initPromise) };
  } catch (error) {
    initPromise = undefined;
    throw error;
  }
}

export async function beginCatalystSignIn(containerId) {
  return zcAuth.signIn(containerId, {
    redirectUrl: '/',
  });
}

export async function signOutCatalyst() {
  await zcAuth.signOut('/');
}

function clients() {
  datastore ??= new Datastore();
  bucket ??= new Stratus().bucket(BUCKET_NAME);
  return { datastore, bucket };
}

async function requireSignedIn() {
  const session = await initializeCatalyst();
  if (!session.available) throw new Error('Open MangaInker from its Zoho Catalyst website to use cloud storage.');
  if (!session.authenticated) throw new Error('Sign in to your MangaInker account first.');
  return clients();
}

async function firstPage(tableName, maxRows = 100) {
  const { datastore } = await requireSignedIn();
  const page = await datastore.table(tableName).getPagedRows({ maxRows });
  return page?.data ?? [];
}

export async function syncUserProfile(user) {
  const { datastore } = await requireSignedIn();
  const table = datastore.table(PROFILE_TABLE);
  const email = String(user?.email_id ?? user?.email ?? '').trim();
  const displayName = [user?.first_name, user?.last_name].filter(Boolean).join(' ').trim() || email || 'MangaInker user';
  const rows = await firstPage(PROFILE_TABLE);
  const existing = rows.find(row => email && String(row.email ?? '').toLowerCase() === email.toLowerCase());
  const values = { email, display_name: displayName };
  if (existing?.ROWID) {
    await table.updateRow({ ROWID: existing.ROWID, ...values });
  } else {
    await table.insertRow({ ...values, avatar_object_key: '' });
  }
}

export async function loadUserSettings() {
  const rows = await firstPage(SETTINGS_TABLE, 20);
  const row = rows[0];
  if (!row?.settings_json) return null;
  try { return JSON.parse(row.settings_json); } catch { return null; }
}

export async function saveUserSettings(settings) {
  const { datastore } = await requireSignedIn();
  const table = datastore.table(SETTINGS_TABLE);
  const rows = await firstPage(SETTINGS_TABLE, 20);
  const values = { settings_json: JSON.stringify(settings), updated_at: new Date().toISOString() };
  if (rows[0]?.ROWID) await table.updateRow({ ROWID: rows[0].ROWID, ...values });
  else await table.insertRow(values);
}

export async function listSavedProjects() {
  const rows = await firstPage(PROJECT_TABLE, 100);
  return rows.sort((a, b) => {
    const da = Date.parse(a.updated_at || a.CREATEDTIME || 0) || 0;
    const db = Date.parse(b.updated_at || b.CREATEDTIME || 0) || 0;
    return db - da;
  });
}

function safeFileName(name) {
  return String(name || 'manga-page.png').replace(/[^a-zA-Z0-9._-]+/g, '_').slice(0, 80);
}

export async function saveProjectToCloud(file, svg, projectName) {
  const { datastore, bucket } = await requireSignedIn();
  if (!(file instanceof File)) throw new Error('Choose an image before saving a project.');
  if (!svg) throw new Error('Process the image before saving the project.');

  const now = new Date().toISOString();
  const name = String(projectName || file.name || 'Untitled manga page').trim().slice(0, 255);
  const projects = datastore.table(PROJECT_TABLE);
  const jobs = datastore.table(JOB_TABLE);
  const project = await projects.insertRow({
    project_name: name,
    source_object_key: '',
    preview_object_key: '',
    created_at: now,
    updated_at: now,
  });
  const rowId = String(project?.ROWID ?? '');
  if (!rowId) throw new Error('Catalyst did not return a project ID. Please retry.');

  const storedProject = project?.CREATORID ? project : await projects.getRow(rowId);
  const creatorId = String(storedProject?.CREATORID || '');
  if (!/^\d+$/.test(creatorId)) {
    await projects.deleteRow(project.ROWID);
    throw new Error('Catalyst did not return the project owner ID. The project was not uploaded.');
  }
  const stem = safeFileName(file.name || 'manga-page.png');
  const ownerPath = 'users/' + creatorId + '/projects/' + rowId;
  const sourceKey = ownerPath + '/source-' + stem;
  const previewKey = ownerPath + '/line-art.svg';
  let job = null;
  try {
    job = await jobs.insertRow({
      status: 'uploading',
      input_object_key: sourceKey,
      output_object_key: previewKey,
      error_message: '',
      created_at: now,
    });
    await bucket.putObject(sourceKey, file);
    await bucket.putObject(previewKey, new Blob([svg], { type: 'image/svg+xml;charset=utf-8' }));
    await projects.updateRow({
      ROWID: project.ROWID,
      project_name: name,
      source_object_key: sourceKey,
      preview_object_key: previewKey,
      updated_at: new Date().toISOString(),
    });
    if (job?.ROWID) await jobs.updateRow({ ROWID: job.ROWID, status: 'completed', error_message: '' });
    return {
      ...project,
      project_name: name,
      source_object_key: sourceKey,
      preview_object_key: previewKey,
      updated_at: now,
    };
  } catch (error) {
    if (job?.ROWID) {
      try {
        await jobs.updateRow({ ROWID: job.ROWID, status: 'failed', error_message: String(error?.message || error).slice(0, 10000) });
      } catch { /* preserve the original upload error */ }
    }
    await Promise.allSettled([
      bucket.deleteObject(sourceKey),
      bucket.deleteObject(previewKey),
      projects.deleteRow(project.ROWID),
    ]);
    if (error?.statusCode === 403 || /forbidden|permission|cors/i.test(String(error?.message || ''))) {
      throw new Error('Catalyst denied file access. In Stratus > 1manga, allow App User GetObject, PutObject and DeleteObject, then add this website URL under Bucket CORS.');
    }
    throw error;
  }
}

async function objectToBlob(data) {
  if (data instanceof Blob) return data;
  if (data instanceof ArrayBuffer) return new Blob([data]);
  if (data && typeof data.getReader === 'function') return new Response(data).blob();
  if (data && Symbol.asyncIterator in Object(data)) {
    const chunks = [];
    for await (const chunk of data) chunks.push(chunk instanceof Uint8Array ? chunk : new Uint8Array(chunk));
    return new Blob(chunks);
  }
  throw new Error('Catalyst returned an unsupported file response.');
}

export async function downloadProjectSource(objectKey) {
  const { bucket } = await requireSignedIn();
  return objectToBlob(await bucket.getObject(objectKey));
}

export async function deleteCloudProject(row) {
  const { datastore, bucket } = await requireSignedIn();
  if (row?.source_object_key) await bucket.deleteObject(row.source_object_key);
  if (row?.preview_object_key) await bucket.deleteObject(row.preview_object_key);
  await datastore.table(PROJECT_TABLE).deleteRow(row.ROWID);
}

export async function listProcessingJobs() {
  const rows = await firstPage(JOB_TABLE, 50);
  return rows.sort((a, b) => {
    const da = Date.parse(a.created_at || a.CREATEDTIME || 0) || 0;
    const db = Date.parse(b.created_at || b.CREATEDTIME || 0) || 0;
    return db - da;
  });
}
