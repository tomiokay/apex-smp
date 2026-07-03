package com.apexsmp.command;

import com.apexsmp.ApexPlugin;
import com.apexsmp.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AbilityCommand implements CommandExecutor {

    private final ApexPlugin plugin;

    public AbilityCommand(ApexPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "<red>Only players have apex abilities.</red>");
            return true;
        }
        plugin.getAbilityManager().cast(player);
        return true;
    }
}
