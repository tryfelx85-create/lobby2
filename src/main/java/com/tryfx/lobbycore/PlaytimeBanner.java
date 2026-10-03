package com.tryfx.lobbycore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.List;
import java.util.UUID;

/**
 * Manages a single floating TextDisplay entity that shows the top-N players
 * by playtime, refreshed on an interval. The entity is billboard-oriented
 * (always faces the viewer) and persists across restarts by being re-found
 * (or re-spawned if missing) at the configured location on startup.
 */
public final class PlaytimeBanner {

    private final LobbyCore plugin;
    private TextDisplay displayEntity;
    private Location bannerLocation;
    private int topCount = 10;

    public PlaytimeBanner(LobbyCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Spawns (or re-locates, if one already exists from a prior run) the
     * banner entity at the given location.
     */
    public void setup(Location location, int topCount) {
        this.bannerLocation = location.clone();
        this.topCount = topCount;

        if (displayEntity != null && displayEntity.isValid()) {
            displayEntity.teleport(bannerLocation);
            return;
        }

        displayEntity = location.getWorld().spawn(bannerLocation, TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setShadowed(false);
            entity.setSeeThrough(false);
            entity.setDefaultBackground(true);
            entity.text(Component.text("Loading leaderboard..."));
            entity.setPersistent(true);
        });
    }

    /**
     * Rebuilds the displayed text from current playtime data. Call on an
     * interval (e.g. every 10 minutes) from the main plugin's scheduler.
     */
    public void refresh(PlaytimeManager playtimeManager) {
        if (displayEntity == null || !displayEntity.isValid()) {
            if (bannerLocation != null) {
                setup(bannerLocation, topCount);
            } else {
                return;
            }
        }

        List<PlaytimeManager.PlaytimeEntry> top = playtimeManager.getTopPlayers(topCount);

        Component text = Component.text("🏆 Top " + topCount + " Playtime 🏆", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .appendNewline();

        if (top.isEmpty()) {
            text = text.append(Component.text("No data yet.", NamedTextColor.GRAY));
        } else {
            for (int i = 0; i < top.size(); i++) {
                PlaytimeManager.PlaytimeEntry entry = top.get(i);
                String formatted = PlaytimeManager.formatTicksAsHoursMinutes(entry.totalTicks());

                NamedTextColor rankColor = switch (i) {
                    case 0 -> NamedTextColor.GOLD;
                    case 1 -> NamedTextColor.GRAY;
                    case 2 -> NamedTextColor.RED;
                    default -> NamedTextColor.WHITE;
                };

                text = text.appendNewline()
                        .append(Component.text("#" + (i + 1) + " ", rankColor).decoration(TextDecoration.BOLD, true))
                        .append(Component.text(entry.name(), NamedTextColor.AQUA))
                        .append(Component.text(" - " + formatted, NamedTextColor.GRAY));
            }
        }

        displayEntity.text(text);
    }

    public void remove() {
        if (displayEntity != null && displayEntity.isValid()) {
            displayEntity.remove();
        }
        displayEntity = null;
    }
}
