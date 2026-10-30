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

/** The spell wheel (the R key with a wand in hand): the wand's spells in a ring, each with its icon; click one to pick it. */
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
        radius = Math.max(36, spells.size() * 6);
        for (int i = 0; i < spells.size(); i++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * i / spells.size(); // the first at the top, round clockwise
            int x = cx + (int) Math.round(Math.cos(angle) * radius) - SIZE / 2, y = cy + (int) Math.round(Math.sin(angle) * radius) - SIZE / 2;
            int index = i;
            buttons.add(addRenderableWidget(Button.builder(Component.empty(), b -> choose(index)).bounds(x, y, SIZE, SIZE)
                    .tooltip(Tooltip.create(spells.get(i).displayName())).build()));
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
            g.blit(icon(spell), b.getX() + 4, b.getY() + 4, b.getX() + 20, b.getY() + 20, 0, 1, 0, 1);
        }
        // in the middle: the spell under the mouse, or the one it has now
        Spell shown = current;
        for (int i = 0; i < buttons.size(); i++) if (buttons.get(i).isHovered()) shown = spells.get(i);
        g.centeredText(font, stack.getItem().getName(stack).copy().withStyle(net.minecraft.ChatFormatting.RED), cx, cy - 14, 0xFFFFFFFF);
        g.centeredText(font, shown.displayName(), cx, cy, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
