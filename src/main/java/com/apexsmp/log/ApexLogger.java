package com.apexsmp.log;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Tracks every apex event (rolls, ability uses, token claims, kills, admin actions)
 * to a daily log file and keeps a ring buffer for in-game querying via /apex logs.
 */
public class ApexLogger {

    public enum LogType { ROLL, EVOLVE, ABILITY, TOKEN_CLAIM, TOKEN_WITHDRAW, KILL, ADMIN }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int BUFFER_SIZE = 1000;

    private final JavaPlugin plugin;
    private final File logDir;
    private final Deque<String> recent = new ArrayDeque<>();

    public ApexLogger(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logDir = new File(plugin.getDataFolder(), "logs");
        if (!logDir.exists() && !logDir.mkdirs()) {
            plugin.getLogger().warning("Could not create log directory: " + logDir.getAbsolutePath());
        }
    }

    public synchronized void log(LogType type, String message) {
        String line = "[" + LocalDateTime.now().format(TIME) + "] [" + type.name() + "] " + message;
        recent.addLast(line);
        while (recent.size() > BUFFER_SIZE) {
            recent.removeFirst();
        }
        plugin.getLogger().info("[ApexLog] " + line);

        File file = new File(logDir, "apex-" + LocalDate.now() + ".log");
        try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {
            out.println(line);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write apex log: " + e.getMessage());
        }
    }

    /** Most recent entries, newest last, optionally filtered by a case-insensitive substring. */
    public synchronized List<String> query(String filter, int limit) {
        List<String> matches = new ArrayList<>();
        String needle = filter == null ? null : filter.toLowerCase(Locale.ROOT);
        for (String line : recent) {
            if (needle == null || line.toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(line);
            }
        }
        if (matches.size() > limit) {
            matches = matches.subList(matches.size() - limit, matches.size());
        }
        return matches;
    }
}
