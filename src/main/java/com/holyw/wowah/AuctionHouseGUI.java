package com.holyw.wowah;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.ChatColor;

import java.util.*;
import java.util.stream.Collectors;

public class AuctionHouseGUI {

    private final SystemShop plugin;
    private static final int ITEMS_PER_PAGE = 45;
    private final Map<UUID, SortPreference> sortPreferences = new HashMap<>();

    public enum SortType {
        PRICE,
        NAME
    }

    public enum SortOrder {
        ASCENDING,
        DESCENDING
    }

    public enum Category {
        DAILY_DEALS(Material.GOLD_INGOT, "Daily Deals"),
        WEAPONS(Material.DIAMOND_SWORD, "Weapons"),
        TOOLS(Material.DIAMOND_PICKAXE, "Tools"),
        ARMOR(Material.DIAMOND_CHESTPLATE, "Armor"),
        POTIONS(Material.POTION, "Potions"),
        BLOCKS(Material.GRASS_BLOCK, "Blocks"),
        MATERIALS(Material.DIAMOND, "Materials"),
        SPAWNERS(Material.SPAWNER, "Spawners"),
        SPAWN_EGGS(Material.PIG_SPAWN_EGG, "Spawn Eggs"), // Display name is correct
        ENCHANTED_BOOKS(Material.ENCHANTED_BOOK, "Enchanted Books"),
        LEGENDARY(Material.NETHER_STAR, "Legendary Items"),
        MISCELLANEOUS(Material.BUCKET, "Miscellaneous");

        private final Material icon;
        private final String displayName;

        Category(Material icon, String displayName) {
            this.icon = icon;
            this.displayName = displayName;
        }

