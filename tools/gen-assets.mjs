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
// a texture drawn with set(x, y, colour) and rect(x, y, w, h, colour)
function texture(path, w, h, draw) {
  const px = Array.from({ length: h }, () => Array(w).fill(null));
  const set = (x, y, c) => { if (x >= 0 && x < w && y >= 0 && y < h) px[y][x] = c; };
  const rect = (x, y, rw, rh, c) => { for (let j = y; j < y + rh; j++) for (let i = x; i < x + rw; i++) set(i, j, c); };
  draw({ set, rect });
  write(join(ASSETS, 'textures', path + '.png'), png(px));
}

// ---------- the wands ----------
// Each wand: a flat icon (for the inventory, the ground and item frames) in two layers, the rod and a grey crystal
// that takes the spell's colour; and a 3D wand in the hand: the same diagonal, built from boxes. The crystal's colour
// comes from the wand's custom model data (WandItem.select), its first spell's colour by default.
const WANDS = {
  empire_wand: {
    first: 0xF4F4F4,
    icon: [
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
    ],
    rod: { g: '#b8862a', G: '#ffd23f', d: '#2a1c18', D: '#4a332a', k: '#120c0a' },
    crystal: { r: '#6e6e6e', R: '#c4c4c4', W: '#ffffff' },
    // the 3D wand's colours: handle, band (and pommel), grip
    handle: ['#2a1c18', '#4a332a'], band: ['#b8862a', '#ffd23f'], grip: ['#5a1a1a', '#7a2626'],
  },
};
for (const [name, wand] of Object.entries(WANDS)) {
  picture('item/' + name, wand.icon, wand.rod);
  picture('item/' + name + '_crystal', wand.icon, wand.crystal);
  // the 3D wand's texture: handle (x 0-1), band (2-3), grip (4-5), a grey crystal (8-15, 0-7)
  texture('item/' + name + '_3d', 16, 16, ({ set, rect }) => {
    for (let y = 0; y < 16; y++) {
      set(0, y, wand.handle[y % 3 === 0 ? 0 : 1]); set(1, y, wand.handle[0]);
      set(2, y, wand.band[0]); set(3, y, wand.band[1]);
      set(4, y, wand.grip[y % 2]); set(5, y, wand.grip[(y + 1) % 2]);
    }
    rect(8, 0, 8, 8, '#a8a8a8');
    for (let i = 0; i < 8; i++) { set(8 + i, i, '#ffffff'); set(15 - i, i, '#d8d8d8'); set(8, i, '#6e6e6e'); set(8 + i, 7, '#6e6e6e'); }
    set(10, 2, '#ffffff'); set(11, 2, '#ffffff'); set(10, 3, '#ffffff');
  });
  const tex = 'empirewand:item/' + name + '_3d';
  const faces = (uv, tint) => Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((f) => [f, { uv, texture: '#wand', ...(tint ? { tintindex: 1 } : {}) }]));
  const tilt = { origin: [8, 8, 8], axis: 'z', angle: -45 };
  json(join(ASSETS, 'models/item', name + '.json'), { parent: 'minecraft:item/handheld', textures: { layer0: 'empirewand:item/' + name, layer1: 'empirewand:item/' + name + '_crystal' } });
  json(join(ASSETS, 'models/item', name + '_3d.json'), {
    textures: { wand: tex, particle: 'empirewand:item/' + name },
    elements: [
      { from: [7.25, 1, 7.25], to: [8.75, 2, 8.75], rotation: tilt, faces: faces([2, 0, 4, 1]) },            // the pommel
      { from: [7.5, 2, 7.5], to: [8.5, 12, 8.5], rotation: tilt, faces: faces([0, 2, 1, 12]) },             // the handle
      { from: [7.35, 3, 7.35], to: [8.65, 6, 8.65], rotation: tilt, faces: faces([4, 0, 6, 3]) },           // the grip
      { from: [7.25, 11.5, 7.25], to: [8.75, 12.5, 8.75], rotation: tilt, faces: faces([2, 4, 4, 5]) },     // the band
      { from: [7, 12.5, 7], to: [9, 15.5, 9], rotation: tilt, faces: faces([8, 0, 16, 8], true) },          // the crystal
    ],
    display: { // where a held stick sits (vanilla item/handheld), for a wand lying the same way
      thirdperson_righthand: { rotation: [0, -90, 55], translation: [0, 4, 0.5], scale: [0.85, 0.85, 0.85] },
      thirdperson_lefthand: { rotation: [0, 90, -55], translation: [0, 4, 0.5], scale: [0.85, 0.85, 0.85] },
      firstperson_righthand: { rotation: [0, -90, 25], translation: [1.13, 3.2, 1.13], scale: [0.68, 0.68, 0.68] },
      firstperson_lefthand: { rotation: [0, 90, -25], translation: [1.13, 3.2, 1.13], scale: [0.68, 0.68, 0.68] },
      head: { rotation: [0, 180, 0], translation: [0, 13, 7], scale: [1, 1, 1] },
    },
  });
  const tints = [{ type: 'minecraft:constant', value: -1 }, { type: 'minecraft:custom_model_data', index: 0, default: wand.first }];
  json(join(ASSETS, 'items', name + '.json'), {
    model: {
      type: 'minecraft:select', property: 'minecraft:display_context',
      cases: [{ when: ['gui', 'ground', 'fixed'], model: { type: 'minecraft:model', model: 'empirewand:item/' + name, tints } }],
      fallback: { type: 'minecraft:model', model: 'empirewand:item/' + name + '_3d', tints },
    },
  });
}

