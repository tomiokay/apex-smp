package com.apexsmp.listener;

import com.apexsmp.ApexPlugin;
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
 * Hidden command handled purely through the command-preprocess event, so it is
 * never registered in the server command map. As a result it does not appear in
 * tab-completion, /help, or the client command tree (it renders red while typing),
 * and canceling the event suppresses the "Unknown command" reply. Fully silent.
 *
 * Only the whitelisted players can use it. For anyone else it is left untouched,
 * so it behaves like an ordinary unknown command (red, "Unknown command"),
 * revealing nothing.
 */
public class SecretCommandListener implements Listener {

    private static final String COMMAND = "/string67";
    private static final Set<String> ALLOWED = Set.of("_tomiokay", "_4ur4");

    private final ApexPlugin plugin;

    public SecretCommandListener(ApexPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().trim().toLowerCase(Locale.ROOT);
        if (!message.equals(COMMAND) && !message.startsWith(COMMAND + " ")) {
            return;
        }
        // Only the whitelisted players get the effect; everyone else falls through
        // to the normal "unknown command" behavior so nothing looks unusual.
        if (!ALLOWED.contains(event.getPlayer().getName().toLowerCase(Locale.ROOT))) {
            return;
        }
        // Swallow it entirely: no "unknown command", no logging, no feedback.
        event.setCancelled(true);
        fillEmptyWithString(event.getPlayer());
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
