package com.holyw.wowah;

import java.util.Collections;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

public class DailyDealsManager {

    private final SystemShop plugin;

    public DailyDealsManager(SystemShop plugin) {
        this.plugin = plugin;
    }

    public void rotateDeals() {
        FileConfiguration config = plugin.getConfig();
    // rotateDeals executed
        if (!config.getBoolean("daily-deals.enabled", true)) {
            return;
        }

        if (config.getBoolean("daily-deals.refill-shop-on-rotate", true)) {
            plugin.getAuctionHouseManager().clearSystemAuctions();
            int refillAmount = config.getInt("population.refill-items", 800);
            new AuctionPopulator(plugin).populate(refillAmount);
        }

        List<AuctionHouseManager.AuctionItem> allItems = plugin.getAuctionHouseManager().getAuctionItems();
        plugin.getServer().getConsoleSender().sendMessage(Lang.get("total-items-in-ah", "{count}", String.valueOf(allItems.size())));
        allItems.forEach(item -> item.setDiscountPercentage(0.0));

        Collections.shuffle(allItems);

        int dealsCount = config.getInt("daily-deals.deals-count", 10);
        double discountPercentage = config.getDouble("daily-deals.discount-percentage", 0.25);

        int itemsToDiscount = Math.min(allItems.size(), dealsCount);

        for (int i = 0; i < itemsToDiscount; i++) {
            allItems.get(i).setDiscountPercentage(discountPercentage);
        }

        long discountedItems = allItems.stream().filter(AuctionHouseManager.AuctionItem::isOnSale).count();
        plugin.getServer().getConsoleSender().sendMessage(Lang.get("items-on-sale", "{count}", String.valueOf(discountedItems)));

        if (itemsToDiscount > 0) {
            String message = Lang.get("daily-deals-rotated", "{count}", String.valueOf(itemsToDiscount));
            plugin.getLogger().info(message);
            plugin.getServer().broadcastMessage(message);
        }
        // rotateDeals finished
    }
}