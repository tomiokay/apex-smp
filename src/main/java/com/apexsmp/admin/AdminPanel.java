package com.apexsmp.admin;

import com.apexsmp.ApexPlugin;
import com.apexsmp.apex.ApexType;
import com.apexsmp.apex.PlayerApexData;
import com.apexsmp.log.ApexLogger;
import com.apexsmp.util.Msg;
import com.apexsmp.util.RollAnimation;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

/**
 * Admin GUI: a roster of online players, and a per-player action view with
 * reroll / token / lock controls. All actions are written to the apex log.
 */
public class AdminPanel implements Listener {

    /** Marks our inventories and remembers which player a detail view targets. */
    private static class PanelHolder implements InventoryHolder {
        final String view;
        final UUID target;
        Inventory inventory;

        PanelHolder(String view, UUID target) {
            this.view = view;
            this.target = target;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final ApexPlugin plugin;

    public AdminPanel(ApexPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Views
    // ------------------------------------------------------------------

    public void openMain(Player admin) {
        PanelHolder holder = new PanelHolder("main", null);
        Inventory inv = Bukkit.createInventory(holder, 54, Msg.mm("<dark_gray>Apex Admin Panel</dark_gray>"));
        holder.inventory = inv;
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 54) {
                break;
            }
            PlayerApexData data = plugin.getApexManager().getData(online.getUniqueId());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(online);
            meta.displayName(Msg.mm("<!italic><yellow>" + online.getName() + "</yellow>"));
            String apexName = data.getApex() == null ? "<red>none</red>" : data.getApex().coloredName();
            meta.lore(List.of(
                    Msg.mm("<!italic><gray>Apex:</gray> " + apexName),
                    Msg.mm("<!italic><gray>Tokens:</gray> <white>" + data.getTokensConsumed() + "</white>"),
                    Msg.mm("<!italic><gray>Ability:</gray> "
                            + (data.isAbilityUnlocked() ? "<green>unlocked</green>" : "<red>locked</red>")),
                    Msg.mm("<!italic><yellow>Click to manage</yellow>")));
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }
        admin.openInventory(inv);
    }

    public void openDetail(Player admin, Player target) {
        PanelHolder holder = new PanelHolder("detail", target.getUniqueId());
        PlayerApexData data = plugin.getApexManager().getData(target.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 27,
                Msg.mm("<dark_gray>Manage: " + target.getName() + "</dark_gray>"));
        holder.inventory = inv;

        inv.setItem(4, icon(Material.PLAYER_HEAD, "<yellow>" + target.getName() + "</yellow>",
                "<gray>Apex:</gray> " + (data.getApex() == null ? "<red>none</red>" : data.getApex().coloredName()),
                "<gray>Tokens:</gray> <white>" + data.getTokensConsumed() + "</white>",
                "<gray>Ability:</gray> " + (data.isAbilityUnlocked() ? "<green>unlocked</green>" : "<red>locked</red>")));
        inv.setItem(10, icon(Material.ENDER_EYE, "<gold>Reroll Apex</gold>", "<gray>Roll a new random apex</gray>"));
        inv.setItem(11, icon(Material.DRAGON_EGG, "<light_purple>Grant Dragon</light_purple>",
                "<gray>Dragon egg trade-in</gray>"));
        inv.setItem(12, icon(Material.NETHER_STAR, "<red>+1 Token</red>", "<gray>Add a consumed kill token</gray>"));
        inv.setItem(13, icon(Material.REDSTONE, "<red>-1 Token</red>", "<gray>Remove a consumed kill token</gray>"));
        inv.setItem(14, icon(Material.LIME_DYE, "<green>Unlock Ability</green>", "<gray>Force the evolution</gray>"));
        inv.setItem(15, icon(Material.RED_DYE, "<red>Lock Ability</red>", "<gray>Re-lock the ability</gray>"));
        inv.setItem(16, icon(Material.NETHER_STAR, "<yellow>Give Token Item</yellow>",
                "<gray>Put a kill token in their inventory</gray>"));
        inv.setItem(22, icon(Material.ARROW, "<gray>Back</gray>"));
        admin.openInventory(inv);
    }

    private ItemStack icon(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Msg.mm("<!italic>" + name));
        meta.lore(java.util.Arrays.stream(lore).map(l -> Msg.mm("<!italic>" + l)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    // ------------------------------------------------------------------
    // Clicks
    // ------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PanelHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin) || event.getCurrentItem() == null) {
            return;
        }
        if (holder.view.equals("main")) {
            ItemMeta meta = event.getCurrentItem().getItemMeta();
            if (meta instanceof SkullMeta skull && skull.getOwningPlayer() != null) {
                Player target = skull.getOwningPlayer().getPlayer();
                if (target != null) {
                    openDetail(admin, target);
                }
            }
            return;
        }

        Player target = holder.target == null ? null : Bukkit.getPlayer(holder.target);
        if (target == null) {
            Msg.send(admin, "<red>That player went offline.</red>");
            admin.closeInventory();
            return;
        }
        PlayerApexData data = plugin.getApexManager().getData(target.getUniqueId());
        switch (event.getSlot()) {
            case 10 -> {
                ApexType result = data.getApex() == null
                        ? ApexType.randomRollable()
                        : ApexType.randomRollableExcept(data.getApex());
                RollAnimation.play(plugin, target, result,
                        rolled -> plugin.getApexManager().assignApex(target, rolled,
                                "admin reroll by " + admin.getName()));
                logAdmin(admin, "rerolled " + target.getName());
            }
            case 11 -> {
                plugin.getApexManager().assignApex(target, ApexType.DRAGON,
                        "dragon trade-in via panel by " + admin.getName());
                logAdmin(admin, "granted DRAGON to " + target.getName());
            }
            case 12 -> {
                data.setTokensConsumed(data.getTokensConsumed() + 1);
                if (!data.isAbilityUnlocked()
                        && data.getTokensConsumed() >= plugin.getApexManager().tokensToUnlock()) {
                    plugin.getApexManager().unlockAbility(target, "admin token grant");
                }
                plugin.getApexManager().save();
                logAdmin(admin, "added a token to " + target.getName());
            }
            case 13 -> {
                data.setTokensConsumed(data.getTokensConsumed() - 1);
                plugin.getApexManager().save();
                logAdmin(admin, "removed a token from " + target.getName());
            }
            case 14 -> {
                plugin.getApexManager().unlockAbility(target, "admin unlock by " + admin.getName());
                logAdmin(admin, "unlocked ability for " + target.getName());
            }
            case 15 -> {
                data.setAbilityUnlocked(false);
                plugin.getApexManager().save();
                Msg.send(target, "<red>Your ability was locked by an admin.</red>");
                logAdmin(admin, "locked ability for " + target.getName());
            }
            case 16 -> {
                target.getInventory().addItem(plugin.getItemManager().killToken(1));
                logAdmin(admin, "gave a kill token item to " + target.getName());
            }
            case 22 -> {
                openMain(admin);
                return;
            }
            default -> {
                return;
            }
        }
        if (event.getSlot() != 10) {
            openDetail(admin, target); // refresh the view
        }
    }

    private void logAdmin(Player admin, String action) {
        plugin.getApexLogger().log(ApexLogger.LogType.ADMIN, admin.getName() + " " + action);
        Msg.send(admin, "<green>Done:</green> <gray>" + action + "</gray>");
    }
}
