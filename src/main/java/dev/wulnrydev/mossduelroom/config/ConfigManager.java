package dev.wulnrydev.mossduelroom.config;

import dev.wulnrydev.mossduelroom.MossDuelRoom;
import dev.wulnrydev.mossduelroom.arena.GateRegion;
import dev.wulnrydev.mossduelroom.util.Cuboid;
import dev.wulnrydev.mossduelroom.util.LocationUtil;
import dev.wulnrydev.mossduelroom.util.SoundEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * config.yml okuyucusu.
 * <p>
 * Tum degerler acilis/reload aninda bir kez okunup alanlara alinir. Oyun
 * icindeki sicak yollarda (hareket olayi, sayac tick'i) hicbir zaman YAML
 * agacina gidilmez.
 */
public final class ConfigManager {

    private final MossDuelRoom plugin;

    // --- Ayarlar -----------------------------------------------------------
    private int countdownSeconds = 10;
    private Set<Integer> countdownAnnounceSeconds = Collections.emptySet();
    private int lootSeconds = 30;
    private int drawRequestTimeout = 20;
    private int lootGuiRows = 6;
    private boolean blockSpectators = true;
    private boolean protectDuel = true;
    private boolean invulnerableWinner = true;
    private boolean healOnStart = true;
    private boolean clearEffectsOnStart = true;
    private boolean dropExp = false;
    private boolean gatePhysics = false;
    private boolean punishCombatLog = false;
    private boolean debug = false;

    // --- Bolgeler ----------------------------------------------------------
    private String worldName;
    private Cuboid arenaRegion;
    private Location spawn;
    private List<GateRegion> gates = Collections.emptyList();

    // --- Sesler ------------------------------------------------------------
    private boolean soundsEnabled = true;
    private SoundEntry countdownSound = SoundEntry.parse(null);
    private SoundEntry fightStartSound = SoundEntry.parse(null);
    private SoundEntry winSound = SoundEntry.parse(null);
    private SoundEntry loseSound = SoundEntry.parse(null);
    private SoundEntry drawSound = SoundEntry.parse(null);
    private SoundEntry lootOpenSound = SoundEntry.parse(null);
    private SoundEntry lootExpireSound = SoundEntry.parse(null);

    public ConfigManager(MossDuelRoom plugin) {
        this.plugin = plugin;
    }

    /** config.yml dosyasini diskten okur ve tum onbellegi tazeler. */
    public void load() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        ConfigurationSection settings = config.getConfigurationSection("settings");
        if (settings != null) {
            countdownSeconds = Math.max(1, settings.getInt("countdown-seconds", 10));
            lootSeconds = Math.max(1, settings.getInt("loot-seconds", 30));
            drawRequestTimeout = Math.max(1, settings.getInt("draw-request-timeout", 20));
            lootGuiRows = Math.min(6, Math.max(1, settings.getInt("loot-gui-rows", 6)));
            blockSpectators = settings.getBoolean("block-spectators", true);
            protectDuel = settings.getBoolean("protect-duel", true);
            invulnerableWinner = settings.getBoolean("invulnerable-winner", true);
            healOnStart = settings.getBoolean("heal-on-start", true);
            clearEffectsOnStart = settings.getBoolean("clear-effects-on-start", true);
            dropExp = settings.getBoolean("drop-exp", false);
            gatePhysics = settings.getBoolean("gate-physics", false);
            punishCombatLog = settings.getBoolean("punish-combat-log", false);
            debug = settings.getBoolean("debug", false);

            Set<Integer> announce = new HashSet<>();
            for (int second : settings.getIntegerList("countdown-announce-seconds")) {
                announce.add(second);
            }
            countdownAnnounceSeconds = announce;
        }

        // --- Arena ---------------------------------------------------------
        ConfigurationSection arena = config.getConfigurationSection("arena");
        worldName = arena == null ? null : arena.getString("world");
        arenaRegion = Cuboid.fromSection(arena, worldName);

        // --- Spawn ---------------------------------------------------------
        spawn = LocationUtil.read(config.getConfigurationSection("spawn"));

        // --- Kapilar -------------------------------------------------------
        gates = readGates(config.getConfigurationSection("gates"));

        // --- Sesler --------------------------------------------------------
        ConfigurationSection sounds = config.getConfigurationSection("sounds");
        if (sounds != null) {
            soundsEnabled = sounds.getBoolean("enabled", true);
            countdownSound = SoundEntry.parse(sounds.getString("countdown"));
            fightStartSound = SoundEntry.parse(sounds.getString("fight-start"));
            winSound = SoundEntry.parse(sounds.getString("win"));
            loseSound = SoundEntry.parse(sounds.getString("lose"));
            drawSound = SoundEntry.parse(sounds.getString("draw"));
            lootOpenSound = SoundEntry.parse(sounds.getString("loot-open"));
            lootExpireSound = SoundEntry.parse(sounds.getString("loot-expire"));
        }
    }

    /** Kapi bolumlerini okur; hatali tanimlar uyari ile atlanir. */
    private List<GateRegion> readGates(ConfigurationSection section) {
        if (section == null) {
            return Collections.emptyList();
        }
        List<GateRegion> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection gate = section.getConfigurationSection(key);
            if (gate == null) {
                continue;
            }
            Cuboid region = Cuboid.fromSection(gate, worldName);
            if (region == null) {
                plugin.getLogger().warning("Kapi '" + key + "' icin pos1/pos2 eksik, atlaniyor.");
                continue;
            }
            Material material = Material.matchMaterial(
                    String.valueOf(gate.getString("material", "BEDROCK")).toUpperCase(Locale.ROOT));
            if (material == null || !material.isBlock()) {
                plugin.getLogger().warning("Kapi '" + key + "' icin gecersiz blok turu, BEDROCK kullaniliyor.");
                material = Material.BEDROCK;
            }
            result.add(new GateRegion(key, region, material));
        }
        return Collections.unmodifiableList(result);
    }

    /** Arena ve spawn tanimliysa eklenti calismaya hazirdir. */
    public boolean isConfigured() {
        return arenaRegion != null && spawn != null && getWorld() != null;
    }

    /** Verilen konum arena bolgesinin icinde mi? */
    public boolean isInsideArena(Location location) {
        return arenaRegion != null && arenaRegion.contains(location);
    }

    public World getWorld() {
        return worldName == null ? null : Bukkit.getWorld(worldName);
    }

    // ------------------------------------------------------------------
    // Kurulum komutlari icin yazma islemleri
    // ------------------------------------------------------------------

    /** Arena bolgesini kaydeder ve yapilandirmayi tazeler. */
    public void saveArena(Cuboid region) {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection arena = config.getConfigurationSection("arena");
        if (arena == null) {
            arena = config.createSection("arena");
        }
        arena.set("world", region.getWorld());
        region.writeTo(arena);
        plugin.saveConfig();
        load();
    }

    /**
     * Yeni bir kapi bolgesi ekler.
     *
     * @return olusturulan kapinin adi
     */
    public String addGate(Cuboid region, Material material) {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("gates");
        if (section == null) {
            section = config.createSection("gates");
        }
        int index = 1;
        while (section.contains("gate" + index)) {
            index++;
        }
        String name = "gate" + index;
        ConfigurationSection gate = section.createSection(name);
        gate.set("material", material.name());
        region.writeTo(gate);
        plugin.saveConfig();
        load();
        return name;
    }

    /** Tum kapi tanimlarini siler. */
    public void clearGates() {
        plugin.getConfig().set("gates", null);
        plugin.saveConfig();
        load();
    }

    /** Cikis (spawn) noktasini kaydeder. */
    public void saveSpawn(Location location) {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("spawn");
        if (section == null) {
            section = config.createSection("spawn");
        }
        LocationUtil.write(section, location);
        plugin.saveConfig();
        load();
    }

    // ------------------------------------------------------------------
    // Erisimciler
    // ------------------------------------------------------------------

    public int getCountdownSeconds() {
        return countdownSeconds;
    }

    public boolean shouldAnnounceCountdown(int second) {
        return countdownAnnounceSeconds.contains(second);
    }

    public int getLootSeconds() {
        return lootSeconds;
    }

    public int getDrawRequestTimeout() {
        return drawRequestTimeout;
    }

    public int getLootGuiRows() {
        return lootGuiRows;
    }

    public boolean isBlockSpectators() {
        return blockSpectators;
    }

    public boolean isProtectDuel() {
        return protectDuel;
    }

    public boolean isInvulnerableWinner() {
        return invulnerableWinner;
    }

    public boolean isHealOnStart() {
        return healOnStart;
    }

    public boolean isClearEffectsOnStart() {
        return clearEffectsOnStart;
    }

    public boolean isDropExp() {
        return dropExp;
    }

    public boolean isGatePhysics() {
        return gatePhysics;
    }

    public boolean isPunishCombatLog() {
        return punishCombatLog;
    }

    public boolean isDebug() {
        return debug;
    }

    public Cuboid getArenaRegion() {
        return arenaRegion;
    }

    public Location getSpawn() {
        return spawn == null ? null : spawn.clone();
    }

    public List<GateRegion> getGates() {
        return gates;
    }

    public boolean isSoundsEnabled() {
        return soundsEnabled;
    }

    public SoundEntry getCountdownSound() {
        return countdownSound;
    }

    public SoundEntry getFightStartSound() {
        return fightStartSound;
    }

    public SoundEntry getWinSound() {
        return winSound;
    }

    public SoundEntry getLoseSound() {
        return loseSound;
    }

    public SoundEntry getDrawSound() {
        return drawSound;
    }

    public SoundEntry getLootOpenSound() {
        return lootOpenSound;
    }

    public SoundEntry getLootExpireSound() {
        return lootExpireSound;
    }
}
