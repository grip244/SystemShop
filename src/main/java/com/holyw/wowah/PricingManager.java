package com.holyw.wowah;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class PricingManager {

    private final SystemShop plugin;
    private FileConfiguration pricingConfig;
    private final Map<String, Double> essentialsWorth = new HashMap<>();
    private final Map<Material, Integer> stockLevels = new HashMap<>();

    private final Map<String, Double> materialTiers = new HashMap<>();
    private final List<Map.Entry<String, List<String>>> tierAssignments = new ArrayList<>();
    private final List<Map.Entry<Double, List<String>>> itemTypeMultipliers = new ArrayList<>();
    private final Map<Material, Map.Entry<Integer, Integer>> specialOverrides = new HashMap<>();
    private final Random random = new Random();

    public PricingManager(SystemShop plugin) {
        this.plugin = plugin;
        load();
    }

    @SuppressWarnings("unchecked")
    public void load() {
        File pricingFile = new File(plugin.getDataFolder(), "pricing.yml");
        if (!pricingFile.exists()) {
            plugin.saveResource("pricing.yml", false);
        }
        pricingConfig = YamlConfiguration.loadConfiguration(pricingFile);

        // Clear old data
        materialTiers.clear();
        tierAssignments.clear();
        itemTypeMultipliers.clear();
        specialOverrides.clear();
        essentialsWorth.clear();

        // Load EssentialsX worth data
        FileConfiguration config = plugin.getConfig();
        if (config.getBoolean("pricing.use-essentials-worth", true)) {
            String essentialsWorthPath = config.getString("pricing.essentials-worth-path", "plugins/Essentials/worth.yml");
            File essentialsWorthFile = new File(essentialsWorthPath);
            if (essentialsWorthFile.exists()) {
                FileConfiguration essentialsWorthConfig = YamlConfiguration.loadConfiguration(essentialsWorthFile);
                if (essentialsWorthConfig.getConfigurationSection("worth") != null) {
                    for (String key : essentialsWorthConfig.getConfigurationSection("worth").getKeys(false)) {
                        essentialsWorth.put(key.toUpperCase(), essentialsWorthConfig.getDouble("worth." + key));
                    }
                    plugin.getLogger().info("Successfully loaded EssentialsX worth data.");
                }
            } else {
                plugin.getLogger().warning("EssentialsX worth.yml file not found at: " + essentialsWorthPath);
            }
        }

        // Load material tiers
        ConfigurationSection tiersSection = pricingConfig.getConfigurationSection("material-tiers");
        if (tiersSection != null) {
            for (String key : tiersSection.getKeys(false)) {
                materialTiers.put(key.toUpperCase(), tiersSection.getDouble(key));
            }
        }

        // Load tier assignments
        for (Map<?, ?> map : pricingConfig.getMapList("tier-assignments")) {
            String tier = (String) map.get("tier");
            List<String> materials = (List<String>) map.get("materials");
            tierAssignments.add(new AbstractMap.SimpleEntry<>(tier.toUpperCase(), materials));
        }

        // Load item type multipliers
        for (Map<?, ?> map : pricingConfig.getMapList("item-type-multipliers")) {
            double multiplier = (double) map.get("multiplier");
            List<String> keywords = (List<String>) map.get("keywords");
            itemTypeMultipliers.add(new AbstractMap.SimpleEntry<>(multiplier, keywords));
        }

        // Load special overrides
        ConfigurationSection overridesSection = pricingConfig.getConfigurationSection("special-overrides");
        if (overridesSection != null) {
            for (String key : overridesSection.getKeys(false)) {
                try {
                    Material material = Material.valueOf(key.toUpperCase());
                    String[] range = overridesSection.getString(key).split(",");
                    int min = Integer.parseInt(range[0]);
                    int max = Integer.parseInt(range[1]);
                    specialOverrides.put(material, new AbstractMap.SimpleEntry<>(min, max));
                } catch (Exception e) {
                    plugin.getLogger().warning("Invalid special override in pricing.yml: " + key);
                }
            }
        }
    }

    public void updateStockLevels() {
        stockLevels.clear();
        for (AuctionHouseManager.AuctionItem item : plugin.getAuctionHouseManager().getAuctionItems()) {
            Material material = item.getItemStack().getType();
            stockLevels.put(material, stockLevels.getOrDefault(material, 0) + item.getItemStack().getAmount());
        }
    }

    public double getPrice(Material material) {
        double basePrice;
        // Check EssentialsX worth data first
        if (essentialsWorth.containsKey(material.name())) {
            basePrice = essentialsWorth.get(material.name());
        } else if (specialOverrides.containsKey(material)) {
            Map.Entry<Integer, Integer> range = specialOverrides.get(material);
            int min = range.getKey();
            int max = range.getValue();
            basePrice = min + random.nextInt(max - min);
        } else {
            String tierName = getTierName(material);
            double tierPrice = materialTiers.getOrDefault(tierName, 10.0);
            double multiplier = getItemTypeMultiplier(material);
            basePrice = tierPrice * multiplier;
        }

        // Apply dynamic pricing
        FileConfiguration config = plugin.getConfig();
        if (config.getBoolean("pricing.dynamic-pricing.enabled", true)) {
            int maxStock = config.getInt("pricing.dynamic-pricing.max-stock", 100);
            double priceVolatility = config.getDouble("pricing.dynamic-pricing.price-volatility", 0.5);
            int currentStock = stockLevels.getOrDefault(material, 0);

            if (currentStock > 0) {
                double stockRatio = (double) (maxStock - currentStock) / maxStock;
                basePrice *= (1 + stockRatio * priceVolatility);
            }
        }

        // Apply event-based pricing
        EventManager.MarketEventType currentEvent = plugin.getEventManager().getCurrentEvent();
        if (currentEvent == EventManager.MarketEventType.MARKET_CRASH) {
            basePrice *= config.getDouble("events.market-crash-multiplier", 0.8);
        } else if (currentEvent == EventManager.MarketEventType.MARKET_BOOM) {
            basePrice *= config.getDouble("events.market-boom-multiplier", 1.2);
        }

        // Add some randomness
        basePrice += random.nextDouble() * basePrice * 0.2; // Add up to 20% random variation

        return Math.round(basePrice * 100.0) / 100.0;
    }

    public String getTierName(Material material) {
        String name = material.name().toLowerCase();
        for (Map.Entry<String, List<String>> assignment : tierAssignments) {
            for (String keyword : assignment.getValue()) {
                if (name.contains(keyword)) {
                    return assignment.getKey();
                }
            }
        }
        return "TRASH"; // Default tier
    }

    public double getBasePriceForTier(String tierName) {
        return materialTiers.getOrDefault(tierName.toUpperCase(), 10.0);
    }

    private double getItemTypeMultiplier(Material material) {
        String name = material.name().toLowerCase();
        for (Map.Entry<Double, List<String>> entry : itemTypeMultipliers) {
            for (String keyword : entry.getValue()) {
                if (name.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return 1.0;
    }
}