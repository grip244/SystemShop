package com.holyw.wowah;

import org.bukkit.Registry;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Arrays;
import java.util.HashSet;

public class AuctionPopulator {

    private final SystemShop plugin;
    private final Random random = new Random();
    public static final UUID SYSTEM_SELLER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public AuctionPopulator(SystemShop plugin) {
        this.plugin = plugin;
    }

    public void fulfillSpecialOrders() {
        int specialOrdersToFulfill = plugin.getConfig().getInt("population.special-orders-per-cycle", 5);
        double priceMultiplier = plugin.getConfig().getDouble("population.special-order-price-multiplier", 2.0);
        for (int i = 0; i < specialOrdersToFulfill; i++) {
            SpecialOrdersManager.SpecialOrderRequest request = plugin.getSpecialOrdersManager().getAndRemoveNextSpecialOrder();
            if (request != null) {
                AuctionHouseManager.AuctionItem auctionItem = createAuctionItem(new ItemStack(request.getMaterial()));
                double newPrice = auctionItem.getPrice() * priceMultiplier;
                long duration = auctionItem.getExpiryTime() - System.currentTimeMillis();
                plugin.getAuctionHouseManager().addAuctionItem(auctionItem.getItemStack(), SYSTEM_SELLER_UUID, request.getPlayerUuid(), newPrice, duration, "Special Orders");
            } else {
                break; // No more special orders
            }
        }
    }

    public void populate(int itemsToCreate) {
        // Clear expired items first
        plugin.getAuctionHouseManager().getAuctionItems().removeIf(AuctionHouseManager.AuctionItem::hasExpired);

        // Fulfill special orders
        fulfillSpecialOrders();

        plugin.getServer().getConsoleSender().sendMessage(Lang.get("population-start", "{items}", String.valueOf(itemsToCreate)));

        // Separate materials by category to control population counts
        Map<String, List<Material>> categorizedMaterials = initializeAndCategorizeMaterials();
        List<AuctionHouseManager.AuctionItem> allNewItems = new ArrayList<>();

        // Use the categorized materials map as the source of truth for what to populate
        for (Map.Entry<String, List<Material>> entry : categorizedMaterials.entrySet()) {
            String categoryName = entry.getKey();
            List<Material> materialsInCategory = entry.getValue();

            // Find the corresponding enum to get the config key, default to miscellaneous
            AuctionHouseGUI.Category categoryEnum = Arrays.stream(AuctionHouseGUI.Category.values())
                    .filter(c -> c.getDisplayName().equals(categoryName))
                    .findFirst()
                    .orElse(AuctionHouseGUI.Category.MISCELLANEOUS);

            String configKey = categoryEnum.name().toLowerCase();
            int capPercent = plugin.getConfig().getInt("population.category-caps." + configKey, 10);
            int itemsForCategory = itemsToCreate * capPercent / 100;

            for (int i = 0; i < itemsForCategory; i++) {
                Material material = materialsInCategory.get(random.nextInt(materialsInCategory.size()));
                allNewItems.add(createAuctionItem(new ItemStack(material)));
            }
        }

        plugin.getAuctionHouseManager().addAuctionItems(allNewItems);
        plugin.getPricingManager().updateStockLevels();
        plugin.getServer().getConsoleSender().sendMessage(Lang.get("population-finish", "{size}", String.valueOf(allNewItems.size())));
        // populate finished
    }

