package com.apexsmp.ability;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stuns freeze a player in place and block their attacks, but never block healing
 * (regen, gapples, potions all still work).
 */
public class StunManager {

    private final Map<UUID, Long> stunnedUntil = new HashMap<>();

    public void stun(Player player, int ticks) {
        stunnedUntil.put(player.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 250, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ticks, 128, true, false));
    }

    public boolean isStunned(Player player) {
        Long until = stunnedUntil.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            stunnedUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public void release(Player player) {
        stunnedUntil.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }
}
