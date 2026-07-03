package com.apexsmp.command;

import com.apexsmp.ApexPlugin;
import com.apexsmp.apex.ApexType;
import com.apexsmp.apex.PlayerApexData;
import com.apexsmp.log.ApexLogger;
import com.apexsmp.util.Msg;
import com.apexsmp.util.RollAnimation;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /apex - player subcommands (info, withdraw) and the full admin suite
 * (start, panel, set, roll, tokens, lock, unlock, dragon, give, logs).
 */
public class ApexCommand implements TabExecutor {

    private static final List<String> PLAYER_SUBS = List.of("info", "withdraw", "help");
    private static final List<String> ADMIN_SUBS = List.of(
            "start", "panel", "set", "roll", "tokens", "unlock", "lock", "give", "logs", "test", "track");

    private final ApexPlugin plugin;

    public ApexCommand(ApexPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("apexsmp.admin");

        switch (sub) {
            case "info" -> info(sender, args);
            case "withdraw" -> withdraw(sender, args);
            case "start" -> requireAdmin(sender, admin, () -> start(sender));
            case "panel" -> requireAdmin(sender, admin, () -> panel(sender));
            case "set" -> requireAdmin(sender, admin, () -> set(sender, args));
            case "roll" -> requireAdmin(sender, admin, () -> roll(sender, args));
            case "tokens" -> requireAdmin(sender, admin, () -> tokens(sender, args));
            case "unlock" -> requireAdmin(sender, admin, () -> setLock(sender, args, true));
            case "lock" -> requireAdmin(sender, admin, () -> setLock(sender, args, false));
            case "give" -> requireAdmin(sender, admin, () -> give(sender, args));
            case "logs" -> requireAdmin(sender, admin, () -> logs(sender, args));
            case "test" -> requireAdmin(sender, admin, () -> test(sender));
            case "track" -> requireAdmin(sender, admin, () -> track(sender));
            default -> help(sender, admin);
        }
        return true;
    }

    private void requireAdmin(CommandSender sender, boolean admin, Runnable action) {
        if (!admin) {
            Msg.send(sender, "<red>You do not have permission for that.</red>");
            return;
        }
        action.run();
    }

    // ------------------------------------------------------------------
    // Player subcommands
    // ------------------------------------------------------------------

