package io.hookdev.empirewand.gametest;

import io.hookdev.empirewand.Content;
import io.hookdev.empirewand.EmpireWand;
import io.hookdev.empirewand.Spell;
import io.hookdev.empirewand.Spells;
import io.hookdev.empirewand.Tasks;
import io.hookdev.empirewand.WandItem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * In-world tests, run with {@code ./gradlew runGameTestServer}. They use the empty "bench" structure (7 wide, 3 deep,
 * standing on the ground at y 0): a player at the west end looks east along it.
 */
public final class WandGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, EmpireWand.MODID);
    private static final Map<String, Integer> MAX_TICKS = new LinkedHashMap<>();

    static {
        test("right_click_goes_through_the_spells", WandGameTests::rightClickGoesThroughTheSpells, 20);
        test("explode_blows_up_what_you_look_at", WandGameTests::explodeBlowsUpWhatYouLookAt, 20);
        test("little_spark_hits_harder_than_spark", WandGameTests::littleSparkHitsHarderThanSpark, 40);
        test("blood_block_takes_two_clicks", WandGameTests::bloodBlockTakesTwoClicks, 100);
        test("capture_picks_it_up_and_throws_it", WandGameTests::capturePicksItUpAndThrowsIt, 20);
        test("confuse_makes_it_dizzy", WandGameTests::confuseMakesItDizzy, 20);
        test("a_scroll_teaches_a_spell", WandGameTests::aScrollTeachesASpell, 20);
        test("scrolls_turn_up_in_chests", WandGameTests::scrollsTurnUpInChests, 20);
        test("scrolls_fit_where_they_lie", WandGameTests::scrollsFitWhereTheyLie, 20);
        test("a_sealed_scroll_opens", WandGameTests::aSealedScrollOpens, 20);
        test("a_frozen_one_shatters", WandGameTests::aFrozenOneShatters, 20);
        test("a_charged_cast_hits_harder", WandGameTests::aChargedCastHitsHarder, 40);
        test("power_makes_it_stronger", WandGameTests::powerMakesItStronger, 20);
        test("every_spell_casts", WandGameTests::everySpellCasts, 600);
    }

    private WandGameTests() {}

    private static void test(String name, Consumer<GameTestHelper> function, int maxTicks) {
        FUNCTIONS.register(name, () -> function);
        MAX_TICKS.put(name, maxTicks);
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(WandGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(EmpireWand.id("default"));
        for (var function : FUNCTIONS.getEntries()) {
            event.registerTest(function.getId(), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, function.getId()),
                    new TestData<>(environment, EmpireWand.id("bench"), MAX_TICKS.get(function.getId().getPath()), 0, true)));
        }
    }

    /** A player at the west end of the bench, looking east (yaw -90) and level. */
    private static ServerPlayer caster(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(0.5, 0, 1.5));
        player.snapTo(at.x, at.y, at.z, -90, 0);
        player.setYHeadRot(-90);
        return player;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Entity> EntityType<E> type(String name) {
        return (EntityType<E>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(name));
    }

    /** A stone wall across the east end, to aim at. */
    private static void wall(GameTestHelper helper) {
        for (int y = 0; y < 3; y++) for (int z = 0; z < 3; z++) helper.setBlock(new BlockPos(6, y, z), Blocks.STONE);
    }

    private static void rightClickGoesThroughTheSpells(GameTestHelper helper) {
        ServerPlayer player = caster(helper);
        ItemStack wand = new ItemStack(Content.EMPIRE_WAND.get());
        WandItem item = Content.EMPIRE_WAND.get();
        helper.assertTrue(item.spell(wand) == Spell.EXPLODE, "a new wand starts on Explode");
        helper.assertTrue(item.next(wand, player) == Spell.LIGHTNING_ARROW, "then Lightning Arrow, as in the plugin");
        for (int i = 1; i < item.starters(); i++) item.next(wand, player);
        helper.assertTrue(item.spell(wand) == Spell.EXPLODE, "a new wand knows four: round to Explode again");
        helper.assertTrue(item.learn(wand, Spell.COMET) && !item.learn(wand, Spell.COMET), "a scroll teaches it Comet, once");
        for (int i = 0; i < 4; i++) item.next(wand, player);
        helper.assertTrue(item.spell(wand) == Spell.COMET, "Comet is in the round now, it skips what it does not know");
        helper.assertFalse(item.learn(wand, Spell.EXPLODE), "it knows Explode already");
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(item.spell(player.getMainHandItem()) == Spell.EXPLODE, "right-click: from Comet round to Explode");
        helper.assertTrue(player.getMainHandItem().getHoverName().getString().contains("Explode"), "its name says which");
        helper.succeed();
    }

    private static void explodeBlowsUpWhatYouLookAt(GameTestHelper helper) {
        wall(helper);
        ServerPlayer player = caster(helper);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE); // the blasts do not hurt it
        Spells.cast(player, Spell.EXPLODE);
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(6, 1, 1));
        helper.succeed();
    }

    private static void littleSparkHitsHarderThanSpark(GameTestHelper helper) {
        wall(helper);
        ServerPlayer player = caster(helper);
        Mob a = helper.spawnWithNoFreeWill(type("pig"), new BlockPos(5, 0, 1));
        float full = a.getHealth();
        Spells.cast(player, Spell.SPARK);
        float afterSpark = a.getHealth();
        helper.assertTrue(full - afterSpark == 3, "Spark: 3 damage, it took " + (full - afterSpark));
        helper.runAfterDelay(20, () -> { // after it got over the first hit
            Spells.cast(player, Spell.LITTLE_SPARK);
            helper.assertTrue(afterSpark - a.getHealth() == 5, "Little Spark: 5, it took " + (afterSpark - a.getHealth()));
            helper.succeed();
        });
    }

    private static void bloodBlockTakesTwoClicks(GameTestHelper helper) {
        wall(helper);
        ServerPlayer player = caster(helper);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE); // the blasts do not hurt it
        Spells.cast(player, Spell.BLOOD_BLOCK);
        helper.assertBlockPresent(Blocks.REDSTONE_BLOCK, new BlockPos(6, 1, 1));
        Spells.cast(player, Spell.BLOOD_BLOCK);
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(6, 1, 1));
        helper.succeedWhen(() -> helper.assertEntityNotPresent(type("falling_block")));
    }

    private static void capturePicksItUpAndThrowsIt(GameTestHelper helper) {
        ServerPlayer player = caster(helper);
        Mob pig = helper.spawnWithNoFreeWill(type("pig"), new BlockPos(4, 0, 1));
        player.snapTo(player.getX(), player.getY(), player.getZ(), -90, 25); // a little down, at the pig
        Spells.cast(player, Spell.CAPTURE);
        helper.assertTrue(Spells.carried(player) == pig && pig.getY() > player.getY() + 1.5, "the pig floats above the player's head");
        Spells.cast(player, Spell.CAPTURE);
        helper.assertTrue(Spells.carried(player) == null && pig.getDeltaMovement().length() > 1, "and is thrown");
        helper.succeed();
    }

    private static void confuseMakesItDizzy(GameTestHelper helper) {
        wall(helper);
        ServerPlayer player = caster(helper);
        Mob pig = helper.spawnWithNoFreeWill(type("pig"), new BlockPos(4, 0, 1));
        player.snapTo(player.getX(), player.getY(), player.getZ(), -90, 25);
        Spells.cast(player, Spell.CONFUSE);
        helper.assertTrue(pig.hasEffect(net.minecraft.world.effect.MobEffects.NAUSEA), "the pig is dizzy");
        helper.succeed();
    }

    private static void aScrollTeachesASpell(GameTestHelper helper) {
        ServerPlayer player = caster(helper);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); // so the scroll is used up
        ItemStack wand = new ItemStack(Content.EMPIRE_WAND.get());
        int blizzard = Spell.EMPIRE.indexOf(Spell.BLIZZARD);
        player.setItemInHand(InteractionHand.OFF_HAND, wand);
        player.setItemInHand(InteractionHand.MAIN_HAND, io.hookdev.empirewand.ScrollItem.of(Spell.BLIZZARD));
        helper.assertFalse(Content.EMPIRE_WAND.get().knows(wand, blizzard), "a new wand does not know Blizzard");
        player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(Content.EMPIRE_WAND.get().knows(player.getOffhandItem(), blizzard), "the scroll teaches it Blizzard");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "and is used up");
        var bookworm = helper.getLevel().getServer().getAdvancements().get(io.hookdev.empirewand.EmpireWand.id("bookworm"));
        helper.assertTrue(bookworm != null && player.getAdvancements().getOrStartProgress(bookworm).isDone(), "and the Bookworm advancement is in");
        helper.succeed();
    }

    private static void scrollsTurnUpInChests(GameTestHelper helper) {
        var level = helper.getLevel();
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO))
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        int library = 0, house = 0;
        for (String table : new String[] {"chests/stronghold_library", "chests/village/village_plains_house"}) {
            var loot = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace(table)));
            for (int i = 0; i < 50; i++) for (ItemStack stack : loot.getRandomItems(params)) {
                if (stack.is(Content.SPELL_SCROLL.get()) && table.contains("library")) library++;
                else if (stack.is(Content.SPELL_SCROLL.get())) house++;
            }
        }
        helper.assertTrue(library > 0, "a stronghold library holds scrolls");
        helper.assertTrue(house == 0, "a village house does not");
        helper.succeed();
    }

    /** What scrolls a loot table gives in so many rolls. */
    private static java.util.List<Spell> scrolls(GameTestHelper helper, String table, int times) {
        var level = helper.getLevel();
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO))
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        var loot = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace(table)));
        java.util.List<Spell> found = new java.util.ArrayList<>();
        for (int i = 0; i < times; i++) for (ItemStack stack : loot.getRandomItems(params)) {
            Spell spell = io.hookdev.empirewand.ScrollItem.spell(stack);
            if (stack.is(Content.SPELL_SCROLL.get()) && spell != null) found.add(spell);
        }
        return found;
    }

    private static void scrollsFitWhereTheyLie(GameTestHelper helper) {
        var igloo = scrolls(helper, "chests/igloo_chest", 60);
        helper.assertTrue(!igloo.isEmpty() && io.hookdev.empirewand.ScrollsModifier.FROST.containsAll(igloo), "an igloo holds ice spells, it held " + igloo);
        var dungeon = scrolls(helper, "chests/simple_dungeon", 80);
        helper.assertTrue(!dungeon.isEmpty() && dungeon.stream().noneMatch(io.hookdev.empirewand.ScrollsModifier.HEAVY::contains), "a dungeon never holds the heavy ones, it held " + dungeon);
        helper.succeed();
    }

    private static void aSealedScrollOpens(GameTestHelper helper) {
        ServerPlayer player = caster(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Content.SPELL_SCROLL.get()));
        helper.assertTrue(io.hookdev.empirewand.ScrollItem.spell(player.getMainHandItem()) == null, "a sealed scroll has no spell yet");
        player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        Spell spell = io.hookdev.empirewand.ScrollItem.spell(player.getMainHandItem());
        helper.assertTrue(spell != null && !io.hookdev.empirewand.ScrollsModifier.HEAVY.contains(spell), "the seal breaks: a spell, not a heavy one, it is " + spell);
        helper.succeed();
    }

    private static void aFrozenOneShatters(GameTestHelper helper) {
        wall(helper);
        ServerPlayer player = caster(helper);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        Mob pig = helper.spawnWithNoFreeWill(type("pig"), new BlockPos(4, 0, 1));
        pig.setTicksFrozen(pig.getTicksRequiredToFreeze() + 100);
        io.hookdev.empirewand.Spells.shatterForTests(player, helper.getLevel(), pig.position());
        helper.assertTrue(pig.getTicksFrozen() == 0 && pig.getHealth() < pig.getMaxHealth(), "a blast near a frozen pig shatters it");
        helper.succeed();
    }

    private static void aChargedCastHitsHarder(GameTestHelper helper) {
        wall(helper);
        ServerPlayer player = caster(helper);
        Mob pig = helper.spawnWithNoFreeWill(type("pig"), new BlockPos(5, 0, 1));
        float full = pig.getHealth();
        Spells.cast(player, Spell.SPARK, Spells.CHARGED);
        helper.assertTrue(full - pig.getHealth() > 3, "a charged Spark does more than 3, it did " + (full - pig.getHealth()));
        helper.succeed();
    }

    private static void powerMakesItStronger(GameTestHelper helper) {
        wall(helper);
        ServerPlayer player = caster(helper);
        ItemStack wand = new ItemStack(Content.EMPIRE_WAND.get());
        var power = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, EmpireWand.id("power")));
        wand.enchant(power, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        Mob pig = helper.spawnWithNoFreeWill(type("pig"), new BlockPos(5, 0, 1));
        float full = pig.getHealth();
        Spells.cast(player, Spell.SPARK, 1);
        helper.assertTrue(full - pig.getHealth() > 3, "Power III: a Spark does more than 3, it did " + (full - pig.getHealth()));
        helper.succeed();
    }

    /** Every spell, one after the other, and nothing goes wrong on the way. */
    private static void everySpellCasts(GameTestHelper helper) {
        wall(helper);
        ServerPlayer player = caster(helper);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE); // the blasts do not hurt it
        helper.spawnWithNoFreeWill(type("pig"), new BlockPos(4, 0, 1));
        int before = Tasks.errors;
        Spell[] all = Spell.values();
        for (int i = 0; i < all.length; i++) {
            Spell spell = all[i];
            helper.runAfterDelay(1 + i * 6, () -> {
                player.snapTo(helper.absoluteVec(new Vec3(0.5, 0, 1.5)).x, helper.absoluteVec(Vec3.ZERO).y, helper.absoluteVec(new Vec3(0.5, 0, 1.5)).z, -90, 0);
                Spells.cast(player, spell);
            });
        }
        helper.runAfterDelay(all.length * 6 + 200, () -> {
            helper.assertTrue(Tasks.errors == before, "no spell went wrong, " + (Tasks.errors - before) + " did");
            helper.succeed();
        });
    }
}