    private AuctionHouseManager.AuctionItem createAuctionItem(ItemStack itemStack) {
        Material material = itemStack.getType();
        String category = getCategory(material);
        double enchantmentValue = 0;
        int stackSize = 1;
 
        if (category.equals(AuctionHouseGUI.Category.POTIONS.getDisplayName())) {
            stackSize = random.nextInt(3) + 1; // 1-3
        }

        if (category.equals(AuctionHouseGUI.Category.BLOCKS.getDisplayName())) {
            // Allow blocks to be sold in stacks
            stackSize = random.nextInt(32) + 1; // 1-32
        }

        if (category.equals(AuctionHouseGUI.Category.MATERIALS.getDisplayName())) {
            // Allow materials to be sold in stacks
            stackSize = random.nextInt(64) + 1; // 1-64
        }

        if (material == Material.TIPPED_ARROW) {
            stackSize = random.nextInt(16) + 1; // 1-16
        }

        if (category.equals(AuctionHouseGUI.Category.FOOD.getDisplayName())) {
            int maxStack = material.getMaxStackSize();
            if (maxStack > 1) {
                // Stack between 1 and a cap of 64, or maxStack if it's smaller
                int upper_bound = Math.min(maxStack, 64);
                stackSize = random.nextInt(upper_bound) + 1;
            }
        }

        if (category.equals(AuctionHouseGUI.Category.MISCELLANEOUS.getDisplayName())) {
            int maxStack = material.getMaxStackSize();
            if (maxStack > 1) { 
                // Stack between 2 and a cap of 16, or maxStack if it's smaller
                int upper_bound = Math.min(maxStack, 16);
                stackSize = random.nextInt(upper_bound) + 1;
            }
        }

        // Create the ItemStack with the final stack size from the start.
        itemStack.setAmount(stackSize);

        String tierName = plugin.getPricingManager().getTierName(material);
        if (category.equals(AuctionHouseGUI.Category.WEAPONS.getDisplayName()) || category.equals(AuctionHouseGUI.Category.TOOLS.getDisplayName()) || category.equals(AuctionHouseGUI.Category.ARMOR.getDisplayName()) || material == Material.ENCHANTED_BOOK) {
            enchantmentValue = addRandomEnchantments(itemStack, tierName);
        }

        if (category.equals(AuctionHouseGUI.Category.POTIONS.getDisplayName())) {
            addRandomPotionEffect(itemStack);
        }

        if (material == Material.TIPPED_ARROW) {
            addRandomPotionEffectToArrow(itemStack);
        }

        double price = (plugin.getPricingManager().getPrice(itemStack) + enchantmentValue) * stackSize;
        price = Math.round(price * 100.0) / 100.0;
 
        long duration = 86400000; // 24 hours
        return new AuctionHouseManager.AuctionItem(itemStack, SYSTEM_SELLER_UUID, price, duration, category);
    }
 
    private double addRandomEnchantments(ItemStack item, String tierName) {
        List<Enchantment> possibleEnchantments = new ArrayList<>();
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            // Cursed items are no longer special, so we don't need to worry about them.
            if (item.getType() == Material.ENCHANTED_BOOK) {
                possibleEnchantments.add(enchantment);
            } else if (enchantment.canEnchantItem(item)) {
                possibleEnchantments.add(enchantment);
            }
        }
        if (possibleEnchantments.isEmpty()) {
            return 0;
        }

