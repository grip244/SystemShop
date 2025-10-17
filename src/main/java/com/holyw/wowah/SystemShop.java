package com.holyw.wowah;

import com.earth2me.essentials.Essentials;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

import java.util.*;
import java.util.stream.Collectors;

import org.bstats.bukkit.Metrics;

public class SystemShop extends JavaPlugin {

    private static Economy econ = null;
    private AuctionHouseManager auctionHouseManager;
    private AuctionHouseGUI auctionHouseGUI;
    private PricingManager pricingManager;
    private EventManager eventManager;
    private SpecialOrdersManager specialOrdersManager;
    private DailyDealsManager dailyDealsManager;
    private ShopScoreboard shopScoreboard;
    private BuybackManager buybackManager;
    private List<String> itemBlacklist;
    private Essentials essentials = null;
    private FileConfiguration motdConfig = null;
    private File motdFile = null;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe(Lang.get("no-vault"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        setupEssentials();
        setupPlaceholderAPI();
        this.saveDefaultConfig();
        Lang.load(this);
        loadMotdConfig();
        setupBlacklist();
        auctionHouseManager = new AuctionHouseManager(this);
        auctionHouseGUI = new AuctionHouseGUI(this);
        pricingManager = new PricingManager(this);
        eventManager = new EventManager(this);
        specialOrdersManager = new SpecialOrdersManager(this);
        dailyDealsManager = new DailyDealsManager(this);
        buybackManager = new BuybackManager(this);
        // Decide initial population behavior.
        // If daily-deals are configured to refill the shop on rotate, run the rotation once on startup
        // so that the populator is executed inside rotateDeals (ensures deals are applied immediately).
        boolean dailyDealsEnabled = this.getConfig().getBoolean("daily-deals.enabled", true);
        boolean refillOnRotate = this.getConfig().getBoolean("daily-deals.refill-shop-on-rotate", true);
        int initialItems = this.getConfig().getInt("population.initial-items", 1500);
        if (dailyDealsEnabled && refillOnRotate) {
            // Run rotateDeals one tick later to let other plugins finish enabling.
            getServer().getScheduler().runTaskLater(this, () -> {
                try {
                    dailyDealsManager.rotateDeals();
                } catch (Throwable t) {
                    getLogger().severe("Failed to run initial daily deals rotation: " + t.getMessage());
                    t.printStackTrace();
                }
            }, 1L);
        } else {
            // Default legacy behavior: populate initial items if none exist.
            if (initialItems > 0 && auctionHouseManager.getTotalItems() == 0) {
                getServer().getScheduler().runTaskLater(this, () -> {
                    try {
                        getLogger().info(Lang.get("population-start", "{items}", String.valueOf(initialItems)));
                        new AuctionPopulator(this).populate(initialItems);
                    } catch (Throwable t) {
                        getLogger().severe("Failed to populate initial system shop items: " + t.getMessage());
                        t.printStackTrace();
                    }
                }, 1L);
            }
        }
        shopScoreboard = new ShopScoreboard(this);
        shopScoreboard.enable();
        new AuctionHouseListener(this);
        new SleepListener(this);
        new MOTDListener(this);
        getLogger().info(Lang.get("plugin-enabled"));

        // bStats
        int pluginId = 27557;
        new Metrics(this, pluginId);
        getLogger().info("bStats metrics enabled.");

        // Schedule daily deals rotation
        long ticksPerSecond = 20L;
        long ticksPerMinute = ticksPerSecond * 60L;
        long ticksPerHour = ticksPerMinute * 60L;
        long periodTicks = ticksPerHour * 24L; // 24 hours
        // If we already ran rotateDeals on startup (dailyDeals enabled and refill-on-rotate true),
        // schedule the first periodic run after the full period so we don't duplicate shortly after startup.
        long initialDelayTicks = (dailyDealsEnabled && refillOnRotate) ? periodTicks : ticksPerMinute; // else 1 minute

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                dailyDealsManager.rotateDeals();
            }
        }.runTaskTimer(this, initialDelayTicks, periodTicks);
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public SpecialOrdersManager getSpecialOrdersManager() {
        return specialOrdersManager;
    }

