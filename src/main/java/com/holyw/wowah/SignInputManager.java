package com.holyw.wowah;

import org.bukkit.Location;
import org.bukkit.Material;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class SignInputManager implements Listener {

    private final Map<UUID, Consumer<String[]>> signSessions = new HashMap<>();

    public SignInputManager(SystemShop plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openSignGUI(Player player, Consumer<String[]> onComplete) {
        Location signLocation = player.getLocation().clone().add(0, 200, 0);
        player.sendBlockChange(signLocation, Material.OAK_SIGN.createBlockData());

        signSessions.put(player.getUniqueId(), onComplete);

        try {
            String version = org.bukkit.Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Object packet = Class.forName("net.minecraft.server." + version + ".PacketPlayOutOpenSignEditor").getConstructor(Class.forName("net.minecraft.server." + version + ".BlockPosition")).newInstance(Class.forName("net.minecraft.server." + version + ".BlockPosition").getConstructor(int.class, int.class, int.class).newInstance(signLocation.getBlockX(), signLocation.getBlockY(), signLocation.getBlockZ()));
            Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Object playerConnection = craftPlayer.getClass().getField("playerConnection").get(craftPlayer);
            playerConnection.getClass().getMethod("sendPacket", Class.forName("net.minecraft.server." + version + ".Packet")).invoke(playerConnection, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        if (signSessions.containsKey(player.getUniqueId())) {
            Consumer<String[]> onComplete = signSessions.remove(player.getUniqueId());
            onComplete.accept(event.getLines());
            event.setCancelled(true);
            player.sendBlockChange(event.getBlock().getLocation(), event.getBlock().getBlockData());
        }
    }
}