        public Material getIcon() {
            return icon;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static class SortPreference {
        private SortType sortType = SortType.PRICE;
        private SortOrder sortOrder = SortOrder.ASCENDING;

        public SortType getSortType() {
            return sortType;
        }

        public void setSortType(SortType sortType) {
            this.sortType = sortType;
        }

        public SortOrder getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(SortOrder sortOrder) {
            this.sortOrder = sortOrder;
        }

        public void toggleSortOrder() {
            this.sortOrder = (this.sortOrder == SortOrder.ASCENDING) ? SortOrder.DESCENDING : SortOrder.ASCENDING;
        }
    }

    public AuctionHouseGUI(SystemShop plugin) {
        this.plugin = plugin;
    }

    public void openCategoryGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 18, Lang.get("title-categories"));
        for (Category category : Category.values()) {
            ItemStack item = new ItemStack(category.getIcon());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.AQUA + category.getDisplayName());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP); // Hide default lore like "No Effects" or attributes
            item.setItemMeta(meta);
            inv.addItem(item);
        }
        player.openInventory(inv);
    }

    public void openAuctionHouse(Player player, String category, int page) {
        SortPreference preference = sortPreferences.computeIfAbsent(player.getUniqueId(), k -> new SortPreference());

        List<AuctionHouseManager.AuctionItem> items;
        // This is a category view
        if (category.equalsIgnoreCase(Category.DAILY_DEALS.getDisplayName())) {
            items = plugin.getAuctionHouseManager().getAuctionItems().stream()
                    .filter(item -> !item.hasExpired() && item.isOnSale())
                    .collect(Collectors.toList());
        } else {
            items = plugin.getAuctionHouseManager().getAuctionItems().stream()
                    .filter(item -> !item.hasExpired() && category.equalsIgnoreCase(item.getCategory()))
                    .collect(Collectors.toList());
        }

        // Sort items
        Comparator<AuctionHouseManager.AuctionItem> comparator;
        if (preference.getSortType() == SortType.PRICE) {
            comparator = Comparator.comparingDouble(AuctionHouseManager.AuctionItem::getPrice);
        } else {
            comparator = Comparator.comparing(item -> item.getItemStack().hasItemMeta() && item.getItemStack().getItemMeta().hasDisplayName() ? item.getItemStack().getItemMeta().getDisplayName() : item.getItemStack().getType().name());
        }
        if (preference.getSortOrder() == SortOrder.DESCENDING) {
            comparator = comparator.reversed();
        }
        items.sort(comparator);

        int totalPages = (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE);
        if (totalPages == 0) {
            totalPages = 1;
        }
        if (page < 1) {
            page = 1;
        }
        if (page > totalPages) {
            page = totalPages;
        }

        String title = Lang.get("title-shop", "{category}", category, "{page}", String.valueOf(page));
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Add items for the current page
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int itemIndex = startIndex + i;
            if (itemIndex < items.size()) {
                AuctionHouseManager.AuctionItem item = items.get(itemIndex);
                ItemStack itemStack = item.getItemStack().clone();
                ItemMeta itemMeta = itemStack.getItemMeta();
                String sellerName;

                if (item.getSeller().equals(AuctionPopulator.SYSTEM_SELLER_UUID)) {
                    sellerName = Lang.get("item-seller-system");
                } else {
                    org.bukkit.OfflinePlayer seller = Bukkit.getOfflinePlayer(item.getSeller());
                    try {
                        sellerName = seller.getName();
                    } catch (Exception e) {
                        sellerName = Lang.get("item-seller-unknown");
                    }
                    if (sellerName == null) {
                        sellerName = Lang.get("item-seller-unknown");
                    }
                }

                List<String> lore = new ArrayList<>();
                lore.add(Lang.get("item-seller", "{seller}", sellerName));
                if (item.isOnSale()) {
                    lore.add("§m" + Lang.get("item-price", "{price}", String.valueOf(item.getOriginalPrice())));
                    int percentOff = (int) (item.getDiscountPercentage() * 100);
                    lore.add(Lang.get("item-price-discounted", "{percent}", String.valueOf(percentOff), "{price}", String.valueOf(item.getPrice())));
                } else {
                    lore.add(Lang.get("item-price", "{price}", String.valueOf(item.getPrice())));
                }
                lore.add(Lang.get("item-expires", "{time}", formatDuration(item.getExpiryTime() - System.currentTimeMillis())));

                itemMeta.setLore(lore);
                // The identifier is now just for debugging, the itemstack itself is the key
                NamespacedKey key = new NamespacedKey(plugin, "auction_item_identifier");
                itemMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, item.getId().toString());
                itemStack.setItemMeta(itemMeta);
                inv.setItem(i, itemStack);
            }
        }

        // Add control buttons
        ItemStack prevPage = new ItemStack(Material.ARROW);
        ItemMeta prevMeta = prevPage.getItemMeta();
        prevMeta.setDisplayName(Lang.get("button-prev-page"));
        prevPage.setItemMeta(prevMeta);

        ItemStack nextPage = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = nextPage.getItemMeta();
        nextMeta.setDisplayName(Lang.get("button-next-page"));
        nextPage.setItemMeta(nextMeta);

        ItemStack sortButton = new ItemStack(Material.HOPPER);
        ItemMeta sortMeta = sortButton.getItemMeta();
        sortMeta.setDisplayName(Lang.get("button-sort"));
        String sortTypeName = preference.getSortType().name().substring(0, 1).toUpperCase() + preference.getSortType().name().substring(1).toLowerCase();
        String sortOrderName = preference.getSortOrder() == SortOrder.ASCENDING ? "Ascending" : "Descending";
        String loreString = Lang.get("button-sort-lore", "{type}", sortTypeName, "{order}", sortOrderName);
        sortMeta.setLore(Arrays.asList(loreString.split("\n")));
        sortButton.setItemMeta(sortMeta);

        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(Lang.get("button-back-categories"));
        backButton.setItemMeta(backMeta);

        if (page > 1) {
            inv.setItem(45, prevPage);
        }
        inv.setItem(48, backButton);
        inv.setItem(49, sortButton); // Center slot for sorting
        if (page < totalPages) {
            inv.setItem(53, nextPage);
        }

        player.openInventory(inv);
    }

    public Map<UUID, SortPreference> getSortPreferences() {
        return sortPreferences;
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "d " + (hours % 24) + "h";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }
}