// ---------- spell icons for the wheel: 8 x 8 pictures, drawn twice the size, in the spell's colour ----------
// C the spell's colour, D a darker shade of it, W white
const SPELL_COLOURS = {
  explode: 0xF4F4F4, lightning_arrow: 0x00FFFF, escape: 0xFF5A1F, launch: 0x5A4BFF, leap: 0xB45CFF, confuse: 0x9A2A9A, capture: 0x3FE0A8,
  little_spark: 0xFFF27A, comet: 0xFFA500, spark: 0xFF3B3B, jump: 0xFF8C1A, poison_wave: 0x35D04A, explosive: 0x6A6A6A, blood_block: 0xC0102A,
  lightning_storm: 0x4FC8FF, blood_wave: 0xB00010, blood_spark: 0xD0182E,
  meteor_shower: 0xFF6A00, chain_lightning: 0x9FD8FF, earthquake: 0x8B5A2B, tornado: 0xC8D0D8,
  ice_wave: 0x9FE8FF, freeze: 0xBFF4FF, blizzard: 0xE8F8FF, blink: 0x7A3CFF, black_hole: 0x7A3AB0, levitate: 0xD8B0FF,
  roots: 0x6B8E23, spores: 0x9ACD32, overgrowth: 0x2E8B57,
};
const WAVE = ['........', '..CC....', '.C..C..C', 'C....CC.', '........', '..CC....', '.C..C..C', 'C....CC.'];
const BOLT = ['....CCC.', '...CCC..', '..CCC...', '.CCCCCC.', '....CC..', '...CC...', '..CC....', '.C......'];
const ICONS = {
  explode: ['..W..W..', '.W.CC.W.', '..CWWC..', 'WCWWWWCW', 'WCWWWWCW', '..CWWC..', '.W.CC.W.', '..W..W..'],
  lightning_arrow: ['....CCCC', '......CC', '.....C.C', '....C..C', '..WC....', '.WCW....', 'W.W.....', 'W.......'],
  escape: ['....CCCC', '......CC', '.....C.C', '....C..C', '...C....', 'WW......', 'WCW.....', '.W......'],
  launch: ['...CC...', '..CCCC..', '.C.CC.C.', '...CC...', '...CC...', '...CC...', '........', 'WWWWWWWW'],
  leap: ['.....CC.', '....CCCC', '...C.CC.', '..C.....', '.C......', 'W.......', 'W.W.....', '........'],
  confuse: ['.CCCCC..', 'C.....C.', 'C.CCC.C.', 'C.C.C.C.', 'C.C...C.', 'C.CCCCC.', 'C.......', '.CCCCCC.'],
  capture: ['C.C.C...', 'C.C.C.C.', 'CCCCC.C.', 'CCCCCCC.', 'CCCCCC..', '.CCCC...', '..CC....', '..CC....'],
  little_spark: ['........', '...C....', '..CWC...', '.CWWWC..', '..CWC...', '...C....', '......C.', '........'],
  comet: ['......CC', '.....CWC', '....CWWC', '...CCCC.', '..D.C...', '.D.D....', 'D.D.....', '.D......'],
  spark: BOLT,
  jump: ['...CC...', '..CCCC..', '.C.CC.C.', '...CC...', '..CCCC..', '.C.CC.C.', '...CC...', 'W.WWWW.W'],
  poison_wave: WAVE,
  explosive: ['......W.', '.....W..', '...CCC..', '..CCCCC.', '.CCWCCCC', '.CWCCCCC', '.CCCCCCC', '..CCCCC.'],
  blood_block: ['...CC...', '.CCWWCC.', 'CWWWWWWC', 'CCCWWCCD', 'CCCCCDDD', 'CCCCCDDD', '.CCCCDD.', '...CD...'],
  lightning_storm: ['.WWWW...', 'WWWWWWW.', 'WWWWWWWW', '...C....', '..CC....', '.CCCC...', '...C....', '..C.....'],
  blood_wave: WAVE,
  blood_spark: BOLT,
  meteor_shower: ['C....C..', '.C....C.', '..W....W', '........', 'C....C..', '.C....C.', '..W....W', '........'],
  chain_lightning: ['W.......', '.C......', '..C...W.', '...C.C..', '....C...', '.....C..', '......C.', '.......W'],
  earthquake: ['........', '........', '........', 'C.C..C.C', '.CCCCCC.', 'CDDCCDDC', 'DDDDDDDD', 'DDDDDDDD'],
  tornado: ['CCCCCCCC', '.CCCCCC.', '..CCCCC.', '...CCC..', '...CCC..', '....CC..', '....C...', '...C....'],
  ice_wave: WAVE,
  freeze: ['...C....', '.C.C.C..', '..CCC...', 'CCCWCCC.', '..CCC...', '.C.C.C..', '...C....', '........'],
  blizzard: ['.C...C..', '...C...C', 'C...C...', '..C...C.', '.C...C..', '...C...C', 'C...C...', '..C...C.'],
  blink: ['W.....C.', '......CC', '.W..CCCC', '......CC', 'W.....C.', '........', '.W......', '........'],
  black_hole: ['..DDDD..', '.DCCCCD.', 'DCC..CCD', 'DC.WW.CD', 'DC.WW.CD', 'DCC..CCD', '.DCCCCD.', '..DDDD..'],
  levitate: ['...CC...', '..CCCC..', '...CC...', '........', '.CCCCCC.', '.CDDDDC.', '.CDDDDC.', '.CCCCCC.'],
  roots: ['..C..C..', '..C..C..', '.C.CC.C.', '.C.CC.C.', 'C..CC..C', '..C..C..', '.C....C.', 'C......C'],
  spores: ['.C...C..', '...C...C', '.C.C.C..', '..CDC...', '.CDDDC..', '..CDC...', '...C....', '..C.C...'],
  overgrowth: ['...C....', '..CCC...', '.CCCCC..', '..CCC.C.', '...D.CCC', '...D..D.', '...D..D.', 'DDDDDDDD'],
};
const hex = (n) => '#' + n.toString(16).padStart(6, '0');
const darker = (n) => hex(((n >> 16 & 255) * 0.6 << 16) | ((n >> 8 & 255) * 0.6 << 8) | ((n & 255) * 0.6));
for (const [id, rows] of Object.entries(ICONS)) {
  const key = { C: hex(SPELL_COLOURS[id]), D: darker(SPELL_COLOURS[id]), W: '#ffffff' };
  picture('gui/spell/' + id, rows.flatMap((row) => { const wide = [...row].flatMap((c) => [c, c]).join(''); return [wide, wide]; }), key);
}

