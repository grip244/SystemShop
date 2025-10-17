package com.holyw.wowah;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

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
            obj = board.registerNewObjective("systemshop", Criteria.DUMMY, ChatColor.GOLD + "SystemShop");
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
        // First apply our own replacements so placeholders are present for PAPI too
        converted = converted.replace("%systemshop_total_items%", String.valueOf(plugin.getAuctionHouseManager().getTotalItems()));
        long discounted = plugin.getAuctionHouseManager().getAuctionItems().stream().filter(com.holyw.wowah.AuctionHouseManager.AuctionItem::isOnSale).count();
        converted = converted.replace("%systemshop_items_on_sale%", String.valueOf(discounted));
        try {
            double price = plugin.getPricingManager().getPrice(org.bukkit.Material.DIAMOND);
            converted = converted.replace("%systemshop_price_DIAMOND%", String.valueOf(price));
        } catch (Exception ex) {
            converted = converted.replace("%systemshop_price_DIAMOND%", "");
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

        // Re-apply our market-specific replacements in case PAPI modified or removed them
        // Market multiplier placeholder
        try {
            EventManager.MarketEventType ev = plugin.getEventManager().getCurrentEvent();
            double multiplier = 1.0;
            if (ev == EventManager.MarketEventType.MARKET_CRASH) multiplier = plugin.getConfig().getDouble("events.market-crash-multiplier", 0.8);
            if (ev == EventManager.MarketEventType.MARKET_BOOM) multiplier = plugin.getConfig().getDouble("events.market-boom-multiplier", 1.2);
            // Determine trend arrow from recent multipliers
            String trend = "";
            try {
                double[] recent = plugin.getEventManager().getRecentMultipliers();
                if (recent != null && recent.length >= 2) {
                    double prev = recent[recent.length - 2];
                    double last = recent[recent.length - 1];
                    if (last > prev) trend = "§a↑"; // green up
                    else if (last < prev) trend = "§c↓"; // red down
                    else trend = "§e→"; // yellow flat
                }
            } catch (Throwable t) {
                trend = "";
            }
            String colored;
            if (multiplier > 1.0) colored = "§a" + String.format("%.2fx", multiplier);
            else if (multiplier < 1.0) colored = "§c" + String.format("%.2fx", multiplier);
            else colored = "§f1.00x";
            converted = converted.replace("%systemshop_market_multiplier%", colored + (trend.isEmpty() ? "" : " " + trend));
        } catch (Throwable ignore) {
            converted = converted.replace("%systemshop_market_multiplier%", "§f1.00x");
        }

        // Market sparkline chart placeholder (compact unicode bars)
        if (converted.contains("%systemshop_market_chart%")) {
            try {
                double[] history = plugin.getEventManager().getRecentMultipliers();
                String chart = buildSparkline(history, 10); // 10-char sparkline
                converted = converted.replace("%systemshop_market_chart%", chart);
            } catch (Throwable t) {
                converted = converted.replace("%systemshop_market_chart%", "[market]");
            }
        }

        return converted;
    }

    // Build a compact sparkline of the recent multipliers using unicode block characters
    private String buildSparkline(double[] values, int width) {
        if (values == null || values.length == 0) return "";
        int n = values.length;
        // compress into width buckets (simple averaging)
        double[] buckets = new double[width];
        for (int i = 0; i < width; i++) {
            int start = (int) Math.floor((double) i * n / width);
            int end = (int) Math.floor((double) (i + 1) * n / width);
            if (end <= start) end = Math.min(start + 1, n);
            double sum = 0;
            for (int j = start; j < end; j++) sum += values[j];
            buckets[i] = sum / (end - start);
        }
        double min = Double.MAX_VALUE, max = Double.MIN_VALUE;
        for (double v : buckets) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        if (min == max) {
            // flat line
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < width; i++) sb.append('▁');
            return sb.toString();
        }
        // Unicode blocks for sparkline (low to high)
        char[] blocks = new char[]{'▁','▂','▃','▄','▅','▆','▇','█'};
        StringBuilder sb = new StringBuilder();
        for (double v : buckets) {
            int idx = (int) Math.floor((v - min) / (max - min) * (blocks.length - 1));
            idx = Math.max(0, Math.min(blocks.length - 1, idx));
            sb.append(blocks[idx]);
        }
        return sb.toString();
    }
}
