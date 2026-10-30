// Generates Empire Wand's textures, item models, recipes and language file. Run from the project root:  node tools/gen-assets.mjs
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { deflateSync } from 'node:zlib';

const ROOT = 'src/main/resources';
const ASSETS = join(ROOT, 'assets/empirewand');
const DATA = join(ROOT, 'data/empirewand');

function write(path, content) {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, content);
}
const json = (path, value) => write(path, JSON.stringify(value, null, 2) + '\n');

// ---------- PNG writer (RGBA, no dependencies) ----------
const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}
function png(pixels) {
  const w = pixels[0].length, h = pixels.length;
  const raw = Buffer.alloc(h * (1 + w * 4));
  pixels.forEach((row, y) => row.forEach((hex, x) => {
    const o = y * (1 + w * 4) + 1 + x * 4;
    if (!hex) return;
    raw[o] = parseInt(hex.slice(1, 3), 16); raw[o + 1] = parseInt(hex.slice(3, 5), 16);
    raw[o + 2] = parseInt(hex.slice(5, 7), 16); raw[o + 3] = 255;
  }));
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
// a 16 x 16 texture drawn from rows of letters, each a colour from the key ('.' is see-through)
function picture(path, rows, key) {
  write(join(ASSETS, 'textures', path + '.png'), png(rows.map((row) => [...row].map((c) => key[c] || null))));
}

// ---------- the wands: held like a blaze rod, from bottom left to top right ----------
// Empire Wand: a dark rod with gold bands and a red crystal on top
picture('item/empire_wand', [
  '...........rRr..',
  '..........rRWRr.',
  '..........RRRRr.',
  '...........rRr..',
  '..........gG....',
  '.........gG.....',
  '........dD......',
  '.......dD.......',
  '......gG........',
  '.....dD.........',
  '....dD..........',
  '...gG...........',
  '..dD............',
  '.dD.............',
  '.k..............',
  '................',
], { r: '#8c1020', R: '#e0283c', W: '#ffb0b8', g: '#b8862a', G: '#ffd23f', d: '#2a1c18', D: '#4a332a', k: '#120c0a' });
// Blood Magic: a bone-white rod with a dark red orb dripping blood
picture('item/blood_magic', [
  '..........oOo...',
  '.........oOPOo..',
  '.........OOOOo..',
  '.........oOOo...',
  '..........ob....',
  '.........bB..o..',
  '........bB......',
  '.......bB....o..',
  '......cC........',
  '.....bB.........',
  '....bB..........',
  '...cC...........',
  '..bB............',
  '.bB.............',
  '.c..............',
  '................',
], { o: '#4a0008', O: '#9a0a1a', P: '#ff5060', b: '#cfc6b0', B: '#efe8d6', c: '#8a1520', C: '#c0202f' });

for (const name of ['empire_wand', 'blood_magic']) {
  json(join(ASSETS, 'models/item', name + '.json'), { parent: 'minecraft:item/handheld', textures: { layer0: 'empirewand:item/' + name } });
  json(join(ASSETS, 'items', name + '.json'), { model: { type: 'minecraft:model', model: 'empirewand:item/' + name } });
}

// ---------- recipes (owner, 31 Oct 2026: the wands are crafted) ----------
const shaped = (name, pattern, key) => json(join(DATA, 'recipe', name + '.json'),
  { type: 'minecraft:crafting_shaped', category: 'equipment', key, pattern, result: { count: 1, id: 'empirewand:' + name } });
shaped('empire_wand', ['  D', ' B ', 'G  '], { D: 'minecraft:diamond', B: 'minecraft:blaze_rod', G: 'minecraft:gold_ingot' });
shaped('blood_magic', ['  W', ' B ', 'R  '], { W: 'minecraft:nether_wart', B: 'minecraft:bone', R: 'minecraft:redstone_block' });

// ---------- English ----------
const SPELLS = {
  explode: 'Explode', lightning_arrow: 'Lightning Arrow', escape: 'Escape', launch: 'Launch', flamethrower: 'Flamethrower', leap: 'Leap',
  ender_egg: 'Ender Egg', confuse: 'Confuse', capture: 'Capture', little_spark: 'Little Spark', comet: 'Comet', spark: 'Spark', jump: 'Jump',
  poison_wave: 'Poison Wave', explosive: 'Explosive', blood_block: 'Blood Block', lightning_storm: 'Lightning Storm',
  blood_wave: 'Blood Wave', blood_spark: 'Blood Spark',
};
json(join(ASSETS, 'lang/en_us.json'), {
  'itemGroup.empirewand': 'Empire Wand',
  'item.empirewand.empire_wand': 'Empire Wand',
  'item.empirewand.blood_magic': 'Blood Magic',
  'message.empirewand.selected': '[X] Selected: %s',
  'message.empirewand.ender_egg': '[X] Can you fly? Sneak to steer where you look. Cast it again to get off.',
  ...Object.fromEntries(Object.entries(SPELLS).map(([id, name]) => ['spell.empirewand.' + id, name])),
});
console.log('assets written');
