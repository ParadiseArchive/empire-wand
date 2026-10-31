package io.hookdev.empirewand;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;

/**
 * Where spell scrolls turn up (owner, 31 Oct 2026): chests, trial chamber vaults, suspicious sand and gravel, and some
 * mobs. Each place has its kind of spell (ice in igloos, the void in strongholds, nature in jungle temples...), and the
 * heavy ones only turn up in hard places. The places and chances live here rather than in loot table files, because
 * 26.3 and 26.1 read those differently (as in Bleep).
 */
public final class ScrollsModifier implements IGlobalLootModifier {
    public static final ScrollsModifier INSTANCE = new ScrollsModifier();
    public static final MapCodec<ScrollsModifier> CODEC = MapCodec.unit(INSTANCE);

    /** The big ones: only in hard places (End cities, ancient cities, bastions, ominous vaults, the Wither). */
    public static final List<Spell> HEAVY = List.of(Spell.METEOR_SHOWER, Spell.BLACK_HOLE, Spell.LIGHTNING_STORM, Spell.TORNADO, Spell.EARTHQUAKE);
    public static final List<Spell> FROST = List.of(Spell.ICE_WAVE, Spell.FREEZE, Spell.BLIZZARD);
    public static final List<Spell> VOID = List.of(Spell.BLINK, Spell.BLACK_HOLE, Spell.LEVITATE, Spell.LEAP, Spell.CAPTURE);
    public static final List<Spell> NATURE = List.of(Spell.ROOTS, Spell.SPORES, Spell.OVERGROWTH, Spell.POISON_WAVE);
    public static final List<Spell> LIGHTNING = List.of(Spell.SPARK, Spell.LITTLE_SPARK, Spell.CHAIN_LIGHTNING, Spell.LIGHTNING_STORM);
    public static final List<Spell> FIRE = List.of(Spell.COMET, Spell.EXPLOSIVE, Spell.METEOR_SHOWER, Spell.JUMP);
    public static final List<Spell> BLOOD = List.of(Spell.BLOOD_WAVE, Spell.BLOOD_SPARK, Spell.BLOOD_BLOCK);

    /**
     * A place: the chance it holds a scroll, its kinds of spell (none: any), whether heavy ones may turn up, and whether
     * the scroll takes the place of what it held (a suspicious block holds only one thing).
     */
    public record Source(float chance, List<List<Spell>> kinds, boolean heavy, boolean replace, int count) {
        public Source(float chance, List<List<Spell>> kinds, boolean heavy, boolean replace) {
            this(chance, kinds, heavy, replace, 1);
        }

        /** The spells a scroll from here can hold. */
        public List<Spell> spells() {
            List<Spell> all = new ArrayList<>();
            for (Spell spell : ScrollItem.findable()) {
                boolean kind = kinds.isEmpty() || kinds.stream().anyMatch(k -> k.contains(spell));
                if (kind && (heavy || !HEAVY.contains(spell))) all.add(spell);
            }
            return all;
        }
    }

    @SafeVarargs
    private static Source chest(float chance, boolean heavy, List<Spell>... kinds) {
        return new Source(chance, List.of(kinds), heavy, false);
    }

    public static final Map<Identifier, Source> SOURCES = Map.ofEntries(
            // chests
            entry("chests/simple_dungeon", chest(0.5f, false)),
            entry("chests/abandoned_mineshaft", chest(0.2f, false)),
            entry("chests/desert_pyramid", chest(0.35f, false)),
            entry("chests/jungle_temple", chest(0.35f, false, NATURE)),
            entry("chests/stronghold_library", chest(0.8f, false, VOID)),
            entry("chests/stronghold_corridor", chest(0.25f, false, VOID)),
            entry("chests/ancient_city", chest(0.4f, true, FROST, HEAVY)),
            entry("chests/woodland_mansion", chest(0.5f, false, NATURE, BLOOD)),
            entry("chests/end_city_treasure", chest(0.4f, true, VOID, HEAVY)),
            entry("chests/bastion_treasure", chest(0.5f, true, FIRE, BLOOD, HEAVY)),
            entry("chests/nether_bridge", chest(0.3f, false, FIRE)),
            entry("chests/pillager_outpost", chest(0.3f, false, LIGHTNING)),
            entry("chests/igloo_chest", chest(0.6f, false, FROST)),
            entry("chests/ruined_portal", chest(0.25f, false, FIRE)),
            // trial chamber vaults
            entry("chests/trial_chambers/reward", chest(0.25f, false)),
            entry("chests/trial_chambers/reward_ominous", chest(0.5f, true)),
            // suspicious sand and gravel: the scroll instead of what it held
            entry("archaeology/desert_pyramid", new Source(0.1f, List.of(), false, true)),
            entry("archaeology/desert_well", new Source(0.1f, List.of(), false, true)),
            entry("archaeology/ocean_ruin_cold", new Source(0.1f, List.of(FROST), false, true)),
            entry("archaeology/ocean_ruin_warm", new Source(0.1f, List.of(), false, true)),
            entry("archaeology/trail_ruins_common", new Source(0.05f, List.of(), false, true)),
            entry("archaeology/trail_ruins_rare", new Source(0.15f, List.of(), true, true)),
            // mobs
            entry("entities/evoker", chest(0.25f, false)),
            entry("entities/illusioner", chest(0.5f, true)),
            entry("entities/witch", chest(0.1f, false, NATURE, BLOOD)),
            entry("entities/wither", chest(1f, true, HEAVY)),
            // the Dark Mage, and his tower's library: three scrolls, the heavy ones too
            Map.entry(EmpireWand.id("entities/dark_mage"), chest(0.6f, false)),
            Map.entry(EmpireWand.id("chests/wizard_tower"), new Source(1f, List.of(), true, false, 3)));

    private static Map.Entry<Identifier, Source> entry(String table, Source source) {
        return Map.entry(Identifier.withDefaultNamespace(table), source);
    }

    /**
     * Where a spell's scroll can turn up, most likely first, as language keys (place.empirewand.<key>): the loot
     * tables, the Ender Dragon for the heavy ones, a librarian's sealed scroll for the rest.
     */
    public static List<String> places(Spell spell) {
        List<Map.Entry<Identifier, Source>> found = new ArrayList<>();
        for (var entry : SOURCES.entrySet()) if (entry.getValue().spells().contains(spell)) found.add(entry);
        found.sort((a, b) -> Float.compare(b.getValue().chance() / Math.max(1, b.getValue().spells().size()), a.getValue().chance() / Math.max(1, a.getValue().spells().size())));
        List<String> places = new ArrayList<>();
        for (var entry : found) places.add(entry.getKey().getPath().replace('/', '.'));
        places.add(HEAVY.contains(spell) ? "ender_dragon" : "librarian");
        return places;
    }

    private ScrollsModifier() {}

    @Override
    public ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        Source source = SOURCES.get(context.getQueriedLootTableId());
        if (source == null || context.getRandom().nextFloat() >= source.chance()) return generatedLoot;
        List<Spell> spells = source.spells();
        if (spells.isEmpty()) return generatedLoot;
        if (source.replace()) generatedLoot.clear();
        for (int i = 0; i < source.count(); i++) generatedLoot.add(ScrollItem.of(spells.get(context.getRandom().nextInt(spells.size()))));
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    public int priority() {
        return DEFAULT_PRIORITY;
    }
}
