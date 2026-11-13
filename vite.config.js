import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // GitHub Pages serves this project from /-mangaink/; Catalyst serves it at /.
  base: process.env.VITE_BASE_PATH || '/',
});
