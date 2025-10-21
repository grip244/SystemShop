package com.holyw.wowah;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class HealthBarListener implements Listener {

    private final HealthBarManager healthBarManager;

    public HealthBarListener(HealthBarManager healthBarManager) {
        this.healthBarManager = healthBarManager;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() == org.bukkit.entity.EntityType.ARMOR_STAND) {
            return;
        }
        if (event.getEntity() instanceof org.bukkit.entity.Player) {
            return;
        }
        healthBarManager.createHealthBar(event.getEntity());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        healthBarManager.handlePlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        healthBarManager.handlePlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity) {
            healthBarManager.updateHealthBar((LivingEntity) event.getEntity());
        }
    }

    @EventHandler
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof LivingEntity) {
            healthBarManager.updateHealthBar((LivingEntity) event.getEntity());
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        healthBarManager.removeHealthBar(event.getEntity());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        healthBarManager.createHealthBar(event.getPlayer());
    }
}
