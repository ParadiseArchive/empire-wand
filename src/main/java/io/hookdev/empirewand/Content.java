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

/** The two wands, the spell a wand has selected, and a creative tab. */
public final class Content {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EmpireWand.MODID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, EmpireWand.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EmpireWand.MODID);

    /** Which of its spells a wand has selected (its place in the wand's list). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SPELL = COMPONENTS.registerComponentType("spell",
            builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredItem<WandItem> EMPIRE_WAND = ITEMS.registerItem("empire_wand", p -> new WandItem(p, Spell.EMPIRE), p -> p.stacksTo(1));
    public static final DeferredItem<WandItem> BLOOD_MAGIC = ITEMS.registerItem("blood_magic", p -> new WandItem(p, Spell.BLOOD), p -> p.stacksTo(1));

    static {
        TABS.register("wands", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.empirewand"))
                .icon(() -> new ItemStack(EMPIRE_WAND.get()))
                .displayItems((parameters, output) -> {
                    output.accept(EMPIRE_WAND.get());
                    output.accept(BLOOD_MAGIC.get());
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
    }
}
