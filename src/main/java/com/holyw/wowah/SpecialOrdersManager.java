package com.holyw.wowah;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class SpecialOrdersManager {

    private final List<Material> specialOrders = new ArrayList<>();

    public SpecialOrdersManager(SystemShop plugin) {
    }

    public void openSpecialOrderGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, Lang.get("title-special-order"));
        for (Material material : Material.values()) {
            if (material.isItem() && !material.isAir()) {
                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(material.name());
                item.setItemMeta(meta);
                inv.addItem(item);
            }
        }
        player.openInventory(inv);
    }

    public void addSpecialOrder(Material material) {
        specialOrders.add(material);
    }

    public List<Material> getSpecialOrders() {
        return specialOrders;
    }

    public Material getAndRemoveNextSpecialOrder() {
        if (!specialOrders.isEmpty()) {
            return specialOrders.remove(0);
        }
        return null;
    }
}
