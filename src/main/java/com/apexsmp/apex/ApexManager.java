package com.apexsmp.apex;

import com.apexsmp.ApexPlugin;
import com.apexsmp.log.ApexLogger;
import com.apexsmp.util.Msg;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns all player apex data: assignment, passives, token progression and persistence.
 */
public class ApexManager {

    private final ApexPlugin plugin;
    private final Map<UUID, PlayerApexData> players = new HashMap<>();
    private boolean smpStarted;

    private final NamespacedKey maxHealthKey;
    private final NamespacedKey speedKey;
    private final NamespacedKey crouchSpeedKey;

    public ApexManager(ApexPlugin plugin) {
        this.plugin = plugin;
        this.maxHealthKey = new NamespacedKey(plugin, "apex_max_health");
        this.speedKey = new NamespacedKey(plugin, "apex_speed");
        this.crouchSpeedKey = new NamespacedKey(plugin, "apex_crouch_speed");
    }

    public PlayerApexData getData(UUID uuid) {
        return players.computeIfAbsent(uuid, id -> new PlayerApexData());
    }

    public Map<UUID, PlayerApexData> allData() {
        return players;
    }

    public boolean isSmpStarted() {
        return smpStarted;
    }

    public void setSmpStarted(boolean smpStarted) {
        this.smpStarted = smpStarted;
    }

    public int tokensToUnlock() {
        return plugin.getConfig().getInt("tokens-to-unlock", 3);
    }

    // ------------------------------------------------------------------
    // Assignment
    // ------------------------------------------------------------------

    /** Sets a player's apex, swaps passives, resets progression state for the new class. */
    public void assignApex(Player player, ApexType type, String reason) {
        PlayerApexData data = getData(player.getUniqueId());
        data.setApex(type);
        data.setSnakeHitCounter(0);
        data.setLastKnownName(player.getName());
        applyPassives(player);
        plugin.getApexLogger().log(ApexLogger.LogType.ROLL,
                player.getName() + " became " + type.displayName() + " (" + reason + ")");
        Msg.send(player, "You are now the " + type.coloredName() + "<yellow>!</yellow>");
        Msg.send(player, "<gray>Passive:</gray> <white>" + type.passiveDescription() + "</white>");
        Msg.send(player, "<gray>Ability:</gray> <white>" + type.abilityDescription() + "</white>"
                + (data.isAbilityUnlocked() ? "" : " <red>(locked - consume "
                + tokensToUnlock() + " kill tokens)</red>"));
        save();
    }

    // ------------------------------------------------------------------
    // Passives
    // ------------------------------------------------------------------

    /** Clears then reapplies every passive for the player's current apex. */
    public void applyPassives(Player player) {
        clearPassives(player);
        PlayerApexData data = getData(player.getUniqueId());
        ApexType type = data.getApex();
        if (type == null) {
            return;
        }
        switch (type) {
            case LION -> {
                addEffect(player, PotionEffectType.STRENGTH, 0);
                addModifier(player, Attribute.MOVEMENT_SPEED, speedKey, 0.10,
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            }
            case WOLF, PANTHER -> addEffect(player, PotionEffectType.SPEED, 0);
            case RHINO -> addModifier(player, Attribute.MAX_HEALTH, maxHealthKey, 8.0,
                    AttributeModifier.Operation.ADD_NUMBER);
            case TREX -> addEffect(player, PotionEffectType.FIRE_RESISTANCE, 0);
            case POLAR_BEAR -> addModifier(player, Attribute.MAX_HEALTH, maxHealthKey, 4.0,
                    AttributeModifier.Operation.ADD_NUMBER);
            case SNAKE -> {
                // Crouch speed bonus is toggled on sneak in PlayerListener.
            }
            case HIPPO -> {
                addEffect(player, PotionEffectType.RESISTANCE, 0);
                addEffect(player, PotionEffectType.WATER_BREATHING, 0);
            }
            case DRAGON -> {
                addEffect(player, PotionEffectType.STRENGTH, 0);
                addEffect(player, PotionEffectType.SPEED, 0);
                addEffect(player, PotionEffectType.FIRE_RESISTANCE, 0);
            }
        }
    }

    public void clearPassives(Player player) {
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.WATER_BREATHING);
        removeModifier(player, Attribute.MAX_HEALTH, maxHealthKey);
        removeModifier(player, Attribute.MOVEMENT_SPEED, speedKey);
        removeModifier(player, Attribute.MOVEMENT_SPEED, crouchSpeedKey);
        if (player.getHealth() > maxHealth(player)) {
            player.setHealth(maxHealth(player));
        }
    }

