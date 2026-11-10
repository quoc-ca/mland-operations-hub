import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

// T110 (Constitution III, FRONTEND-ARCHITECTURE §3–4): ui -> application -> infrastructure. Screens reach the REST
// adapter only through application/, and domain/ stays free of React, HTTP and browser code.
const FEATURE_DIR = path.resolve(__dirname, '..');

const sourceFiles = dir => fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
  const full = path.join(dir, entry.name);
  if (entry.isDirectory()) return sourceFiles(full);
  return /\.(js|jsx)$/.test(entry.name) ? [full] : [];
});

const importsOf = file => [...fs.readFileSync(file, 'utf8').matchAll(/(?:import|export)\s[^'"]*?from\s+['"]([^'"]+)['"]/g)]
  .map(match => match[1]);

const offenders = (layer, isForbidden) => sourceFiles(path.join(FEATURE_DIR, layer))
  .flatMap(file => importsOf(file).filter(isForbidden).map(spec => `${path.relative(FEATURE_DIR, file)} -> ${spec}`));

describe('002 frontend layer boundaries', () => {
  it('ui does not import infrastructure', () => {
    expect(offenders('ui', spec => /(^|\/)infrastructure(\/|$)/.test(spec))).toEqual([]);
  });

  it('ui does not call fetch() directly', () => {
    const direct = sourceFiles(path.join(FEATURE_DIR, 'ui'))
      .filter(file => /\bfetch\s*\(/.test(fs.readFileSync(file, 'utf8')))
      .map(file => path.relative(FEATURE_DIR, file));
    expect(direct).toEqual([]);
  });

  it('reaches other features only through their public port (index.js)', () => {
    // FRONTEND-ARCHITECTURE §2: "<feature>" or "<feature>/index" only, never a file inside another feature. Paths are
    // resolved against src/features; shared/ and test/ (outside features/) are technical and allowed.
    const crossing = sourceFiles(FEATURE_DIR).flatMap(file => importsOf(file)
      .map(spec => ({ spec, resolved: path.relative(path.resolve(FEATURE_DIR, '..'), path.resolve(path.dirname(file), spec)).replace(/\\/g, '/') }))
      .filter(({ spec, resolved }) => spec.startsWith('.') && !resolved.startsWith('product-management') && !resolved.startsWith('..')
        && resolved.includes('/') && !/\/index(\.js)?$/.test(resolved))
      .map(({ spec }) => `${path.relative(FEATURE_DIR, file)} -> ${spec}`));
    expect(crossing).toEqual([]);
  });

  it('domain does not depend on React, routing, HTTP or other layers', () => {
    expect(offenders('domain', spec => /^react|^react-router|shared\/api|firebase|(^|\/)(ui|application|infrastructure)(\/|$)/.test(spec)))
      .toEqual([]);
  });
});