    private void info(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2 && sender.hasPermission("apexsmp.admin")) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                Msg.send(sender, "<red>Player not found.</red>");
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            Msg.send(sender, "<red>Usage: /apex info <player></red>");
            return;
        }
        PlayerApexData data = plugin.getApexManager().getData(target.getUniqueId());
        ApexType type = data.getApex();
        Msg.sendRaw(sender, "<gold><bold>--- " + target.getName() +"'s Apex ---</bold></gold>");
        if (type == null) {
            Msg.sendRaw(sender, "<red>No apex assigned yet.</red>");
            return;
        }
        Msg.sendRaw(sender, "<gray>Apex:</gray> " + type.coloredName());
        Msg.sendRaw(sender, "<gray>Passive:</gray> <white>" + type.passiveDescription() + "</white>");
        Msg.sendRaw(sender, "<gray>Ability:</gray> <white>" + type.abilityDescription() + "</white>");
        Msg.sendRaw(sender, "<gray>Tokens consumed:</gray> <white>" + data.getTokensConsumed()
                + "/" + plugin.getApexManager().tokensToUnlock() + "</white>");
        Msg.sendRaw(sender, "<gray>Ability status:</gray> "
                + (data.isAbilityUnlocked() ? "<green>UNLOCKED</green>" : "<red>LOCKED</red>"));
    }

    private void withdraw(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "<red>Only players can withdraw tokens.</red>");
            return;
        }
        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                Msg.send(sender, "<red>Usage: /apex withdraw [amount]</red>");
                return;
            }
        }
        int withdrawn = plugin.getApexManager().withdrawTokens(player, amount);
        if (withdrawn == 0) {
            Msg.send(player, "<red>You have no consumed tokens to withdraw.</red>");
            return;
        }
        player.getInventory().addItem(plugin.getItemManager().killToken(withdrawn))
                .values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        Msg.send(player, "<yellow>Withdrew " + withdrawn + " kill token(s).</yellow>");
    }

    // ------------------------------------------------------------------
    // Admin subcommands
    // ------------------------------------------------------------------

    private void start(CommandSender sender) {
        plugin.getApexManager().setSmpStarted(true);
        plugin.getApexManager().save();
        int count = 0;
        // Give a roll totem to everyone online who doesn't already have an apex.
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (plugin.getApexManager().getData(online.getUniqueId()).getApex() == null) {
                plugin.getRollTokenListener().ensureRollToken(online);
                count++;
            }
        }
        plugin.getApexLogger().log(ApexLogger.LogType.ADMIN,
                sender.getName() + " ran /apex start - SMP started, " + count + " roll totems handed out");
        Msg.send(sender, "<green>Apex SMP started.</green> <gray>Gave a roll totem to " + count
                + " players; future joiners get one automatically.</gray>");
    }

    private void panel(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "<red>The panel is only available in game.</red>");
            return;
        }
        plugin.getAdminPanel().openMain(player);
    }

    private void set(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Msg.send(sender, "<red>Usage: /apex set <player> <apex></red>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        ApexType type = ApexType.fromString(args[2]);
        if (target == null || type == null) {
            Msg.send(sender, "<red>Unknown player or apex. Apexes: "
                    + Arrays.toString(ApexType.values()) + "</red>");
            return;
        }
        plugin.getApexManager().assignApex(target, type, "admin set by " + sender.getName());
        plugin.getApexLogger().log(ApexLogger.LogType.ADMIN,
                sender.getName() + " set " + target.getName() + " to " + type.name());
        Msg.send(sender, "<green>" + target.getName() + " is now " + type.displayName() + ".</green>");
    }

    private void roll(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.send(sender, "<red>Usage: /apex roll <player></red>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            Msg.send(sender, "<red>Player not found.</red>");
            return;
        }
        PlayerApexData data = plugin.getApexManager().getData(target.getUniqueId());
        ApexType result = data.getApex() == null
                ? ApexType.randomRollable()
                : ApexType.randomRollableExcept(data.getApex());
        RollAnimation.play(plugin, target, result,
                rolled -> plugin.getApexManager().assignApex(target, rolled,
                        "admin roll by " + sender.getName()));
        Msg.send(sender, "<green>Rolling a new apex for " + target.getName() + "...</green>");
    }

    private void tokens(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Msg.send(sender, "<red>Usage: /apex tokens <player> <amount></red>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            Msg.send(sender, "<red>Player not found.</red>");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            Msg.send(sender, "<red>Amount must be a number.</red>");
            return;
        }
        PlayerApexData data = plugin.getApexManager().getData(target.getUniqueId());
        data.setTokensConsumed(amount);
        if (!data.isAbilityUnlocked() && data.getTokensConsumed() >= plugin.getApexManager().tokensToUnlock()) {
            plugin.getApexManager().unlockAbility(target, "admin token set");
        }
        plugin.getApexManager().save();
        plugin.getApexLogger().log(ApexLogger.LogType.ADMIN,
                sender.getName() + " set " + target.getName() + "'s tokens to " + data.getTokensConsumed());
        Msg.send(sender, "<green>" + target.getName() + " now has "
                + data.getTokensConsumed() + " consumed tokens.</green>");
    }

    private void setLock(CommandSender sender, String[] args, boolean unlock) {
        if (args.length < 2) {
            Msg.send(sender, "<red>Usage: /apex " + (unlock ? "unlock" : "lock") + " <player></red>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            Msg.send(sender, "<red>Player not found.</red>");
            return;
        }
        if (unlock) {
            plugin.getApexManager().unlockAbility(target, "admin unlock by " + sender.getName());
        } else {
            plugin.getApexManager().getData(target.getUniqueId()).setAbilityUnlocked(false);
            plugin.getApexManager().save();
            Msg.send(target, "<red>Your ability was locked by an admin.</red>");
        }
        plugin.getApexLogger().log(ApexLogger.LogType.ADMIN, sender.getName()
                + (unlock ? " unlocked " : " locked ") + target.getName() + "'s ability");
        Msg.send(sender, "<green>Done.</green>");
    }

    /** Toggles ability test mode: abilities target mobs and skip lock + cooldown. */
    private void test(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "<red>Test mode is only available in game.</red>");
            return;
        }
        boolean on = plugin.getAbilityManager().toggleTestMode(player);
        plugin.getApexLogger().log(ApexLogger.LogType.ADMIN,
                player.getName() + " turned test mode " + (on ? "ON" : "OFF"));
        if (on) {
            Msg.send(player, "<green>Test mode ON.</green> <gray>Abilities ignore the lock and "
                    + "cooldown and can target mobs. Run /apex test again to turn off.</gray>");
        } else {
            Msg.send(player, "<yellow>Test mode OFF.</yellow>");
        }
    }

    /** Scans loaded areas for kill token items and reports where they are. */
    private void track(CommandSender sender) {
        List<String> found = new ArrayList<>();
        int[] total = {0};

        // Online players: inventory + ender chest.
        for (Player online : Bukkit.getOnlinePlayers()) {
            int inv = plugin.getItemManager().countKillTokens(online.getInventory().getContents());
            int ender = plugin.getItemManager().countKillTokens(online.getEnderChest().getContents());
            if (inv > 0) {
                found.add("<white>" + inv + "</white> <gray>in</gray> <yellow>" + online.getName()
                        + "</yellow><gray>'s inventory</gray>");
                total[0] += inv;
            }
            if (ender > 0) {
                found.add("<white>" + ender + "</white> <gray>in</gray> <yellow>" + online.getName()
                        + "</yellow><gray>'s ender chest</gray>");
                total[0] += ender;
            }
        }

        // Loaded containers + dropped items across every world.
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                for (org.bukkit.block.BlockState state : chunk.getTileEntities()) {
                    if (state instanceof org.bukkit.block.Container container) {
                        int n = plugin.getItemManager().countKillTokens(container.getInventory().getContents());
                        if (n > 0) {
                            var loc = state.getLocation();
                            found.add("<white>" + n + "</white> <gray>in a</gray> <aqua>"
                                    + prettyType(state.getType().name()) + "</aqua> <gray>@ " + world.getName()
                                    + " " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "</gray>");
                            total[0] += n;
                        }
                    }
                }
            }
            for (org.bukkit.entity.Item drop : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
                int n = plugin.getItemManager().countKillTokens(new org.bukkit.inventory.ItemStack[]{drop.getItemStack()});
                if (n > 0) {
                    var loc = drop.getLocation();
                    found.add("<white>" + n + "</white> <gray>dropped @ " + world.getName() + " "
                            + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "</gray>");
                    total[0] += n;
                }
            }
        }

        Msg.sendRaw(sender, "<gold><bold>--- Kill Token Tracker ---</bold></gold>");
        if (found.isEmpty()) {
            Msg.sendRaw(sender, "<gray>No kill token items found in online inventories, ender chests, "
                    + "loaded containers, or on the ground.</gray>");
        } else {
            found.forEach(line -> Msg.sendRaw(sender, "<gray>-</gray> " + line));
            Msg.sendRaw(sender, "<yellow>Total tracked: <white>" + total[0] + "</white></yellow>");
        }
        Msg.sendRaw(sender, "<dark_gray>(Offline players and unloaded chunks are not scanned.)</dark_gray>");
    }

    private String prettyType(String enumName) {
        return enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Msg.send(sender, "<red>Usage: /apex give <player> <token|roller|reroll|trader> [amount]</red>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            Msg.send(sender, "<red>Player not found.</red>");
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                Msg.send(sender, "<red>Amount must be a number.</red>");
                return;
            }
        }
        var itemManager = plugin.getItemManager();
        var stack = switch (args[2].toLowerCase(Locale.ROOT)) {
            case "token" -> itemManager.killToken(amount);
            case "roller" -> itemManager.rollItem(amount);
            case "reroll" -> itemManager.rerollItem(amount);
            case "trader" -> itemManager.traderItem(amount);
            default -> null;
        };
        if (stack == null) {
            Msg.send(sender, "<red>Unknown item. Use token, roller, reroll or trader.</red>");
            return;
        }
        target.getInventory().addItem(stack)
                .values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        plugin.getApexLogger().log(ApexLogger.LogType.ADMIN, sender.getName()
                + " gave " + amount + "x " + args[2] + " to " + target.getName());
        Msg.send(sender, "<green>Gave " + amount + "x " + args[2] + " to " + target.getName() + ".</green>");
    }

    private void logs(CommandSender sender, String[] args) {
        String filter = args.length >= 2 ? args[1] : null;
        int limit = 10;
        if (args.length >= 3) {
            try {
                limit = Math.min(50, Math.max(1, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                Msg.send(sender, "<red>Usage: /apex logs [filter] [count]</red>");
                return;
            }
        }
        List<String> lines = plugin.getApexLogger().query(filter, limit);
        Msg.sendRaw(sender, "<gold><bold>--- Apex Logs"
                + (filter == null ? "" : " (filter: " + filter + ")") + " ---</bold></gold>");
        if (lines.isEmpty()) {
            Msg.sendRaw(sender, "<gray>No matching entries in the recent buffer.</gray>");
        }
        for (String line : lines) {
            Msg.sendRaw(sender, "<gray>" + line.replace("<", "\\<") + "</gray>");
        }
    }

    private void help(CommandSender sender, boolean admin) {
        Msg.sendRaw(sender, "<gold><bold>--- Apex SMP ---</bold></gold>");
        Msg.sendRaw(sender, "<yellow>/ability</yellow> <gray>- use your apex ability</gray>");
        Msg.sendRaw(sender, "<yellow>/apex info</yellow> <gray>- view your apex, passive and progress</gray>");
        Msg.sendRaw(sender, "<yellow>/apex withdraw [amount]</yellow> <gray>- withdraw consumed kill tokens</gray>");
        if (admin) {
            Msg.sendRaw(sender, "<red>Admin:</red>");
            Msg.sendRaw(sender, "<yellow>/apex start</yellow> <gray>- give everyone a roll totem</gray>");
            Msg.sendRaw(sender, "<yellow>/apex panel</yellow> <gray>- open the admin panel</gray>");
            Msg.sendRaw(sender, "<yellow>/apex set <player> <apex></yellow> <gray>- set an apex</gray>");
            Msg.sendRaw(sender, "<yellow>/apex roll <player></yellow> <gray>- reroll a player</gray>");
            Msg.sendRaw(sender, "<yellow>/apex tokens <player> <n></yellow> <gray>- set consumed tokens</gray>");
            Msg.sendRaw(sender, "<yellow>/apex unlock|lock <player></yellow> <gray>- force ability state</gray>");
            Msg.sendRaw(sender, "<yellow>/apex give <player> <item> [n]</yellow> <gray>- give apex items</gray>");
            Msg.sendRaw(sender, "<yellow>/apex logs [filter] [count]</yellow> <gray>- query the apex log</gray>");
            Msg.sendRaw(sender, "<yellow>/apex test</yellow> <gray>- toggle ability test mode (mobs, no lock/cooldown)</gray>");
            Msg.sendRaw(sender, "<yellow>/apex track</yellow> <gray>- find kill token items</gray>");
            Msg.sendRaw(sender, "<yellow>/cooldown [player|all]</yellow> <gray>- reset ability cooldowns</gray>");
            Msg.sendRaw(sender, "<gray>Dragon: whoever holds the dragon egg becomes the Dragon apex.</gray>");
        }
    }

    // ------------------------------------------------------------------
    // Tab completion
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission("apexsmp.admin");
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(PLAYER_SUBS);
            if (admin) {
                subs.addAll(ADMIN_SUBS);
            }
            return filter(subs, args[0]);
        }
        if (args.length == 2 && List.of("set", "roll", "tokens", "unlock", "lock", "give", "info")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return filter(Arrays.stream(ApexType.values()).map(Enum::name).toList(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("token", "roller", "reroll", "trader"), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
