package com.tryfx.lobbycore;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class LobbyCommand implements CommandExecutor {

    private final LobbyCore plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public LobbyCommand(LobbyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Location spawn = plugin.getLobbyWorldManager().getSpawnLocation();
        if (spawn == null) {
            player.sendMessage(MM.deserialize("<red>Lobby is not available right now."));
            return true;
        }

        player.teleport(spawn);
        player.sendMessage(MM.deserialize("<green>Teleported to the lobby."));
        return true;
    }
}
