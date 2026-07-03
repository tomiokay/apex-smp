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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
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
    private final Set<UUID> testMode = new HashSet<>();
    private final Map<UUID, Double> trueDamageMarks = new HashMap<>();

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

    /**
     * Deals armor-piercing damage that still credits the attacker (for kill tokens),
     * knockback, and the hurt animation. The exact final amount is enforced by the
     * combat listener via {@link #consumeTrueDamageMark}.
     */
    private void dealTrueDamage(LivingEntity target, double amount, Player source) {
        trueDamageMarks.put(target.getUniqueId(), amount);
        target.damage(amount, source);
        // Clear the mark if no damage event fired (e.g. target was invulnerable).
        trueDamageMarks.remove(target.getUniqueId());
    }

    /** The combat listener calls this during the damage event to force true damage. */
    public Double consumeTrueDamageMark(UUID uuid) {
        return trueDamageMarks.remove(uuid);
    }

    /** Clears one player's ability cooldown. */
    public void resetCooldown(Player player) {
        cooldownUntil.remove(player.getUniqueId());
    }

    /** Clears every player's ability cooldown. */
    public void resetAllCooldowns() {
        cooldownUntil.clear();
    }

    public boolean isTestMode(Player player) {
        return testMode.contains(player.getUniqueId());
    }

    /** Toggles test mode; returns the new state (true = now on). */
    public boolean toggleTestMode(Player player) {
        if (testMode.remove(player.getUniqueId())) {
            return false;
        }
        testMode.add(player.getUniqueId());
        return true;
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
        boolean testing = isTestMode(player);
        if (!testing && !data.isAbilityUnlocked()) {
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
        if (!testing && readyAt != null && now < readyAt) {
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
        if (!testing) {
            int cooldown = plugin.getConfig().getInt("ability-cooldown-seconds", 45);
            cooldownUntil.put(player.getUniqueId(), now + cooldown * 1000L);
        }
        plugin.getApexLogger().log(ApexLogger.LogType.ABILITY,
                player.getName() + " used " + type.displayName() + " ability"
                        + (testing ? " (test mode)" : ""));
    }

    // ------------------------------------------------------------------
    // Targeting helper
    // ------------------------------------------------------------------

    /** The living entity the caster is looking at. Mobs count only in test mode. */
    private LivingEntity getTarget(Player caster, double range) {
        Entity target = caster.getTargetEntity((int) range, false);
        if (!(target instanceof LivingEntity hit) || hit.equals(caster)) {
            return null;
        }
        if (hit instanceof Player p) {
            if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) {
                return null;
            }
            return p;
        }
        return isTestMode(caster) ? hit : null;
    }

    /** All valid targets within radius. Mobs are included only in test mode. */
    private List<LivingEntity> nearbyTargets(Player caster, Location center, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        double rSq = radius * radius;
        for (Player other : center.getWorld().getPlayers()) {
            if (other.equals(caster) || other.getGameMode() == GameMode.SPECTATOR
                    || other.getGameMode() == GameMode.CREATIVE) {
                continue;
            }
            if (other.getLocation().distanceSquared(center) <= rSq) {
                result.add(other);
            }
        }
        if (isTestMode(caster)) {
            for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                if (e instanceof LivingEntity le && !(e instanceof Player) && !le.equals(caster)
                        && !isOwnPet(caster, le)
                        && le.getLocation().distanceSquared(center) <= rSq) {
                    result.add(le);
                }
            }
        }
        return result;
    }

    /** True if the entity is a tamed pet owned by the caster (never a valid target). */
    private boolean isOwnPet(Player caster, LivingEntity entity) {
        return entity instanceof org.bukkit.entity.Tameable t && t.isTamed()
                && caster.equals(t.getOwner());
    }

    /** Player-only message helper; silently no-ops for mob targets. */
    private void tell(LivingEntity target, String message) {
        if (target instanceof Player p) {
            Msg.send(p, message);
        }
    }

    /** Draws a flat particle ring around a center point (for dramatic AoE flair). */
    private void particleRing(Location center, Particle particle, double radius, int points, double y) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            Location p = center.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            center.getWorld().spawnParticle(particle, p, 1, 0, 0, 0, 0);
        }
    }

    private void dustRing(Location center, org.bukkit.Color color, float size, double radius, int points, double y) {
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            Location p = center.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            center.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust);
        }
    }

    // ------------------------------------------------------------------
    // Abilities
    // ------------------------------------------------------------------

    /** Lion - Blood Frenzy: +20% damage dealt for 10 seconds. */
    private boolean castLion(Player player) {
        lionBuffUntil.put(player.getUniqueId(), System.currentTimeMillis() + 10_000L);
        Location loc = player.getLocation();
        player.getWorld().playSound(loc, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 1.3f);
        player.getWorld().playSound(loc, Sound.ITEM_TOTEM_USE, 0.7f, 1.4f);
        player.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, loc.clone().add(0, 1.5, 0), 15, 0.5, 0.5, 0.5);
        player.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 50, 0.6, 0.9, 0.6, 0.06);
        player.getWorld().spawnParticle(Particle.LAVA, loc, 12, 0.5, 0.3, 0.5);
        dustRing(loc, org.bukkit.Color.fromRGB(255, 160, 0), 1.6f, 1.4, 24, 0.2);
        // Lingering flame aura while the buff is active.
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ >= 20 || !player.isOnline() || !hasLionBuff(player)) {
                    cancel();
                    return;
                }
                dustRing(player.getLocation(), org.bukkit.Color.fromRGB(200, 40, 0), 1.2f, 0.9, 10, 1.0);
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0),
                        4, 0.4, 0.5, 0.4, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 10L);
        Msg.send(player, "<gold>Blood Frenzy!</gold> <yellow>+20% damage for 10 seconds.</yellow>");
        return true;
    }

    /** Wolf - Pack Hunt: 5 wolves + glowing on all enemies within 30 blocks for 20s. */
    private boolean castWolf(Player player) {
        int lifetime = plugin.getConfig().getInt("wolf-lifetime-seconds", 30);
        double radius = plugin.getConfig().getDouble("wolf-tracking-radius", 30);
        Location origin = player.getLocation();
        player.getWorld().spawnParticle(Particle.SONIC_BOOM, origin.clone().add(0, 1, 0), 1);
        dustRing(origin, org.bukkit.Color.fromRGB(120, 120, 130), 1.5f, 2.0, 30, 0.1);

        // Find enemies BEFORE summoning so the pack never targets or glows itself.
        List<LivingEntity> enemies = nearbyTargets(player, origin, radius);
        for (LivingEntity enemy : enemies) {
            enemy.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 20, 0, true, false));
        }
        LivingEntity firstEnemy = enemies.isEmpty() ? null : enemies.get(0);

        int effectTicks = lifetime * 20;
        List<Wolf> pack = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            double angle = Math.PI * 2 * i / 5;
            Location spawn = origin.clone().add(Math.cos(angle) * 2, 0, Math.sin(angle) * 2);
            spawn.getWorld().spawnParticle(Particle.POOF, spawn.clone().add(0, 0.5, 0), 25, 0.3, 0.4, 0.3, 0.05);
            spawn.getWorld().spawnParticle(Particle.CLOUD, spawn.clone().add(0, 0.3, 0), 15, 0.2, 0.2, 0.2, 0.03);
            spawn.getWorld().playSound(spawn, Sound.ENTITY_WOLF_GROWL, 0.8f, 1.1f);
            Wolf wolf = spawn.getWorld().spawn(spawn, Wolf.class, w -> {
                w.setTamed(true);
                w.setOwner(player);
                w.setAdult();
                // Do not save to disk, so they vanish on chunk unload / relog instead of lingering.
                w.setPersistent(false);
                w.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, effectTicks, 0, true, false));
                w.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, effectTicks, 1, true, false));
                if (w.getEquipment() != null) {
                    w.getEquipment().setItem(org.bukkit.inventory.EquipmentSlot.BODY,
                            new org.bukkit.inventory.ItemStack(Material.WOLF_ARMOR));
                }
                if (firstEnemy != null) {
                    w.setTarget(firstEnemy);
                }
            });
            pack.add(wolf);
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Wolf wolf : pack) {
                    if (wolf.isValid()) {
                        wolf.getWorld().spawnParticle(Particle.POOF, wolf.getLocation(), 8, 0.3, 0.3, 0.3, 0.02);
                    }
                    wolf.remove();
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
        player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_STEP, 1f, 0.6f);
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
                Location trail = player.getLocation();
                trail.getWorld().spawnParticle(Particle.CLOUD, trail, 10, 0.3, 0.2, 0.3, 0.03);
                trail.getWorld().spawnParticle(Particle.BLOCK, trail, 12, 0.3, 0.1, 0.3,
                        trail.getBlock().getRelative(0, -1, 0).getBlockData());
                dustRing(trail, org.bukkit.Color.fromRGB(120, 90, 60), 1.4f, 0.8, 8, 0.1);
                for (LivingEntity hit : nearbyTargets(player, player.getLocation(), 1.4)) {
                    dealTrueDamage(hit, 9.0, player); // true 4.5 hearts through armor
                    hit.setVelocity(dir.clone().multiply(2.0).setY(0.7)); // medium-high knockback
                    Location impact = hit.getLocation().add(0, 1, 0);
                    hit.getWorld().playSound(hit.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.8f);
                    hit.getWorld().spawnParticle(Particle.EXPLOSION, impact, 2);
                    hit.getWorld().spawnParticle(Particle.CRIT, impact, 25, 0.4, 0.4, 0.4, 0.6);
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
        LivingEntity target = getTarget(player, 5);
        if (target == null) {
            Msg.send(player, "<red>No target in range - look at a target within 5 blocks.</red>");
            return false;
        }
        player.swingMainHand();
        dealTrueDamage(target, 8.0, player); // true 4 hearts through armor
        Location slashAt = target.getLocation().add(0, 1, 0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.8f, 1.5f);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, slashAt, 5);
        player.getWorld().spawnParticle(Particle.CRIT, slashAt, 30, 0.5, 0.5, 0.5, 0.6);
        player.getWorld().spawnParticle(Particle.DUST, slashAt, 30, 0.5, 0.6, 0.5,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(140, 0, 0), 1.5f));
        tell(target, "<dark_red>You are bleeding! Half a heart per second for 10 seconds.</dark_red>");
        new BukkitRunnable() {
            int seconds = 0;

            @Override
            public void run() {
                if (seconds++ >= 10 || !target.isValid()) {
                    cancel();
                    return;
                }
                dealTrueDamage(target, 1.0, player); // true half a heart per second through armor
                Location b = target.getLocation().add(0, 1, 0);
                target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, b, 6, 0.3, 0.4, 0.3, 0.05);
                target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 0.3, 0),
                        8, 0.3, 0.1, 0.3, new Particle.DustOptions(org.bukkit.Color.fromRGB(120, 0, 0), 1.2f));
            }
        }.runTaskTimer(plugin, 20L, 20L);
        Msg.send(player, "<dark_green>Rend!</dark_green> <yellow>" + target.getName() + " is bleeding.</yellow>");
        return true;
    }

    /**
     * Polar Bear - Deep Freeze: stun the target for 3 seconds inside a swirl of 3 spinning
     * ice shards and blue frost. The freeze visual is applied, but the target can still
     * right-click (eat golden apples, drink potions, heal) like every stun in this plugin.
     */
    private boolean castPolarBear(Player player) {
        LivingEntity target = getTarget(player, 6);
        if (target == null) {
            Msg.send(player, "<red>No target in range - look at a target within 6 blocks.</red>");
            return false;
        }
        stunManager.stun(target, 60);
        target.setFreezeTicks(target.getMaxFreezeTicks());
        Location loc = target.getLocation();
        target.getWorld().playSound(loc, Sound.BLOCK_GLASS_PLACE, 1f, 0.6f);
        target.getWorld().playSound(loc, Sound.BLOCK_POWDER_SNOW_PLACE, 1f, 0.8f);
        target.getWorld().spawnParticle(Particle.BLOCK, loc.clone().add(0, 1, 0), 40, 0.4, 0.7, 0.4,
                Material.BLUE_ICE.createBlockData());

        // Three little floating ice blocks that orbit the target.
        final org.bukkit.entity.BlockDisplay[] shards = new org.bukkit.entity.BlockDisplay[3];
        for (int i = 0; i < 3; i++) {
            shards[i] = target.getWorld().spawn(loc, org.bukkit.entity.BlockDisplay.class, d -> {
                d.setBlock(Material.BLUE_ICE.createBlockData());
                d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                d.setTransformation(new org.bukkit.util.Transformation(
                        new org.joml.Vector3f(-0.15f, -0.15f, -0.15f),
                        new org.joml.AxisAngle4f(0, 0, 0, 1),
                        new org.joml.Vector3f(0.3f, 0.3f, 0.3f),
                        new org.joml.AxisAngle4f(0, 0, 0, 1)));
            });
        }

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= 60 || !target.isValid()) {
                    for (org.bukkit.entity.BlockDisplay shard : shards) {
                        if (shard != null && shard.isValid()) {
                            shard.getWorld().spawnParticle(Particle.SNOWFLAKE, shard.getLocation(),
                                    10, 0.2, 0.2, 0.2, 0.03);
                            shard.remove();
                        }
                    }
                    if (target.isValid()) {
                        stunManager.release(target);
                    }
                    cancel();
                    return;
                }
                target.setFreezeTicks(target.getMaxFreezeTicks());
                Location c = target.getLocation();
                for (int i = 0; i < shards.length; i++) {
                    double angle = t * 0.22 + Math.PI * 2 * i / shards.length;
                    double y = 0.7 + Math.sin(t * 0.18 + i * 2.0) * 0.35;
                    Location orbit = new Location(c.getWorld(),
                            c.getX() + Math.cos(angle) * 1.1, c.getY() + y, c.getZ() + Math.sin(angle) * 1.1);
                    if (shards[i] != null && shards[i].isValid()) {
                        shards[i].teleport(orbit);
                    }
                    orbit.getWorld().spawnParticle(Particle.DUST, orbit, 3, 0.08, 0.08, 0.08,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(130, 205, 255), 1.1f));
                    orbit.getWorld().spawnParticle(Particle.SNOWFLAKE, orbit, 1, 0.05, 0.05, 0.05, 0.005);
                }
                // Blue frost ring swirling at the target's feet.
                double ring = t * 0.32;
                Location rp = c.clone().add(Math.cos(ring) * 0.9, 0.15, Math.sin(ring) * 0.9);
                c.getWorld().spawnParticle(Particle.DUST, rp, 1, 0, 0, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(160, 225, 255), 1.3f));
                t++;
            }
        }.runTaskTimer(plugin, 1L, 1L);

        Msg.send(player, "<aqua>Deep Freeze!</aqua> <yellow>" + target.getName() + " is frozen for 3 seconds.</yellow>");
        tell(target, "<aqua>You are frozen for 3 seconds - you can still eat and heal!</aqua>");
        return true;
    }

    /** Snake - Venomous Bite: Poison II for 15 seconds. */
    private boolean castSnake(Player player) {
        LivingEntity target = getTarget(player, 4);
        if (target == null) {
            Msg.send(player, "<red>No target in range - look at a target within 4 blocks.</red>");
            return false;
        }
        player.swingMainHand();
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 15 * 20, 1, false, true));
        Location bite = target.getLocation().add(0, 1, 0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SPIDER_HURT, 1f, 1.5f);
        player.getWorld().playSound(bite, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.3f);
        target.getWorld().spawnParticle(Particle.ITEM_SLIME, bite, 30, 0.4, 0.6, 0.4, 0.05);
        target.getWorld().spawnParticle(Particle.SNEEZE, bite, 20, 0.4, 0.5, 0.4, 0.05);
        target.getWorld().spawnParticle(Particle.DUST, bite, 25, 0.4, 0.6, 0.4,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(60, 200, 30), 1.4f));
        Msg.send(player, "<green>Venomous Bite!</green> <yellow>" + target.getName()
                + " is poisoned for 15 seconds.</yellow>");
        tell(target, "<green>You were bitten - Poison II for 15 seconds!</green>");
        return true;
    }

    /** Panther - Shadow Dance: 3 teleport slashes of 2.5 hearts; target stunned until it ends. */
    private boolean castPanther(Player player) {
        LivingEntity target = getTarget(player, 8);
        if (target == null) {
            Msg.send(player, "<red>No target in range - look at a target within 8 blocks.</red>");
            return false;
        }
        stunManager.stun(target, 40); // stunned for the ~1.5s of slashes plus a beat
        tell(target, "<dark_purple>A panther dances around you - you are stunned!</dark_purple>");
        new BukkitRunnable() {
            int slash = 0;

            @Override
            public void run() {
                if (slash >= 3 || !player.isOnline() || !target.isValid()) {
                    if (target.isValid()) {
                        stunManager.release(target);
                    }
                    cancel();
                    return;
                }
                double angle = Math.PI * 2 * slash / 3 + Math.PI / 6;
                Location spot = target.getLocation().clone()
                        .add(Math.cos(angle) * 1.8, 0, Math.sin(angle) * 1.8);
                spot.setDirection(target.getLocation().toVector().subtract(spot.toVector()));
                // Trail of shadow between where the panther was and where it reappears.
                player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0),
                        15, 0.2, 0.4, 0.2, 0.02);
                player.teleport(spot);
                player.swingMainHand(); // actually swing the sword
                target.setNoDamageTicks(0); // let every slash land through i-frames
                dealTrueDamage(target, 5.0, player); // true 2.5 hearts through armor
                Location mid = target.getLocation().add(0, 1, 0);
                player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, mid, 3);
                player.getWorld().spawnParticle(Particle.CRIT, mid, 20, 0.4, 0.4, 0.4, 0.4);
                player.getWorld().spawnParticle(Particle.DUST, mid, 25, 0.5, 0.6, 0.5,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(80, 0, 120), 1.4f));
                player.getWorld().spawnParticle(Particle.PORTAL, spot, 30, 0.3, 0.8, 0.3);
                player.getWorld().playSound(spot, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.4f);
                player.getWorld().playSound(mid, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.2f);
                slash++;
            }
        }.runTaskTimer(plugin, 0L, 10L);
        return true;
    }

    /** Hippo - Riverquake: leap up (keeping momentum), slam for 4 hearts + huge knockback, 5 block radius. */
    private boolean castHippo(Player player) {
        // Keep the player's current horizontal momentum (leap forward if moving) and
        // launch ~4.5 blocks up (about 1.5 blocks higher than before).
        Vector momentum = player.getVelocity();
        player.setVelocity(new Vector(momentum.getX() * 1.5, 1.15, momentum.getZ() * 1.5));
        Location launch = player.getLocation();
        player.getWorld().playSound(launch, Sound.ENTITY_HOGLIN_ANGRY, 1f, 0.6f);
        player.getWorld().spawnParticle(Particle.CLOUD, launch, 30, 0.4, 0.1, 0.4, 0.1);
        player.getWorld().spawnParticle(Particle.SPLASH, launch, 40, 0.5, 0.2, 0.5, 0.1);
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
                // Trail while airborne.
                player.getWorld().spawnParticle(Particle.SPLASH, player.getLocation(), 6, 0.2, 0.2, 0.2, 0.02);
                if (!descending && ticks >= 13) {
                    // Slam straight down but keep the forward drift from the leap.
                    Vector v = player.getVelocity();
                    player.setVelocity(new Vector(v.getX(), -1.8, v.getZ()));
                    descending = true;
                }
                if (descending && player.isOnGround()) {
                    slam();
                    cancel();
                }
            }

            private void slam() {
                Location center = player.getLocation();
                center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 3);
                center.getWorld().spawnParticle(Particle.BLOCK, center, 120, 3.0, 0.3, 3.0,
                        center.getBlock().getRelative(0, -1, 0).getBlockData());
                center.getWorld().spawnParticle(Particle.SPLASH, center, 80, 3.0, 0.4, 3.0, 0.2);
                center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
                center.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
                // Expanding shockwave rings.
                for (int r = 1; r <= 5; r++) {
                    dustRing(center, org.bukkit.Color.fromRGB(90, 140, 200), 1.6f, r, 12 * r, 0.1);
                    particleRing(center, Particle.CLOUD, r, 10 * r, 0.15);
                }
                for (LivingEntity hit : nearbyTargets(player, center, 5)) {
                    dealTrueDamage(hit, 8.0, player); // true 4 hearts through armor
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

    /** Dragon - Skyfall: 2.5 seconds of flight, then a mace-like ground slam for a true 4 hearts. */
    private boolean castDragon(Player player) {
        boolean couldFly = player.getAllowFlight();
        player.setAllowFlight(true);
        player.setFlying(true);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
        Msg.send(player, "<light_purple>Skyfall!</light_purple> <yellow>2.5 seconds of flight, then you slam down.</yellow>");
        // Purple flight aura for the 2.5s airborne phase.
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ >= 50 || !player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                Location w = player.getLocation();
                w.getWorld().spawnParticle(Particle.DRAGON_BREATH, w.clone().add(0, 0.2, 0), 8, 0.5, 0.2, 0.5, 0.01);
                dustRing(w, org.bukkit.Color.fromRGB(170, 40, 220), 1.3f, 1.0, 8, 0.4);
                if (t % 6 == 0) {
                    w.getWorld().playSound(w, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 1.1f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
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
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 1f, 0.8f);
                new BukkitRunnable() {
                    int ticks = 0;

                    @Override
                    public void run() {
                        if (!player.isOnline() || player.isDead() || ticks++ > 80) {
                            cancel();
                            return;
                        }
                        Location w = player.getLocation();
                        w.getWorld().spawnParticle(Particle.DRAGON_BREATH, w, 20, 0.3, 0.3, 0.3, 0.03);
                        w.getWorld().spawnParticle(Particle.FLAME, w, 8, 0.2, 0.2, 0.2, 0.02);
                        if (player.isOnGround()) {
                            Location center = player.getLocation();
                            center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 4);
                            center.getWorld().spawnParticle(Particle.DRAGON_BREATH, center, 120, 3.0, 0.4, 3.0, 0.15);
                            center.getWorld().spawnParticle(Particle.FLAME, center, 80, 2.5, 0.3, 2.5, 0.1);
                            center.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.2f);
                            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.9f);
                            for (int r = 1; r <= 4; r++) {
                                dustRing(center, org.bukkit.Color.fromRGB(150, 30, 210), 1.7f, r, 12 * r, 0.1);
                            }
                            for (LivingEntity hit : nearbyTargets(player, center, 4)) {
                                dealTrueDamage(hit, 8.0, player); // true 4 hearts, ignores armor
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
        }.runTaskLater(plugin, 50L); // 2.5 seconds of flight
        return true;
    }
}
