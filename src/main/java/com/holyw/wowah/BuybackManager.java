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
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::resetDailyPrices, 0, 20L * 60 * 60 * 24);
    }

    public void resetDailyPrices() {
        buybackPrices.clear();
        // You can add logic here to pre-populate with certain items if you want
        plugin.getLogger().info("Daily buyback prices have been reset.");
    }

    public double getBuybackPrice(ItemStack itemStack) {
        FileConfiguration config = plugin.getConfig();
        boolean enabled = config.getBoolean("buyback.enabled", true);
        if (!enabled) {
            return plugin.getPricingManager().getPrice(itemStack) * config.getDouble("pricing.sell-price-multiplier", 0.75);
        }

        Material material = itemStack.getType();
        BuybackInfo info = buybackPrices.get(material);
        if (info == null) {
            double basePrice = plugin.getPricingManager().getPrice(itemStack);

            if (basePrice <= 0) {
                return 0; // Not sellable
            }

            double minMultiplier = config.getDouble("buyback.min-multiplier", 0.5);
            double maxMultiplier = config.getDouble("buyback.max-multiplier", 1.5);
            double randomMultiplier = minMultiplier + (maxMultiplier - minMultiplier) * random.nextDouble();
            info = new BuybackInfo(basePrice * randomMultiplier);
            buybackPrices.put(material, info);
        }

        double decreaseFactor = config.getDouble("buyback.decrease-factor", 0.01);
        double price = info.basePrice * Math.pow(1 - decreaseFactor, info.quantitySold);
        double minPrice = config.getDouble("buyback.min-price", 0.01); // Set a very low min price to avoid issues

        return Math.max(minPrice, price);
    }

    public void recordSale(Material material, int amount) {
        FileConfiguration config = plugin.getConfig();
        boolean enabled = config.getBoolean("buyback.enabled", true);
        if (!enabled) {
            return;
        }

        BuybackInfo info = buybackPrices.get(material);
        if (info != null) {
            info.quantitySold += amount;
        }
    }
}
