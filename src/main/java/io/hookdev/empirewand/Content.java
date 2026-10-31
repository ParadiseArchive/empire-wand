package io.hookdev.empirewand;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.List;

/** The Empire Wand, the spell it has selected and the ones it knows, spell scrolls, and a creative tab. */
public final class Content {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EmpireWand.MODID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, EmpireWand.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EmpireWand.MODID);
    private static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.loot.IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, EmpireWand.MODID);

    /** Which of its spells a wand has selected (its place in the wand's list). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SPELL = COMPONENTS.registerComponentType("spell",
            builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** Which of its spells a wand knows (a bit for each place in its list); a new wand knows its first few. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> KNOWN = COMPONENTS.registerComponentType("known",
            builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** The wand's favourites: the spell (its place in the list, -1 for none) on each of the number keys 1 to 5. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.List<Integer>>> FAVOURITES = COMPONENTS.registerComponentType("favourites",
            builder -> builder.persistent(Codec.INT.listOf()).networkSynchronized(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list())));
    /** The spell on a scroll (its Spell ordinal). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SCROLL_SPELL = COMPONENTS.registerComponentType("scroll_spell",
            builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredItem<WandItem> EMPIRE_WAND = ITEMS.registerItem("empire_wand", p -> new WandItem(p, Spell.EMPIRE, 4), p -> p.stacksTo(1).enchantable(15));
    public static final DeferredItem<SpellbookItem> SPELLBOOK = ITEMS.registerItem("spellbook", SpellbookItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<ScrollItem> SPELL_SCROLL = ITEMS.registerItem("spell_scroll", ScrollItem::new, p -> p.stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE));

    static {
        LOOT_MODIFIERS.register("scrolls", () -> ScrollsModifier.CODEC);
    }

    /** All the wands. */
    public static List<WandItem> wands() {
        return List.of(EMPIRE_WAND.get());
    }

    static {
        TABS.register("wands", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.empirewand"))
                .icon(() -> new ItemStack(EMPIRE_WAND.get()))
                .displayItems((parameters, output) -> {
                    output.accept(EMPIRE_WAND.get());
                    output.accept(SPELLBOOK.get());
                    output.accept(SPELL_SCROLL.get()); // a sealed one
                    for (Spell spell : ScrollItem.findable()) output.accept(ScrollItem.of(spell));
                })
                .build());
    }

    private Content() {}

    /** Lightning (26.1 and 26.3 keep the vanilla entity types in different classes, so it is looked up by name). */
    @SuppressWarnings("unchecked")
    public static net.minecraft.world.entity.EntityType<net.minecraft.world.entity.LightningBolt> lightningType() {
        return (net.minecraft.world.entity.EntityType<net.minecraft.world.entity.LightningBolt>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getValue(net.minecraft.resources.Identifier.withDefaultNamespace("lightning_bolt"));
    }

    static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        TABS.register(modEventBus);
        LOOT_MODIFIERS.register(modEventBus);
    }
}
