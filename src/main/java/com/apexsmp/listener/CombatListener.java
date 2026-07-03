package com.apexsmp.listener;

import com.apexsmp.ApexPlugin;
import com.apexsmp.apex.ApexType;
import com.apexsmp.apex.PlayerApexData;
import com.apexsmp.log.ApexLogger;
import com.apexsmp.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Combat hooks: lion frenzy bonus, snake hit-counter poison, stun attack
 * blocking, slam fall-damage cancels, and kill token drops on player kills.
 */
public class CombatListener implements Listener {

    private final ApexPlugin plugin;

    public CombatListener(ApexPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        // Stunned players cannot attack (they can still heal).
        if (plugin.getAbilityManager().getStunManager().isStunned(attacker)) {
            event.setCancelled(true);
            return;
        }
        // Lion Blood Frenzy: +20% damage while active.
        if (plugin.getAbilityManager().hasLionBuff(attacker)) {
            event.setDamage(event.getDamage() * 1.2);
        }
        // Snake passive: every 20th hit poisons the victim.
        PlayerApexData data = plugin.getApexManager().getData(attacker.getUniqueId());
        if (data.getApex() == ApexType.SNAKE && event.getEntity() instanceof Player victim) {
            int hits = data.getSnakeHitCounter() + 1;
            if (hits >= 20) {
                hits = 0;
                victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 10 * 20, 0, false, true));
                Msg.send(attacker, "<green>Your fangs find their mark - "
                        + victim.getName() + " is poisoned!</green>");
            }
            data.setSnakeHitCounter(hits);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL
                || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (plugin.getAbilityManager().consumeNoFallDamage(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        event.getDrops().add(plugin.getItemManager().killToken(1));
        plugin.getApexLogger().log(ApexLogger.LogType.KILL,
                killer.getName() + " killed " + victim.getName() + " - kill token dropped");
        Msg.send(killer, "<red>" + victim.getName()
                + "</red> <yellow>dropped a</yellow> <red>Kill Token</red><yellow>!</yellow>");
    }
}
