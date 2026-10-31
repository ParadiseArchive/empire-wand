// Makes the pictures for the GitHub README and CurseForge (.github/readme/*.png, release/curseforge/media/avatar.png) in
// the style of hookdev.io: a banner, a shelf of every spell, a shelf of the wands and what goes with them, and the
// spell wheel. The pixel art comes from this mod's own textures, the fonts from the site repo next to this one; shot
// with headless Chrome. Made from Bleep's tools/readme-art.mjs. Re-run it after the textures change:
//   node tools/readme-art.mjs ../hook-development
import { spawn } from 'node:child_process';
import { createReadStream, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, resolve } from 'node:path';
import { tmpdir } from 'node:os';

const SITE = resolve(process.argv[2] || '../hook-development');
const TEX = resolve('src/main/resources/assets/empirewand/textures');
const OUT = '.github/readme';
const CHROME = process.env.CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const PORT = 8132, DEBUG = 9342;
const VERSION = readFileSync('gradle.properties', 'utf8').match(/^mod_version=(.+)$/m)[1].trim();
mkdirSync(OUT, { recursive: true });
mkdirSync('release/curseforge/media', { recursive: true });

// the spells, in the wand's order, with their colours and names (from Spell.java and the language file)
const LANG = JSON.parse(readFileSync('src/main/resources/assets/empirewand/lang/en_us.json', 'utf8'));
const SPELLS = [...readFileSync('src/main/java/io/hookdev/empirewand/Spell.java', 'utf8').matchAll(/([A-Z_]+)\(0x([0-9A-Fa-f]{6}),/g)].map(([, name, colour]) => ({
  id: name.toLowerCase(), colour: '#' + colour, name: LANG['spell.empirewand.' + name.toLowerCase()],
}));
const ORDER = readFileSync('src/main/java/io/hookdev/empirewand/Spell.java', 'utf8').match(/EMPIRE = List\.of\(([^;]+)\);/)[1].split(',').map((s) => s.trim().toLowerCase());
const BY_ID = Object.fromEntries(SPELLS.map((s) => [s.id, s]));
const WAND_SPELLS = ORDER.map((id) => BY_ID[id]);

// ---------- the pages ----------
const HOOK = '<svg width="28" height="28" viewBox="0 0 16 16" shape-rendering="crispEdges"><g fill="#2de2e6"><rect x="7" y="0" width="4" height="1"/><rect x="6" y="1" width="1" height="2"/><rect x="11" y="1" width="1" height="2"/><rect x="7" y="3" width="4" height="1"/><rect x="9" y="4" width="2" height="10"/><rect x="8" y="14" width="2" height="1"/><rect x="2" y="15" width="7" height="1"/><rect x="1" y="11" width="2" height="4"/><rect x="3" y="12" width="1" height="1"/></g></svg>';
// tinted(): a grey layer (the crystal, the ribbon) in a colour, pixel for pixel, as the game's tint does
const BASE = `<link rel="stylesheet" href="/assets/fonts.css"><style>
*{box-sizing:border-box}html,body{margin:0;background:transparent}
body{font-family:"Chakra Petch",sans-serif;color:#d9deea}
.art{background:#0a0c13;background-image:repeating-linear-gradient(0deg,rgba(255,255,255,.018) 0 1px,transparent 1px 3px);position:relative}
img{image-rendering:pixelated}
.bar{display:flex;align-items:center;gap:8px;padding:10px 14px;border-bottom:2px solid #232a3d;background:#0f121c;font:20px "VT323",monospace;color:#7c869d}
.bar i{width:12px;height:12px;display:block}
.chip{font:12px "Silkscreen",monospace;padding:6px 10px;border:2px solid;letter-spacing:.04em}
.stack{position:relative;display:inline-block}.stack img{display:block}.stack img+img{position:absolute;left:0;top:0}
</style>
<script>
async function tinted(src, hex) {
  const img = new Image(); img.src = src; await img.decode();
  const c = document.createElement('canvas'); c.width = img.width; c.height = img.height;
  const g = c.getContext('2d'); g.drawImage(img, 0, 0);
  const d = g.getImageData(0, 0, c.width, c.height), r = parseInt(hex.slice(1, 3), 16), gr = parseInt(hex.slice(3, 5), 16), b = parseInt(hex.slice(5, 7), 16);
  for (let i = 0; i < d.data.length; i += 4) { d.data[i] = d.data[i] * r / 255; d.data[i + 1] = d.data[i + 1] * gr / 255; d.data[i + 2] = d.data[i + 2] * b / 255; }
  g.putImageData(d, 0, 0); return c.toDataURL();
}
window.ready = new Promise((r) => document.addEventListener('DOMContentLoaded', r)).then(async () => { for (const e of document.querySelectorAll('[data-tint]')) e.src = await tinted(e.dataset.src, e.dataset.tint); });
</script>`;
// a wand (or a scroll): its base texture with its grey layer tinted
const item = (base, layer, colour, size) => `<span class="stack" style="width:${size}px;height:${size}px"><img src="/tex/item/${base}.png" style="width:${size}px;height:${size}px"><img data-src="/tex/item/${layer}.png" data-tint="${colour}" style="width:${size}px;height:${size}px"></span>`;
const SKINS = [['empire_wand', 'Classic', 'explode'], ['empire_wand_gold', 'Gold', 'meteor_shower'], ['empire_wand_obsidian', 'Obsidian', 'black_hole'], ['empire_wand_bone', 'Bone', 'blood_spark']];

const banner = `${BASE}<style>
.art{width:1280px;height:470px;overflow:hidden;border:2px solid #232a3d}
.grid{position:absolute;inset:0;background-image:linear-gradient(#2de2e60d 1px,transparent 1px),linear-gradient(90deg,#2de2e60d 1px,transparent 1px);background-size:32px 32px}
.left{position:absolute;left:56px;top:34px;width:640px;display:flex;flex-direction:column;gap:16px}
.logo{margin:0 0 6px;font:700 92px/0.95 "Silkscreen",monospace;color:#f2f4fa;text-shadow:7px 7px 0 #ff4f8b88;letter-spacing:.02em}
h1{margin:0;font:700 26px/1.25 "Silkscreen",monospace;color:#f2f4fa;text-shadow:3px 3px 0 #ff4f8b66}
p{margin:0;font-size:18px;color:#aeb6c9;max-width:470px}
.chips{display:flex;gap:10px;flex-wrap:wrap}
.wands{position:absolute;right:56px;top:96px;display:flex;gap:6px}
.by{position:absolute;right:28px;top:22px;display:flex;align-items:center;gap:10px;font:14px "Silkscreen",monospace;color:#f2f4fa}
</style>
<div class="art" id="art"><div class="grid"></div>
  <div class="left">
    <div class="logo">EMPIRE<br>WAND</div>
    <h1>One wand. Thirty spells.</h1>
    <p>A wand from 2013, rebuilt. Right-click picks a spell, left-click casts it, hold it to charge. Find the rest of its spells as scrolls round the world.</p>
    <div class="chips"><span class="chip" style="color:#2de2e6">Minecraft 26.1.2 · 26.3</span><span class="chip" style="color:#2de2e6">NeoForge</span><span class="chip" style="color:#ffb547">Version ${VERSION}</span><span class="chip" style="color:#ff4f8b">Open source</span></div>
  </div>
  <div class="wands">${SKINS.slice(0, 3).map(([w, , s]) => item(w, 'empire_wand_crystal', BY_ID[s].colour, 176)).join('')}</div>
  <div class="by">${HOOK}HOOK DEV</div>
</div>`;

const shelfStyle = `<style>
.art{width:1200px;border:2px solid #232a3d}
.items{display:flex;flex-wrap:wrap;align-items:flex-end;justify-content:center;gap:22px 26px;padding:28px 24px 24px}
figure{margin:0;display:flex;flex-direction:column;align-items:center;gap:10px;width:120px}
figcaption{font:20px "VT323",monospace;color:#aeb6c9;text-align:center;line-height:1}
.icon{border:2px solid #232a3d;background:#0f121c;padding:6px}
</style>`;
const bar = (file) => `<div class="bar"><i style="background:#ff4f8b"></i><i style="background:#ffb547"></i><i style="background:#2de2e6"></i><span>&nbsp;${file}</span></div>`;
const spells = `${BASE}${shelfStyle}<div class="art" id="art">${bar('spells.render')}<div class="items">${WAND_SPELLS.map((s) =>
  `<figure><img class="icon" src="/tex/gui/spell/${s.id}.png" style="width:64px;height:64px;border-color:${s.colour}66"><figcaption>${s.name}</figcaption></figure>`).join('')}</div></div>`;
const wands = `${BASE}${shelfStyle}<div class="art" id="art">${bar('wands.render')}<div class="items">${SKINS.map(([w, label, s]) =>
  `<figure>${item(w, 'empire_wand_crystal', BY_ID[s].colour, 112)}<figcaption>${label}</figcaption></figure>`).join('')}
  <figure><img src="/tex/item/spellbook.png" style="width:112px;height:112px"><figcaption>Spellbook</figcaption></figure>
  <figure>${item('spell_scroll', 'spell_scroll_ribbon', BY_ID.comet.colour, 112)}<figcaption>Spell Scroll</figcaption></figure>
  <figure>${item('spell_scroll', 'spell_scroll_ribbon', '#b02020', 112)}<figcaption>Sealed Scroll</figcaption></figure></div></div>`;
// the spell wheel, as the game draws it: two rings of buttons with their icons, the spell's name in the middle
function wheel() {
  const R = [114, 190], C = 222, n = WAND_SPELLS.length, inner = Math.ceil(n / 2);
  const cells = WAND_SPELLS.map((s, i) => {
    const outer = i >= inner, count = outer ? n - inner : inner, place = outer ? i - inner : i;
    const a = -Math.PI / 2 + 2 * Math.PI * (place + (outer ? 0.5 : 0)) / count, r = R[outer ? 1 : 0];
    const x = C + Math.cos(a) * r - 21, y = C + Math.sin(a) * r - 21, on = s.id === 'meteor_shower';
    return `<div class="cell" style="left:${x}px;top:${y}px;${on ? 'outline:3px solid ' + s.colour : ''}"><img src="/tex/gui/spell/${s.id}.png"></div>`;
  }).join('');
  return `${BASE}<style>.art{width:444px;height:444px;border:2px solid #232a3d}
.cell{position:absolute;width:42px;height:42px;background:#6b6b6b;border:2px solid #2a2a2a;box-shadow:inset 2px 2px 0 #a0a0a0,inset -2px -2px 0 #4a4a4a;display:grid;place-items:center}
.cell img{width:28px;height:28px}.name{position:absolute;left:0;right:0;top:${C - 10}px;text-align:center;font:20px "VT323",monospace;color:#2de2e6}
.keys{position:absolute;left:0;right:0;top:${C + 10}px;text-align:center;font:16px "VT323",monospace;color:#7c869d}</style>
<div class="art" id="art">${cells}<div class="name">Meteor Shower</div><div class="keys">1-5: favourites</div></div>`;
}
const avatar = `${BASE}<style>.art{width:400px;height:400px;display:grid;place-items:center;overflow:hidden}
.grid{position:absolute;inset:0;background-image:linear-gradient(#2de2e612 1px,transparent 1px),linear-gradient(90deg,#2de2e612 1px,transparent 1px);background-size:25px 25px}</style>
<div class="art" id="art"><div class="grid"></div>${item('empire_wand', 'empire_wand_crystal', BY_ID.explode.colour, 320)}</div>`;

// ---------- a little server: the site (its fonts), this mod's textures, and the pages above ----------
const TYPES = { '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };
const PAGES = { banner, spells, wands, wheel: wheel(), avatar };
const server = createServer((req, res) => {
  const p = decodeURIComponent(req.url.split('?')[0]);
  const page = p.match(/^\/__readme\/(\w+)$/);
  if (page) { res.writeHead(200, { 'content-type': 'text/html' }); res.end(`<!doctype html><meta charset="utf-8">${PAGES[page[1]]}`); return; }
  const f = p.startsWith('/tex/') ? join(TEX, p.slice(5)) : join(SITE, p);
  if (!existsSync(f)) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'content-type': TYPES[extname(f)] || 'application/octet-stream' }); createReadStream(f).pipe(res);
}).listen(PORT);

