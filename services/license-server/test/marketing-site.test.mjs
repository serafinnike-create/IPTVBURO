/**
 * Contract tests for the public GitHub Pages presentation and activation handoff.
 *
 * The marketing page is intentionally static. It may explain the product and collect the public
 * device code, but price authority, checkout creation, and activation stay in the Worker.
 */

import { strict as assert } from 'node:assert';
import { existsSync, readFileSync } from 'node:fs';
import { test } from 'node:test';

const siteRoot = new URL('../../../site/', import.meta.url);
const html = readFileSync(new URL('index.html', siteRoot), 'utf8');
const script = readFileSync(new URL('site.js', siteRoot), 'utf8');
const headers = readFileSync(new URL('_headers', siteRoot), 'utf8');

test('the public site presents the product before asking for payment', () => {
  for (const anchor of ['inicio', 'demonstracao', 'recursos', 'plataformas', 'pagamento', 'perguntas']) {
    assert.ok(html.includes(`id="${anchor}"`), `missing public section: ${anchor}`);
  }

  assert.ok(html.includes('data-demo-tab="multiview"'), 'the interactive product tour is missing');
  assert.ok(html.includes('assets/buro-reel.mp4'), 'the product reel is missing');
  assert.ok(html.includes('7 dias grátis'), 'the trial must be stated before checkout');
  assert.ok(html.includes('730 dias'), 'the real license term must be explicit');
});

test('Offline Vault is prominent without pretending the mobile release gate already passed', () => {
  const all = `${html}\n${script}`;
  assert.ok(html.includes('id="offline"'), 'Offline Vault needs its own product story');
  assert.ok(html.includes('data-demo-tab="offline"'), 'downloads need a place in the interactive tour');
  assert.ok(all.includes('BURO OFFLINE VAULT'));
  assert.ok(all.includes('EM PREPARAÇÃO PARA MOBILE'));
  assert.ok(all.includes('sem baixar TV ao vivo nem contornar proteção'));

  const androidCapabilities = JSON.parse(
    readFileSync(new URL('../../../packages/platform-capabilities/android-adaptive.json', import.meta.url), 'utf8'),
  );
  const releaseManifest = JSON.parse(
    readFileSync(new URL('../../../packages/release-manifest/platforms.json', import.meta.url), 'utf8'),
  );
  const androidMobile = releaseManifest.platforms.find((platform) => platform.id === 'android-mobile');
  assert.equal(androidCapabilities.offline.supported, true, 'phone offline support is now a real capability');
  assert.equal(androidCapabilities.offline.backgroundJobs, false, 'background download remains unavailable');
  assert.notEqual(androidMobile?.status, 'RELEASE_READY', 'mobile has not passed its release gate');
  assert.ok(
    androidMobile?.notes.some((note) => /remains hidden/i.test(note)),
    'the release manifest must explain why the implemented capability is not marketed as released',
  );
  assert.ok(!/offline vault (?:já )?(?:está )?disponível no mobile/i.test(all));
});

