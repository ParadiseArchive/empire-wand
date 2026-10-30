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
- Game tests in `gametest/WandGameTests` on the bench structure (7 x 3, ground at y 0); `every_spell_casts` casts all 19 and checks `Tasks.errors`.

## Spells (from the 2013 code)
Empire Wand, in order: Explode (blast 5), Lightning Arrow, Escape, Launch, Flamethrower, Leap, Ender Egg, Confuse, Capture, Little Spark (5 damage), Comet, Spark (3 damage), Jump (blast 7 on landing), Poison Wave, Explosive (blast 4 with fire), Blood Block, Lightning Storm. Blood Magic: Blood Wave, Blood Spark, Blood Block.

What changed from 2013, and why:
- The plugin's sparks were lightning strikes it cancelled, only to damage what was within two blocks: now a crackle of sparks and the damage, no bolt (Lightning Storm and Lightning Arrow still strike real lightning).
- State the plugin shared between all players (spark flags, the blood block) is per player.
- Capture: the creature rode on the player in 2013; Minecraft no longer lets anything ride a player (players cannot be saved as vehicles), so it floats above the head and follows each tick.
- Ender Egg: sneaking steers (the dismount is cancelled), casting it again gets you off; it never lands (a dragon egg block would be left behind).
- Comet never exploded in 2013 either (small fireballs do not explode): it is a fast fireball with a big orange firework.
- No fall damage after Leap, Jump, Escape and the egg (the plugin set the fall distance to minus ten million).

## Backlog
- [ ] Playtest in the game with the owner.
- Ideas only when the owner asks.
