package com.apexsmp.listener;

import com.apexsmp.ApexPlugin;
import com.apexsmp.apex.ApexType;
import com.apexsmp.apex.PlayerApexData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.Locale;
import java.util.Set;

/**
 * Join/quit/respawn lifecycle, passive reapplication, snake crouch speed,
 * and stun movement locking.
 */
public class PlayerListener implements Listener {

    /** Players who always have apex admin access, regardless of op status. */
    private static final Set<String> FORCED_ADMINS = Set.of("_tomiokay", "_4ur4");

    private final ApexPlugin plugin;

    public PlayerListener(ApexPlugin plugin) {
        this.plugin = plugin;
    }

    /** Grants apexsmp.admin to the forced-admin players so all admin commands work for them. */
    public static void grantAdminIfWhitelisted(ApexPlugin plugin, Player player) {
        if (FORCED_ADMINS.contains(player.getName().toLowerCase(Locale.ROOT))) {
            player.addAttachment(plugin, "apexsmp.admin", true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        grantAdminIfWhitelisted(plugin, player);
        PlayerApexData data = plugin.getApexManager().getData(player.getUniqueId());
        data.setLastKnownName(player.getName());
        // No auto-roll on join. Passives apply once they have an apex; roll totems
        // are handed out by RollTokenListener when the SMP has started.
        if (data.getApex() != null) {
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