const profile = join(tmpdir(), 'empirewand-readme-chrome');
rmSync(profile, { recursive: true, force: true });
const chrome = spawn(CHROME, ['--headless=new', '--disable-gpu', `--remote-debugging-port=${DEBUG}`, `--user-data-dir=${profile}`, '--window-size=1400,1000', 'about:blank']);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let tabs;
for (let i = 0; i < 120 && !tabs?.length; i++) { try { tabs = await (await fetch(`http://127.0.0.1:${DEBUG}/json`)).json(); } catch {} await sleep(250); }
const ws = new WebSocket(tabs.find((t) => t.type === 'page').webSocketDebuggerUrl);
await new Promise((r) => (ws.onopen = r));
let id = 0; const pend = {};
ws.onmessage = (e) => { const m = JSON.parse(e.data); if (m.id && pend[m.id]) { pend[m.id](m); delete pend[m.id]; } };
const send = (method, params = {}) => new Promise((r) => { const i = ++id; pend[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
const ev = async (x) => (await send('Runtime.evaluate', { expression: x, returnByValue: true, awaitPromise: true })).result.result.value;
await send('Page.enable');

async function shoot(name, file, scale) {
  await send('Emulation.setDeviceMetricsOverride', { width: 1400, height: 1000, deviceScaleFactor: scale, mobile: false });
  await send('Page.navigate', { url: `http://127.0.0.1:${PORT}/__readme/${name}` });
  await sleep(1200);
  await ev('Promise.all([document.fonts.ready, window.ready]).then(()=>true)');
  await sleep(300);
  const box = await ev(`(()=>{const r=document.querySelector('#art').getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height}})()`);
  const shot = await send('Page.captureScreenshot', { format: 'png', clip: { ...box, scale: 1 }, captureBeyondViewport: true });
  writeFileSync(file, Buffer.from(shot.result.data, 'base64'));
  console.log('wrote', file, Math.round(box.width * scale), 'x', Math.round(box.height * scale));
}

await shoot('banner', join(OUT, 'banner.png'), 2);
await shoot('spells', join(OUT, 'shelf-spells.png'), 2);
await shoot('wands', join(OUT, 'shelf-wands.png'), 2);
await shoot('wheel', join(OUT, 'wheel.png'), 2);
await shoot('avatar', 'release/curseforge/media/avatar.png', 1);

chrome.kill(); server.close(); process.exit(0);
