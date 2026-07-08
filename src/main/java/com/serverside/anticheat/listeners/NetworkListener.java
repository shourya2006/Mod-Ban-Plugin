package com.serverside.anticheat.listeners;

import com.serverside.anticheat.ServerSideAntiCheat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;

public class NetworkListener implements Listener, PluginMessageListener {
    
    private final ServerSideAntiCheat plugin;

    public NetworkListener(ServerSideAntiCheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChannelRegister(PlayerRegisterChannelEvent event) {
        String channel = event.getChannel();
        for (String banned : plugin.getBannedChannels()) {
            if (channel.equalsIgnoreCase(banned)) {
                kickPlayer(event.getPlayer(), "Banned Channel: " + banned);
                break;
            }
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel.equals("minecraft:brand") || channel.equals("MC|Brand")) {
            String brand = new String(message, StandardCharsets.UTF_8);
            // The brand string usually starts with a length byte or varint. We can just check if it contains the brand name.
            brand = brand.toLowerCase();
            
            for (String banned : plugin.getBannedBrands()) {
                if (brand.contains(banned.toLowerCase())) {
                    kickPlayer(player, "Banned Client Brand: " + banned);
                    break;
                }
            }
        }
    }

    private void kickPlayer(Player player, String reason) {
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            String msg = plugin.getKickMessage().replace("%reason%", reason);
            player.kickPlayer(org.bukkit.ChatColor.translateAlternateColorCodes('&', msg));
        });
    }
}
