package com.holyw.wowah;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AuctionHouseManager {

    private final SystemShop plugin;
    private final List<AuctionItem> auctionItems = new ArrayList<>();
    private File auctionsFile;
    private FileConfiguration auctionsConfig;

    public AuctionHouseManager(SystemShop plugin) {
        this.plugin = plugin;
        setup();
        loadAuctionItems();
    }

    private void setup() {
        auctionsFile = new File(plugin.getDataFolder(), "auctions.yml");
        if (!auctionsFile.exists()) {
            try {
                auctionsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        auctionsConfig = YamlConfiguration.loadConfiguration(auctionsFile);
    }

    public void addAuctionItem(ItemStack itemStack, UUID seller, double price, long duration, String category) {
        auctionItems.add(new AuctionItem(itemStack, seller, price, duration, category));
        saveAuctionItems();
    }

    public void addAuctionItem(ItemStack itemStack, UUID seller, UUID originalOwner, double price, long duration, String category) {
        auctionItems.add(new AuctionItem(itemStack, seller, originalOwner, price, duration, category));
        saveAuctionItems();
    }

    public void addAuctionItems(List<AuctionItem> items) {
        auctionItems.addAll(items);
        saveAuctionItems();
        plugin.getPricingManager().updateStockLevels();
    }

    public void removeAuctionItem(AuctionItem auctionItem) {
        auctionItems.remove(auctionItem);
        saveAuctionItems();
        plugin.getPricingManager().updateStockLevels();
    }

    public void clearSystemAuctions() {
        auctionItems.removeIf(item -> item.getSeller().equals(AuctionPopulator.SYSTEM_SELLER_UUID));
        saveAuctionItems();
        plugin.getPricingManager().updateStockLevels();
    }

    public void clearPlayerAuctions() {
        auctionItems.removeIf(item -> !item.getSeller().equals(AuctionPopulator.SYSTEM_SELLER_UUID));
        saveAuctionItems();
        plugin.getPricingManager().updateStockLevels();
    }

    public void clearAllAuctions() {
        auctionItems.clear();
        saveAuctionItems();
        plugin.getPricingManager().updateStockLevels();
    }

    public List<AuctionItem> getAuctionItems() {
        return auctionItems;
    }

    public void saveAuctionItems() {
        auctionsConfig.set("auctions", null);
        for (int i = 0; i < auctionItems.size(); i++) {
            AuctionItem item = auctionItems.get(i);
            String path = "auctions." + i;
            auctionsConfig.set(path + ".id", item.getId().toString());
            auctionsConfig.set(path + ".item", item.getItemStack());
            auctionsConfig.set(path + ".seller", item.getSeller().toString());
            if (item.getOriginalOwner() != null) {
                auctionsConfig.set(path + ".originalOwner", item.getOriginalOwner().toString());
            }
            auctionsConfig.set(path + ".price", item.getPrice());
            auctionsConfig.set(path + ".expiryTime", item.getExpiryTime());
            auctionsConfig.set(path + ".category", item.getCategory());
        }
        try {
            auctionsConfig.save(auctionsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadAuctionItems() {
        auctionItems.clear();
        if (auctionsConfig.contains("auctions")) {
            for (String key : auctionsConfig.getConfigurationSection("auctions").getKeys(false)) {
                String path = "auctions." + key;
                UUID id = UUID.fromString(auctionsConfig.getString(path + ".id"));
                ItemStack itemStack = auctionsConfig.getItemStack(path + ".item");
                UUID seller = UUID.fromString(auctionsConfig.getString(path + ".seller"));
                String originalOwnerString = auctionsConfig.getString(path + ".originalOwner");
                UUID originalOwner = null;
                if (originalOwnerString != null) {
                    originalOwner = UUID.fromString(originalOwnerString);
                }
                double price = auctionsConfig.getDouble(path + ".price");
                long expiryTime = auctionsConfig.getLong(path + ".expiryTime");
                String category = auctionsConfig.getString(path + ".category");
                auctionItems.add(new AuctionItem(id, itemStack, seller, originalOwner, price, expiryTime, category));
            }
        }
    }

    public static class AuctionItem {
        private final UUID id;
        private final ItemStack itemStack;
        private final UUID seller;
        private final UUID originalOwner;
        private final double price;
        private final long expiryTime;
        private final String category;
        private double discountPercentage = 0.0; // 0.0 = no discount, 0.2 = 20% off

        public AuctionItem(UUID id, ItemStack itemStack, UUID seller, UUID originalOwner, double price, long expiryTime, String category) {
            this.id = id;
            this.itemStack = itemStack;
            this.seller = seller;
            this.originalOwner = originalOwner;
            this.price = price;
            this.expiryTime = expiryTime;
            this.category = category;
        }

        public AuctionItem(ItemStack itemStack, UUID seller, UUID originalOwner, double price, long duration, String category) {
            this(UUID.randomUUID(), itemStack, seller, originalOwner, price, System.currentTimeMillis() + duration, category);
        }

        public AuctionItem(ItemStack itemStack, UUID seller, double price, long duration, String category) {
            this(UUID.randomUUID(), itemStack, seller, null, price, System.currentTimeMillis() + duration, category);
        }

        public UUID getId() {
            return id;
        }

        public ItemStack getItemStack() {
            return itemStack;
        }

        public UUID getSeller() {
            return seller;
        }

        public UUID getOriginalOwner() {
            return originalOwner;
        }

        public double getPrice() {
            if (isOnSale()) {
                double discountedPrice = price * (1.0 - discountPercentage);
                return Math.round(discountedPrice * 100.0) / 100.0;
            }
            return Math.round(price * 100.0) / 100.0;
        }

        public long getExpiryTime() {
            return expiryTime;
        }

        public String getCategory() {
            return category;
        }

        public boolean isOnSale() {
            return discountPercentage > 0.0;
        }

        public double getOriginalPrice() {
            return Math.round(price * 100.0) / 100.0;
        }

        public void setDiscountPercentage(double discount) {
            this.discountPercentage = discount;
        }

        public double getDiscountPercentage() {
            return discountPercentage;
        }

        public boolean hasExpired() {
            return System.currentTimeMillis() >= expiryTime;
        }
    }
}
