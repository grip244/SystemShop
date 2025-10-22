package com.holyw.wowah;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HealthBarManager {

    private final SystemShop plugin;
    private final Map<UUID, HealthBar> healthBars = new HashMap<>();
    private BukkitTask updaterTask;

    public HealthBarManager(SystemShop plugin) {
        this.plugin = plugin;
    }

    public void startUpdater() {
        if (updaterTask != null) {
            updaterTask.cancel();
        }
        updaterTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (HealthBar healthBar : new ArrayList<>(healthBars.values())) {
                healthBar.update();
            }
        }, 0L, 1L);
    }

    public void stopUpdater() {
        if (updaterTask != null) {
            updaterTask.cancel();
            updaterTask = null;
        }
    }

    public void createHealthBar(LivingEntity entity) {
        if (!plugin.getConfig().getBoolean("healthbar.enabled", true)) return;

        // Clean up any old bars for this entity before creating a new one
        removeHealthBar(entity);

        HealthBar healthBar = new HealthBar(entity, plugin);
        healthBars.put(entity.getUniqueId(), healthBar);
    }

    public void removeHealthBar(LivingEntity entity) {
        // Remove from map
        HealthBar healthBar = healthBars.remove(entity.getUniqueId());
        if (healthBar != null) {
            healthBar.remove();
        }
    }

    public void updateHealthBar(LivingEntity entity) {
        if (!plugin.getConfig().getBoolean("healthbar.enabled", true)) return;
        if (!healthBars.containsKey(entity.getUniqueId())) {
            createHealthBar(entity);
        }
        HealthBar healthBar = healthBars.get(entity.getUniqueId());
        if (healthBar != null) {
            healthBar.update();
        }
    }

    public void handlePlayerJoin(Player player) {
        createHealthBar(player);
    }

    public void handlePlayerQuit(Player player) {
        removeHealthBar(player);
    }

    public void enableHealthBars() {
        startUpdater();
        for (Player player : Bukkit.getOnlinePlayers()) {
            createHealthBar(player);
        }
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity instanceof Player) {
                    continue;
                }
                createHealthBar(entity);
            }
        }
    }

    public void disableHealthBars() {
        stopUpdater();
        // Create a copy of the keys to iterate over, to avoid ConcurrentModificationException
        for (UUID entityId : new ArrayList<>(healthBars.keySet())) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity instanceof LivingEntity) {
                removeHealthBar((LivingEntity) entity);
            }
        }
        healthBars.clear();

        // Brute-force cleanup of any remaining armor stands
        NamespacedKey key = new NamespacedKey(plugin, "healthbar_owner");
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (ArmorStand as : world.getEntitiesByClass(ArmorStand.class)) {
                if (as.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    as.remove();
                }
            }
        }
    }

    public boolean isUpdaterRunning() {
        return updaterTask != null && !updaterTask.isCancelled();
    }

    public void removeAllHealthBars() {
        stopUpdater();
        for (UUID entityId : new ArrayList<>(healthBars.keySet())) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity instanceof LivingEntity) {
                removeHealthBar((LivingEntity) entity);
            }
        }
        healthBars.clear();
    }
}
