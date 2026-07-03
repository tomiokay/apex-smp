package com.apexsmp.command;

import com.apexsmp.ApexPlugin;
import com.apexsmp.log.ApexLogger;
import com.apexsmp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * /cooldown - admin resets ability cooldowns.
 *   /cooldown          reset your own
 *   /cooldown all      reset everyone online
 *   /cooldown <player> reset one player
 */
public class CooldownCommand implements TabExecutor {

    private final ApexPlugin plugin;

    public CooldownCommand(ApexPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("apexsmp.admin")) {
            Msg.send(sender, "<red>You do not have permission for that.</red>");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                Msg.send(sender, "<red>Usage from console: /cooldown <player|all></red>");
                return true;
            }
            plugin.getAbilityManager().resetCooldown(player);
            Msg.send(player, "<green>Your ability cooldown was reset.</green>");
            log(sender, "reset their own cooldown");
            return true;
        }

        if (args[0].equalsIgnoreCase("all")) {
            plugin.getAbilityManager().resetAllCooldowns();
            Bukkit.getOnlinePlayers().forEach(p ->
                    Msg.send(p, "<green>All ability cooldowns were reset.</green>"));
            Msg.send(sender, "<green>Reset ability cooldowns for everyone.</green>");
            log(sender, "reset ALL cooldowns");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            Msg.send(sender, "<red>Player not found.</red>");
            return true;
        }
        plugin.getAbilityManager().resetCooldown(target);
        Msg.send(target, "<green>Your ability cooldown was reset.</green>");
        Msg.send(sender, "<green>Reset " + target.getName() + "'s ability cooldown.</green>");
        log(sender, "reset " + target.getName() + "'s cooldown");
        return true;
    }

    private void log(CommandSender sender, String what) {
        plugin.getApexLogger().log(ApexLogger.LogType.ADMIN, sender.getName() + " " + what);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("apexsmp.admin")) {
            String lower = args[0].toLowerCase(Locale.ROOT);
            return java.util.stream.Stream.concat(
                            java.util.stream.Stream.of("all"),
                            Bukkit.getOnlinePlayers().stream().map(Player::getName))
                    .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower))
                    .toList();
        }
        return List.of();
    }
}