        double totalValue = 0;
        double tierBasePrice = plugin.getPricingManager().getBasePriceForTier(tierName);
        int tierOrdinal = Arrays.asList("TRASH", "COMMON", "COPPER", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC").indexOf(tierName);

        if ("MYTHIC".equals(tierName)) {
            if (item.getType() == Material.ENCHANTED_BOOK) {
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.GOLD + "Mythic Book");
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.LIGHT_PURPLE + "A book of immense power.");
                meta.setLore(lore);
                item.setItemMeta(meta);

                Collections.shuffle(possibleEnchantments);
                Set<Enchantment> appliedEnchantments = new HashSet<>();

                int enchantmentsToAdd = 5; // Number of enchantments for a Mythic Book

                for (int i = 0; i < enchantmentsToAdd && !possibleEnchantments.isEmpty(); i++) {
                    Enchantment enchantment = possibleEnchantments.get(i);

                    int level = enchantment.getMaxLevel(); // Max level for Mythic Books
                    applyEnchantment(item, enchantment, level);
                    appliedEnchantments.add(enchantment);
                    totalValue += (tierBasePrice * 2.0) * level; // Higher value for Mythic Books
                }

                return totalValue;
            }
            // --- Mythic Item Logic ---
            boolean allowUnsafe = plugin.getConfig().getBoolean("population.mythic-items.allow-unsafe-enchantments", true);
            double levelMultiplier = plugin.getConfig().getDouble("population.mythic-items.enchantment-level-multiplier", 1.5);

            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                meta = plugin.getServer().getItemFactory().getItemMeta(item.getType());
            }
            String originalName;
            if (meta != null && meta.hasDisplayName()) {
                originalName = meta.getDisplayName();
            } else {
                originalName = formatDefaultItemName(item.getType());
            }
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + "Mythic " + ChatColor.stripColor(originalName));
                item.setItemMeta(meta);
            }

            Collections.shuffle(possibleEnchantments);
            Set<Enchantment> appliedEnchantments = new HashSet<>();

            for (Enchantment enchantment : possibleEnchantments) {
                boolean conflicts = false;
                if (!allowUnsafe) {
                    for (Enchantment applied : appliedEnchantments) {
                        if (enchantment.conflictsWith(applied)) {
                            conflicts = true;
                            break;
                        }
                    }
                }

                if (!conflicts) {
                    int level = (int) (enchantment.getMaxLevel() * levelMultiplier);
                    applyEnchantment(item, enchantment, level);
                    appliedEnchantments.add(enchantment);
                    totalValue += (tierBasePrice * 1.5) * level;
                }
            }
        } else {
            // --- Standard Enchantment Logic ---
            if (item.getType() == Material.ENCHANTED_BOOK) {
                int numberOfEnchantments = random.nextInt(3) + 1; // 1-3 enchantments for books
                for (int i = 0; i < numberOfEnchantments && !possibleEnchantments.isEmpty(); i++) {
                    Enchantment enchantment = possibleEnchantments.remove(random.nextInt(possibleEnchantments.size()));
                    int maxLevel = enchantment.getMaxLevel();
                    if (maxLevel == 0) {
                        i--; // try again with another enchantment
                        continue;
                    }
                    int level = random.nextInt(maxLevel) + 1; // Level between 1 and maxLevel

                    // Mythic books are handled in the MYTHIC tier
                    applyEnchantment(item, enchantment, level);
                    totalValue += (tierBasePrice * 0.5) * level;
                }
            } else {
                int numberOfEnchantments = random.nextInt(tierOrdinal + 1) + 1; // Trash: 1, Common: 1-2, etc.
                Set<Enchantment> appliedEnchantments = new HashSet<>();
                for (int i = 0; i < numberOfEnchantments && !possibleEnchantments.isEmpty(); i++) {
                    Enchantment enchantment = possibleEnchantments.remove(random.nextInt(possibleEnchantments.size()));
                    int level = random.nextInt(Math.min(enchantment.getMaxLevel(), tierOrdinal + 1)) + 1;

                    boolean conflicts = false;
                    for (Enchantment applied : appliedEnchantments) {
                        if (enchantment.conflictsWith(applied)) {
                            conflicts = true;
                            break;
                        }
                    }
                    if (conflicts) {
                        i--; // Try again
                        continue;
                    }

                    applyEnchantment(item, enchantment, level);
                    appliedEnchantments.add(enchantment);
                    totalValue += (tierBasePrice * 0.5) * level;
                }
            }
        }
        return totalValue;
    }

    private void applyEnchantment(ItemStack item, Enchantment enchantment, int level) {
        if (item.getType() == Material.ENCHANTED_BOOK) {
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            if (meta.hasDisplayName() && meta.getDisplayName().equals(ChatColor.GOLD + "Mythic Book")) {
                meta.addStoredEnchant(enchantment, level, true);
            } else {
                meta.addStoredEnchant(enchantment, level, false);
            }
            item.setItemMeta(meta);
        } else {
            item.addUnsafeEnchantment(enchantment, level);
        }
    }

    private void addRandomPotionEffect(ItemStack item) {
        if (item.getItemMeta() instanceof PotionMeta) {
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta == null) {
                meta = (PotionMeta) plugin.getServer().getItemFactory().getItemMeta(item.getType());
            }
            
            // A list of base potion types that are desirable.
            List<PotionType> desirableBaseTypes = Arrays.asList(
                    PotionType.SWIFTNESS, PotionType.STRENGTH, PotionType.LEAPING, PotionType.REGENERATION,
                    PotionType.FIRE_RESISTANCE, PotionType.WATER_BREATHING, PotionType.INVISIBILITY,
                    PotionType.NIGHT_VISION, PotionType.SLOW_FALLING, PotionType.TURTLE_MASTER, PotionType.HEALING, PotionType.LONG_FIRE_RESISTANCE, PotionType.LONG_WATER_BREATHING, PotionType.LONG_INVISIBILITY, PotionType.LONG_NIGHT_VISION, PotionType.LONG_SLOW_FALLING, PotionType.LONG_SWIFTNESS, PotionType.LONG_STRENGTH, PotionType.LONG_LEAPING, PotionType.LONG_REGENERATION, PotionType.STRONG_HEALING, PotionType.STRONG_SWIFTNESS, PotionType.STRONG_STRENGTH, PotionType.STRONG_LEAPING, PotionType.STRONG_REGENERATION, PotionType.INFESTED, PotionType.LUCK, PotionType.WIND_CHARGED
            );

            // Pick a random base type
            PotionType chosenType = desirableBaseTypes.get(random.nextInt(desirableBaseTypes.size()));

            // Randomly decide to upgrade or extend the potion, if possible
            if (chosenType.isExtendable() && random.nextBoolean()) {
                chosenType = PotionType.valueOf("LONG_" + chosenType.name());
            } else if (chosenType.isUpgradeable() && random.nextBoolean()) {
                chosenType = PotionType.valueOf("STRONG_" + chosenType.name());
            }

            // Set the base potion type, which correctly names the potion and applies its effects.
            meta.setBasePotionType(chosenType);
            item.setItemMeta(meta);
        }
    }

    private void addRandomPotionEffectToArrow(ItemStack item) {
        if (item.getItemMeta() instanceof PotionMeta) {
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta == null) {
                meta = (PotionMeta) plugin.getServer().getItemFactory().getItemMeta(item.getType());
            }
            
            // A list of base potion types that are desirable.
            List<PotionType> desirableBaseTypes = Arrays.asList(
                    PotionType.SWIFTNESS, PotionType.STRENGTH, PotionType.LEAPING, PotionType.REGENERATION,
                    PotionType.FIRE_RESISTANCE, PotionType.WATER_BREATHING, PotionType.INVISIBILITY,
                    PotionType.NIGHT_VISION, PotionType.SLOW_FALLING, PotionType.TURTLE_MASTER, PotionType.HEALING, PotionType.LONG_FIRE_RESISTANCE, PotionType.LONG_WATER_BREATHING, PotionType.LONG_INVISIBILITY, PotionType.LONG_NIGHT_VISION, PotionType.LONG_SLOW_FALLING, PotionType.LONG_SWIFTNESS, PotionType.LONG_STRENGTH, PotionType.LONG_LEAPING, PotionType.LONG_REGENERATION, PotionType.STRONG_HEALING, PotionType.STRONG_SWIFTNESS, PotionType.STRONG_STRENGTH, PotionType.STRONG_LEAPING, PotionType.STRONG_REGENERATION, PotionType.INFESTED, PotionType.LUCK, PotionType.WIND_CHARGED
            );

            // Pick a random base type
            PotionType chosenType = desirableBaseTypes.get(random.nextInt(desirableBaseTypes.size()));

            // Randomly decide to upgrade or extend the potion, if possible
            if (chosenType.isExtendable() && random.nextBoolean()) {
                chosenType = PotionType.valueOf("LONG_" + chosenType.name());
            } else if (chosenType.isUpgradeable() && random.nextBoolean()) {
                chosenType = PotionType.valueOf("STRONG_" + chosenType.name());
            }

            // Set the base potion type, which correctly names the potion and applies its effects.
            meta.setBasePotionType(chosenType);
            item.setItemMeta(meta);
        }
    }

    private Map<String, List<Material>> initializeAndCategorizeMaterials() {
        Map<String, List<Material>> categorized = new HashMap<>();
        List<String> blacklist = plugin.getItemBlacklist();
        for (Material material : Material.values()) {
            if (material.isItem() && !material.isAir()) {
                // Exclude powerful or unobtainable items from the blacklist
                if (blacklist.contains(material.name())) {
                    continue;
                }
                String category = getCategory(material);
                categorized.computeIfAbsent(category, k -> new ArrayList<>()).add(material);
            }
        }
        return categorized;
    }

    private String formatDefaultItemName(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        String[] words = name.split(" ");
        StringBuilder formattedName = new StringBuilder();
        for (String word : words) {
            formattedName.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return formattedName.toString().trim();
    }

    public static String getCategory(Material material) {
        String name = material.name();
        // Check for legendary items first
        if (name.equals("ELYTRA") || name.equals("BEACON") || name.equals("CONDUIT") || name.equals("DRAGON_EGG") || name.equals("NETHER_STAR") || name.equals("HEAVY_CORE") || name.equals("TOTEM_OF_UNDYING") || name.equals("SHULKER_BOX") || name.equals("SHULKER_SHELL") || name.equals("ENCHANTED_GOLDEN_APPLE")) {
            return AuctionHouseGUI.Category.LEGENDARY.getDisplayName();
        }
        // Use getDisplayName() to be consistent with the GUI
        if (name.endsWith("_SWORD") || name.endsWith("_BOW") || name.endsWith("CROSSBOW") || name.equals("TIPPED_ARROW") || name.equals("SPECTRAL_ARROW") || (name.endsWith("_AXE") && !material.name().contains("PICKAXE") || name.equals("TRIDENT") || name.equals("MACE"))) {
            return AuctionHouseGUI.Category.WEAPONS.getDisplayName();
        }
        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.endsWith("_AXE")) {
            return AuctionHouseGUI.Category.TOOLS.getDisplayName();
        }
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") || name.equals("SHIELD") || name.endsWith("_ARMOR") && !name.endsWith("_HORSE_ARMOR")) {
            return AuctionHouseGUI.Category.ARMOR.getDisplayName();
        }
        if (name.endsWith("_INGOT") || name.endsWith("_NUGGET") || name.endsWith("_SCRAP") || name.endsWith("_ORE") || name.endsWith("_DUST") || name.startsWith("RAW_") || name.equals("DIAMOND") || name.equals("COAL") || name.equals("CHARCOAL") || name.equals("LAPIS_LAZULI") || name.equals("EMERALD") || name.equals("NETHER_QUARTZ") || name.equals("QUARTZ") || name.equals("REDSTONE")) {
            return AuctionHouseGUI.Category.MATERIALS.getDisplayName();
        }
        if (name.endsWith("SPAWNER")) {
            return AuctionHouseGUI.Category.SPAWNERS.getDisplayName();
        }
        if (name.contains("POTION")) {
            return AuctionHouseGUI.Category.POTIONS.getDisplayName();
        }
        if (name.equals("ENCHANTED_BOOK")) {
            return AuctionHouseGUI.Category.ENCHANTED_BOOKS.getDisplayName();
        }

        if (material.isEdible() && !name.equals("SPIDER_EYE") && !name.equals("ROTTEN_FLESH") && !name.equals("POISONOUS_POTATO") && !name.equals("PUFFERFISH") && !name.equals("CHORUS_FRUIT")) {
            return AuctionHouseGUI.Category.FOOD.getDisplayName();
        }

        if (material.isBlock() || name.endsWith("_SAPLING") || name.endsWith("_PROPOGULE")) {
            return AuctionHouseGUI.Category.BLOCKS.getDisplayName();
        }
        if (name.endsWith("_SPAWN_EGG")) {
            return AuctionHouseGUI.Category.SPAWN_EGGS.getDisplayName();
        }
        return AuctionHouseGUI.Category.MISCELLANEOUS.getDisplayName();
    }

}
