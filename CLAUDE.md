# Empire Wand

The owner's Bukkit plugin "Empire" (v1.1, 2013, Minecraft 1.7.2) rebuilt as a NeoForge mod for Minecraft 26.3 and 26.1.2.
The original jar is in this folder (`empire_V1.1.jar`, ignored by git: it stays on this computer).

## Working with the owner
- Talk to the owner in **Dutch**. All mod text is **English** (owner, 31 Oct 2026: no Dutch translation).
- **Private for now** (owner, 31 Oct 2026): a local git repository only. No GitHub, no CurseForge, not on hookdev.io, until the owner says so. License: All Rights Reserved until then.
- **Privacy:** never put the owner's name, old usernames or job in the code, the repository or anything published. The 2013 jar's package name holds an old username: it never goes into this code (the mod is `io.hookdev.empirewand`, author Hook Development).
- **Secrets** (as in hook-development and Setpoint): never commit a token, key, password or .env file. `.gitignore` blocks them; `.git/hooks/pre-commit` (copied from Bleep, not in git) refuses secret-looking files.
- Owner's choices (31 Oct 2026): the wands are crafted (no commands); explosions break blocks; the firework effects stay; no cooldown, spamming spells is the whole idea.
- Do not build while the owner's Minecraft is open.

## Technical
- NeoForge MDG, Java 25, two Minecraft versions like Bleep: `./gradlew build runGameTestServer` (26.3), `./gradlew -Pmc=26.1 build runGameTestServer` (26.1.2, builds in build-26.1/, runs in run-26.1/). `./gradlew runClient` to play.
- `node tools/gen-assets.mjs` writes the textures (16 x 16 pictures from rows of letters), item models, recipes and en_us.json.
- Classes: `Spell` (every spell, `EMPIRE` and `BLOOD` in the plugin's right-click order), `WandItem` (right-click: next spell, the name shows it; data component `empirewand:spell`), `CastSpell` (the client's left-click: `client/EmpireWandClient` cancels the attack and sends it), `Spells` (all spells, per-player state), `Tasks` (the plugin's BukkitRunnables: delay, period, most), `Fx` (fireworks that burst on the spot: a rocket, entity event 17, discarded; block dust is level event 2001).
- Game tests in `gametest/WandGameTests` on the bench structure (7 x 3, ground at y 0); `every_spell_casts` casts all 17 and checks `Tasks.errors`.

## Spells (from the 2013 code)
Empire Wand, in order: Explode (blast 5), Lightning Arrow, Escape, Launch, Leap, Confuse, Capture, Little Spark (5 damage), Comet, Spark (3 damage), Jump (blast 7 on landing), Poison Wave, Explosive (blast 4 with fire), Blood Block, Lightning Storm. (Owner, 31 Oct 2026: Flamethrower and Ender Egg removed.) Blood Magic: Blood Wave, Blood Spark, Blood Block.

What changed from 2013, and why:
- The plugin's sparks were lightning strikes it cancelled, only to damage what was within two blocks: now a crackle of sparks and the damage, no bolt (Lightning Storm and Lightning Arrow still strike real lightning).
- State the plugin shared between all players (spark flags, the blood block) is per player.
- Capture: the creature rode on the player in 2013; Minecraft no longer lets anything ride a player (players cannot be saved as vehicles), so it floats above the head and follows each tick.
- Comet never exploded in 2013 either (small fireballs do not explode): it is a fast fireball with a big orange firework.
- No fall damage after Leap, Jump and Escape (the plugin set the fall distance to minus ten million).

## One wand (owner, 31 Oct 2026)
Only the Empire Wand: Blood Magic, Frost, Void and Nature are gone, their spells are on the Empire Wand (30 in all, Spell.EMPIRE: the fifteen from 2013, Meteor Shower, Chain Lightning, Earthquake, Tornado, Blood Wave, Blood Spark, Ice Wave, Freeze, Blizzard, Blink, Black Hole, Levitate, Roots, Spores, Overgrowth). No blood price: every spell is free. A new wand knows the first four; scrolls teach the rest. The wheel has two rings (68 and 116).

## Backlog
- [ ] Playtest in the game with the owner (above all the 3D wand in the hand: the 45 degree tilt and the display transforms were not seen yet).
- Ideas only when the owner asks.
- [x] Pimp it up (owner, 31 Oct 2026: yes to all of these, built in this order, not playtested yet):
  1. Looks: items/<wand>.json selects by display context: a flat icon (gui, ground, fixed: layer0 rod, layer1 grey crystal) or a 3D model (<wand>_3d: boxes tilted 45 degrees, vanilla handheld display); the crystal is tinted from custom model data (WandItem.select sets the spell's colour; tint index 1); isFoil; Fx.beam from Spells.tip; Spells.castSound per spell.
  2. Spell wheel: client/WheelScreen (key R, KeyMapping in GAMEPLAY), a ring of buttons with icons (textures/gui/spell/<id>.png, 8 x 8 drawn twice the size), SelectSpell payload; sneak and scroll (MouseScrollingEvent) goes to the next known spell.
  3. (Removed with the other wands, 31 Oct 2026.) Blood Magic: Spells.cast(player, spell, blood): costs BLOOD_PRICE 1 health straight off (a hurt would give half a second of no damage: spamming free), not in creative, not with 1 health left; stronger: Blood Spark 7, Blood Wave 6, Blood Block blast 7.
  4. Empire Wand: METEOR_SHOWER (12 large fireballs, power 2), CHAIN_LIGHTNING (6 damage, 5 leaps within 8), EARTHQUAKE (rings 2-10 of falling blocks, 4 damage), TORNADO (6 s, pulls round and up, flings at the end).
  5. FROST_WAND (ICE_WAVE: frosted ice, slowness, freeze damage; FREEZE: ticks frozen + slowness 10; BLIZZARD: 8 s, snow layers), VOID_WAND (BLINK 40 blocks; BLACK_HOLE: pulls within 10 for 4 s, blast 4; LEVITATE: levitation + up to 12 floating blocks), NATURE_WAND (ROOTS: slowness 10 + jump boost 128; SPORES: an area effect cloud of poison and weakness; OVERGROWTH: bone meal in radius 5). Recipes with breeze rods, moss, saplings.
  6. Scrolls: component KNOWN (a bit per place in the wand's list, default the first ones (WandItem.starters): Empire 4, the others 1); ScrollItem (component SCROLL_SPELL, ribbon tinted), right-click teaches the wand in the other hand or the inventory; ScrollsModifier (loot_modifiers/scrolls.json) in 13 kinds of chest; the wheel shows ? for the rest; creative tab has every scroll.
  Not: server options (a gamerule for block damage, teams).
