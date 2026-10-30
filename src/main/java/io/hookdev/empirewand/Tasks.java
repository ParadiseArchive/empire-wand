package io.hookdev.empirewand;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

/**
 * The server's clock for spells that take a while (the plugin's BukkitRunnables): a step runs after a delay, then every
 * period ticks, until it says it is done or has run its most times.
 */
public final class Tasks {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Task> RUNNING = new ArrayList<>(), ADDED = new ArrayList<>();
    /** Steps that threw (the tests check none do). */
    public static int errors;

    /** One step; n is how many times it ran before. False: done. */
    public interface Step {
        boolean run(int n);
    }

    private static final class Task {
        private final int period, most;
        private final Step step;
        private int wait, runs;

        Task(int delay, int period, int most, Step step) {
            this.wait = Math.max(1, delay);
            this.period = Math.max(1, period);
            this.most = most;
            this.step = step;
        }
    }

    private Tasks() {}

    /** Runs step after delay ticks, then every period ticks, at most `most` times. */
    public static void every(int delay, int period, int most, Step step) {
        ADDED.add(new Task(delay, period, most, step));
    }

    /** Runs once, after delay ticks. */
    public static void later(int delay, Runnable action) {
        every(delay, 1, 1, n -> {
            action.run();
            return false;
        });
    }

    static void tick() {
        RUNNING.addAll(ADDED);
        ADDED.clear();
        RUNNING.removeIf(task -> {
            if (--task.wait > 0) return false;
            task.wait = task.period;
            try {
                return !task.step.run(task.runs++) || task.runs >= task.most;
            } catch (RuntimeException e) {
                errors++;
                LOGGER.error("A spell went wrong", e);
                return true;
            }
        });
    }

    static void clear() {
        RUNNING.clear();
        ADDED.clear();
    }
}