    /** Snake crouch bonus - applied while sneaking only. */
    public void setCrouchSpeedBonus(Player player, boolean active) {
        removeModifier(player, Attribute.MOVEMENT_SPEED, crouchSpeedKey);
        if (active) {
            addModifier(player, Attribute.MOVEMENT_SPEED, crouchSpeedKey, 0.20,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        }
    }

    private double maxHealth(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        return attr == null ? 20.0 : attr.getValue();
    }

    private void addEffect(Player player, PotionEffectType type, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, true, false));
    }

    private void addModifier(Player player, Attribute attribute, NamespacedKey key, double amount,
                             AttributeModifier.Operation operation) {
        AttributeInstance attr = player.getAttribute(attribute);
        if (attr == null) {
            return;
        }
        removeModifier(player, attribute, key);
        attr.addTransientModifier(new AttributeModifier(key, amount, operation));
    }

    private void removeModifier(Player player, Attribute attribute, NamespacedKey key) {
        AttributeInstance attr = player.getAttribute(attribute);
        if (attr == null) {
            return;
        }
        for (AttributeModifier modifier : attr.getModifiers()) {
            if (modifier.getKey().equals(key)) {
                attr.removeModifier(modifier);
            }
        }
    }

    // ------------------------------------------------------------------
    // Token progression
    // ------------------------------------------------------------------

    /** True when the player is already at the consumed-token cap. */
    public boolean isAtTokenCap(Player player) {
        return getData(player.getUniqueId()).getTokensConsumed() >= tokensToUnlock();
    }

    /**
     * Absorbs one kill token into the counter, capped at tokensToUnlock, unlocking
     * the ability at the threshold. Returns false if already at the cap (nothing consumed).
     */
    public boolean consumeToken(Player player) {
        PlayerApexData data = getData(player.getUniqueId());
        if (data.getTokensConsumed() >= tokensToUnlock()) {
            return false;
        }
        data.setTokensConsumed(data.getTokensConsumed() + 1);
        plugin.getApexLogger().log(ApexLogger.LogType.TOKEN_CLAIM,
                player.getName() + " consumed a kill token (" + data.getTokensConsumed()
                        + "/" + tokensToUnlock() + ")");
        player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.PLAYER, 1f, 1.4f));
        if (!data.isAbilityUnlocked() && data.getTokensConsumed() >= tokensToUnlock()) {
            unlockAbility(player, "consumed " + tokensToUnlock() + " kill tokens");
        } else {
            Msg.send(player, "<yellow>Kill token consumed.</yellow> <gray>("
                    + data.getTokensConsumed() + "/" + tokensToUnlock() + ")</gray>");
        }
        save();
        return true;
    }

    /** Apex evolution - ability unlocked. */
    public void unlockAbility(Player player, String reason) {
        PlayerApexData data = getData(player.getUniqueId());
        data.setAbilityUnlocked(true);
        ApexType type = data.getApex();
        String name = type == null ? "<gold><bold>APEX</bold></gold>" : type.coloredName();
        player.showTitle(Title.title(
                Msg.mm("<gold><bold>APEX EVOLUTION</bold></gold>"),
                Msg.mm(name + " <yellow>ability unlocked - use /ability</yellow>"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofSeconds(1))));
        player.playSound(Sound.sound(org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, Sound.Source.PLAYER, 1f, 1f));
        player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0), 60, 0.6, 0.8, 0.6, 0.15);
        plugin.getApexLogger().log(ApexLogger.LogType.EVOLVE,
                player.getName() + " unlocked their ability (" + reason + ")");
        save();
    }

    /** Withdraws consumed tokens back into items; re-locks the ability if below the threshold. */
    public int withdrawTokens(Player player, int amount) {
        PlayerApexData data = getData(player.getUniqueId());
        int withdrawn = Math.min(amount, data.getTokensConsumed());
        if (withdrawn <= 0) {
            return 0;
        }
        data.setTokensConsumed(data.getTokensConsumed() - withdrawn);
        if (data.getTokensConsumed() < tokensToUnlock() && data.isAbilityUnlocked()) {
            data.setAbilityUnlocked(false);
            Msg.send(player, "<red>Your ability re-locked - you are below "
                    + tokensToUnlock() + " consumed tokens.</red>");
        }
        plugin.getApexLogger().log(ApexLogger.LogType.TOKEN_WITHDRAW,
                player.getName() + " withdrew " + withdrawn + " kill token(s), now at "
                        + data.getTokensConsumed());
        save();
        return withdrawn;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    public void load() {
        File file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        smpStarted = yaml.getBoolean("started", false);
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                PlayerApexData data = new PlayerApexData();
                data.setApex(ApexType.fromString(section.getString(key + ".apex", "")));
                data.setTokensConsumed(section.getInt(key + ".tokens", 0));
                data.setAbilityUnlocked(section.getBoolean(key + ".unlocked", false));
                data.setSnakeHitCounter(section.getInt(key + ".hits", 0));
                data.setLastKnownName(section.getString(key + ".name", ""));
                players.put(uuid, data);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping invalid data.yml entry: " + key);
            }
        }
        plugin.getLogger().info("Loaded apex data for " + players.size() + " players.");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("started", smpStarted);
        for (Map.Entry<UUID, PlayerApexData> entry : players.entrySet()) {
            PlayerApexData data = entry.getValue();
            String base = "players." + entry.getKey() + ".";
            yaml.set(base + "apex", data.getApex() == null ? null : data.getApex().name());
            yaml.set(base + "tokens", data.getTokensConsumed());
            yaml.set(base + "unlocked", data.isAbilityUnlocked());
            yaml.set(base + "hits", data.getSnakeHitCounter());
            yaml.set(base + "name", data.getLastKnownName());
        }
        try {
            yaml.save(new File(plugin.getDataFolder(), "data.yml"));
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save data.yml: " + e.getMessage());
        }
    }
}
