/**
 * The drawings on the site.
 *
 * Inline SVG rather than image files, for three reasons that all matter here:
 *
 * 1. The content security policy on these pages is `default-src 'none'`. An `<img src="...">`
 *    pointing anywhere — even at this same origin — would need the policy loosened, and the page
 *    being loosened is the one where somebody types card details.
 * 2. No extra request. The page is one document and arrives complete.
 * 3. They inherit the palette. A drawing that uses `currentColor` and the CSS variables changes with
 *    the design instead of needing to be re-exported.
 *
 * Everything here is drawn from primitives. Nothing is traced from another product's site and no
 * third-party brand mark appears — GDD 9 §10 forbids it, and a store badge or a competitor's icon
 * copied onto a commercial page is a trademark problem regardless of how it was produced.
 */

/**
 * The device screen showing its activation code.
 *
 * This is the first drawing a customer sees, and it does the job a paragraph would do worse: it
 * shows them where on their own screen the code they are being asked about actually appears.
 */
export function screenWithCode() {
  return `
<svg viewBox="0 0 120 84" role="img" aria-hidden="true" class="art">
  <rect x="4" y="4" width="112" height="66" rx="6" fill="var(--raised)" stroke="var(--line)"/>
  <rect x="4" y="4" width="112" height="12" rx="6" fill="var(--surface)"/>
  <circle cx="12" cy="10" r="1.6" fill="var(--subtle)"/>
  <circle cx="18" cy="10" r="1.6" fill="var(--subtle)"/>
  <circle cx="24" cy="10" r="1.6" fill="var(--subtle)"/>
  <rect x="30" y="30" width="60" height="16" rx="3" fill="var(--canvas)" stroke="var(--gold)"/>
  <text x="60" y="41" text-anchor="middle" font-family="ui-monospace, monospace"
        font-size="8" font-weight="700" fill="var(--gold)">FP86-XARB</text>
  <rect x="42" y="54" width="36" height="3" rx="1.5" fill="var(--subtle)" opacity=".5"/>
  <rect x="46" y="70" width="28" height="8" rx="2" fill="var(--surface)"/>
  <rect x="36" y="78" width="48" height="3" rx="1.5" fill="var(--surface)"/>
</svg>`;
}

/**
 * A phone reading the code from the screen.
 *
 * The QR code is drawn as a suggestion — a few blocks and three corner markers — rather than as a
 * real scannable code. A real one on a marketing page would be scanned by someone, and it would
 * lead nowhere useful.
 */
export function phoneScanning() {
  return `
<svg viewBox="0 0 120 84" role="img" aria-hidden="true" class="art">
  <rect x="8" y="14" width="52" height="46" rx="4" fill="var(--raised)" stroke="var(--line)"/>
  <g fill="var(--gold)" opacity=".85">
    <rect x="16" y="22" width="9" height="9" rx="1"/>
    <rect x="43" y="22" width="9" height="9" rx="1"/>
    <rect x="16" y="43" width="9" height="9" rx="1"/>
    <rect x="30" y="24" width="4" height="4"/><rect x="36" y="30" width="4" height="4"/>
    <rect x="30" y="36" width="4" height="4"/><rect x="43" y="38" width="4" height="4"/>
    <rect x="36" y="44" width="4" height="4"/><rect x="48" y="46" width="4" height="4"/>
    <rect x="30" y="50" width="4" height="4"/><rect x="43" y="52" width="4" height="4"/>
  </g>
  <rect x="76" y="6" width="34" height="62" rx="6" fill="var(--surface)" stroke="var(--line)"/>
  <rect x="80" y="14" width="26" height="44" rx="2" fill="var(--canvas)"/>
  <rect x="88" y="9" width="10" height="2" rx="1" fill="var(--line)"/>
  <g stroke="var(--gold)" stroke-width="1.6" fill="none" stroke-linecap="round">
    <path d="M84 22v-4h4"/><path d="M102 22v-4h-4"/>
    <path d="M84 50v4h4"/><path d="M102 50v4h-4"/>
  </g>
  <path d="M64 34h8" stroke="var(--gold)" stroke-width="1.4" stroke-dasharray="2 2" opacity=".7"/>
</svg>`;
}

/** The card payment. A card and a tick — the whole of what happens at this step. */
export function cardPayment() {
  return `
<svg viewBox="0 0 120 84" role="img" aria-hidden="true" class="art">
  <rect x="16" y="20" width="76" height="48" rx="6" fill="var(--raised)" stroke="var(--line)"/>
  <rect x="16" y="30" width="76" height="9" fill="var(--surface)"/>
  <rect x="24" y="48" width="22" height="4" rx="2" fill="var(--subtle)" opacity=".6"/>
  <rect x="24" y="56" width="34" height="3" rx="1.5" fill="var(--subtle)" opacity=".35"/>
  <rect x="72" y="46" width="14" height="10" rx="2" fill="var(--gold)" opacity=".25"/>
  <circle cx="88" cy="58" r="14" fill="var(--canvas)"/>
  <circle cx="88" cy="58" r="11" fill="none" stroke="var(--ok)" stroke-width="2"/>
  <path d="M83 58l4 4 7-8" fill="none" stroke="var(--ok)" stroke-width="2.4"
        stroke-linecap="round" stroke-linejoin="round"/>
</svg>`;
}

/** The app, unlocked. Posters in a grid: what was bought, not an abstract padlock. */
export function unlockedApp() {
  return `
<svg viewBox="0 0 120 84" role="img" aria-hidden="true" class="art">
  <rect x="4" y="4" width="112" height="66" rx="6" fill="var(--raised)" stroke="var(--line)"/>
  <rect x="4" y="4" width="112" height="12" rx="6" fill="var(--surface)"/>
  <rect x="10" y="8.5" width="18" height="3" rx="1.5" fill="var(--gold)" opacity=".8"/>
  <g fill="var(--surface)">
    <rect x="10" y="22" width="22" height="30" rx="2"/>
    <rect x="36" y="22" width="22" height="30" rx="2"/>
    <rect x="62" y="22" width="22" height="30" rx="2"/>
    <rect x="88" y="22" width="22" height="30" rx="2"/>
  </g>
  <g fill="var(--gold)" opacity=".28">
    <rect x="10" y="22" width="22" height="30" rx="2"/>
    <rect x="62" y="22" width="22" height="30" rx="2"/>
  </g>
  <g fill="var(--subtle)" opacity=".45">
    <rect x="10" y="56" width="16" height="2.5" rx="1.25"/>
    <rect x="36" y="56" width="19" height="2.5" rx="1.25"/>
    <rect x="62" y="56" width="14" height="2.5" rx="1.25"/>
    <rect x="88" y="56" width="18" height="2.5" rx="1.25"/>
  </g>
  <rect x="46" y="70" width="28" height="8" rx="2" fill="var(--surface)"/>
  <rect x="36" y="78" width="48" height="3" rx="1.5" fill="var(--surface)"/>
</svg>`;
}

/**
 * The product mark.
 *
 * Drawn rather than an image file, and deliberately simple: a rounded frame with a play triangle.
 * It reads at 24px in a header, which a detailed logo does not.
 */
export function buroMark(size = 34) {
  return `
<svg viewBox="0 0 40 40" width="${size}" height="${size}" aria-hidden="true" focusable="false">
  <rect x="2" y="2" width="36" height="36" rx="10" fill="none" stroke="var(--gold)" stroke-width="2.4"/>
  <path d="M16 13l12 7-12 7z" fill="var(--gold)"/>
</svg>`;
}
