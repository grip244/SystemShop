package com.holyw.wowah;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Random;

public class EventManager {

    private final SystemShop plugin;
    private final Random random = new Random();
    private MarketEventType currentEvent = MarketEventType.NONE;
    private long eventEndTime = 0;

    public enum MarketEventType {
        NONE,
        MARKET_CRASH,
        MARKET_BOOM
    }

    public EventManager(SystemShop plugin) {
        this.plugin = plugin;
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

    public MarketEventType getCurrentEvent() {
        return currentEvent;
    }
}
