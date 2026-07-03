package com.apexsmp;

import com.apexsmp.ability.AbilityManager;
import com.apexsmp.ability.StunManager;
import com.apexsmp.admin.AdminPanel;
import com.apexsmp.apex.ApexManager;
import com.apexsmp.apex.ApexType;
import com.apexsmp.command.AbilityCommand;
import com.apexsmp.command.ApexCommand;
import com.apexsmp.item.ItemManager;
import com.apexsmp.item.RecipeManager;
import com.apexsmp.listener.CombatListener;
import com.apexsmp.listener.ItemUseListener;
import com.apexsmp.listener.PlayerListener;
import com.apexsmp.listener.RollTokenListener;
import com.apexsmp.log.ApexLogger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;

public final class ApexPlugin extends JavaPlugin {

    private static final Set<Material> FROZEN_GROUND = Set.of(
            Material.SNOW, Material.SNOW_BLOCK, Material.POWDER_SNOW,
            Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.FROSTED_ICE);

    private ApexLogger apexLogger;
    private ApexManager apexManager;
    private AbilityManager abilityManager;
    private ItemManager itemManager;
    private AdminPanel adminPanel;
    private RollTokenListener rollTokenListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        apexLogger = new ApexLogger(this);
        apexManager = new ApexManager(this);
        abilityManager = new AbilityManager(this, new StunManager());
        itemManager = new ItemManager(this);
        adminPanel = new AdminPanel(this);
        rollTokenListener = new RollTokenListener(this);

        apexManager.load();
        new RecipeManager(this, itemManager).registerAll();

        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemUseListener(this), this);
        getServer().getPluginManager().registerEvents(rollTokenListener, this);
        getServer().getPluginManager().registerEvents(adminPanel, this);

        registerCommand("ability", new AbilityCommand(this));
        PluginCommand apex = getCommand("apex");
        if (apex != null) {
            ApexCommand executor = new ApexCommand(this);
            apex.setExecutor(executor);
            apex.setTabCompleter(executor);
        }

        // Polar Bear passive: Speed II while standing on snow or ice, checked twice a second.
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                if (apexManager.getData(player.getUniqueId()).getApex() != ApexType.POLAR_BEAR) {
                    continue;
                }
                Block below = player.getLocation().getBlock().getRelative(0, -1, 0);
                Block at = player.getLocation().getBlock();
                if (FROZEN_GROUND.contains(below.getType()) || FROZEN_GROUND.contains(at.getType())) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 15, 1, true, false));
                }
            }
        }, 20L, 10L);

        // Periodic safety save every 5 minutes.
        getServer().getScheduler().runTaskTimer(this, () -> apexManager.save(), 6000L, 6000L);

        // Re-apply passives for anyone online across a /reload.
        for (Player player : getServer().getOnlinePlayers()) {
            apexManager.applyPassives(player);
        }

        getLogger().info("ApexSMP enabled - 8 apex predators plus the Dragon are on the prowl.");
    }

    @Override
    public void onDisable() {
        if (apexManager != null) {
            apexManager.save();
        }
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command /" + name + " missing from plugin.yml!");
            return;
        }
        command.setExecutor(executor);
    }

    public ApexLogger getApexLogger() {
        return apexLogger;
    }

    public ApexManager getApexManager() {
        return apexManager;
    }

    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public AdminPanel getAdminPanel() {
        return adminPanel;
    }

    public RollTokenListener getRollTokenListener() {
        return rollTokenListener;
    }
}
