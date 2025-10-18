package com.holyw.wowah;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SpecialOrdersManager {

    private final List<SpecialOrderRequest> specialOrders = new ArrayList<>();

    public static class SpecialOrderRequest {
        private final UUID playerUuid;
        private final Material material;

        public SpecialOrderRequest(UUID playerUuid, Material material) {
            this.playerUuid = playerUuid;
            this.material = material;
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public Material getMaterial() {
            return material;
        }
    }

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

    public void addSpecialOrder(Player player, Material material) {
        specialOrders.add(new SpecialOrderRequest(player.getUniqueId(), material));
    }


    public SpecialOrderRequest getAndRemoveNextSpecialOrder() {
        if (!specialOrders.isEmpty()) {
            return specialOrders.remove(0);
        }
        return null;
    }
}
