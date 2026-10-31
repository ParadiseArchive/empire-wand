package io.hookdev.empirewand.client;

import io.hookdev.empirewand.ScrollsModifier;
import io.hookdev.empirewand.Spell;
import io.hookdev.empirewand.WandItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The spellbook: all of the Empire Wand's spells, six to a page, each with its icon, name and what it does. The ones
 * your wands know are bright; the others are ??? with where their scroll lies.
 */
public class SpellbookScreen extends Screen {
    private static final int W = 320, H = 214, PER_PAGE = 6, CELL_W = 148, CELL_H = 52;
    private static final int PAPER = 0xFFE9DDB8, INK = 0xFF3A2A1A, FADED = 0xFF8C7A5A, EDGE = 0xFF6A4A2A;
    private final List<Spell> spells = io.hookdev.empirewand.Spell.EMPIRE;
    private int page, left, top, known;
    private Button back, next;

    public SpellbookScreen() {
        super(Component.translatable("screen.empirewand.spellbook"));
    }

    /** The spells known by any Empire Wand the player carries (a bit for each place in the list). */
    private static int known() {
        var player = Minecraft.getInstance().player;
        int mask = 0;
        if (player == null) return 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof WandItem wand) mask |= wand.known(stack);
        }
        return mask;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        known = known();
        int pages = (spells.size() + PER_PAGE - 1) / PER_PAGE;
        back = addRenderableWidget(Button.builder(Component.literal("<"), b -> page = Math.max(0, page - 1)).bounds(left + 10, top + H - 26, 20, 18).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> page = Math.min(pages - 1, page + 1)).bounds(left + W - 30, top + H - 26, 20, 18).build());
    }

    @Override
    public void tick() {
        super.tick();
        int pages = (spells.size() + PER_PAGE - 1) / PER_PAGE;
        back.active = page > 0;
        next.active = page < pages - 1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        // the open book: a brown cover, two pages of paper
        g.fill(left, top, left + W, top + H, EDGE);
        g.fill(left + 4, top + 4, left + W / 2 - 1, top + H - 4, PAPER);
        g.fill(left + W / 2 + 1, top + 4, left + W - 4, top + H - 4, PAPER);
        int count = Integer.bitCount(known & ((1 << spells.size()) - 1));
        g.centeredText(font, Component.translatable("screen.empirewand.spellbook.title", count, spells.size()), left + W / 2, top + 10, INK);
        for (int k = 0; k < PER_PAGE; k++) {
            int index = page * PER_PAGE + k;
            if (index >= spells.size()) break;
            Spell spell = spells.get(index);
            int x = left + 10 + (k % 2) * (CELL_W + 12), y = top + 26 + (k / 2) * CELL_H;
            boolean has = (known >> index & 1) != 0;
            if (has) g.blit(WheelScreen.icon(spell), x, y, x + 16, y + 16, 0, 1, 0, 1);
            else g.text(font, "?", x + 5, y + 4, FADED, false);
            g.text(font, has ? Component.translatable("spell.empirewand." + spell.id()) : Component.literal("???"), x + 20, y + 4, has ? INK : FADED, false);
            Component text = has ? Component.translatable("spell.empirewand." + spell.id() + ".desc")
                    : Component.translatable("screen.empirewand.spellbook.found", places(spell));
            g.textWithWordWrap(font, text, x, y + 19, CELL_W, has ? INK : FADED);
        }
        g.centeredText(font, (page + 1) + " / " + ((spells.size() + PER_PAGE - 1) / PER_PAGE), left + W / 2, top + H - 21, FADED);
        super.extractRenderState(g, mouseX, mouseY, partialTicks);
    }

    /** Where its scroll can turn up: the first few places. */
    private static Component places(Spell spell) {
        var places = ScrollsModifier.places(spell);
        net.minecraft.network.chat.MutableComponent line = Component.empty();
        for (int i = 0; i < Math.min(3, places.size()); i++) {
            if (i > 0) line.append(", ");
            line.append(Component.translatable("place.empirewand." + places.get(i)));
        }
        if (places.size() > 3) line.append(Component.translatable("screen.empirewand.spellbook.more"));
        return line.withStyle(ChatFormatting.ITALIC);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
