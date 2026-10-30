package io.hookdev.empirewand.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** The client calls that differ between Minecraft versions, as in 26.3. versions/26.1/java has the same class for 26.1.2. */
public final class McVersionClient {
    private McVersionClient() {}

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    /** No screen open: the player is in the game. */
    public static boolean noScreen() {
        return Minecraft.getInstance().gui.screen() == null;
    }
}
