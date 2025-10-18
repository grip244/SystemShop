package com.holyw.wowah;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Random;

public class EventManager {

    private final SystemShop plugin;
    private final Random random = new Random();
    private MarketEventType currentEvent = MarketEventType.NONE;
    private long eventEndTime = 0;
    // Recent market multipliers for simple charting (stores last N samples)
    private final double[] recentMultipliers;
    private int recentIndex = 0;
    private final int recentSize = 60; // 60 samples = last 60 seconds by default

    public enum MarketEventType {
        NONE,
        MARKET_CRASH,
        MARKET_BOOM
    }

    public EventManager(SystemShop plugin) {
        this.plugin = plugin;
        this.recentMultipliers = new double[recentSize];
        for (int i = 0; i < recentSize; i++) recentMultipliers[i] = 1.0;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L); // Tick every second
    }

    private void tick() {
        FileConfiguration config = plugin.getConfig();
        if (System.currentTimeMillis() > eventEndTime) {
            if (currentEvent != MarketEventType.NONE) {
                currentEvent = MarketEventType.NONE;
                plugin.getServer().broadcastMessage(Lang.get("event-end"));
            }

            double eventChance = config.getDouble("events.chance", 0.01);
            if (random.nextDouble() < eventChance) {
                startRandomEvent();
            }
        }
        // Record current multiplier for charting
        double multiplier = 1.0;
        if (currentEvent == MarketEventType.MARKET_CRASH) {
            multiplier = config.getDouble("events.market-crash-multiplier", 0.8);
        } else if (currentEvent == MarketEventType.MARKET_BOOM) {
            multiplier = config.getDouble("events.market-boom-multiplier", 1.2);
        }
        recentMultipliers[recentIndex] = multiplier;
        recentIndex = (recentIndex + 1) % recentSize;
    }

    private void startRandomEvent() {
        FileConfiguration config = plugin.getConfig();
        int duration = config.getInt("events.duration", 600) * 1000; // in seconds
        eventEndTime = System.currentTimeMillis() + duration;

        if (random.nextBoolean()) {
            currentEvent = MarketEventType.MARKET_CRASH;
            plugin.getServer().broadcastMessage(Lang.get("event-market-crash"));
        } else {
            currentEvent = MarketEventType.MARKET_BOOM;
            plugin.getServer().broadcastMessage(Lang.get("event-market-boom"));
        }
    }

    public void startBoom() {
        FileConfiguration config = plugin.getConfig();
        int duration = config.getInt("events.duration", 600) * 1000; // in seconds
        eventEndTime = System.currentTimeMillis() + duration;
        currentEvent = MarketEventType.MARKET_BOOM;
        plugin.getServer().broadcastMessage(Lang.get("event-market-boom"));
    }

    public void startCrash() {
        FileConfiguration config = plugin.getConfig();
        int duration = config.getInt("events.duration", 600) * 1000; // in seconds
        eventEndTime = System.currentTimeMillis() + duration;
        currentEvent = MarketEventType.MARKET_CRASH;
        plugin.getServer().broadcastMessage(Lang.get("event-market-crash"));
    }

    public void stopEvent() {
        currentEvent = MarketEventType.NONE;
        eventEndTime = System.currentTimeMillis();
        plugin.getServer().broadcastMessage(Lang.get("event-end"));
    }

    public MarketEventType getCurrentEvent() {
        return currentEvent;
    }

    /**
     * Return a copy of recent multipliers in chronological order (oldest -> newest)
     */
    public double[] getRecentMultipliers() {
        double[] out = new double[recentSize];
        int idx = recentIndex;
        for (int i = 0; i < recentSize; i++) {
            out[i] = recentMultipliers[idx];
            idx = (idx + 1) % recentSize;
        }
        return out;
    }
}
