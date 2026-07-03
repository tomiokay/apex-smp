package com.apexsmp.listener;

import com.apexsmp.ApexPlugin;
import com.apexsmp.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The dragon egg is a legendary, indestructible item. As a dropped item it glows,
 * cannot burn, be destroyed by lava or explosions, or despawn. If it falls into the
 * void it reappears on the End spawn platform and the whole server is told.
 */
public class DragonEggListener implements Listener {

    // Vanilla End obsidian spawn platform is centered at x=100, z=0.
    private static final int PLATFORM_X = 100;
    private static final int PLATFORM_Y = 49;
    private static final int PLATFORM_Z = 0;

    private final ApexPlugin plugin;

    public DragonEggListener(ApexPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isDragonEgg(Item item) {
        ItemStack stack = item.getItemStack();
        return stack != null && stack.getType() == Material.DRAGON_EGG;
    }

    /** Every dragon egg item is made invulnerable and immortal the moment it appears. */
    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (isDragonEgg(item)) {
            item.setInvulnerable(true);
            item.setUnlimitedLifetime(true);
            item.setWillAge(false);
            item.setGlowing(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        if (isDragonEgg(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Invulnerable items still take void damage, so intercept it and relocate. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item) || !isDragonEgg(item)) {
            return;
        }
        event.setCancelled(true);
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            item.remove();
            respawnAtEndPlatform(item.getItemStack().clone());
        }
    }

    /** Keep the placed dragon egg block safe from explosions. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> block.getType() == Material.DRAGON_EGG);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> block.getType() == Material.DRAGON_EGG);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (event.getBlock().getType() == Material.DRAGON_EGG) {
            event.setCancelled(true);
        }
    }

    private void respawnAtEndPlatform(ItemStack stack) {
        World end = null;
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getEnvironment() == World.Environment.THE_END) {
                end = world;
                break;
            }
        }
        // Fall back to the main world spawn if there is no End dimension.
        Location drop;
        if (end != null) {
            Block base = new Location(end, PLATFORM_X, PLATFORM_Y, PLATFORM_Z).getBlock();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = base.getRelative(dx, 0, dz);
                    if (!b.getType().isSolid()) {
                        b.setType(Material.OBSIDIAN);
                    }
                }
            }
            drop = new Location(end, PLATFORM_X + 0.5, PLATFORM_Y + 1.2, PLATFORM_Z + 0.5);
        } else {
            World main = plugin.getServer().getWorlds().get(0);
            drop = main.getSpawnLocation().clone().add(0.5, 1.2, 0.5);
        }
        drop.getWorld().dropItem(drop, stack);
        plugin.getServer().broadcast(Msg.mm(
                "<light_purple><bold>A legendary item has appeared at the End spawn platform!</bold></light_purple>"));
    }
}