    @Override
    public void onDisable() {
        auctionHouseManager.clearSystemAuctions();
        if (shopScoreboard != null) {
            shopScoreboard.disable();
        }
        getLogger().info(Lang.get("plugin-disabled"));
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public Essentials getEssentials() {
        return essentials;
    }

    private void setupEssentials() {
        Plugin essentialsPlugin = getServer().getPluginManager().getPlugin("Essentials");
        if (essentialsPlugin instanceof Essentials) {
            essentials = (Essentials) essentialsPlugin;
            getLogger().info("Essentials found, using it for worth values.");
        } else {
            getLogger().info("Essentials not found, using pricing.yml for worth values.");
        }
    }

    private void setupPlaceholderAPI() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPI(this).register();
            getLogger().info("PlaceholderAPI found, placeholders registered.");
        } else {
            getLogger().info("PlaceholderAPI not found, placeholders will not work.");
        }
    }

    public void loadMotdConfig() {
        motdFile = new File(getDataFolder(), "motd.yml");
        if (!motdFile.exists()) {
            saveResource("motd.yml", false);
        }
        motdConfig = YamlConfiguration.loadConfiguration(motdFile);
    }

    public FileConfiguration getMotdConfig() {
        return motdConfig;
    }

    private void setupBlacklist() {
        File blacklistFile = new File(getDataFolder(), "blacklist.yml");
        if (!blacklistFile.exists()) {
            saveResource("blacklist.yml", false);
        }
        FileConfiguration blacklistConfig = YamlConfiguration.loadConfiguration(blacklistFile);
        itemBlacklist = blacklistConfig.getStringList("blacklist");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("shop")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("refill")) {
                if (sender instanceof ConsoleCommandSender) {
                    auctionHouseManager.clearSystemAuctions();
                    int refillAmount = this.getConfig().getInt("population.refill-items", 800);
                    new AuctionPopulator(this).populate(refillAmount);
                    sender.sendMessage(Lang.get("shop-refilled"));
                } else {
                    sender.sendMessage(Lang.get("console-only"));
                }
                return true;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("systemshop.admin")) {
                    this.reloadConfig();
                    Lang.load(this);
                    loadMotdConfig();
                    pricingManager.load();
                    sender.sendMessage(Lang.get("config-reloaded"));
                } else {
                    sender.sendMessage(Lang.get("no-permission"));
                }
                return true;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("purge")) {
                if(args.length > 1 && args[1].equalsIgnoreCase("playerauctions")) {
                    if (sender.hasPermission("systemshop.admin")) {
                        auctionHouseManager.clearPlayerAuctions(); // This method is now redundant if no players can sell
                        sender.sendMessage(Lang.get("player-auctions-purged"));
                    } else {
                        sender.sendMessage(Lang.get("no-permission"));
                    }
                    return true;
                }
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("order")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    specialOrdersManager.openSpecialOrderGUI(player);
                } else {
                    sender.sendMessage(Lang.get("player-only"));
                }
                return true;
            }

                if (args.length > 0 && args[0].equalsIgnoreCase("scoreboard")) {
                if (args.length == 1) {
                    if (sender instanceof Player) {
                        Player player = (Player) sender;
                        if (shopScoreboard != null) {
                            if (shopScoreboard.isHidden(player)) {
                                shopScoreboard.showForPlayer(player);
                                player.sendMessage(Lang.get("scoreboard-shown"));
                            } else {
                                shopScoreboard.hideForPlayer(player);
                                player.sendMessage(Lang.get("scoreboard-hidden"));
                            }
                        } else {
                            player.sendMessage(Lang.get("scoreboard-disabled"));
                        }
                    } else {
                        sender.sendMessage(Lang.get("player-only"));
                    }
                    return true;
                }

                if (args.length == 2 && args[1].equalsIgnoreCase("toggle")) {
                    if (!sender.hasPermission("systemshop.admin")) {
                        sender.sendMessage(Lang.get("no-permission"));
                        return true;
                    }
                    boolean enabled = !this.getConfig().getBoolean("scoreboard.enabled", true);
                    this.getConfig().set("scoreboard.enabled", enabled);
                    this.saveConfig();
                    if (enabled) {
                        if (shopScoreboard != null) shopScoreboard.enable();
                        sender.sendMessage(Lang.get("scoreboard-enabled"));
                    } else {
                        if (shopScoreboard != null) shopScoreboard.disable();
                        sender.sendMessage(Lang.get("scoreboard-disabled"));
                    }
                    return true;
                }
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("sell")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    ItemStack itemInHand = player.getInventory().getItemInMainHand();

                    if (itemInHand == null || itemInHand.getType().isAir()) {
                        player.sendMessage(Lang.get("sell-no-item"));
                        return true;
                    }

                    double price = buybackManager.getBuybackPrice(itemInHand);

                    if (price <= 0) {
                        player.sendMessage(Lang.get("sell-not-sellable"));
                        return true;
                    }

                    int amount = itemInHand.getAmount();
                    double totalPrice = price * amount;

                    econ.depositPlayer(player, totalPrice);
                    buybackManager.recordSale(itemInHand.getType(), amount);
                    player.getInventory().setItemInMainHand(null);
                    player.sendMessage(Lang.get("sell-success", "{amount}", String.valueOf(amount), "{item}", itemInHand.getType().toString(), "{price}", String.valueOf(totalPrice)));

                } else {
                    sender.sendMessage(Lang.get("player-only"));
                }
                return true;
            }

            if (sender instanceof Player) {
                Player player = (Player) sender;
                auctionHouseGUI.openCategoryGUI(player);
            }
            else {
                sender.sendMessage(Lang.get("player-only"));
            }
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("shop")) {
            if (args.length == 1) {
                List<String> subcommands = new ArrayList<>();
                if (sender.hasPermission("systemshop.admin")) {
                    subcommands.add("refill");
                    subcommands.add("purge");
                    subcommands.add("reload");
                }
                subcommands.add("order");
                subcommands.add("sell");

                return subcommands.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("purge") && sender.hasPermission("systemshop.admin")) {
                    if ("playerauctions".toLowerCase().startsWith(args[1].toLowerCase())) {
                        return Collections.singletonList("playerauctions");
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    public static Economy getEconomy() {
        return econ;
    }

    public AuctionHouseManager getAuctionHouseManager() {
        return auctionHouseManager;
    }

    public AuctionHouseGUI getAuctionHouseGUI() {
        return auctionHouseGUI;
    }

    public PricingManager getPricingManager() {
        return pricingManager;
    }

    public BuybackManager getBuybackManager() {
        return buybackManager;
    }

    public List<String> getItemBlacklist() {
        return itemBlacklist;
    }
}