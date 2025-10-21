package com.holyw.wowah;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class AuctionHouseListener implements Listener {

    private final SystemShop plugin;

    public AuctionHouseListener(SystemShop plugin) {
        this.plugin = plugin;
    }




    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView() == null ? "" : event.getView().getTitle();
        String stripped = title == null ? "" : ChatColor.stripColor(title);
        // Cancel drags inside any of our plugin GUIs
        if (stripped.equals(ChatColor.stripColor(Lang.get("title-categories"))) ||
                stripped.startsWith(ChatColor.stripColor(Lang.get("title-shop")).split(" - ")[0]) ||
                stripped.equals(ChatColor.stripColor(Lang.get("title-special-order")))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String rawTitle = event.getView().getTitle();
        String title = rawTitle == null ? "" : rawTitle;
        String strippedTitle = ChatColor.stripColor(title);
        Player player = (Player) event.getWhoClicked();

        // Category selection GUI
        if (strippedTitle.equals(ChatColor.stripColor(Lang.get("title-categories")))) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType().isAir()) {
                return;
            }
            if (clickedItem.getItemMeta() == null || clickedItem.getItemMeta().getDisplayName() == null) {
                plugin.getLogger().warning("Clicked category item without a display name in GUI: " + clickedItem);
                return;
            }

            String categoryName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
            plugin.getAuctionHouseGUI().openAuctionHouse(player, categoryName, 1);

        // Shop view GUI (title: "Shop - {category} - Page {page}")
        } else if (strippedTitle.startsWith(ChatColor.stripColor(Lang.get("title-shop")).split(" - ")[0])) {

            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType().isAir()) {
                return;
            }

            // Parse the stripped title safely
            String[] titleParts = strippedTitle.split(" - ");
            if (titleParts.length < 3) {
                plugin.getLogger().warning("Unexpected shop title format: '" + title + "'");
                return;
            }
            String category = titleParts[1];
            int currentPage = 1;
            try {
                String pagePart = titleParts[2]; // e.g., "Page 1"
                String[] pageParts = pagePart.split(" ");
                currentPage = Integer.parseInt(pageParts[pageParts.length - 1]);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse current page from title: '" + title + "' - " + e.getMessage());
            }

            // Handle page and sort buttons
            if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                if (displayName.equals(Lang.get("button-next-page"))) {
                    plugin.getAuctionHouseGUI().openAuctionHouse(player, category, currentPage + 1);
                    return;
                } else if (displayName.equals(Lang.get("button-prev-page"))) {
                    plugin.getAuctionHouseGUI().openAuctionHouse(player, category, currentPage - 1);
                    return;
                } else if (displayName.equals(Lang.get("button-back-categories"))) {
                    plugin.getAuctionHouseGUI().openCategoryGUI(player);
                    return;
                }
            }

            if (clickedItem.getType() == Material.HOPPER) {
                AuctionHouseGUI.SortPreference preference = plugin.getAuctionHouseGUI().getSortPreferences().get(player.getUniqueId());
                if (preference != null) {
                    if (event.isLeftClick()) {
                        preference.toggleSortOrder();
                    } else if (event.isRightClick()) {
                        // Cycle sort type
                        preference.setSortType(preference.getSortType() == AuctionHouseGUI.SortType.PRICE ? AuctionHouseGUI.SortType.NAME : AuctionHouseGUI.SortType.PRICE);
                    }
                }
                plugin.getAuctionHouseGUI().openAuctionHouse(player, category, currentPage);
                return;
            }

            // Handle item purchase
            if (clickedItem.getItemMeta() == null) {
                return;
            }
            // The clicked item in the GUI is a clone of the real auction item's stack.
            // We use a persistent data container to uniquely identify auction items.
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta == null) {
                return;
            }
            NamespacedKey key = new NamespacedKey(plugin, "auction_item_identifier");
            String identifier = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);

            if (identifier == null) {
                return;
            }

            UUID itemUuid = UUID.fromString(identifier);
            AuctionHouseManager.AuctionItem auctionItem = plugin.getAuctionHouseManager().getAuctionItems().stream()
                    .filter(item -> item.getId().equals(itemUuid))
                    .findFirst()
                    .orElse(null);

            if (auctionItem != null) {
                purchaseItem(player, auctionItem, category, currentPage);
            }
        } else if (title.equals(Lang.get("title-special-order"))) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType().isAir()) {
                return;
            }

            Material material = clickedItem.getType();
            plugin.getSpecialOrdersManager().addSpecialOrder(player, material);
            new AuctionPopulator(plugin).fulfillSpecialOrders();
            player.closeInventory();
            player.sendMessage(Lang.get("special-order-placed", "{item}", material.name()));
        }
    }


    private void purchaseItem(Player player, AuctionHouseManager.AuctionItem auctionItem, String category, int currentPage) {
        Economy econ = SystemShop.getEconomy();
        double price = auctionItem.getPrice();
        if (econ.has(player, price)) {
            econ.withdrawPlayer(player, price);

            if (auctionItem.getOriginalOwner() != null) {
                // This is a player-owned item
                double commissionPercentage = plugin.getConfig().getDouble("consignment.commission-percentage", 5.0);
                double commission = price * (commissionPercentage / 100.0);
                double payout = price - commission;
                econ.depositPlayer(Bukkit.getOfflinePlayer(auctionItem.getOriginalOwner()), payout);
            }

            player.getInventory().addItem(auctionItem.getItemStack());
            plugin.getAuctionHouseManager().removeAuctionItem(auctionItem);
            player.closeInventory();
            player.sendMessage(Lang.get("item-bought", "{price}", String.valueOf(auctionItem.getPrice())));
            plugin.getAuctionHouseGUI().openAuctionHouse(player, category, currentPage); // Refresh the view
        } else {
            player.sendMessage(Lang.get("not-enough-money"));
        }
    }
}
