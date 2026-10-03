package com.tryfx.lobbycore;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class LobbyCore extends JavaPlugin {

    private LobbyWorldManager lobbyWorldManager;
    private PlaytimeManager playtimeManager;
    private PlaytimeBanner playtimeBanner;
    private PortalManager portalManager;

    @Override
    public void onEnable() {
        // IMPORTANT: isPluginEnabled(), not just getPlugin() != null.
        // getPlugin() only checks that the jar is present/loaded - it stays
        // non-null even if that plugin's own onEnable() failed (e.g. "not
        // compatible with your version of Minecraft. Disabling..." from
        // Citizens). Checking only for presence let LobbyCore continue
        // past this point with Citizens actually unavailable, construct
        // PortalManager (which immediately calls into CitizensAPI), throw,
        // and get silently auto-disabled by Paper with no useful message -
        // which is why /lobby then failed and nobody ever got redirected
        // to the lobby on join.
        boolean citizensOk = getServer().getPluginManager().isPluginEnabled("Citizens");
        boolean worldEditOk = getServer().getPluginManager().isPluginEnabled("WorldEdit");

        if (!citizensOk) {
            getLogger().severe("Citizens is not installed OR failed to enable (check the server log above this line "
                    + "for a Citizens error - a common cause is Citizens not yet supporting your exact Minecraft "
                    + "version). LobbyCore requires a working Citizens install for portal NPCs. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!worldEditOk) {
            getLogger().severe("WorldEdit is not installed or failed to enable. LobbyCore requires WorldEdit to paste "
                    + "the lobby schematic. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!getServer().getPluginManager().isPluginEnabled("AuthMe")) {
            getLogger().warning("AuthMe is not installed or not enabled. Lobby will function, but no login/verification gating will occur.");
        }

        saveDefaultConfig();
        saveResource("schematics/freelobby0.schem", false);

        // World creation and schematic paste do NOT depend on Citizens, so
        // this always runs regardless of the Citizens check above - this is
        // why tryfx_lobby was created successfully even while the rest of
        // the plugin silently failed.
        this.lobbyWorldManager = new LobbyWorldManager(this);
        this.lobbyWorldManager.setup();

        // From here on, everything DOES depend on Citizens being truly up,
        // which we've now actually verified above.
        this.playtimeManager = new PlaytimeManager(this);
        this.playtimeBanner = new PlaytimeBanner(this);
        this.portalManager = new PortalManager(this);

        setupPortalsAndBanner();

        // This listener is what redirects join/spawn to the lobby - it was
        // never reached before if an earlier step threw.
        getServer().getPluginManager().registerEvents(new LobbyJoinListener(this), this);

        getCommand("lobby").setExecutor(new LobbyCommand(this));

        // Playtime ticking (every server tick, cheap counter increment)
        Bukkit.getScheduler().runTaskTimer(this, playtimeManager::tick, 1L, 1L);

        // Flush playtime to disk periodically (every 5 minutes = 6000 ticks)
        Bukkit.getScheduler().runTaskTimer(this, playtimeManager::flushAll, 6000L, 6000L);

        // Refresh the banner every 10 minutes = 12000 ticks, as configured
        int refreshIntervalTicks = getConfig().getInt("banner.refresh-interval-ticks", 12000);
        Bukkit.getScheduler().runTaskTimer(this,
                () -> playtimeBanner.refresh(playtimeManager),
                100L, refreshIntervalTicks);

        getLogger().info("LobbyCore enabled.");
    }

    @Override
    public void onDisable() {
        if (playtimeManager != null) {
            playtimeManager.flushAll();
        }
        getLogger().info("LobbyCore disabled.");
    }

    public LobbyWorldManager getLobbyWorldManager() {
        return lobbyWorldManager;
    }

    public PlaytimeManager getPlaytimeManager() {
        return playtimeManager;
    }

    public PlaytimeBanner getPlaytimeBanner() {
        return playtimeBanner;
    }

    public PortalManager getPortalManager() {
        return portalManager;
    }

    /**
     * Reads portal and banner coordinates from config.yml and spawns/places
     * them in the lobby world. If coordinates are still at the placeholder
     * defaults (all zero), this logs a warning rather than silently placing
     * NPCs at an unintended location - the operator should fill in real
     * coordinates in config.yml before relying on this.
     */
    private void setupPortalsAndBanner() {
        var lobbyWorld = lobbyWorldManager.getLobbyWorld();
        if (lobbyWorld == null) {
            getLogger().warning("Lobby world unavailable - skipping portal/banner setup.");
            return;
        }

        var smpSection = getConfig().getConfigurationSection("portals.smp-portal");
        var trainingSection = getConfig().getConfigurationSection("portals.training-portal");
        var bannerSection = getConfig().getConfigurationSection("banner");

        if (smpSection != null) {
            org.bukkit.Location loc = lobbyWorldManager.offsetToWorldLocation(
                    smpSection.getDouble("offset-x"), smpSection.getDouble("offset-y"), smpSection.getDouble("offset-z"));
            if (loc != null) {
                loc.setYaw((float) smpSection.getDouble("yaw"));
                loc.setPitch((float) smpSection.getDouble("pitch"));
                portalManager.createOrUpdatePortal(PortalManager.PortalDestination.SMP, loc,
                        smpSection.getString("display-name", "Travel to SMP"));
            }
        }

        if (trainingSection != null) {
            org.bukkit.Location loc = lobbyWorldManager.offsetToWorldLocation(
                    trainingSection.getDouble("offset-x"), trainingSection.getDouble("offset-y"), trainingSection.getDouble("offset-z"));
            if (loc != null) {
                loc.setYaw((float) trainingSection.getDouble("yaw"));
                loc.setPitch((float) trainingSection.getDouble("pitch"));
                portalManager.createOrUpdatePortal(PortalManager.PortalDestination.TRAINING, loc,
                        trainingSection.getString("display-name", "Travel to Training"));
            }
        }

        if (bannerSection != null) {
            org.bukkit.Location loc = lobbyWorldManager.offsetToWorldLocation(
                    bannerSection.getDouble("offset-x"), bannerSection.getDouble("offset-y"), bannerSection.getDouble("offset-z"));
            if (loc != null) {
                int topCount = bannerSection.getInt("top-count", 10);
                playtimeBanner.setup(loc, topCount);
            }
        }
    }
}
