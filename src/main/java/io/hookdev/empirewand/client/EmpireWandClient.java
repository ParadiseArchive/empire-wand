package io.hookdev.empirewand.client;

import io.hookdev.empirewand.CastSpell;
import io.hookdev.empirewand.Content;
import io.hookdev.empirewand.EmpireWand;
import io.hookdev.empirewand.SelectSpell;
import io.hookdev.empirewand.WandItem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only: left-click with a wand casts its spell; R opens the spell wheel; sneak and scroll goes through the spells. */
@Mod(value = EmpireWand.MODID, dist = Dist.CLIENT)
public class EmpireWandClient {
    public static final KeyMapping WHEEL = new KeyMapping("key.empirewand.wheel", com.mojang.blaze3d.platform.InputConstants.KEY_R, KeyMapping.Category.GAMEPLAY);

    public EmpireWandClient(IEventBus modEventBus) {
        modEventBus.addListener((RegisterKeyMappingsEvent event) -> event.register(WHEEL));
        NeoForge.EVENT_BUS.addListener(EmpireWandClient::leftClick);
        NeoForge.EVENT_BUS.addListener(EmpireWandClient::scroll);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            var mc = Minecraft.getInstance();
            while (WHEEL.consumeClick()) {
                if (mc.player != null && McVersionClient.noScreen() && mc.player.getMainHandItem().getItem() instanceof WandItem wand) {
                    McVersionClient.setScreen(new WheelScreen(wand, mc.player.getMainHandItem()));
                }
            }
        });
    }

    private static void leftClick(InputEvent.InteractionKeyMappingTriggered event) {
        var player = Minecraft.getInstance().player;
        if (!event.isAttack() || player == null || !(player.getMainHandItem().getItem() instanceof WandItem)) return;
        event.setCanceled(true);
        event.setSwingHand(true);
        ClientPacketDistributor.sendToServer(CastSpell.INSTANCE);
    }

    /** Sneak and scroll: the next or the previous spell (the hotbar stays where it is). */
    private static void scroll(InputEvent.MouseScrollingEvent event) {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.isShiftKeyDown() || !(player.getMainHandItem().getItem() instanceof WandItem) || event.getScrollDeltaY() == 0) return;
        event.setCanceled(true);
        var stack = player.getMainHandItem();
        WandItem wand = (WandItem) stack.getItem();
        ClientPacketDistributor.sendToServer(new SelectSpell(wand.nextKnown(stack, stack.getOrDefault(Content.SPELL.get(), 0), event.getScrollDeltaY() > 0 ? -1 : 1)));
    }
}
