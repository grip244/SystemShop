package com.holyw.wowah;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ShopScoreboard implements Listener {

    private final SystemShop plugin;
    private final ScoreboardManager manager;
    private final Set<java.util.UUID> hiddenPlayers = new HashSet<>();

    public ShopScoreboard(SystemShop plugin) {
        this.plugin = plugin;
        this.manager = plugin.getServer().getScoreboardManager();
    }

    public void enable() {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // create scoreboards for online players
        // load persisted opt-outs
        List<String> persisted = plugin.getConfig().getStringList("scoreboard.opt-outs");
        for (String s : persisted) {
            try {
                hiddenPlayers.add(java.util.UUID.fromString(s));
            } catch (Exception ignored) {}
        }

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (hiddenPlayers.contains(p.getUniqueId())) continue;
            if (!p.hasPermission(plugin.getConfig().getString("scoreboard.opt-out-permission", "systemshop.scoreboard.optout"))) {
                setupScoreboard(p);
            }
        }
        startUpdateTask();
    }

    public void disable() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.setScoreboard(manager.getMainScoreboard());
        }
    }

    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    updateScoreboard(p);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L * plugin.getConfig().getInt("scoreboard.update-interval-seconds", 5));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (hiddenPlayers.contains(p.getUniqueId())) return;
        if (p.hasPermission(plugin.getConfig().getString("scoreboard.opt-out-permission", "systemshop.scoreboard.optout"))) return;
        setupScoreboard(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        e.getPlayer().setScoreboard(manager.getMainScoreboard());
    }

    private void setupScoreboard(Player player) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        if (hiddenPlayers.contains(player.getUniqueId())) return;
        if (player.hasPermission(plugin.getConfig().getString("scoreboard.opt-out-permission", "systemshop.scoreboard.optout"))) return;
        Scoreboard board = manager.getNewScoreboard();
        Objective obj = board.getObjective("systemshop");
        if (obj == null) {
            // Use modern API (non-deprecated) to register objective without Adventure
            obj = board.registerNewObjective("systemshop", Criteria.DUMMY, ChatColor.translateAlternateColorCodes('&', "&6&l« &e&lSystemShop &6&l»"));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        setLines(board, player);
        player.setScoreboard(board);
    }

    // Hide scoreboard for player (persisted)
    public void hideForPlayer(Player player) {
        hiddenPlayers.add(player.getUniqueId());
        player.setScoreboard(manager.getMainScoreboard());
        persistOptOuts();
    }

    // Show scoreboard for player (persisted)
    public void showForPlayer(Player player) {
        hiddenPlayers.remove(player.getUniqueId());
        setupScoreboard(player);
        persistOptOuts();
    }

    public boolean isHidden(Player player) {
        return hiddenPlayers.contains(player.getUniqueId());
    }

    private void persistOptOuts() {
        List<String> out = new java.util.ArrayList<>();
        for (java.util.UUID id : hiddenPlayers) out.add(id.toString());
        plugin.getConfig().set("scoreboard.opt-outs", out);
        plugin.saveConfig();
    }

    private void setLines(Scoreboard board, Player player) {
        Objective obj = board.getObjective("systemshop");
        if (obj == null) return;

        // clear existing entries
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

        // Load lines from config
        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
        if (lines == null || lines.isEmpty()) return;
        // add in reverse order so first config line appears at the top
        int score = lines.size();
        for (String raw : lines) {
            String parsed = parsePlaceholders(player, raw);
            addScore(board, obj, parsed, score);
            score--;
        }
    }

    private void updateScoreboard(Player player) {
        Scoreboard board = player.getScoreboard();
        Objective obj = board.getObjective("systemshop");
        if (obj == null) return;
        setLines(board, player);
    }

    private void addScore(Scoreboard board, Objective obj, String text, int score) {
        String entry = ChatColor.translateAlternateColorCodes('&', text);
        Score s = obj.getScore(entry);
        s.setScore(score);
    }

    private String parsePlaceholders(Player player, String text) {
        String converted = ChatColor.translateAlternateColorCodes('&', text);

        // Manual replacements
        if (converted.contains("%systemshop_price_DIAMOND%")) {
            try {
                double price = plugin.getPricingManager().getPrice(new ItemStack(Material.DIAMOND));
                converted = converted.replace("%systemshop_price_DIAMOND%", String.format("%,.2f", price));
            } catch (Exception ex) {
                converted = converted.replace("%systemshop_price_DIAMOND%", "");
            }
        }

        Plugin papi = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (papi != null) {
            try {
                // Let PAPI replace placeholders too (in case other plugins add tokens)
                converted = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, converted);
            } catch (Throwable t) {
                // ignore and continue with our own values
            }
        }

        return converted;
    }
}
