import React, { useEffect, useState } from 'react';
import { Archive, Cloud, CloudDownload, CloudUpload, LogIn, LogOut, RefreshCw, Save, Settings2, Trash2, X } from 'lucide-react';
import {
  beginCatalystSignIn, deleteCloudProject, downloadProjectSource, initializeCatalyst,
  listSavedProjects, saveProjectToCloud, saveUserSettings, signOutCatalyst,
  syncUserProfile, loadUserSettings,
} from '../services/catalyst.js';

function errorText(error) { return error?.message || 'Something went wrong. Please try again.'; }

export default function CatalystWorkspace({ file, svg, settings, onLoadFile, onRestoreSettings }) {
  const [available, setAvailable] = useState(false);
  const [user, setUser] = useState(null);
  const [projects, setProjects] = useState([]);
  const [loginOpen, setLoginOpen] = useState(false);
  const [projectsOpen, setProjectsOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [messageError, setMessageError] = useState(false);

  function showMessage(text, isError = false) { setMessage(text); setMessageError(isError); }

  useEffect(() => {
    let active = true;
    initializeCatalyst().then(async session => {
      if (!active) return;
      setAvailable(session.available);
      setUser(session.user);
      if (session.authenticated && session.user) {
        try {
          await syncUserProfile(session.user);
          const [saved, preferences] = await Promise.all([listSavedProjects(), loadUserSettings()]);
          if (!active) return;
          setProjects(saved);
          if (preferences && typeof preferences === 'object') onRestoreSettings?.(preferences);
        } catch (error) { if (active) showMessage(errorText(error), true); }
      }
    }).catch(error => { if (active) showMessage(errorText(error), true); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!loginOpen || !available) return undefined;
    let cancelled = false;
    const frame = requestAnimationFrame(() => {
      beginCatalystSignIn('manga-login-container').catch(error => {
        if (!cancelled) showMessage(errorText(error), true);
      });
    });
    return () => { cancelled = true; cancelAnimationFrame(frame); };
  }, [loginOpen, available]);

  async function handleRefresh() {
    setBusy(true);
    try { setProjects(await listSavedProjects()); showMessage('Your cloud projects are up to date.'); }
    catch (error) { showMessage(errorText(error), true); }
    finally { setBusy(false); }
  }

  async function handleSave() {
    if (!file || !svg) { showMessage('Import and process an image before saving it.', true); return; }
    setBusy(true);
    try {
      const saved = await saveProjectToCloud(file, svg, file.name.replace(/\.[^.]+$/, ''));
      setProjects(current => [saved, ...current.filter(item => item.ROWID !== saved.ROWID)]);
      showMessage('Project saved securely to 1manga.');
    } catch (error) { showMessage(errorText(error), true); }
    finally { setBusy(false); }
  }

  async function handleLoad(row) {
    setBusy(true);
    try {
      const blob = await downloadProjectSource(row.source_object_key);
      const extension = row.source_object_key?.split('.').pop()?.toLowerCase() || 'png';
      const fileType = blob.type || (extension === 'jpg' ? 'image/jpeg' : 'image/' + extension);
      const loaded = new File([blob], (row.project_name || 'manga-page') + '.' + extension, { type: fileType });
      onLoadFile?.(loaded);
      setProjectsOpen(false);
      showMessage('Loaded "' + (row.project_name || 'Untitled project') + '".');
    } catch (error) { showMessage(errorText(error), true); }
    finally { setBusy(false); }
  }

  async function handleDelete(row) {
    if (!window.confirm('Delete "' + (row.project_name || 'Untitled project') + '" and its stored files?')) return;
    setBusy(true);
    try {
      await deleteCloudProject(row);
      setProjects(current => current.filter(item => item.ROWID !== row.ROWID));
      showMessage('Cloud project deleted.');
    } catch (error) { showMessage(errorText(error), true); }
    finally { setBusy(false); }
  }

  async function handleSaveSettings() {
    setBusy(true);
    try { await saveUserSettings(settings); showMessage('Your studio settings were saved to your account.'); }
    catch (error) { showMessage(errorText(error), true); }
    finally { setBusy(false); }
  }

  async function handleSignOut() {
    try { await signOutCatalyst(); }
    catch (error) { showMessage(errorText(error), true); }
  }

  return (
    <section className="cloud-workspace">
      <div className="cloud-header">
        <div className="cloud-identity">
          <span className="cloud-icon"><Cloud size={17} /></span>
          <span><b>My MangaInker cloud</b><small>{user ? (user.email_id || user.email || 'Signed in') : available ? 'Sign in to save projects across devices' : 'Cloud features activate on the Catalyst website'}</small></span>
        </div>
        <div className="cloud-actions">
          {user ? (
            <>
              <button className="cloud-button" onClick={() => setProjectsOpen(value => !value)} disabled={busy}><Archive size={14} /> My projects <span className="cloud-count">{projects.length}</span></button>
              <button className="cloud-button" onClick={handleSave} disabled={busy || !file || !svg}><CloudUpload size={14} /> Save project</button>
              <button className="cloud-icon-button" onClick={handleSaveSettings} title="Save studio settings" disabled={busy}><Save size={15} /></button>
              <button className="cloud-icon-button" onClick={handleSignOut} title="Sign out"><LogOut size={15} /></button>
            </>
          ) : (
            <button className="cloud-button cloud-primary" onClick={() => available ? setLoginOpen(true) : showMessage('This preview is not hosted on Zoho Catalyst yet. Upload the Catalyst web-client ZIP to enable account sign-in.', true)}><LogIn size={14} /> Sign in</button>
          )}
        </div>
      </div>

      {projectsOpen && user && (
        <div className="cloud-projects">
          <div className="cloud-projects-heading"><b>Saved projects</b><button className="cloud-icon-button" onClick={handleRefresh} disabled={busy} title="Refresh"><RefreshCw size={14} /></button></div>
          {projects.length ? projects.map(project => (
            <div className="cloud-project" key={project.ROWID}>
              <button className="cloud-project-load" onClick={() => handleLoad(project)} disabled={busy}>
                <span className="cloud-project-thumb"><CloudDownload size={16} /></span>
                <span><b>{project.project_name || 'Untitled project'}</b><small>{project.updated_at || project.CREATEDTIME || 'Saved to cloud'}</small></span>
              </button>
              <button className="cloud-icon-button cloud-delete" onClick={() => handleDelete(project)} disabled={busy} title="Delete project"><Trash2 size={14} /></button>
            </div>
          )) : <p className="cloud-empty">No saved projects yet. Save your first manga page to keep it here.</p>}
        </div>
      )}

      {message && <div className={'cloud-message ' + (messageError ? 'is-error' : '')} role="status">{message}<button onClick={() => setMessage('')} aria-label="Dismiss message"><X size={13} /></button></div>}

      {loginOpen && available && (
        <div className="cloud-login-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) setLoginOpen(false); }}>
          <div className="cloud-login-dialog" role="dialog" aria-modal="true" aria-label="MangaInker sign in">
            <button className="cloud-login-close" onClick={() => setLoginOpen(false)} aria-label="Close sign in"><X size={17} /></button>
            <div className="cloud-login-mark"><Settings2 size={21} /></div>
            <h2>Welcome to MangaInker</h2>
            <p>Sign in or create an account to save your manga projects and studio settings.</p>
            <div id="manga-login-container" className="manga-login-container" />
            <small>Authentication is handled securely by Zoho Catalyst.</small>
          </div>
        </div>
      )}
    </section>
  );
}
