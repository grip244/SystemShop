package com.holyw.wowah;


import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;


import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class BuybackManager {

    private final SystemShop plugin;
    private final Map<Material, BuybackInfo> buybackPrices = new HashMap<>();
    private final Random random = new Random();

    // Configuration keys
    private static final String BUYBACK_ENABLED = "buyback.enabled";
    private static final String SELL_PRICE_MULTIPLIER = "pricing.sell-price-multiplier";
    private static final String MIN_MULTIPLIER = "buyback.min-multiplier";
    private static final String MAX_MULTIPLIER = "buyback.max-multiplier";
    private static final String DECREASE_FACTOR = "buyback.decrease-factor";
    private static final String MIN_PRICE = "buyback.min-price";

    public static class BuybackInfo {
        public double basePrice;
        public int quantitySold;

        public BuybackInfo(double basePrice) {
            this.basePrice = basePrice;
            this.quantitySold = 0;
        }
    }

    public BuybackManager(SystemShop plugin) {
        this.plugin = plugin;
        long ticksPerDay = 20L * 60 * 60 * 24; // 20 ticks/sec * 60 sec/min * 60 min/hr * 24 hr/day
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::resetDailyPrices, 0, ticksPerDay);
    }

    /**
     * Clears out the daily buyback prices. This is intended to be run once per day.
     * A possible refinement is to pre-populate this with certain items or tiers.
     */
    public void resetDailyPrices() {
        buybackPrices.clear();
        plugin.getLogger().info("Daily buyback prices have been reset.");
    }

    /**
     * Calculates the buyback price for a given ItemStack.
     * If the dynamic buyback system is disabled, it returns a flat percentage of the item's regular price.
     * Otherwise, it calculates a dynamic price that decreases as more of the item is sold.
     * @param itemStack The item to get the buyback price for.
     * @return The calculated buyback price.
     */
    public double getBuybackPrice(ItemStack itemStack) {
        FileConfiguration config = plugin.getConfig();
        boolean enabled = config.getBoolean(BUYBACK_ENABLED, true);
        if (!enabled) {
            return plugin.getPricingManager().getPrice(itemStack) * config.getDouble(SELL_PRICE_MULTIPLIER, 0.75);
        }

        Material material = itemStack.getType();
        BuybackInfo info = buybackPrices.get(material);
        if (info == null) {
            double basePrice = plugin.getPricingManager().getPrice(itemStack);

            if (basePrice <= 0) {
                return 0; // Not sellable
            }

            double minMultiplier = config.getDouble(MIN_MULTIPLIER, 0.5);
            double maxMultiplier = config.getDouble(MAX_MULTIPLIER, 1.5);
            double randomMultiplier = minMultiplier + (maxMultiplier - minMultiplier) * random.nextDouble();
            info = new BuybackInfo(basePrice * randomMultiplier);
            buybackPrices.put(material, info);
        }

        // The price decreases exponentially based on the quantity sold.
        // This prevents players from flooding the market with a single item.
        double decreaseFactor = config.getDouble(DECREASE_FACTOR, 0.01);
        double price = info.basePrice * Math.pow(1 - decreaseFactor, info.quantitySold);
        double minPrice = config.getDouble(MIN_PRICE, 0.01); // A floor price to prevent items from becoming worthless.

        return Math.max(minPrice, price);
    }

    /**
     * Records that an item has been sold to the server, increasing its quantitySold count.
     * @param material The material of the item that was sold.
     * @param amount The amount of the item that was sold.
     */
    public void recordSale(Material material, int amount) {
        FileConfiguration config = plugin.getConfig();
        boolean enabled = config.getBoolean(BUYBACK_ENABLED, true);
        if (!enabled) {
            return;
        }

        BuybackInfo info = buybackPrices.get(material);
        if (info != null) {
            info.quantitySold += amount;
        }
    }
}
