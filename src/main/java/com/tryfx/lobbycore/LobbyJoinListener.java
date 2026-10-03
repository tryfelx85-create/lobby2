package com.tryfx.lobbycore;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

public final class LobbyJoinListener implements Listener {

    private final LobbyCore plugin;

    public LobbyJoinListener(LobbyCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Sets the player's spawn/join location to the lobby for genuinely new
     * players (no prior world). This fires before PlayerJoinEvent and is the
     * correct hook for controlling first-join location without a visible
     * teleport flicker.
     *
     * NOTE: If AuthMe is installed, AuthMe itself typically controls
     * post-login teleport destinations (e.g. teleporting unauthenticated
     * players to a fixed spawn until they /login). This listener only
     * handles the Bukkit-level spawn location; AuthMe's own configuration
     * should point its "unauthenticated spawn" at the same lobby world/coords
     * for a consistent experience - see the README for the exact AuthMe
     * config keys to set.
     */
    @EventHandler
    public void onSpawnLocation(PlayerSpawnLocationEvent event) {
        Location lobbySpawn = plugin.getLobbyWorldManager().getSpawnLocation();
        if (lobbySpawn != null) {
            event.setSpawnLocation(lobbySpawn);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getPlaytimeManager().onJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPlaytimeManager().onQuit(event.getPlayer());
    }
}
