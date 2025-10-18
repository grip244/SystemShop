package com.holyw.wowah;

import org.bukkit.Bukkit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;

import java.util.List;
import java.util.stream.Collectors;

public class AdminGUI {

    private final SystemShop plugin;

    public AdminGUI(SystemShop plugin) {
        this.plugin = plugin;
    }

    public void openAdminGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "SystemShop Admin");

        ItemStack manageItems = new ItemStack(Material.CHEST);
        ItemMeta manageItemsMeta = manageItems.getItemMeta();
        manageItemsMeta.setDisplayName("§aManage Items");
        manageItems.setItemMeta(manageItemsMeta);

        ItemStack manageEvents = new ItemStack(Material.BEACON);
        ItemMeta manageEventsMeta = manageEvents.getItemMeta();
        manageEventsMeta.setDisplayName("§bManage Market Events");
        manageEvents.setItemMeta(manageEventsMeta);

        inv.setItem(3, manageItems);
        inv.setItem(5, manageEvents);

        player.openInventory(inv);
    }

    public void openManageEventsGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "Manage Market Events");

        ItemStack startBoom = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta startBoomMeta = startBoom.getItemMeta();
        startBoomMeta.setDisplayName("§aStart Market Boom");
        startBoom.setItemMeta(startBoomMeta);

        ItemStack startCrash = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta startCrashMeta = startCrash.getItemMeta();
        startCrashMeta.setDisplayName("§cStart Market Crash");
        startCrash.setItemMeta(startCrashMeta);

        ItemStack stopEvent = new ItemStack(Material.BARRIER);
        ItemMeta stopEventMeta = stopEvent.getItemMeta();
        stopEventMeta.setDisplayName("§eStop Event");
        stopEvent.setItemMeta(stopEventMeta);

        inv.setItem(2, startBoom);
        inv.setItem(4, startCrash);
        inv.setItem(6, stopEvent);

        player.openInventory(inv);
    }

    public void openManageItemsCategoryGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 18, "Manage Items - Categories");
        ItemStack allItems = new ItemStack(Material.CHEST);
        ItemMeta allItemsMeta = allItems.getItemMeta();
        allItemsMeta.setDisplayName("§aAll Items");
        allItems.setItemMeta(allItemsMeta);
        inv.addItem(allItems);

        for (AuctionHouseGUI.Category category : AuctionHouseGUI.Category.values()) {
            ItemStack item = new ItemStack(category.getIcon());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§b" + category.getDisplayName());
            item.setItemMeta(meta);
            inv.addItem(item);
        }
        player.openInventory(inv);
    }

    public void openManageItemsGUI(Player player, String category, int page) {
        List<AuctionHouseManager.AuctionItem> items;
        if (category.equalsIgnoreCase("All")) {
            items = plugin.getAuctionHouseManager().getAuctionItems().stream()
                    .filter(item -> item.getSeller().equals(AuctionPopulator.SYSTEM_SELLER_UUID))
                    .collect(Collectors.toList());
        } else {
            items = plugin.getAuctionHouseManager().getAuctionItems().stream()
                    .filter(item -> item.getSeller().equals(AuctionPopulator.SYSTEM_SELLER_UUID) && item.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }

        int totalPages = (int) Math.ceil((double) items.size() / 45.0);
        if (totalPages == 0) {
            totalPages = 1;
        }
        if (page < 1) {
            page = 1;
        }
        if (page > totalPages) {
            page = totalPages;
        }

        Inventory inv = Bukkit.createInventory(null, 54, "Manage Items - " + category + " - " + page);

        int startIndex = (page - 1) * 45;
        for (int i = 0; i < 45; i++) {
            int itemIndex = startIndex + i;
            if (itemIndex < items.size()) {
                AuctionHouseManager.AuctionItem item = items.get(itemIndex);
                ItemStack itemStack = item.getItemStack().clone();
                ItemMeta itemMeta = itemStack.getItemMeta();

                List<String> lore = new ArrayList<>();
                lore.add("§ePrice: §6" + item.getPrice());
                lore.add("§aLeft-click to edit price");
                lore.add("§cRight-click to remove");
                itemMeta.setLore(lore);
                itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                itemStack.setItemMeta(itemMeta);
                inv.setItem(i, itemStack);
            }
        }

        // Add controls
        ItemStack prevPage = new ItemStack(Material.ARROW);
        ItemMeta prevMeta = prevPage.getItemMeta();
        prevMeta.setDisplayName("Previous Page");
        prevPage.setItemMeta(prevMeta);

        ItemStack nextPage = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = nextPage.getItemMeta();
        nextMeta.setDisplayName("Next Page");
        nextPage.setItemMeta(nextMeta);

        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName("Back");
        backButton.setItemMeta(backMeta);

        if (page > 1) {
            inv.setItem(45, prevPage);
        }
        inv.setItem(49, backButton);
        if (page < totalPages) {
            inv.setItem(53, nextPage);
        }

        player.openInventory(inv);
    }
}