package com.holyw.wowah;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SleepListener implements Listener {

    private final SystemShop plugin;
    private final Set<UUID> sleepingPlayers = new HashSet<>();

    public SleepListener(SystemShop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        // Only proceed if the player successfully entered the bed
        if (event.getBedEnterResult() == PlayerBedEnterEvent.BedEnterResult.OK) {
            Player player = event.getPlayer();
            sleepingPlayers.add(player.getUniqueId());
            checkSleepStatus(player.getWorld());
        }
    }

    @EventHandler
    public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
        sleepingPlayers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sleepingPlayers.remove(event.getPlayer().getUniqueId());
    }

    private void checkSleepStatus(World world) {
        long onlinePlayersInWorld = world.getPlayers().size();
        if (onlinePlayersInWorld == 0) return;

        double sleepPercentageRequired = plugin.getConfig().getDouble("sleep.percentage-required", 0.5);
        double percentageSleeping = (double) sleepingPlayers.stream().filter(uuid -> Bukkit.getPlayer(uuid) != null && Bukkit.getPlayer(uuid).getWorld().equals(world)).count() / onlinePlayersInWorld;

        // Check if the threshold is met for the first time
        if (percentageSleeping >= sleepPercentageRequired && world.getTime() > 12541) { // 12541 is when beds can be used
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Re-check the condition after the delay
                long currentOnline = world.getPlayers().size();
                if (currentOnline == 0) return;

                double currentPercentage = (double) sleepingPlayers.stream().filter(uuid -> Bukkit.getPlayer(uuid) != null && Bukkit.getPlayer(uuid).getWorld().equals(world)).count() / currentOnline;

                if (currentPercentage >= sleepPercentageRequired) {
                    world.setTime(1000); // Set time to morning
                    world.setStorm(false);
                    world.setThundering(false);
                    Bukkit.broadcastMessage(Lang.get("sleep-success"));
                    for (Player p : world.getPlayers()) {
                        p.playSound(p.getLocation(), Sound.ENTITY_CHICKEN_AMBIENT, SoundCategory.MASTER, 1.0f, 1.0f);
                    }
                }
            }, 30L); // 1.5-second delay (20 ticks per second)
        } 
    }
}