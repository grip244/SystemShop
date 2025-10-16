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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AuctionHouseListener implements Listener {

    private final SystemShop plugin;
    private final Map<UUID, AuctionHouseManager.AuctionItem> pendingConfirmations = new HashMap<>();

    public AuctionHouseListener(SystemShop plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }



    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        pendingConfirmations.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        Player player = (Player) event.getWhoClicked();

        if (title.equals(Lang.get("title-categories"))) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType().isAir()) {
                return;
            }

            String categoryName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
            plugin.getAuctionHouseGUI().openAuctionHouse(player, categoryName, 1);

        } else if (ChatColor.stripColor(title).startsWith(ChatColor.stripColor(Lang.get("title-shop")).split(" - ")[0])) {

            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType().isAir()) {
                return;
            }

            String[] titleParts = title.split(" - ");
            String category = ChatColor.stripColor(titleParts[1]);
            int currentPage = Integer.parseInt(title.substring(title.lastIndexOf(" ") + 1));

            // Handle page and sort buttons
            if (clickedItem.getType() == Material.ARROW) {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                if (displayName.equals(Lang.get("button-next-page"))) {
                    plugin.getAuctionHouseGUI().openAuctionHouse(player, category, currentPage + 1);
                } else if (displayName.equals(Lang.get("button-prev-page"))) {
                    plugin.getAuctionHouseGUI().openAuctionHouse(player, category, currentPage - 1);
                }
                return;
            } else if (clickedItem.getType() == Material.HOPPER) {
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
            } else if (clickedItem.getType() == Material.BARRIER) {
                plugin.getAuctionHouseGUI().openCategoryGUI(player);
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
                // Confirmation GUI for high-value items
                if (auctionItem.getPrice() >= 50000) {
                    openConfirmationGUI(player, auctionItem);
                } else {
                    purchaseItem(player, auctionItem, category, currentPage);
                }
            }
        } else if (title.equals(Lang.get("title-confirm-purchase"))) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType().isAir()) return;

            AuctionHouseManager.AuctionItem itemToBuy = pendingConfirmations.get(player.getUniqueId());
            if (itemToBuy == null) {
                player.closeInventory();
                return;
            }

            if (clickedItem.getType() == Material.GREEN_WOOL) {
                // Find original category and page to refresh
                String category = itemToBuy.getCategory();
                purchaseItem(player, itemToBuy, category, 1); // Refresh to page 1 for simplicity
            } else if (clickedItem.getType() == Material.RED_WOOL) {
                player.closeInventory();
            }
        } else if (title.equals(Lang.get("title-special-order"))) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType().isAir()) {
                return;
            }

            Material material = clickedItem.getType();
            plugin.getSpecialOrdersManager().addSpecialOrder(material);
            player.closeInventory();
            player.sendMessage(Lang.get("special-order-placed", "{item}", material.name()));
        }
    }

    private void openConfirmationGUI(Player player, AuctionHouseManager.AuctionItem item) {
        pendingConfirmations.put(player.getUniqueId(), item);
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 27, Lang.get("title-confirm-purchase"));

        inv.setItem(13, item.getItemStack());

        ItemStack confirm = new ItemStack(Material.GREEN_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName(Lang.get("button-confirm-purchase"));
        confirmMeta.setLore(Arrays.asList(Lang.get("item-price", "{price}", String.valueOf(item.getPrice()))));
        confirm.setItemMeta(confirmMeta);
        inv.setItem(11, confirm);

        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName(Lang.get("button-cancel"));
        cancel.setItemMeta(cancelMeta);
        inv.setItem(15, cancel);

        player.openInventory(inv);
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
            } else if (!auctionItem.getSeller().equals(AuctionPopulator.SYSTEM_SELLER_UUID)) {
                // This is for backward compatibility with old player auctions
                econ.depositPlayer(Bukkit.getOfflinePlayer(auctionItem.getSeller()), price);
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