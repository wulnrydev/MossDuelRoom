package dev.wulnrydev.mossduelroom;

import dev.wulnrydev.mossduelroom.arena.ArenaManager;
import dev.wulnrydev.mossduelroom.command.DuelCommand;
import dev.wulnrydev.mossduelroom.config.ConfigManager;
import dev.wulnrydev.mossduelroom.config.MessageManager;
import dev.wulnrydev.mossduelroom.gui.LootGuiManager;
import dev.wulnrydev.mossduelroom.listener.ArenaMovementListener;
import dev.wulnrydev.mossduelroom.listener.ConnectionListener;
import dev.wulnrydev.mossduelroom.listener.DuelCombatListener;
import dev.wulnrydev.mossduelroom.listener.LootGuiListener;
import dev.wulnrydev.mossduelroom.util.TaskScheduler;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MossDuelRoom — BoxMining 1v1 duel odasi.
 *
 * <p>Isleyis: arenaya tam iki oyuncu girer, geri sayim biter, kapilar kapanir.
 * Kaybedenin esyalari yere dusmez; kazananin ekraninda sanal bir sandik acilir.
 * Sure dolunca sandik kapanir, kazanan spawn'a gonderilir, kapilar acilir ve
 * o duelloya ait tum veri bellekten silinir.</p>
 *
 * @author wulnrydev
 */
public final class MossDuelRoom extends JavaPlugin {

    private TaskScheduler taskScheduler;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private LootGuiManager lootGuiManager;
    private ArenaManager arenaManager;
    private DuelCommand duelCommand;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();

        // Varsayilan dosyalar (yoksa) olusturulur.
        saveDefaultConfig();

        // Sira onemlidir: alt bilesenler bir ustundekine baglidir.
        this.taskScheduler = new TaskScheduler(this);

        this.configManager = new ConfigManager(this);
        this.configManager.load();

        this.messageManager = new MessageManager(this);
        this.messageManager.load();

        this.lootGuiManager = new LootGuiManager(this);
        this.arenaManager = new ArenaManager(this);
        this.duelCommand = new DuelCommand(this);

        registerCommands();
        registerListeners();

        this.arenaManager.start();

        getLogger().info("MossDuelRoom v" + getDescription().getVersion() + " etkinlestirildi ("
                + (System.currentTimeMillis() - start) + "ms).");
        getLogger().info("Zamanlayici: " + (taskScheduler.isFolia() ? "Folia bolge tabanli" : "Paper/Spigot"));
        if (!configManager.isConfigured()) {
            getLogger().warning("Arena veya spawn tanimlanmamis. Kurulum icin: /1v1 bilgi");
        }
    }

    @Override
    public void onDisable() {
        // Kapilar acilir, sandiklar imha edilir, bellek temizlenir.
        if (arenaManager != null) {
            arenaManager.shutdown();
        }
        getLogger().info("MossDuelRoom devre disi birakildi.");
    }

    private void registerCommands() {
        PluginCommand command = getCommand("1v1");
        if (command == null) {
            getLogger().severe("'1v1' komutu plugin.yml icinde bulunamadi, komutlar devre disi.");
            return;
        }
        command.setExecutor(duelCommand);
        command.setTabCompleter(duelCommand);
    }

    private void registerListeners() {
        PluginManager manager = getServer().getPluginManager();
        manager.registerEvents(new ArenaMovementListener(arenaManager), this);
        manager.registerEvents(new ConnectionListener(this), this);
        manager.registerEvents(new DuelCombatListener(this), this);
        manager.registerEvents(new LootGuiListener(this), this);
    }

    /** Yalnizca settings.debug acikken konsola yazar. */
    public void debug(String message) {
        if (configManager != null && configManager.isDebug()) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    // ------------------------------------------------------------------
    // Bilesen erisimcileri
    // ------------------------------------------------------------------

    public TaskScheduler getTaskScheduler() {
        return taskScheduler;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public LootGuiManager getLootGuiManager() {
        return lootGuiManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public DuelCommand getDuelCommand() {
        return duelCommand;
    }
}
