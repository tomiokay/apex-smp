package com.apexsmp.ability;

import com.apexsmp.ApexPlugin;
import com.apexsmp.apex.ApexType;
import com.apexsmp.apex.PlayerApexData;
import com.apexsmp.log.ApexLogger;
import com.apexsmp.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import org.bukkit.GameMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dispatches and implements every apex ability. Damage cap per the design:
 * no single ability hit exceeds 5.5 hearts (11.0 damage).
 */
public class AbilityManager {

    private final ApexPlugin plugin;
    private final StunManager stunManager;

    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, Long> lionBuffUntil = new HashMap<>();
    private final Set<UUID> noFallDamage = new HashSet<>();

    public AbilityManager(ApexPlugin plugin, StunManager stunManager) {
        this.plugin = plugin;
        this.stunManager = stunManager;
    }

    public StunManager getStunManager() {
        return stunManager;
    }

    public boolean hasLionBuff(Player player) {
        Long until = lionBuffUntil.get(player.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }

    /** Consumed by the combat listener to cancel fall damage after slam abilities. */
    public boolean consumeNoFallDamage(Player player) {
        return noFallDamage.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Dispatch
    // ------------------------------------------------------------------

    public void cast(Player player) {
        PlayerApexData data = plugin.getApexManager().getData(player.getUniqueId());
        ApexType type = data.getApex();
        if (type == null) {
            Msg.send(player, "<red>You have no apex yet. Wait for your roll!</red>");
            return;
        }
        if (!data.isAbilityUnlocked()) {
            Msg.send(player, "<red>Your ability is locked.</red> <gray>Consume "
                    + plugin.getApexManager().tokensToUnlock() + " kill tokens to evolve ("
                    + data.getTokensConsumed() + "/" + plugin.getApexManager().tokensToUnlock() + ").</gray>");
            return;
        }
        if (stunManager.isStunned(player)) {
            Msg.send(player, "<red>You are stunned!</red>");
            return;
        }
        long now = System.currentTimeMillis();
        Long readyAt = cooldownUntil.get(player.getUniqueId());
        if (readyAt != null && now < readyAt) {
            Msg.send(player, "<red>Ability on cooldown:</red> <yellow>"
                    + ((readyAt - now) / 1000 + 1) + "s</yellow>");
            return;
        }

        boolean success = switch (type) {
            case LION -> castLion(player);
            case WOLF -> castWolf(player);
            case RHINO -> castRhino(player);
            case TREX -> castTrex(player);
            case POLAR_BEAR -> castPolarBear(player);
            case SNAKE -> castSnake(player);
            case PANTHER -> castPanther(player);
            case HIPPO -> castHippo(player);
            case DRAGON -> castDragon(player);
        };
        if (!success) {
            return;
        }
        int cooldown = plugin.getConfig().getInt("ability-cooldown-seconds", 45);
        cooldownUntil.put(player.getUniqueId(), now + cooldown * 1000L);
        plugin.getApexLogger().log(ApexLogger.LogType.ABILITY,
                player.getName() + " used " + type.displayName() + " ability");
    }

    // ------------------------------------------------------------------
    // Targeting helper
    // ------------------------------------------------------------------

    private Player getTargetPlayer(Player caster, double range) {
        Entity target = caster.getTargetEntity((int) range, false);
        if (target instanceof Player hit && !hit.equals(caster)
                && hit.getGameMode() != GameMode.SPECTATOR && hit.getGameMode() != GameMode.CREATIVE) {
            return hit;
        }
        return null;
    }

    private List<Player> nearbyEnemies(Player caster, Location center, double radius) {
        List<Player> result = new ArrayList<>();
        for (Player other : center.getWorld().getPlayers()) {
            if (other.equals(caster) || other.getGameMode() == GameMode.SPECTATOR
                    || other.getGameMode() == GameMode.CREATIVE) {
                continue;
            }
            if (other.getLocation().distanceSquared(center) <= radius * radius) {
                result.add(other);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Abilities
    // ------------------------------------------------------------------

    /** Lion - Blood Frenzy: +20% damage dealt for 10 seconds. */
    private boolean castLion(Player player) {
        lionBuffUntil.put(player.getUniqueId(), System.currentTimeMillis() + 10_000L);
        player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 1.3f);
        player.getWorld().spawnParticle(Particle.ANGRY_VILLAGER,
                player.getLocation().add(0, 1.5, 0), 15, 0.5, 0.5, 0.5);
        Msg.send(player, "<gold>Blood Frenzy!</gold> <yellow>+20% damage for 10 seconds.</yellow>");
        return true;
    }

    /** Wolf - Pack Hunt: 5 wolves + glowing on all enemies within 30 blocks for 20s. */
    private boolean castWolf(Player player) {
        int lifetime = plugin.getConfig().getInt("wolf-lifetime-seconds", 30);
        double radius = plugin.getConfig().getDouble("wolf-tracking-radius", 30);
        List<Wolf> pack = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            double angle = Math.PI * 2 * i / 5;
            Location spawn = player.getLocation().clone().add(Math.cos(angle) * 2, 0, Math.sin(angle) * 2);
            Wolf wolf = player.getWorld().spawn(spawn, Wolf.class, w -> {
                w.setTamed(true);
                w.setOwner(player);
                w.setAdult();
            });
            pack.add(wolf);
        }
        List<Player> enemies = nearbyEnemies(player, player.getLocation(), radius);
        for (Player enemy : enemies) {
            enemy.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 20, 0, true, false));
        }
        if (!enemies.isEmpty() && !pack.isEmpty()) {
            pack.get(0).setTarget(enemies.get(0));
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Wolf wolf : pack) {
                    if (wolf.isValid()) {
                        wolf.getWorld().spawnParticle(Particle.POOF, wolf.getLocation(), 8, 0.3, 0.3, 0.3, 0.02);
                        wolf.remove();
                    }
                }
            }
        }.runTaskLater(plugin, lifetime * 20L);
        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_GROWL, 1f, 0.8f);
        Msg.send(player, "<gray>Pack Hunt!</gray> <yellow>" + enemies.size()
                + " enemies revealed for 20 seconds.</yellow>");
        return true;
    }

    /** Rhino - Charge: 7 block dash; a player in the way takes 4.5 hearts + heavy knockback. */
    private boolean castRhino(Player player) {
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, 1f, 0.7f);
        new BukkitRunnable() {
            final Location start = player.getLocation().clone();
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                // 7 block total distance at ~0.7 blocks/tick.
                if (ticks++ >= 10 || player.getLocation().distanceSquared(start) >= 49) {
                    cancel();
                    return;
                }
                player.setVelocity(dir.clone().multiply(0.9).setY(player.getVelocity().getY() * 0.5));
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 6, 0.2, 0.2, 0.2, 0.02);
                for (Player hit : nearbyEnemies(player, player.getLocation(), 1.4)) {
                    hit.damage(9.0, player); // 4.5 hearts
                    hit.setVelocity(dir.clone().multiply(2.0).setY(0.7)); // medium-high knockback
                    hit.getWorld().playSound(hit.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.8f);
                    player.setVelocity(new Vector(0, 0, 0));
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /** T-Rex - Rend: 4 heart slash + bleed of half a heart per second for 10 seconds. */
    private boolean castTrex(Player player) {
        Player target = getTargetPlayer(player, 5);
        if (target == null) {
            Msg.send(player, "<red>No target in range - look at a player within 5 blocks.</red>");
            return false;
        }
        target.damage(8.0, player); // 4 hearts
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 3);
        Msg.send(target, "<dark_red>You are bleeding! Half a heart per second for 10 seconds.</dark_red>");
        new BukkitRunnable() {
            int seconds = 0;

            @Override
            public void run() {
                if (seconds++ >= 10 || !target.isOnline() || target.isDead()) {
                    cancel();
                    return;
                }
                target.damage(1.0); // half a heart per second, ignores the attacker so no kill-credit spam
                target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                        target.getLocation().add(0, 1, 0), 5, 0.3, 0.4, 0.3, 0.05);
            }
        }.runTaskTimer(plugin, 20L, 20L);
        Msg.send(player, "<dark_green>Rend!</dark_green> <yellow>" + target.getName() + " is bleeding.</yellow>");
        return true;
    }

    /** Polar Bear - Deep Freeze: encase the target in an ice cube and stun for 3 seconds. */
    private boolean castPolarBear(Player player) {
        Player target = getTargetPlayer(player, 6);
        if (target == null) {
            Msg.send(player, "<red>No target in range - look at a player within 6 blocks.</red>");
            return false;
        }
        stunManager.stun(target, 60);
        target.setFreezeTicks(target.getMaxFreezeTicks());
        target.playSound(target.getLocation(), Sound.BLOCK_GLASS_PLACE, 1f, 0.6f);

        // Build a hollow ice shell around the target, restoring the old blocks after 3s.
        List<BlockState> replaced = new ArrayList<>();
        Location feet = target.getLocation().getBlock().getLocation();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    boolean shell = Math.abs(dx) == 1 || Math.abs(dz) == 1 || dy == -1 || dy == 2;
                    if (!shell) {
                        continue;
                    }
                    Block block = feet.clone().add(dx, dy, dz).getBlock();
                    if (block.getType().isAir() || block.isReplaceable()) {
                        replaced.add(block.getState());
                        block.setType(Material.PACKED_ICE);
                    }
                }
            }
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                for (BlockState state : replaced) {
                    // Only restore if our ice is still there (avoid clobbering mined blocks).
                    if (state.getBlock().getType() == Material.PACKED_ICE) {
                        state.update(true, false);
                    }
                }
                if (target.isOnline()) {
                    stunManager.release(target);
                }
            }
        }.runTaskLater(plugin, 60L);
        Msg.send(player, "<aqua>Deep Freeze!</aqua> <yellow>" + target.getName() + " is frozen for 3 seconds.</yellow>");
        Msg.send(target, "<aqua>You have been frozen solid for 3 seconds!</aqua>");
        return true;
    }

    /** Snake - Venomous Bite: Poison II for 15 seconds. */
    private boolean castSnake(Player player) {
        Player target = getTargetPlayer(player, 4);
        if (target == null) {
            Msg.send(player, "<red>No target in range - look at a player within 4 blocks.</red>");
            return false;
        }
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 15 * 20, 1, false, true));
        player.playSound(player.getLocation(), Sound.ENTITY_SPIDER_HURT, 1f, 1.5f);
        target.getWorld().spawnParticle(Particle.ITEM_SLIME, target.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3);
        Msg.send(player, "<green>Venomous Bite!</green> <yellow>" + target.getName()
                + " is poisoned for 15 seconds.</yellow>");
        Msg.send(target, "<green>You were bitten - Poison II for 15 seconds!</green>");
        return true;
    }

    /** Panther - Shadow Dance: 3 teleport slashes of 2.5 hearts; target stunned until it ends. */
    private boolean castPanther(Player player) {
        Player target = getTargetPlayer(player, 8);
        if (target == null) {
            Msg.send(player, "<red>No target in range - look at a player within 8 blocks.</red>");
            return false;
        }
        stunManager.stun(target, 40); // stunned for the ~1.5s of slashes plus a beat
        Msg.send(target, "<dark_purple>A panther dances around you - you are stunned!</dark_purple>");
        new BukkitRunnable() {
            int slash = 0;

            @Override
            public void run() {
                if (slash >= 3 || !player.isOnline() || !target.isOnline() || target.isDead()) {
                    if (target.isOnline() && !target.isDead()) {
                        stunManager.release(target);
                    }
                    cancel();
                    return;
                }
                double angle = Math.PI * 2 * slash / 3 + Math.PI / 6;
                Location spot = target.getLocation().clone()
                        .add(Math.cos(angle) * 1.8, 0, Math.sin(angle) * 1.8);
                spot.setDirection(target.getLocation().toVector().subtract(spot.toVector()));
                player.teleport(spot);
                target.damage(5.0, player); // 2.5 hearts per slash
                player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 2);
                player.getWorld().spawnParticle(Particle.PORTAL, spot, 20, 0.3, 0.6, 0.3);
                player.getWorld().playSound(spot, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.4f);
                slash++;
            }
        }.runTaskTimer(plugin, 0L, 10L);
        return true;
    }

    /** Hippo - Riverquake: leap 3 blocks up, slam for 4 hearts + huge knockback, 5 block radius. */
    private boolean castHippo(Player player) {
        player.setVelocity(new Vector(0, 0.95, 0)); // ~3 blocks of height
        player.playSound(player.getLocation(), Sound.ENTITY_HOGLIN_ANGRY, 1f, 0.6f);
        noFallDamage.add(player.getUniqueId());
        new BukkitRunnable() {
            int ticks = 0;
            boolean descending = false;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || ticks++ > 80) {
                    cancel();
                    return;
                }
                if (!descending && ticks >= 8) {
                    player.setVelocity(new Vector(0, -1.8, 0));
                    descending = true;
                }
                if (descending && player.isOnGround()) {
                    slam();
                    cancel();
                }
            }

            private void slam() {
                Location center = player.getLocation();
                center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 2);
                center.getWorld().spawnParticle(Particle.BLOCK, center, 80, 2.5, 0.3, 2.5,
                        center.getBlock().getRelative(0, -1, 0).getBlockData());
                center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
                for (Player hit : nearbyEnemies(player, center, 5)) {
                    hit.damage(8.0, player); // 4 hearts
                    Vector away = hit.getLocation().toVector().subtract(center.toVector()).setY(0);
                    if (away.lengthSquared() < 0.01) {
                        away = new Vector(1, 0, 0);
                    }
                    hit.setVelocity(away.normalize().multiply(2.6).setY(1.1)); // very high knockback
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        Msg.send(player, "<blue>Riverquake!</blue>");
        return true;
    }

    /** Dragon - Skyfall: 5 seconds of flight, then a ground slam for 4.5 hearts. */
    private boolean castDragon(Player player) {
        boolean couldFly = player.getAllowFlight();
        player.setAllowFlight(true);
        player.setFlying(true);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1f);
        Msg.send(player, "<light_purple>Skyfall!</light_purple> <yellow>5 seconds of flight, then you slam down.</yellow>");
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    return;
                }
                if (player.getGameMode() != GameMode.CREATIVE) {
                    player.setAllowFlight(couldFly);
                    player.setFlying(false);
                }
                noFallDamage.add(player.getUniqueId());
                player.setVelocity(new Vector(0, -2.5, 0));
                new BukkitRunnable() {
                    int ticks = 0;

                    @Override
                    public void run() {
                        if (!player.isOnline() || player.isDead() || ticks++ > 80) {
                            cancel();
                            return;
                        }
                        player.getWorld().spawnParticle(Particle.DRAGON_BREATH, player.getLocation(), 10, 0.3, 0.3, 0.3, 0.02);
                        if (player.isOnGround()) {
                            Location center = player.getLocation();
                            center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 3);
                            center.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.2f);
                            for (Player hit : nearbyEnemies(player, center, 4)) {
                                hit.damage(9.0, player); // 4.5 hearts
                                Vector away = hit.getLocation().toVector().subtract(center.toVector()).setY(0);
                                if (away.lengthSquared() < 0.01) {
                                    away = new Vector(1, 0, 0);
                                }
                                hit.setVelocity(away.normalize().multiply(1.4).setY(0.8));
                            }
                            cancel();
                        }
                    }
                }.runTaskTimer(plugin, 1L, 1L);
            }
        }.runTaskLater(plugin, 100L);
        return true;
    }
}
