package com.holyw.wowah;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
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
    private ConsignmentManager consignmentManager;
    private SignInputManager signInputManager;
    private DailyDealsManager dailyDealsManager;
    private List<String> itemBlacklist;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe(Lang.get("no-vault"));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.saveDefaultConfig();
        Lang.load(this);
        setupBlacklist();
        auctionHouseManager = new AuctionHouseManager(this);
        auctionHouseGUI = new AuctionHouseGUI(this);
        pricingManager = new PricingManager(this);
        eventManager = new EventManager(this);
        specialOrdersManager = new SpecialOrdersManager(this);
        consignmentManager = new ConsignmentManager(this);
        signInputManager = new SignInputManager(this);
        dailyDealsManager = new DailyDealsManager(this);
        new AuctionHouseListener(this);
        new SleepListener(this); // Register the new sleep listener
        getLogger().info(Lang.get("plugin-enabled"));

        // bStats
        int pluginId = 27557;
        Metrics metrics = new Metrics(this, pluginId);
        getLogger().info("bStats metrics enabled.");

        // Schedule daily deals rotation
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                dailyDealsManager.rotateDeals();
            }
        }.runTaskTimer(this, 20L * 60, 20L * 60 * 60 * 24); // 1 minute delay, then every 24 hours
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public SpecialOrdersManager getSpecialOrdersManager() {
        return specialOrdersManager;
    }

    public ConsignmentManager getConsignmentManager() {
        return consignmentManager;
    }

    public SignInputManager getSignInputManager() {
        return signInputManager;
    }

    @Override
    public void onDisable() {
        auctionHouseManager.clearSystemAuctions();
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

            if (args.length > 0 && args[0].equalsIgnoreCase("sell")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    consignmentManager.openConsignmentGUI(player);
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

    public List<String> getItemBlacklist() {
        return itemBlacklist;
    }
}