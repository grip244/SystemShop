package com.holyw.wowah;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class PlaceholderAPI extends PlaceholderExpansion {

    private SystemShop plugin;

    public PlaceholderAPI(SystemShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "systemshop";
    }

    @Override
    public String getAuthor() {
        return "holyw";
    }

    @Override
    public String getVersion() {
        return "1.0.3";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        // Server-side placeholders (work even if player is null)
        if (identifier.equals("total_items")) {
            return String.valueOf(plugin.getAuctionHouseManager().getTotalItems());
        }

        if (identifier.equals("items_on_sale")) {
            long discounted = plugin.getAuctionHouseManager().getAuctionItems().stream().filter(com.holyw.wowah.AuctionHouseManager.AuctionItem::isOnSale).count();
            return String.valueOf(discounted);
        }

        if (identifier.startsWith("price_")) {
            String mat = identifier.substring("price_".length()).toUpperCase();
            try {
                org.bukkit.Material material = org.bukkit.Material.valueOf(mat);
                double price = plugin.getPricingManager().getPrice(material);
                return String.valueOf(price);
            } catch (IllegalArgumentException e) {
                return "";
            }
        }

        // If we get here, the placeholder may be player-specific. If there's no player, return empty.
        if (player == null) {
            return "";
        }

        // No player-specific placeholders implemented yet; return empty for unknowns.
        return "";
    }
}
