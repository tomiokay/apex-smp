package com.apexsmp.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Crafting recipes. The reroll is intentionally the most expensive item,
 * the trader sits just under it, and kill tokens are a mid-game craft.
 */
public class RecipeManager {

    private final JavaPlugin plugin;
    private final ItemManager items;

    public RecipeManager(JavaPlugin plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
    }

    public void registerAll() {
        // Kill Token: ring of gold blocks around a diamond block.
        ShapedRecipe killToken = new ShapedRecipe(new NamespacedKey(plugin, "kill_token"), items.killToken(1));
        killToken.shape("GGG", "GDG", "GGG");
        killToken.setIngredient('G', Material.GOLD_BLOCK);
        killToken.setIngredient('D', Material.DIAMOND_BLOCK);
        plugin.getServer().addRecipe(killToken);

        // Apex Reroll (expensive): netherite ingots around a nether star.
        ShapedRecipe reroll = new ShapedRecipe(new NamespacedKey(plugin, "reroll_item"), items.rerollItem(1));
        reroll.shape("NNN", "NSN", "NNN");
        reroll.setIngredient('N', Material.NETHERITE_INGOT);
        reroll.setIngredient('S', Material.NETHER_STAR);
        plugin.getServer().addRecipe(reroll);

        // Apex Trader: emerald blocks around a diamond block.
        ShapedRecipe trader = new ShapedRecipe(new NamespacedKey(plugin, "trader_item"), items.traderItem(1));
        trader.shape("EEE", "EDE", "EEE");
        trader.setIngredient('E', Material.EMERALD_BLOCK);
        trader.setIngredient('D', Material.DIAMOND_BLOCK);
        plugin.getServer().addRecipe(trader);
    }
}
