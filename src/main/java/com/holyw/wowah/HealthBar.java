package com.holyw.wowah;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;


public class HealthBar {

    private final LivingEntity entity;
    private ArmorStand healthArmorStand;
    private ArmorStand armorArmorStand;
    private final SystemShop plugin;

    public HealthBar(LivingEntity entity, SystemShop plugin) {
        this.entity = entity;
        this.plugin = plugin;
        create();
    }

    private void create() {
        NamespacedKey key = new NamespacedKey(plugin, "healthbar_owner");
        String ownerUUID = entity.getUniqueId().toString();

        // Position for the health bar (bottom)
        Location healthLocation = entity.getEyeLocation().add(0, 0.25, 0);
        healthArmorStand = entity.getWorld().spawn(healthLocation, ArmorStand.class, as -> {
            as.setGravity(false);
            as.setSmall(true);
            as.setMarker(true);
            as.setVisible(false);
            as.setCustomNameVisible(true);
            as.getPersistentDataContainer().set(key, PersistentDataType.STRING, ownerUUID);
        });
        

        // Position for the armor bar (top)
        Location armorLocation = entity.getEyeLocation().add(0, 0.5, 0);
        armorArmorStand = entity.getWorld().spawn(armorLocation, ArmorStand.class, as -> {
            as.setGravity(false);
            as.setSmall(true);
            as.setMarker(true);
            as.setVisible(false);
            as.setCustomNameVisible(true);
            as.getPersistentDataContainer().set(key, PersistentDataType.STRING, ownerUUID);
        });

        update();
    }

    public void update() {
        if (healthArmorStand == null || !healthArmorStand.isValid() || armorArmorStand == null || !armorArmorStand.isValid()) {
            plugin.getHealthBarManager().removeHealthBar(entity);
            return;
        }

        if (entity instanceof Player && !((Player) entity).isOnline()) {
            plugin.getHealthBarManager().removeHealthBar(entity);
            return;
        }

        if (entity.isDead() || !entity.isValid()) {
            plugin.getHealthBarManager().removeHealthBar(entity);
            return;
        }

        Location eyeLocation = entity.getEyeLocation();

        // Update Health Bar
        double health = entity.getHealth();
        double maxHealth = entity.getAttribute(Attribute.MAX_HEALTH).getValue();
        String healthStr = String.format("%.1f", health);
        String maxHealthStr = String.format("%.1f", maxHealth);

        String healthFormat = Lang.get("healthbar-health-style");
        String healthName = healthFormat
                .replace("{health}", healthStr)
                .replace("{max_health}", maxHealthStr);
        healthArmorStand.setCustomName(healthName);
        healthArmorStand.teleport(eyeLocation.clone().add(0, 0.25, 0));

        // Update Armor Bar
        if (entity instanceof Player) {
            int armor = (int) entity.getAttribute(Attribute.ARMOR).getValue();
            String armorStr = String.valueOf(armor);
            String armorFormat = Lang.get("healthbar-armor-style");
            String armorName = armorFormat.replace("{armor}", armorStr);
            armorArmorStand.setCustomName(armorName);
            armorArmorStand.setCustomNameVisible(true);
        } else {
            armorArmorStand.setCustomNameVisible(false);
        }
        armorArmorStand.teleport(eyeLocation.clone().add(0, 0.5, 0));
    }

    public void remove() {
        
        if (healthArmorStand != null) {
            
            healthArmorStand.remove();
        }
        if (armorArmorStand != null) {
           
            armorArmorStand.remove();
        }
    }
}
