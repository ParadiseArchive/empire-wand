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

/** Client-only: left-click with a wand casts its spell (hold it to charge); R opens the spell wheel; sneak and scroll goes through the spells. */
@Mod(value = EmpireWand.MODID, dist = Dist.CLIENT)
public class EmpireWandClient {
    public static final KeyMapping WHEEL = new KeyMapping("key.empirewand.wheel", com.mojang.blaze3d.platform.InputConstants.KEY_R, KeyMapping.Category.GAMEPLAY);

    public EmpireWandClient(IEventBus modEventBus) {
        modEventBus.addListener((RegisterKeyMappingsEvent event) -> event.register(WHEEL));
        io.hookdev.empirewand.SpellbookItem.open = () -> McVersionClient.setScreen(new SpellbookScreen());
        NeoForge.EVENT_BUS.addListener(EmpireWandClient::leftClick);
        NeoForge.EVENT_BUS.addListener(EmpireWandClient::scroll);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            charging();
            var mc = Minecraft.getInstance();
            while (WHEEL.consumeClick()) {
                if (mc.player != null && McVersionClient.noScreen() && mc.player.getMainHandItem().getItem() instanceof WandItem wand) {
                    McVersionClient.setScreen(new WheelScreen(wand, mc.player.getMainHandItem()));
                }
            }
        });
    }

    /** Ticks left-click has been held with a wand, or -1. A click casts when it is let go; held a second, the cast is charged. */
    private static int charge = -1;

    private static void leftClick(InputEvent.InteractionKeyMappingTriggered event) {
        var player = Minecraft.getInstance().player;
        if (!event.isAttack() || player == null || !(player.getMainHandItem().getItem() instanceof WandItem)) return;
        event.setCanceled(true);
        event.setSwingHand(true);
        if (charge < 0) charge = 0;
    }

    /** While left-click is held: sparks in the spell's colour at the tip, rising chimes, a ding when it is full. Let go: cast. */
    private static void charging() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (charge < 0) return;
        if (player == null || !(player.getMainHandItem().getItem() instanceof WandItem wand)) {
            charge = -1;
            return;
        }
        if (!mc.options.keyAttack.isDown()) {
            ClientPacketDistributor.sendToServer(new CastSpell(charge));
            charge = -1;
            return;
        }
        charge++;
        if (charge < 4) return; // a click, not a hold
        var look = player.getLookAngle();
        var right = look.cross(new net.minecraft.world.phys.Vec3(0, 1, 0)).normalize();
        var tip = player.getEyePosition().add(look.scale(0.8)).add(right.scale(0.35)).add(0, -0.25, 0);
        int colour = wand.spell(player.getMainHandItem()).colour;
        for (int i = 0; i < (charge >= CastSpell.FULL ? 3 : 1); i++) {
            player.level().addParticle(new net.minecraft.core.particles.DustParticleOptions(colour, 1f), tip.x + (Math.random() - 0.5) * 0.3, tip.y + (Math.random() - 0.5) * 0.3, tip.z + (Math.random() - 0.5) * 0.3, 0, 0, 0);
        }
        if (charge < CastSpell.FULL && charge % 4 == 0) player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.4f, 0.6f + charge / (float) CastSpell.FULL);
        if (charge == CastSpell.FULL) player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
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
