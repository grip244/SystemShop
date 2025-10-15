package com.holyw.wowah;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;

public class ConsignmentManager {

    private final SystemShop plugin;

    public ConsignmentManager(SystemShop plugin) {
        this.plugin = plugin;
    }

    public void openConsignmentGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, Lang.get("title-consignment"));
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                ItemStack displayItem = item.clone();
                ItemMeta meta = displayItem.getItemMeta();
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                lore.add(" ");
                lore.add(Lang.get("consignment-listing-fee", "{fee}", String.valueOf(plugin.getConfig().getDouble("consignment.listing-fee", 100.0))));
                meta.setLore(lore);
                displayItem.setItemMeta(meta);
                inv.addItem(displayItem);
            }
        }
        player.openInventory(inv);
    }
}
