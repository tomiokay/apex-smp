package com.apexsmp.ability;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stuns freeze an entity in place and block their attacks, but never block healing
 * (regen, gapples, potions all still work). Works on players and, for test mode, mobs.
 */
public class StunManager {

    private final Map<UUID, Long> stunnedUntil = new HashMap<>();

    public void stun(LivingEntity entity, int ticks) {
        stunnedUntil.put(entity.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 250, true, false));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ticks, 128, true, false));
    }

    public boolean isStunned(LivingEntity entity) {
        Long until = stunnedUntil.get(entity.getUniqueId());
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            stunnedUntil.remove(entity.getUniqueId());
            return false;
        }
        return true;
    }

    public void release(LivingEntity entity) {
        stunnedUntil.remove(entity.getUniqueId());
        entity.removePotionEffect(PotionEffectType.SLOWNESS);
        entity.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }
}
