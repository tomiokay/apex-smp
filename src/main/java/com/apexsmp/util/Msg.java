package com.apexsmp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public final class Msg {

    public static final String PREFIX = "<dark_gray>[<gold>Apex</gold>]</dark_gray> ";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Msg() {
    }

    public static Component mm(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    public static void send(CommandSender to, String miniMessage) {
        to.sendMessage(MM.deserialize(PREFIX + miniMessage));
    }

    public static void sendRaw(CommandSender to, String miniMessage) {
        to.sendMessage(MM.deserialize(miniMessage));
    }
}
