package com.holyw.wowah;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

public class SignInputManager implements Listener {

    private final Map<UUID, Consumer<String[]>> signSessions = new HashMap<>();
    private final SystemShop plugin;

    public SignInputManager(SystemShop plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openSignGUI(Player player, Consumer<String[]> onComplete) {
        Location signLocation = player.getLocation().clone().add(0, 200, 0);
        if (signLocation.getY() > 319) {
            signLocation.setY(319);
        }
        player.sendBlockChange(signLocation, Material.OAK_SIGN.createBlockData());

        signSessions.put(player.getUniqueId(), onComplete);

        try {
            Class<?> blockPositionClass = Class.forName("net.minecraft.core.BlockPosition");
            Constructor<?> blockPositionConstructor = blockPositionClass.getConstructor(int.class, int.class, int.class);
            Object blockPosition = blockPositionConstructor.newInstance(signLocation.getBlockX(), signLocation.getBlockY(), signLocation.getBlockZ());

            Class<?> packetPlayOutOpenSignEditorClass = Class.forName("net.minecraft.network.protocol.game.PacketPlayOutOpenSignEditor");
            Constructor<?> packetPlayOutOpenSignEditorConstructor = packetPlayOutOpenSignEditorClass.getConstructor(blockPositionClass, boolean.class);
            Object packet = packetPlayOutOpenSignEditorConstructor.newInstance(blockPosition, true);

            Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Object playerConnection = craftPlayer.getClass().getField("playerConnection").get(craftPlayer);
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
            playerConnection.getClass().getMethod("sendPacket", packetClass).invoke(playerConnection, packet);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to open sign GUI for " + player.getName(), e);
            player.sendMessage(Lang.get("error-opening-sign"));
            signSessions.remove(player.getUniqueId());
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
