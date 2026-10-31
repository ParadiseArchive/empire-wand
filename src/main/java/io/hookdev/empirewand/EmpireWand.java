package io.hookdev.empirewand;

import io.hookdev.empirewand.gametest.WandGameTests;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Empire Wand: the owner's Bukkit plugin "Empire" from 2013 (Minecraft 1.7.2), rebuilt as a NeoForge mod. Two wands:
 * the Empire Wand with seventeen spells and Blood Magic with three. Right-click picks the next spell, left-click casts
 * it (the client sends CastSpell, the server casts it in Spells).
 */
@Mod(EmpireWand.MODID)
public class EmpireWand {
    public static final String MODID = "empirewand";

    public EmpireWand(IEventBus modEventBus, ModContainer modContainer) {
        Content.register(modEventBus);
        modEventBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1").playToServer(CastSpell.TYPE, CastSpell.CODEC, CastSpell::handle)
                .playToServer(SelectSpell.TYPE, SelectSpell.CODEC, SelectSpell::handle));
        WandGameTests.register(modEventBus);
        // spells that take a while run on the server's clock
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> Tasks.tick());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> Tasks.clear());
        // no fall damage after Leap, Jump and Escape
        NeoForge.EVENT_BUS.addListener((LivingFallEvent event) -> {
            if (event.getEntity() instanceof Player player && Spells.landsSafely(player)) event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> Spells.forget(event.getEntity()));
        // a wand casts, it does not dig: holding left-click on a block with one breaks nothing
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) -> {
            if (event.getEntity().getMainHandItem().getItem() instanceof WandItem) event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.block.BreakBlockEvent event) -> {
            if (event.getPlayer() != null && event.getPlayer().getMainHandItem().getItem() instanceof WandItem) event.setCanceled(true);
        });
        // the Ender Dragon always leaves a heavy scroll, on top of the exit portal (it dies in the air)
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon && dragon.level() instanceof net.minecraft.server.level.ServerLevel level) {
                var top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, net.minecraft.core.BlockPos.ZERO).above(2);
                var spells = ScrollsModifier.HEAVY.stream().filter(ScrollItem.findable()::contains).toList();
                var scroll = new net.minecraft.world.entity.item.ItemEntity(level, top.getX() + 0.5, top.getY(), top.getZ() + 0.5, ScrollItem.of(spells.get(level.getRandom().nextInt(spells.size()))));
                scroll.setGlowingTag(true);
                level.addFreshEntity(scroll);
            }
        });
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
