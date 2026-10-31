package io.hookdev.empirewand.client;

import io.hookdev.empirewand.EmpireWand;
import io.hookdev.empirewand.SelectSpell;
import io.hookdev.empirewand.Spell;
import io.hookdev.empirewand.WandItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The spell wheel (the R key with a wand in hand): the wand's spells in two rings, each with its icon; click one to pick
 * it. Point at one and press 1 to 5 to put it on that key; press a key without pointing to pick that favourite.
 */
public class WheelScreen extends Screen {
    private static final int SIZE = 24;
    private final WandItem wand;
    private final ItemStack stack;
    private final List<Button> buttons = new ArrayList<>();
    private int cx, cy, radius;

    public WheelScreen(WandItem wand, ItemStack stack) {
        super(Component.translatable("screen.empirewand.wheel"));
        this.wand = wand;
        this.stack = stack;
    }

    public static Identifier icon(Spell spell) {
        return EmpireWand.id("textures/gui/spell/" + spell.id() + ".png");
    }

    @Override
    protected void init() {
        buttons.clear();
        List<Spell> spells = wand.spells();
        cx = width / 2;
        cy = height / 2;
        // two rings: the first half of its spells inside, the rest round them (each from the top, round clockwise)
        int inner = (spells.size() + 1) / 2;
        for (int i = 0; i < spells.size(); i++) {
            boolean outer = i >= inner;
            int count = outer ? spells.size() - inner : inner, place = outer ? i - inner : i;
            radius = outer ? 116 : 68;
            double angle = -Math.PI / 2 + 2 * Math.PI * (place + (outer ? 0.5 : 0)) / count;
            int x = cx + (int) Math.round(Math.cos(angle) * radius) - SIZE / 2, y = cy + (int) Math.round(Math.sin(angle) * radius) - SIZE / 2;
            int index = i;
            boolean known = wand.knows(stack, i);
            Button button = Button.builder(Component.empty(), b -> choose(index)).bounds(x, y, SIZE, SIZE)
                    .tooltip(Tooltip.create(known ? spells.get(i).displayName() : Component.translatable("screen.empirewand.unknown"))).build();
            button.active = known; // not learnt yet: find its scroll
            buttons.add(addRenderableWidget(button));
        }
    }

    private void choose(int index) {
        ClientPacketDistributor.sendToServer(new SelectSpell(index));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        Spell current = wand.spell(stack);
        super.extractRenderState(g, mouseX, mouseY, partialTicks);
        List<Spell> spells = wand.spells();
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            Spell spell = spells.get(i);
            if (spell == current) { // the one it has now: a frame in its colour
                int c = 0xFF000000 | spell.colour;
                g.fill(b.getX() - 2, b.getY() - 2, b.getX() + SIZE + 2, b.getY(), c);
                g.fill(b.getX() - 2, b.getY() + SIZE, b.getX() + SIZE + 2, b.getY() + SIZE + 2, c);
                g.fill(b.getX() - 2, b.getY(), b.getX(), b.getY() + SIZE, c);
                g.fill(b.getX() + SIZE, b.getY(), b.getX() + SIZE + 2, b.getY() + SIZE, c);
            }
            if (wand.knows(stack, i)) g.blit(icon(spell), b.getX() + 4, b.getY() + 4, b.getX() + 20, b.getY() + 20, 0, 1, 0, 1);
            else g.centeredText(font, "?", b.getX() + SIZE / 2, b.getY() + 8, 0xFF8A93A0);
        }
        for (int slot = 0; slot < WandItem.FAVOURITES; slot++) { // the number keys, in the corner of their spell
            int index = wand.favourite(stack, slot);
            if (index < 0 || index >= buttons.size()) continue;
            Button b = buttons.get(index);
            g.text(font, String.valueOf(slot + 1), b.getX() + SIZE - 6, b.getY() + 1, 0xFFFFD23F, true);
        }
        // in the middle: the spell under the mouse, or the one it has now
        Spell shown = current;
        for (int i = 0; i < buttons.size(); i++) if (buttons.get(i).isHovered() && wand.knows(stack, i)) shown = spells.get(i);
        g.centeredText(font, shown.displayName(), cx, cy - 8, 0xFFFFFFFF);
        g.centeredText(font, Component.translatable("screen.empirewand.wheel.keys"), cx, cy + 4, 0xFF8A93A0);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int slot = event.input() - 49; // the number keys 1 to 5 (GLFW 49 to 53)
        if (slot < 0 || slot >= WandItem.FAVOURITES) return super.keyPressed(event);
        for (int i = 0; i < buttons.size(); i++) {
            if (!buttons.get(i).isHovered() || !wand.knows(stack, i)) continue;
            wand.setFavourite(stack, slot, i); // here at once, the server's copy follows
            ClientPacketDistributor.sendToServer(new io.hookdev.empirewand.SetFavourite(slot, i));
            return true;
        }
        int favourite = wand.favourite(stack, slot);
        if (favourite >= 0 && wand.knows(stack, favourite)) choose(favourite);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
