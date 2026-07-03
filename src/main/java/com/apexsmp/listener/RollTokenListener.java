package com.apexsmp.listener;

import com.apexsmp.ApexPlugin;
import com.apexsmp.item.ItemManager;
import com.apexsmp.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * The Apex Roll Totem is soulbound: it cannot be dropped, moved into any
 * container, used in crafting, or lost on death. The only interaction is
 * right-clicking it to roll. Once the SMP has started, anyone online without
 * an apex is guaranteed to hold one.
 */
public class RollTokenListener implements Listener {

    private final ApexPlugin plugin;

    public RollTokenListener(ApexPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isRollToken(ItemStack stack) {
        return ItemManager.ROLL_ITEM.equals(plugin.getItemManager().identify(stack));
    }

    /** Hands a roll totem to anyone who should have one but doesn't. */
    public void ensureRollToken(Player player) {
        if (!plugin.getApexManager().isSmpStarted()) {
            return;
        }
        if (plugin.getApexManager().getData(player.getUniqueId()).getApex() != null) {
            return;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isRollToken(stack)) {
                return;
            }
        }
        // Server-dropped leftovers (full inventory) are allowed; only player-initiated
        // drops are blocked, so the token still can't be thrown away deliberately.
        player.getInventory().addItem(plugin.getItemManager().rollItem(1))
                .values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        Msg.send(player, "<gold>The Apex SMP has begun!</gold> <yellow>Right-click your "
                + "Apex Roll Totem to roll your predator.</yellow>");
    }

    // ------------------------------------------------------------------
    // Distribution
    // ------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Delay a tick so ApexManager data (and any fresh /apex start) is settled.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                ensureRollToken(player);
            }
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                ensureRollToken(player);
            }
        });
    }

    // ------------------------------------------------------------------
    // Soulbound restrictions
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        var dropped = event.getItemDrop().getItemStack();
        if (isRollToken(dropped)) {
            event.setCancelled(true);
            Msg.send(event.getPlayer(), "<red>You cannot drop your Apex Roll Totem - right-click to roll!</red>");
            return;
        }
        // Players at 0 absorbed tokens cannot throw away kill token items.
        if (ItemManager.KILL_TOKEN.equals(plugin.getItemManager().identify(dropped))
                && plugin.getApexManager().getData(event.getPlayer().getUniqueId()).getTokensConsumed() <= 0) {
            event.setCancelled(true);
            Msg.send(event.getPlayer(), "<red>You can't drop kill tokens while you have none absorbed.</red>");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // Never hits the ground on death; re-given on respawn by ensureRollToken.
        event.getDrops().removeIf(this::isRollToken);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        boolean containerOpen = top.getType() != InventoryType.CRAFTING;
        Inventory clicked = event.getClickedInventory();

        // Shift-clicking the token out of the player inventory while a container is open.
        if (containerOpen && event.isShiftClick() && clicked == event.getView().getBottomInventory()
                && isRollToken(event.getCurrentItem())) {
            deny(event, player);
            return;
        }
        // Placing a cursor-held token into the top inventory (container or crafting grid).
        if (clicked == top && isRollToken(event.getCursor())) {
            deny(event, player);
            return;
        }
        // Hotbar-swapping the token into the top inventory.
        if (clicked == top && event.getClick() == ClickType.NUMBER_KEY
                && isRollToken(player.getInventory().getItem(event.getHotbarButton()))) {
            deny(event, player);
            return;
        }
        // Dropping the token with Q / Ctrl-Q from an inventory screen.
        if ((event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP)
                && isRollToken(event.getCurrentItem())) {
            deny(event, player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isRollToken(event.getOldCursor())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean intoTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (intoTop && event.getView().getTopInventory().getType() != InventoryType.CRAFTING
                || intoTop && event.getView().getTopInventory().getType() == InventoryType.CRAFTING
                && event.getRawSlots().stream().anyMatch(slot -> slot < 5)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (isRollToken(stack)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    /** Blocks putting the token into item frames, armor stands, etc. */
    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isRollToken(event.getPlayer().getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
        }
    }

    private void deny(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        Msg.send(player, "<red>Your Apex Roll Totem stays with you - right-click to roll!</red>");
    }
}
