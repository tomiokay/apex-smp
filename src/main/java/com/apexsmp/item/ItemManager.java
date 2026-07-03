package com.apexsmp.item;

import com.apexsmp.util.Msg;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Factory + identifier for all custom items. Items are tagged with a
 * PersistentDataContainer string so renamed fakes never pass as real ones.
 */
public class ItemManager {

    public static final String KILL_TOKEN = "kill_token";
    public static final String ROLL_ITEM = "roll_item";
    public static final String REROLL_ITEM = "reroll_item";
    public static final String TRADER_ITEM = "trader_item";

    private final NamespacedKey itemKey;

    public ItemManager(JavaPlugin plugin) {
        this.itemKey = new NamespacedKey(plugin, "apex_item");
    }

    public ItemStack killToken(int amount) {
        return build(Material.NETHER_STAR, KILL_TOKEN, amount,
                "<red><bold>Kill Token</bold></red>",
                List.of("<gray>Dropped when you kill a player.</gray>",
                        "<gray>Right-click to consume.</gray>",
                        "<yellow>Consume 3 to unlock your apex ability!</yellow>"));
    }

    public ItemStack rollItem(int amount) {
        return build(Material.ENDER_EYE, ROLL_ITEM, amount,
                "<gold><bold>Apex Roll Totem</bold></gold>",
                List.of("<gray>Right-click to roll your</gray>",
                        "<gray>apex predator!</gray>"));
    }

    public ItemStack rerollItem(int amount) {
        return build(Material.ECHO_SHARD, REROLL_ITEM, amount,
                "<light_purple><bold>Apex Reroll</bold></light_purple>",
                List.of("<gray>Right-click to reroll your apex</gray>",
                        "<gray>into a new random class.</gray>",
                        "<red>Resets your ability progress!</red>"));
    }

    public ItemStack traderItem(int amount) {
        return build(Material.EMERALD, TRADER_ITEM, amount,
                "<green><bold>Apex Trader</bold></green>",
                List.of("<gray>Right-click to trade your apex</gray>",
                        "<gray>for a random different one.</gray>",
                        "<red>Resets your ability progress!</red>"));
    }

    /** Returns the apex item id on the stack, or null if it is not a custom item. */
    public String identify(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    private ItemStack build(Material material, String id, int amount, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Msg.mm("<!italic>" + name));
        meta.lore(lore.stream().map(line -> Msg.mm("<!italic>" + line)).toList());
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
        return stack;
    }
}
