// Writes what the Empire Wand docs on hookdev.io need into the site repo next to this one: the spell icons, the wand
// in its skins (its grey crystal tinted, as the game does), the spellbook, scrolls and the spawn egg, the vanilla items
// in its recipes (cut from the Minecraft client jar: never stand-ins), the README pictures, and spells.js (every spell
// with its name, what it does, its cooldown and where its scroll lies, from the mod's own sources).
//   node tools/site-art.mjs ../hook-development
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { deflateSync, inflateRawSync, inflateSync } from 'node:zlib';

const SITE = resolve(process.argv[2] || '../hook-development');
const OUT = join(SITE, 'projects/minecraft-mods/empire-wand/img');
const TEX = 'src/main/resources/assets/empirewand/textures';
const mk = (f) => mkdirSync(dirname(f), { recursive: true });

// ---------- PNG in and out (RGBA, 8 bit) ----------
function readPng(buf) {
  let o = 8, w, h, type, idat = [];
  while (o < buf.length) {
    const len = buf.readUInt32BE(o), t = buf.toString('ascii', o + 4, o + 8);
    if (t === 'IHDR') { w = buf.readUInt32BE(o + 8); h = buf.readUInt32BE(o + 12); type = buf[o + 17]; }
    if (t === 'IDAT') idat.push(buf.subarray(o + 8, o + 8 + len));
    o += 12 + len;
  }
  const bpp = type === 6 ? 4 : type === 2 ? 3 : type === 4 ? 2 : 1, raw = inflateSync(Buffer.concat(idat)), stride = w * bpp;
  const px = Buffer.alloc(w * h * 4), line = Buffer.alloc(stride), prev = Buffer.alloc(stride);
  let p = 0;
  for (let y = 0; y < h; y++) {
    const f = raw[p++];
    for (let x = 0; x < stride; x++) {
      const a = x >= bpp ? line[x - bpp] : 0, b = prev[x], c = x >= bpp ? prev[x - bpp] : 0;
      let v = raw[p++];
      if (f === 1) v += a; else if (f === 2) v += b; else if (f === 3) v += (a + b) >> 1;
      else if (f === 4) { const q = a + b - c, pa = Math.abs(q - a), pb = Math.abs(q - b), pc = Math.abs(q - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
      line[x] = v & 255;
    }
    for (let x = 0; x < w; x++) {
      const s = x * bpp, d = (y * w + x) * 4;
      if (bpp >= 3) { px[d] = line[s]; px[d + 1] = line[s + 1]; px[d + 2] = line[s + 2]; px[d + 3] = bpp === 4 ? line[s + 3] : 255; }
      else { px[d] = px[d + 1] = px[d + 2] = line[s]; px[d + 3] = bpp === 2 ? line[s + 1] : 255; }
    }
    line.copy(prev);
  }
  return { w, h, px };
}
const T = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = (b) => { let c = 0xffffffff; for (const x of b) c = T[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
function writePng(file, { w, h, px }) {
  const raw = Buffer.alloc(h * (1 + w * 4));
  for (let y = 0; y < h; y++) px.copy(raw, y * (1 + w * 4) + 1, y * w * 4, (y + 1) * w * 4);
  const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 6;
  mk(file);
  writeFileSync(file, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ih), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]));
}
/** A base texture with a grey layer on top, tinted (the game's custom model data tint). */
function layered(base, layer, rgb) {
  const a = readPng(readFileSync(join(TEX, base))), b = readPng(readFileSync(join(TEX, layer)));
  for (let i = 0; i < a.px.length; i += 4) {
    if (!b.px[i + 3]) continue;
    a.px[i] = b.px[i] * (rgb >> 16 & 255) / 255; a.px[i + 1] = b.px[i + 1] * (rgb >> 8 & 255) / 255; a.px[i + 2] = b.px[i + 2] * (rgb & 255) / 255; a.px[i + 3] = 255;
  }
  return a;
}

// ---------- the spells, from the mod's sources ----------
const LANG = JSON.parse(readFileSync('src/main/resources/assets/empirewand/lang/en_us.json', 'utf8'));
const SPELL_SRC = readFileSync('src/main/java/io/hookdev/empirewand/Spell.java', 'utf8');
const SPELLS = Object.fromEntries([...SPELL_SRC.matchAll(/([A-Z_]+)\(0x([0-9A-Fa-f]{6}), (\d+)\)/g)].map(([, n, c, cd]) => [n, { colour: parseInt(c, 16), cooldown: +cd }]));
const ORDER = SPELL_SRC.match(/EMPIRE = List\.of\(([^;]+)\);/)[1].split(',').map((s) => s.trim());
const STARTERS = +readFileSync('src/main/java/io/hookdev/empirewand/Content.java', 'utf8').match(/new WandItem\(p, Spell\.EMPIRE, (\d+)\)/)[1];
const MOD = readFileSync('src/main/java/io/hookdev/empirewand/ScrollsModifier.java', 'utf8');
const KINDS = Object.fromEntries([...MOD.matchAll(/public static final List<Spell> ([A-Z]+) = List\.of\(([^;]+)\);/g)].map(([, k, l]) => [k, l.split(',').map((s) => s.trim().replace('Spell.', ''))]));
// the sources: chest(chance, heavy, kinds...) or new Source(chance, List.of(kinds), heavy, replace[, count])
const SOURCES = [];
for (const [, table, args] of MOD.matchAll(/entry\("([^"]+)", (chest\([^)]*\)|new Source\([^;]*?\))\)/g)) {
  const chest = args.match(/^chest\(([\d.]+)f, (true|false)(?:, )?([A-Z, ]*)\)$/);
  const src = args.match(/^new Source\(([\d.]+)f, List\.of\(([A-Z, ]*)\), (true|false), (true|false)/);
  if (chest) SOURCES.push({ key: table.replace(/\//g, '.'), chance: +chest[1], heavy: chest[2] === 'true', kinds: chest[3] ? chest[3].split(',').map((s) => s.trim()) : [] });
  else if (src) SOURCES.push({ key: table.replace(/\//g, '.'), chance: +src[1], heavy: src[3] === 'true', kinds: src[2] ? src[2].split(',').map((s) => s.trim()) : [] });
}
for (const [, path, args] of MOD.matchAll(/Map\.entry\(EmpireWand\.id\("([^"]+)"\), (chest\([^)]*\)|new Source\([^;]*?\))\)/g)) {
  const chest = args.match(/^chest\(([\d.]+)f, (true|false)/), src = args.match(/^new Source\(([\d.]+)f, List\.of\(\), (true|false)/);
  const m = chest || src;
  SOURCES.push({ key: path.replace(/\//g, '.'), chance: +m[1], heavy: m[2] === 'true', kinds: [], ours: true });
}
const FINDABLE = ORDER.slice(STARTERS);
function places(spell) {
  const found = SOURCES.filter((s) => {
    const pool = FINDABLE.filter((x) => (s.kinds.length === 0 || s.kinds.some((k) => KINDS[k].includes(x))) && (s.heavy || !KINDS.HEAVY.includes(x)));
    s.size = pool.length;
    return pool.includes(spell);
  }).sort((a, b) => b.chance / b.size - a.chance / a.size);
  const name = (k) => LANG['place.empirewand.' + k] || { 'entities.dark_mage': 'Dark Mages', 'chests.wizard_tower': 'Wizard Towers' }[k] || k;
  return [...new Set([...found.map((s) => name(s.key)), KINDS.HEAVY.includes(spell) ? 'the Ender Dragon' : 'librarians'])];
}
const FROM_2013 = ['EXPLODE', 'LIGHTNING_ARROW', 'ESCAPE', 'LAUNCH', 'LEAP', 'CONFUSE', 'CAPTURE', 'LITTLE_SPARK', 'COMET', 'SPARK', 'JUMP', 'POISON_WAVE', 'EXPLOSIVE', 'BLOOD_BLOCK', 'LIGHTNING_STORM'];
const spells = ORDER.map((n, i) => {
  const id = n.toLowerCase();
  return { id, name: LANG['spell.empirewand.' + id], desc: LANG['spell.empirewand.' + id + '.desc'], cooldown: SPELLS[n].cooldown / 20, colour: '#' + SPELLS[n].colour.toString(16).padStart(6, '0'),
    from2013: FROM_2013.includes(n), heavy: KINDS.HEAVY.includes(n), starter: i < STARTERS, places: i < STARTERS ? [] : places(n) };
});
const PLACES = SOURCES.map((s) => ({ name: LANG['place.empirewand.' + s.key] || { 'entities.dark_mage': 'Dark Mages', 'chests.wizard_tower': 'Wizard Towers' }[s.key] || s.key,
  chance: Math.round(s.chance * 100), heavy: s.heavy, kinds: s.kinds.map((k) => k.toLowerCase()), what: s.key.split('.')[0] }));
mk(join(OUT, 'spells.js'));
writeFileSync(join(OUT, 'spells.js'), '// generated by empirewand/tools/site-art.mjs: never edit by hand\nwindow.EW_SPELLS = ' + JSON.stringify(spells, null, 1) + ';\nwindow.EW_PLACES = '
  + JSON.stringify(PLACES, null, 1) + ';\nwindow.EW_KINDS = ' + JSON.stringify(Object.fromEntries(Object.entries(KINDS).map(([k, l]) => [k.toLowerCase(), l.map((x) => LANG['spell.empirewand.' + x.toLowerCase()])]))) + ';\n');
console.log('wrote spells.js:', spells.length, 'spells,', PLACES.length, 'places');

// ---------- the pictures ----------
for (const f of readdirSync(join(TEX, 'gui/spell'))) { mk(join(OUT, 'spell', f)); copyFileSync(join(TEX, 'gui/spell', f), join(OUT, 'spell', f)); }
const SKINS = [['empire_wand', 'EXPLODE'], ['empire_wand_gold', 'METEOR_SHOWER'], ['empire_wand_obsidian', 'BLACK_HOLE'], ['empire_wand_bone', 'BLOOD_SPARK']];
for (const [w, s] of SKINS) writePng(join(OUT, 'items', w + '.png'), layered('item/' + w + '.png', 'item/empire_wand_crystal.png', SPELLS[s].colour));
writePng(join(OUT, 'items/spell_scroll.png'), layered('item/spell_scroll.png', 'item/spell_scroll_ribbon.png', SPELLS.COMET.colour));
writePng(join(OUT, 'items/sealed_scroll.png'), layered('item/spell_scroll.png', 'item/spell_scroll_ribbon.png', 0xB02020));
for (const f of ['spellbook', 'dark_mage_spawn_egg']) copyFileSync(join(TEX, 'item', f + '.png'), join(OUT, 'items', f + '.png'));
for (const f of ['banner', 'shelf-spells', 'shelf-wands', 'wheel']) copyFileSync(join('.github/readme', f + '.png'), join(OUT, f + '.png'));

// the vanilla items in the recipes, cut from the client jar
const JARS = join(homedir(), '.gradle/caches/neoformruntime/artifacts');
const JAR = join(JARS, readdirSync(JARS).filter((n) => /^minecraft_26\.3.*_client\.jar$/.test(n)).sort().pop());
const jar = readFileSync(JAR);
function fromJar(want) {
  let end = jar.length - 22; while (jar.readUInt32LE(end) !== 0x06054b50) end--;
  let o = jar.readUInt32LE(end + 16);
  for (let i = 0, n = jar.readUInt16LE(end + 10); i < n; i++) {
    const nl = jar.readUInt16LE(o + 28), el = jar.readUInt16LE(o + 30), cl = jar.readUInt16LE(o + 32), name = jar.toString('utf8', o + 46, o + 46 + nl);
    if (name === want) {
      const local = jar.readUInt32LE(o + 42), size = jar.readUInt32LE(o + 20), method = jar.readUInt16LE(o + 10);
      const start = local + 30 + jar.readUInt16LE(local + 26) + jar.readUInt16LE(local + 28), d = jar.subarray(start, start + size);
      return method === 8 ? inflateRawSync(d) : d;
    }
    o += 46 + nl + el + cl;
  }
  throw new Error('not in the jar: ' + want);
}
for (const item of ['gold_ingot', 'blaze_rod', 'diamond', 'book', 'blaze_powder', 'lapis_lazuli', 'stick']) {
  const f = join(OUT, 'items/minecraft', item + '.png'); mk(f); writeFileSync(f, fromJar('assets/minecraft/textures/item/' + item + '.png'));
}
console.log('wrote the pictures in', OUT, existsSync(OUT) ? '' : '(missing?)');
