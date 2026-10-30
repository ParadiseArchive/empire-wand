package io.hookdev.empirewand.client;

import io.hookdev.empirewand.CastSpell;
import io.hookdev.empirewand.EmpireWand;
import io.hookdev.empirewand.WandItem;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only: a left-click with a wand casts its spell (and does not hit or break anything). */
@Mod(value = EmpireWand.MODID, dist = Dist.CLIENT)
public class EmpireWandClient {
    public EmpireWandClient() {
        NeoForge.EVENT_BUS.addListener(EmpireWandClient::leftClick);
    }

    private static void leftClick(InputEvent.InteractionKeyMappingTriggered event) {
        var player = Minecraft.getInstance().player;
        if (!event.isAttack() || player == null || !(player.getMainHandItem().getItem() instanceof WandItem)) return;
        event.setCanceled(true);
        event.setSwingHand(true);
        ClientPacketDistributor.sendToServer(CastSpell.INSTANCE);
    }
}
