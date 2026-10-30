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
        test("blood_magic_costs_blood", WandGameTests::bloodMagicCostsBlood, 20);
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
        for (int i = 1; i < Spell.EMPIRE.size(); i++) item.next(wand, player);
        helper.assertTrue(item.spell(wand) == Spell.EXPLODE, "once round all its spells: Explode again");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Content.BLOOD_MAGIC.get()));
        player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(Content.BLOOD_MAGIC.get().spell(player.getMainHandItem()) == Spell.BLOOD_SPARK, "Blood Magic: Blood Wave, then Blood Spark");
        helper.assertTrue(player.getMainHandItem().getHoverName().getString().contains("Blood Spark"), "its name says which");
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

    private static void bloodMagicCostsBlood(GameTestHelper helper) {
        wall(helper);
        ServerPlayer player = caster(helper);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); // in creative blood is free
        float full = player.getHealth();
        Spells.cast(player, Spell.BLOOD_SPARK, true);
        helper.assertTrue(full - player.getHealth() == Spells.BLOOD_PRICE, "half a heart, it cost " + (full - player.getHealth()));
        player.setHealth(Spells.BLOOD_PRICE);
        Spells.cast(player, Spell.BLOOD_SPARK, true);
        helper.assertTrue(player.isAlive() && player.getHealth() == Spells.BLOOD_PRICE, "with half a heart left: no blood to give, and alive");
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
