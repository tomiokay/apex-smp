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
        // Only a death at the hands of another player has consequences.
        if (killer == null || killer.equals(victim)) {
            return;
        }

        // Slain by a player: the victim loses one absorbed token.
        PlayerApexData victimData = plugin.getApexManager().getData(victim.getUniqueId());
        if (victimData.getTokensConsumed() > 0) {
            victimData.setTokensConsumed(victimData.getTokensConsumed() - 1);
            if (victimData.isAbilityUnlocked()
                    && victimData.getTokensConsumed() < plugin.getApexManager().tokensToUnlock()) {
                victimData.setAbilityUnlocked(false);
            }
            plugin.getApexManager().save();
            Msg.send(victim, "<red>You were slain by " + killer.getName()
                    + " and lost a kill token.</red> <gray>(" + victimData.getTokensConsumed()
                    + "/" + plugin.getApexManager().tokensToUnlock() + ")</gray>");
        }
        if (!plugin.getApexManager().isAtTokenCap(killer)) {
            // Below the cap: auto-absorb the kill's token straight into progress.
            plugin.getApexManager().consumeToken(killer);
            plugin.getApexLogger().log(ApexLogger.LogType.KILL,
                    killer.getName() + " killed " + victim.getName() + " - token auto-absorbed");
            Msg.send(killer, "<red>" + victim.getName()
                    + "</red> <yellow>was slain - kill token absorbed!</yellow>");
        } else {
            // At the cap: hand over a Kill Token item instead (drop if inventory full).
            var leftover = killer.getInventory().addItem(plugin.getItemManager().killToken(1));
            boolean dropped = !leftover.isEmpty();
            leftover.values().forEach(left ->
                    killer.getWorld().dropItemNaturally(killer.getLocation(), left));
            plugin.getApexLogger().log(ApexLogger.LogType.KILL,
                    killer.getName() + " killed " + victim.getName() + " - kill token "
                            + (dropped ? "dropped (inventory full)" : "given"));
            Msg.send(killer, "<red>" + victim.getName() + "</red> <yellow>was slain - kill token "
                    + (dropped ? "dropped at your feet" : "added to your inventory") + "!</yellow>");
        }
    }
}