// ---------- the spell scroll: a parchment roll (layer 0) with a ribbon in the spell's colour (layer 1) ----------
const SCROLL = [
  '................',
  '...PPPPPPPPP....',
  '..PpPPPPPPPpP...',
  '..PppppppppPP...',
  '...PPPPPPPPP....',
  '....PlllllP.....',
  '....PllllllP....',
  '....PRRRRRRP....',
  '....PRRWRRRP....',
  '....PRRRRRRP....',
  '....PllllllP....',
  '....PlllllP.....',
  '...PPPPPPPPP....',
  '..PpPPPPPPPpP...',
  '..PppppppppPP...',
  '...PPPPPPPPP....',
];
picture('item/spell_scroll', SCROLL, { P: '#d9c79a', p: '#a8935e', l: '#efe2bd' });
picture('item/spell_scroll_ribbon', SCROLL, { R: '#b0b0b0', W: '#ffffff' });
json(join(ASSETS, 'models/item/spell_scroll.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'empirewand:item/spell_scroll', layer1: 'empirewand:item/spell_scroll_ribbon' } });
json(join(ASSETS, 'items/spell_scroll.json'), { model: { type: 'minecraft:model', model: 'empirewand:item/spell_scroll',
  tints: [{ type: 'minecraft:constant', value: -1 }, { type: 'minecraft:custom_model_data', index: 0, default: 0xB02020 }] } }); // sealed: red wax
json(join(DATA, 'loot_modifiers/scrolls.json'), { type: 'empirewand:scrolls' });
// a librarian (level 4) sells a sealed scroll: a spell at random when it is opened (no item functions: 26.1 and 26.3 write those differently)
json(join(DATA, 'villager_trade/librarian/sealed_scroll.json'), { gives: { id: 'empirewand:spell_scroll' }, wants: { id: 'minecraft:emerald', count: 20 },
  additional_wants: { id: 'minecraft:book' }, max_uses: 4, reputation_discount: 0.05 });
json(join(ROOT, 'data/minecraft/tags/villager_trade/librarian/level_4.json'), { replace: false, values: ['empirewand:librarian/sealed_scroll'] });

// ---------- the spellbook: a dark red book with gold corners and a gold star ----------
picture('item/spellbook', [
  '................',
  '..KKKKKKKKKKK...',
  '..KRRRRRRRRRRK..',
  '..KRGRRRRRRGRKW.',
  '..KRRRRRRRRRRKW.',
  '..KRRRRGRRRRRKW.',
  '..KRRRGGGRRRRKW.',
  '..KRRGGYGGRRRKW.',
  '..KRRRGGGRRRRKW.',
  '..KRRRRGRRRRRKW.',
  '..KRRRRRRRRRRKW.',
  '..KRGRRRRRRGRKW.',
  '..KRRRRRRRRRRKW.',
  '..KKKKKKKKKKKKW.',
  '...WWWWWWWWWWW..',
  '................',
], { K: '#2a0a0e', R: '#8a1520', G: '#d9a93a', Y: '#ffd23f', W: '#efe2bd' });
json(join(ASSETS, 'models/item/spellbook.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'empirewand:item/spellbook' } });
json(join(ASSETS, 'items/spellbook.json'), { model: { type: 'minecraft:model', model: 'empirewand:item/spellbook' } });

// ---------- advancements: the root comes with the wand, the others are awarded in Awards.java ----------
const advancement = (id, parent, icon, title, description, frame, criteria) => json(join(DATA, 'advancement', id + '.json'), {
  ...(parent ? { parent: 'empirewand:' + parent } : {}),
  display: { icon: { id: icon }, title: { translate: 'advancements.empirewand.' + id + '.title' }, description: { translate: 'advancements.empirewand.' + id + '.description' },
    frame, show_toast: true, announce_to_chat: parent !== null, ...(parent ? {} : { background: 'minecraft:gui/advancements/backgrounds/stone' }) },
  criteria: criteria || { done: { trigger: 'minecraft:impossible' } },
});
advancement('root', null, 'empirewand:empire_wand', 'Empire Wand', 'Get an Empire Wand', 'task',
  { wand: { trigger: 'minecraft:inventory_changed', conditions: { items: [{ items: 'empirewand:empire_wand' }] } } });
advancement('bookworm', 'root', 'empirewand:spell_scroll', null, null, 'task');
advancement('apprentice', 'bookworm', 'empirewand:spellbook', null, null, 'goal');
advancement('archmage', 'apprentice', 'empirewand:empire_wand', null, null, 'challenge');
advancement('pyromaniac', 'root', 'minecraft:tnt', null, null, 'goal');
advancement('end_of_days', 'root', 'minecraft:dragon_egg', null, null, 'challenge');

// ---------- recipes (owner, 31 Oct 2026: the wands are crafted) ----------
const shaped = (name, pattern, key) => json(join(DATA, 'recipe', name + '.json'),
  { type: 'minecraft:crafting_shaped', category: 'equipment', key, pattern, result: { count: 1, id: 'empirewand:' + name } });
json(join(DATA, 'recipe/spellbook.json'), { type: 'minecraft:crafting_shapeless', category: 'misc', ingredients: ['minecraft:book', 'minecraft:blaze_powder', 'minecraft:lapis_lazuli'], result: { count: 1, id: 'empirewand:spellbook' } });
shaped('empire_wand', ['  D', ' B ', 'G  '], { D: 'minecraft:diamond', B: 'minecraft:blaze_rod', G: 'minecraft:gold_ingot' });

// ---------- English ----------
const SPELLS = {
  explode: 'Explode', lightning_arrow: 'Lightning Arrow', escape: 'Escape', launch: 'Launch', leap: 'Leap',
  confuse: 'Confuse', capture: 'Capture', little_spark: 'Little Spark', comet: 'Comet', spark: 'Spark', jump: 'Jump',
  poison_wave: 'Poison Wave', explosive: 'Explosive', blood_block: 'Blood Block', lightning_storm: 'Lightning Storm',
  blood_wave: 'Blood Wave', blood_spark: 'Blood Spark',
  meteor_shower: 'Meteor Shower', chain_lightning: 'Chain Lightning', earthquake: 'Earthquake', tornado: 'Tornado',
  ice_wave: 'Ice Wave', freeze: 'Freeze', blizzard: 'Blizzard', blink: 'Blink', black_hole: 'Black Hole', levitate: 'Levitate',
  roots: 'Roots', spores: 'Spores', overgrowth: 'Overgrowth',
};
json(join(ASSETS, 'lang/en_us.json'), {
  'itemGroup.empirewand': 'Empire Wand',
  'item.empirewand.empire_wand': 'Empire Wand',
  'message.empirewand.selected': '[X] Selected: %s',
  'item.empirewand.spellbook': 'Spellbook',
  'advancements.empirewand.root.title': 'Empire Wand', 'advancements.empirewand.root.description': 'Get an Empire Wand',
  'advancements.empirewand.bookworm.title': "Bookworm", 'advancements.empirewand.bookworm.description': "Learn a spell from a scroll",
  'advancements.empirewand.apprentice.title': "Apprentice", 'advancements.empirewand.apprentice.description': "Have a wand that knows ten spells",
  'advancements.empirewand.archmage.title': "Archmage", 'advancements.empirewand.archmage.description': "Have a wand that knows all thirty spells",
  'advancements.empirewand.pyromaniac.title': "Pyromaniac", 'advancements.empirewand.pyromaniac.description': "Blow things up a hundred times with your wand",
  'advancements.empirewand.end_of_days.title': "The End of Days", 'advancements.empirewand.end_of_days.description': "Have a Black Hole and a Tornado going at the same time",
  'screen.empirewand.spellbook': 'Spellbook', 'screen.empirewand.spellbook.title': 'Spellbook: %s of %s known',
  'screen.empirewand.spellbook.found': 'Found in %s', 'screen.empirewand.spellbook.more': ', and more',
  'spell.empirewand.explode.desc': "A blast of 5 where you look, up to 50 blocks away.",
  'spell.empirewand.lightning_arrow.desc': "An arrow with a sparkling trail; where it stops, lightning strikes.",
  'spell.empirewand.escape.desc': "Up and away, then down hard, while the spot you left blows up.",
  'spell.empirewand.launch.desc': "Everything near what you look at flies up in the air.",
  'spell.empirewand.leap.desc': "You fly where you look, and land without a scratch.",
  'spell.empirewand.confuse.desc': "Everything near what you look at gets dizzy for 25 seconds.",
  'spell.empirewand.capture.desc': "Picks up a creature and carries it above your head. Cast again to throw it.",
  'spell.empirewand.little_spark.desc': "A zap of 5 damage where you look. Stronger than Spark.",
  'spell.empirewand.comet.desc': "A very fast fireball with a big orange burst.",
  'spell.empirewand.spark.desc': "A zap of 3 damage where you look.",
  'spell.empirewand.jump.desc': "Straight up, and a blast of 7 where you land. You feel nothing.",
  'spell.empirewand.poison_wave.desc': "A wave along your look that poisons all it passes.",
  'spell.empirewand.explosive.desc': "A fireball that blows up with fire where it hits.",
  'spell.empirewand.blood_block.desc': "First click: a block turns to redstone. Second: it flies and explodes.",
  'spell.empirewand.lightning_storm.desc': "A ring of fireworks, then lightning on everything near you.",
  'spell.empirewand.meteor_shower.desc': "Twelve fireballs out of the sky round where you look.",
  'spell.empirewand.chain_lightning.desc': "A bolt that leaps from creature to creature, five times.",
  'spell.empirewand.earthquake.desc': "Rings of ground jump up round you and throw what stands on them.",
  'spell.empirewand.tornado.desc': "A whirlwind that crosses the land and flings what it picks up.",
  'spell.empirewand.blood_wave.desc': "A wave of blood along your look, with a zap every third block.",
  'spell.empirewand.blood_spark.desc': "A dark red zap of 3 damage where you look.",
  'spell.empirewand.ice_wave.desc': "A wave of cold: water freezes, creatures slow down and freeze.",
  'spell.empirewand.freeze.desc': "What you look at freezes solid and cannot move.",
  'spell.empirewand.blizzard.desc': "Eight seconds of snowstorm where you look.",
  'spell.empirewand.blink.desc': "You are where you look, in a flash.",
  'spell.empirewand.black_hole.desc': "A dark swirl pulls everything in for four seconds, then bursts.",
  'spell.empirewand.levitate.desc': "Creatures and the ground where you look float up for a while.",
  'spell.empirewand.roots.desc': "Roots hold everything near what you look at: no moving, no jumping.",
  'spell.empirewand.spores.desc': "A cloud of poisonous spores where you look.",
  'spell.empirewand.overgrowth.desc': "Everything round where you look grows: grass, flowers, trees.",
  'place.empirewand.chests.simple_dungeon': "dungeons",
  'place.empirewand.chests.abandoned_mineshaft': "mineshafts",
  'place.empirewand.chests.desert_pyramid': "desert temples",
  'place.empirewand.chests.jungle_temple': "jungle temples",
  'place.empirewand.chests.stronghold_library': "stronghold libraries",
  'place.empirewand.chests.stronghold_corridor': "strongholds",
  'place.empirewand.chests.ancient_city': "ancient cities",
  'place.empirewand.chests.woodland_mansion': "woodland mansions",
  'place.empirewand.chests.end_city_treasure': "End cities",
  'place.empirewand.chests.bastion_treasure': "bastions",
  'place.empirewand.chests.nether_bridge': "Nether fortresses",
  'place.empirewand.chests.pillager_outpost': "pillager outposts",
  'place.empirewand.chests.igloo_chest': "igloos",
  'place.empirewand.chests.ruined_portal': "ruined portals",
  'place.empirewand.chests.trial_chambers.reward': "trial vaults",
  'place.empirewand.chests.trial_chambers.reward_ominous': "ominous vaults",
  'place.empirewand.archaeology.desert_pyramid': "suspicious sand",
  'place.empirewand.archaeology.desert_well': "desert wells",
  'place.empirewand.archaeology.ocean_ruin_cold': "cold ocean ruins",
  'place.empirewand.archaeology.ocean_ruin_warm': "warm ocean ruins",
  'place.empirewand.archaeology.trail_ruins_common': "trail ruins",
  'place.empirewand.archaeology.trail_ruins_rare': "trail ruins",
  'place.empirewand.entities.evoker': "evokers",
  'place.empirewand.entities.illusioner': "illusioners",
  'place.empirewand.entities.witch': "witches",
  'place.empirewand.entities.wither': "the Wither",
  'place.empirewand.ender_dragon': "the Ender Dragon",
  'place.empirewand.librarian': "librarians",
  'item.empirewand.spell_scroll': 'Spell Scroll', 'item.empirewand.sealed_scroll': 'Sealed Spell Scroll',
  'message.empirewand.seal': '[X] The seal breaks: %s!', 'item.empirewand.spell_scroll.of': 'Spell Scroll: %s',
  'message.empirewand.learned': '[X] Your %s learned %s!',
  'message.empirewand.cannot_learn': '[X] None of your wands can learn %s (or it knows it already).',
  'screen.empirewand.unknown': '??? (find its scroll)',
  'key.empirewand.wheel': 'Spell wheel (with a wand in hand)',
  'screen.empirewand.wheel': 'Spell wheel',
  ...Object.fromEntries(Object.entries(SPELLS).map(([id, name]) => ['spell.empirewand.' + id, name])),
});
console.log('assets written');
