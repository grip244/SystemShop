package com.holyw.wowah;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;


import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AdminGUIListener implements Listener {

    private final SystemShop plugin;
    private final Map<UUID, AuctionHouseManager.AuctionItem> editingItem = new ConcurrentHashMap<>();

    public AdminGUIListener(SystemShop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        Player player = (Player) event.getWhoClicked();

        if (title.equals("SystemShop Admin")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) {
                return;
            }
            switch (event.getCurrentItem().getType()) {
                case CHEST:
                    plugin.getAdminGUI().openManageItemsCategoryGUI(player);
                    break;
                case BEACON:
                    plugin.getAdminGUI().openManageEventsGUI(player);
                    break;
                default:
                    break;
            }
        } else if (title.equals("Manage Market Events")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) {
                return;
            }
            switch (event.getCurrentItem().getType()) {
                case EMERALD_BLOCK:
                    plugin.getEventManager().startBoom();
                    player.closeInventory();
                    break;
                case REDSTONE_BLOCK:
                    plugin.getEventManager().startCrash();
                    player.closeInventory();
                    break;
                case BARRIER:
                    plugin.getEventManager().stopEvent();
                    player.closeInventory();
                    break;
                default:
                    break;
            }
        } else if (title.equals("Manage Items - Categories")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) {
                return;
            }
            String category = ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());
            plugin.getAdminGUI().openManageItemsGUI(player, category, 1);
        } else if (title.startsWith("Manage Items - ")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) {
                return;
            }

            String[] titleParts = title.split(" - ");
            String category = titleParts[1];
            int page = Integer.parseInt(titleParts[2]);

            if (event.getCurrentItem().getType() == Material.ARROW) {
                if (event.getCurrentItem().getItemMeta().getDisplayName().equals("Next Page")) {
                    plugin.getAdminGUI().openManageItemsGUI(player, category, page + 1);
                } else if (event.getCurrentItem().getItemMeta().getDisplayName().equals("Previous Page")) {
                    plugin.getAdminGUI().openManageItemsGUI(player, category, page - 1);
                }
            } else if (event.getCurrentItem().getType() == Material.BARRIER) {
                plugin.getAdminGUI().openManageItemsCategoryGUI(player);
            } else {
                int slot = event.getSlot();
                if (slot >= 0 && slot < 45) {
                    java.util.List<AuctionHouseManager.AuctionItem> items;
                    if (category.equalsIgnoreCase("All")) {
                        items = plugin.getAuctionHouseManager().getAuctionItems().stream()
                                .filter(item -> item.getSeller().equals(com.holyw.wowah.AuctionPopulator.SYSTEM_SELLER_UUID))
                                .collect(java.util.stream.Collectors.toList());
                    } else {
                        items = plugin.getAuctionHouseManager().getAuctionItems().stream()
                                .filter(item -> item.getSeller().equals(com.holyw.wowah.AuctionPopulator.SYSTEM_SELLER_UUID) && item.getCategory().equalsIgnoreCase(category))
                                .collect(java.util.stream.Collectors.toList());
                    }

                    int startIndex = (page - 1) * 45;
                    int itemIndex = startIndex + slot;

                    if (itemIndex < items.size()) {
                        AuctionHouseManager.AuctionItem auctionItem = items.get(itemIndex);
                        if (event.isLeftClick()) {
                            editingItem.put(player.getUniqueId(), auctionItem);
                            player.closeInventory();
                            player.sendMessage("§aEnter the new price for the item in chat.");
                        } else if (event.isRightClick()) {
                            plugin.getAuctionHouseManager().removeAuctionItem(auctionItem);
                            player.sendMessage("§aItem removed from the shop.");
                            plugin.getAdminGUI().openManageItemsGUI(player, category, page);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (editingItem.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            try {
                double newPrice = Double.parseDouble(event.getMessage());
                AuctionHouseManager.AuctionItem auctionItem = editingItem.get(player.getUniqueId());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getPricingManager().setPrice(auctionItem.getItemStack(), newPrice);
                    player.sendMessage("§aPrice updated successfully.");
                    editingItem.remove(player.getUniqueId());
                });
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid price. Please enter a valid number.");
            }
        }
    }
}