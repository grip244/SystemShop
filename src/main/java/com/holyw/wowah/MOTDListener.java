package com.holyw.wowah;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Random;

public class MOTDListener implements Listener {

    private final SystemShop plugin;
    private final Random random = new Random();

    public MOTDListener(SystemShop plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        if (plugin.getMotdConfig() == null) {
            plugin.getLogger().warning("MOTD config not loaded; skipping MOTD.");
            return;
        }

        if (!plugin.getMotdConfig().getBoolean("motd.enabled", false)) {
            return;
        }

        List<String> line1Options = plugin.getMotdConfig().getStringList("motd.line1");
        String line1 = "";
        if (line1Options != null && !line1Options.isEmpty()) {
            try {
                line1 = line1Options.get(random.nextInt(line1Options.size()));
            } catch (Exception e) {
                plugin.getLogger().warning("Error picking MOTD line1: " + e.getMessage());
            }
        }

        String line2 = plugin.getMotdConfig().getString("motd.line2", "");

        if (line2 != null && line2.contains("{uptime}")) {
            long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
            line2 = line2.replace("{uptime}", formatUptime(uptimeMillis));
        }

        event.setMotd(ChatColor.translateAlternateColorCodes('&', line1 == null ? "" : line1) + "\n" + ChatColor.translateAlternateColorCodes('&', line2 == null ? "" : line2));
    }

    private String formatUptime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%d days, %d hours, %d minutes", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%d hours, %d minutes, %d seconds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%d minutes, %d seconds", minutes, seconds % 60);
        } else {
            return String.format("%d seconds", seconds);
        }
    }
}