test('the static page hands payment to the Worker without handling card data', () => {
  assert.match(html, /<form[^>]+action="https:\/\/iptvburo\.iptvburo\.workers\.dev\/comprar"[^>]+method="get"/);
  assert.ok(html.includes('name="lang"'), 'the non-JavaScript checkout fallback must preserve the language');
  assert.ok(script.includes("const WORKER_ORIGIN = 'https://iptvburo.iptvburo.workers.dev'"));
  assert.ok(script.includes("new URL('/comprar', WORKER_ORIGIN)"));
  assert.ok(script.includes("destination.searchParams.set('device'"));
  assert.ok(script.includes("destination.searchParams.set('lang'"));
  assert.ok(!/stripe[_-]secret|whsec_|sk_(?:test|live)_/i.test(`${html}\n${script}`), 'a payment secret reached the static site');
  assert.ok(!/<input[^>]+(?:card|cvc|expiry)/i.test(html), 'the static page must not collect card data');
  assert.ok(!/\bfetch\s*\(/.test(script), 'the static page should redirect, not call payment APIs');
});

test('the public site ships security and framing policies', () => {
  assert.ok(headers.includes("default-src 'self'"));
  assert.ok(headers.includes("form-action https://iptvburo.iptvburo.workers.dev"));
  assert.ok(headers.includes("frame-ancestors 'none'"));
  assert.ok(headers.includes('Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=()'));
  assert.ok(headers.includes('X-Content-Type-Options: nosniff'));
  assert.ok(!/\sstyle=/.test(html), 'inline styles would be blocked by the site policy');
  assert.ok(!script.includes('.style.'), 'runtime inline styles would be blocked by the site policy');
});

test('interactive product tabs expose their relationship to the demo panel', () => {
  for (const tab of ['home', 'live', 'movies', 'series', 'multiview', 'offline']) {
    assert.ok(html.includes(`id="demo-tab-${tab}"`));
    assert.match(html, new RegExp(`id="demo-tab-${tab}"[^>]+aria-controls="demo-panel"`));
  }
  assert.match(html, /id="demo-panel"[^>]+role="tabpanel"/);
  assert.ok(script.includes("setAttribute('aria-labelledby', selectedTab.id)"));
});

test('device codes are normalized and checked before redirecting', () => {
  assert.ok(script.includes("DEVICE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'"));
  assert.ok(script.includes('DEVICE_PATTERN.test(input.value)'));
  assert.ok(script.includes("params.get('code') || params.get('device')"), 'QR deep links should prefill the public code');
  assert.ok(script.includes("input.value = formatDeviceCode"), 'manual entry should be normalized');
});

test('all four shipped languages are wired into the presentation and checkout handoff', () => {
  for (const language of ['pt', 'en', 'de', 'it']) {
    assert.ok(script.includes(`${language}: {`) || script.includes(`const ${language} = {`), `${language} copy is missing`);
  }

  assert.ok(script.includes('document.documentElement.lang'));
  assert.ok(script.includes("url.searchParams.set('lang'"));
  assert.ok(!script.includes('innerHTML ='), 'translated copy must not be injected as HTML');
});

test('every local image and video referenced by the public page exists', () => {
  const references = [...html.matchAll(/(?:src|href|content)="((?:assets\/|buro-mark\.png|og\.png)[^"]*)"/g)]
    .map((match) => match[1]);

  assert.ok(references.length >= 8, 'too few product assets are connected');
  for (const reference of new Set(references)) {
    assert.ok(existsSync(new URL(reference, siteRoot)), `missing site asset: ${reference}`);
  }
});

test('the public promise is current and legally bounded', () => {
  const all = `${html}\n${script}`.toLowerCase();
  assert.ok(all.includes('não vende, hospeda ou fornece conteúdo'));
  assert.ok(all.includes('fontes de mídia que possui autorização'));
  assert.ok(!all.includes('sem servidor, nenhuma compra'));
  assert.ok(!all.includes('licença vitalícia'));
  for (const provider of ['netflix', 'disney+', 'prime video', 'hbo max']) {
    assert.ok(!all.includes(provider), `third-party entertainment brand leaked into the site: ${provider}`);
  }
});

test('the app QR and the public metadata point to the clean Pages address', () => {
  const publicOrigin = 'https://iptvburo.pages.dev/';
  assert.ok(html.includes(`<link rel="canonical" href="${publicOrigin}">`));
  assert.ok(html.includes(`content="${publicOrigin}og.png"`));

  const appSource = readFileSync(
    new URL('../../../apps/android-tv/src/main/kotlin/com/lucasserafin94/iptvburo/ui/screens/AppShellScreen.kt', import.meta.url),
    'utf8',
  );
  assert.ok(appSource.includes('?code=$deviceId&lang=$portalLanguage'));

  const languageFolders = { values: 'en', 'values-pt-rBR': 'pt', 'values-de': 'de', 'values-it': 'it' };
  for (const [folder, language] of Object.entries(languageFolders)) {
    const strings = readFileSync(
      new URL(`../../../apps/android-tv/src/main/res/${folder}/strings.xml`, import.meta.url),
      'utf8',
    );
    assert.ok(strings.includes('<string name="license_portal">iptvburo.pages.dev</string>'));
    assert.ok(strings.includes(`<string name="license_portal_language">${language}</string>`));
    assert.ok(!strings.toLowerCase().includes('server is not live'));
  }
});
