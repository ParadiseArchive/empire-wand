package io.hookdev.empirewand;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;

/**
 * Puts spell scrolls in chests round the world (a spell no wand knows from the start, at random). The places and chances
 * live here rather than in loot table files, because 26.3 and 26.1 read those differently (as in Bleep).
 */
public final class ScrollsModifier implements IGlobalLootModifier {
    public static final ScrollsModifier INSTANCE = new ScrollsModifier();
    public static final MapCodec<ScrollsModifier> CODEC = MapCodec.unit(INSTANCE);

    /** The chests, and the chance one holds a scroll. */
    public static final Map<Identifier, Float> CHESTS = Map.ofEntries(
            chest("simple_dungeon", 0.5f), chest("abandoned_mineshaft", 0.2f), chest("desert_pyramid", 0.35f), chest("jungle_temple", 0.35f),
            chest("stronghold_library", 0.8f), chest("stronghold_corridor", 0.25f), chest("ancient_city", 0.4f), chest("woodland_mansion", 0.5f),
            chest("end_city_treasure", 0.4f), chest("bastion_treasure", 0.4f), chest("pillager_outpost", 0.3f), chest("igloo_chest", 0.5f),
            chest("ruined_portal", 0.25f));

    private static Map.Entry<Identifier, Float> chest(String name, float chance) {
        return Map.entry(Identifier.withDefaultNamespace("chests/" + name), chance);
    }

    private ScrollsModifier() {}

    @Override
    public ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        Float chance = CHESTS.get(context.getQueriedLootTableId());
        if (chance != null && context.getRandom().nextFloat() < chance) {
            List<Spell> spells = ScrollItem.findable();
            generatedLoot.add(ScrollItem.of(spells.get(context.getRandom().nextInt(spells.size()))));
        }
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
