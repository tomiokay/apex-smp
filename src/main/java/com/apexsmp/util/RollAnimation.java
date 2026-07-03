package com.apexsmp.util;

import com.apexsmp.apex.ApexType;
import net.kyori.adventure.title.Title;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Slot-machine style title animation: cycles apex names fast, decelerates,
 * then reveals the result with a fanfare. Calls onDone with the rolled apex.
 */
public final class RollAnimation {

    private static final Random RANDOM = new Random();
    // Gaps (ticks) between each name flash - accelerating drum roll that slows to a stop.
    private static final int[] STEPS = {2, 2, 2, 2, 3, 3, 3, 4, 4, 5, 6, 7, 9, 11, 14};

    private RollAnimation() {
    }

    public static void play(Plugin plugin, Player player, ApexType result, Consumer<ApexType> onDone) {
        List<ApexType> pool = ApexType.rollable();

        new BukkitRunnable() {
            int step = 0;
            int countdown = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (countdown-- > 0) {
                    return;
                }
                if (step < STEPS.length) {
                    ApexType flash = pool.get(RANDOM.nextInt(pool.size()));
                    player.showTitle(Title.title(
                            Msg.mm(flash.coloredName()),
                            Msg.mm("<gray>Rolling your apex...</gray>"),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(600), Duration.ZERO)));
                    float pitch = 0.8f + (step / (float) STEPS.length) * 1.0f;
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, pitch);
                    countdown = STEPS[step++];
                    return;
                }
                // Reveal
                player.showTitle(Title.title(
                        Msg.mm(result.coloredName()),
                        Msg.mm("<yellow>is your apex predator!</yellow>"),
                        Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofSeconds(1))));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.6f);
                player.getWorld().spawnParticle(Particle.FIREWORK,
                        player.getLocation().add(0, 1, 0), 50, 0.5, 0.7, 0.5, 0.1);
                cancel();
                onDone.accept(result);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
