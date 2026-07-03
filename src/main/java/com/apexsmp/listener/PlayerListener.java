package com.apexsmp.listener;

import com.apexsmp.ApexPlugin;
import com.apexsmp.apex.ApexType;
import com.apexsmp.apex.PlayerApexData;
import com.apexsmp.util.RollAnimation;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/**
 * Join/quit/respawn lifecycle, the first-spawn roll, snake crouch speed,
 * and stun movement locking.
 */
public class PlayerListener implements Listener {

    private final ApexPlugin plugin;

    public PlayerListener(ApexPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerApexData data = plugin.getApexManager().getData(player.getUniqueId());
        data.setLastKnownName(player.getName());
        if (data.getApex() == null) {
            // First spawn: roll an apex with the full animation after a short beat.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    RollAnimation.play(plugin, player, ApexType.randomRollable(),
                            result -> plugin.getApexManager().assignApex(player, result, "first spawn"));
                }
            }, 40L);
        } else {
            plugin.getApexManager().applyPassives(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getApexManager().save();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // Potion passives are lost on death; reapply next tick.
        plugin.getServer().getScheduler().runTask(plugin,
                () -> plugin.getApexManager().applyPassives(event.getPlayer()));
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        PlayerApexData data = plugin.getApexManager().getData(player.getUniqueId());
        if (data.getApex() == ApexType.SNAKE) {
            plugin.getApexManager().setCrouchSpeedBonus(player, event.isSneaking());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getAbilityManager().getStunManager().isStunned(event.getPlayer())) {
            return;
        }
        // Allow head rotation but freeze position while stunned.
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom().clone().setDirection(event.getTo().getDirection()));
        }
    }
}
