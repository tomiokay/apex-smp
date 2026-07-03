package com.apexsmp.listener;

import com.apexsmp.ApexPlugin;
import com.apexsmp.apex.ApexType;
import com.apexsmp.apex.PlayerApexData;
import com.apexsmp.item.ItemManager;
import com.apexsmp.util.Msg;
import com.apexsmp.util.RollAnimation;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Right-click behavior for every custom item: claim kill tokens, roll totems,
 * rerolls, and apex traders.
 */
public class ItemUseListener implements Listener {

    private final ApexPlugin plugin;

    public ItemUseListener(ApexPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        String id = plugin.getItemManager().identify(item);
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        switch (id) {
            case ItemManager.KILL_TOKEN -> {
                item.subtract(1);
                plugin.getApexManager().consumeToken(player);
            }
            case ItemManager.ROLL_ITEM -> {
                item.subtract(1);
                rollNewApex(player, "roll totem", false);
            }
            case ItemManager.REROLL_ITEM -> {
                item.subtract(1);
                rollNewApex(player, "reroll", true);
            }
            case ItemManager.TRADER_ITEM -> {
                item.subtract(1);
                rollNewApex(player, "apex trader", true);
            }
            default -> event.setCancelled(false);
        }
    }

    /** Rolls with the animation; rerolls/trades always land on a different apex. */
    private void rollNewApex(Player player, String reason, boolean mustDiffer) {
        PlayerApexData data = plugin.getApexManager().getData(player.getUniqueId());
        ApexType current = data.getApex();
        ApexType result = (mustDiffer && current != null)
                ? ApexType.randomRollableExcept(current)
                : ApexType.randomRollable();
        RollAnimation.play(plugin, player, result, rolled -> {
            if (mustDiffer) {
                // Rerolls and trades reset ability progress, as the item lore warns.
                data.setTokensConsumed(0);
                data.setAbilityUnlocked(false);
            }
            plugin.getApexManager().assignApex(player, rolled, reason);
        });
    }
}
