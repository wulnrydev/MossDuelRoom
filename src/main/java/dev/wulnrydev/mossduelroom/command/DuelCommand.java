package dev.wulnrydev.mossduelroom.command;

import dev.wulnrydev.mossduelroom.MossDuelRoom;
import dev.wulnrydev.mossduelroom.arena.GateRegion;
import dev.wulnrydev.mossduelroom.config.ConfigManager;
import dev.wulnrydev.mossduelroom.config.MessageManager;
import dev.wulnrydev.mossduelroom.util.Cuboid;
import dev.wulnrydev.mossduelroom.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /1v1} komut isleyicisi.
 * <p>
 * Oyuncu komutlari: {@code berabere}, {@code sandik}, {@code yardim}.
 * Yonetici komutlari kurulum icindir ve {@code mossduelroom.admin} ister.
 */
public final class DuelCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "mossduelroom.admin";
    private static final String USE_PERMISSION = "mossduelroom.use";

    /** Yonetici kose secimleri: [0] = pos1, [1] = pos2. */
    private final Map<UUID, Location[]> selections = new ConcurrentHashMap<>();

    private static final List<String> PLAYER_SUBCOMMANDS =
            Arrays.asList("berabere", "sandik", "yardim");
    private static final List<String> ADMIN_SUBCOMMANDS =
            Arrays.asList("pos1", "pos2", "setarena", "addgate", "cleargates", "setspawn", "bilgi", "reload");
    private static final List<String> COMMON_GATE_BLOCKS =
            Arrays.asList("BEDROCK", "BARRIER", "OBSIDIAN", "GLASS", "STONE", "IRON_BARS");

    private final MossDuelRoom plugin;
    private final MessageManager messages;
    private final ConfigManager config;

    public DuelCommand(MossDuelRoom plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessageManager();
        this.config = plugin.getConfigManager();
    }

    /** Oyuncu ciktiginda kose secimini bellekten siler. */
    public void clearSelection(UUID id) {
        selections.remove(id);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "yardim":
            case "yardım":
            case "help":
            case "?":
                sendHelp(sender);
                return true;

            case "berabere":
            case "draw":
                handleDraw(sender);
                return true;

            case "sandik":
            case "sandık":
            case "chest":
            case "loot":
                handleLoot(sender);
                return true;

            case "reload":
                handleReload(sender);
                return true;

            case "pos1":
                handlePosition(sender, 0, "pos1");
                return true;

            case "pos2":
                handlePosition(sender, 1, "pos2");
                return true;

            case "setarena":
                handleSetArena(sender);
                return true;

            case "addgate":
                handleAddGate(sender, args.length > 1 ? args[1] : "BEDROCK");
                return true;

            case "cleargates":
                handleClearGates(sender);
                return true;

            case "setspawn":
                handleSetSpawn(sender);
                return true;

            case "bilgi":
            case "info":
                handleInfo(sender);
                return true;

            default:
                messages.send(sender, "general.unknown-command");
                return true;
        }
    }

    // ==================================================================
    // Oyuncu komutlari
    // ==================================================================

    private void sendHelp(CommandSender sender) {
        boolean admin = sender.hasPermission(ADMIN_PERMISSION);
        for (String line : messages.getList("help.lines")) {
            if (!line.equals("%admin%")) {
                sender.sendMessage(line);
                continue;
            }
            if (admin) {
                for (String adminLine : messages.getList("help.admin-lines")) {
                    sender.sendMessage(adminLine);
                }
            }
        }
    }

    private void handleDraw(CommandSender sender) {
        Player player = requirePlayer(sender, USE_PERMISSION);
        if (player == null) {
            return;
        }
        plugin.getArenaManager().handleDrawCommand(player);
    }

    private void handleLoot(CommandSender sender) {
        Player player = requirePlayer(sender, USE_PERMISSION);
        if (player == null) {
            return;
        }
        plugin.getArenaManager().handleLootCommand(player);
    }

    // ==================================================================
    // Yonetici komutlari
    // ==================================================================

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            messages.send(sender, "general.no-permission");
            return;
        }
        long start = System.currentTimeMillis();
        config.load();
        messages.load();
        messages.send(sender, "general.reloaded",
                "%time%", String.valueOf(System.currentTimeMillis() - start));
    }

    private void handlePosition(CommandSender sender, int index, String label) {
        Player player = requirePlayer(sender, ADMIN_PERMISSION);
        if (player == null) {
            return;
        }
        Location location = player.getLocation();
        selections.computeIfAbsent(player.getUniqueId(), key -> new Location[2])[index] = location;

        messages.send(player, "setup.pos-set",
                "%pos%", label,
                "%x%", String.valueOf(location.getBlockX()),
                "%y%", String.valueOf(location.getBlockY()),
                "%z%", String.valueOf(location.getBlockZ()));
    }

    private void handleSetArena(CommandSender sender) {
        Player player = requirePlayer(sender, ADMIN_PERMISSION);
        if (player == null) {
            return;
        }
        Cuboid region = buildSelection(player);
        if (region == null) {
            return;
        }
        config.saveArena(region);
        messages.send(player, "setup.arena-set", "%blocks%", String.valueOf(region.volume()));
    }

    private void handleAddGate(CommandSender sender, String materialName) {
        Player player = requirePlayer(sender, ADMIN_PERMISSION);
        if (player == null) {
            return;
        }

        Material material = Material.matchMaterial(materialName.toUpperCase(Locale.ROOT));
        if (material == null || !material.isBlock()) {
            messages.send(player, "setup.invalid-material", "%material%", materialName);
            return;
        }

        Cuboid region = buildSelection(player);
        if (region == null) {
            return;
        }

        String name = config.addGate(region, material);
        messages.send(player, "setup.gate-added",
                "%name%", name,
                "%material%", material.name(),
                "%blocks%", String.valueOf(region.volume()));
    }

    private void handleClearGates(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            messages.send(sender, "general.no-permission");
            return;
        }
        config.clearGates();
        messages.send(sender, "setup.gates-cleared");
    }

    private void handleSetSpawn(CommandSender sender) {
        Player player = requirePlayer(sender, ADMIN_PERMISSION);
        if (player == null) {
            return;
        }
        Location location = player.getLocation();
        config.saveSpawn(location);
        messages.send(player, "setup.spawn-set",
                "%x%", String.valueOf(location.getBlockX()),
                "%y%", String.valueOf(location.getBlockY()),
                "%z%", String.valueOf(location.getBlockZ()));
    }

    private void handleInfo(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            messages.send(sender, "general.no-permission");
            return;
        }

        String none = messages.raw("setup.info-none", "Tanimlanmamis");
        Cuboid arena = config.getArenaRegion();
        Location spawn = config.getSpawn();

        StringBuilder gates = new StringBuilder();
        for (GateRegion gate : config.getGates()) {
            if (gates.length() > 0) {
                gates.append(", ");
            }
            gates.append(gate.getName()).append(" (").append(gate.getMaterial().name())
                    .append(", ").append(gate.getRegion().volume()).append(")");
        }

        messages.sendList(sender, "setup.info",
                "%world%", arena == null ? none : arena.getWorld(),
                "%arena%", arena == null ? none : arena.describe(),
                "%gates%", gates.length() == 0 ? none : gates.toString(),
                "%spawn%", spawn == null ? none : LocationUtil.describe(spawn),
                "%status%", config.isConfigured()
                        ? messages.raw("setup.info-ready", "Hazir")
                        : messages.raw("setup.info-missing", "Eksik yapilandirma"));
    }

    // ==================================================================
    // Yardimcilar
    // ==================================================================

    /** Komutu oyuncunun ve yetkilinin calistirdigini dogrular. */
    private Player requirePlayer(CommandSender sender, String permission) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return null;
        }
        if (!player.hasPermission(permission)) {
            messages.send(player, "general.no-permission");
            return null;
        }
        return player;
    }

    /** Secili iki koseden bolge olusturur; eksik/hatali secimde null doner. */
    private Cuboid buildSelection(Player player) {
        Location[] selection = selections.get(player.getUniqueId());
        if (selection == null || selection[0] == null || selection[1] == null) {
            messages.send(player, "setup.need-positions");
            return null;
        }
        if (selection[0].getWorld() == null || selection[1].getWorld() == null
                || !selection[0].getWorld().equals(selection[1].getWorld())) {
            messages.send(player, "setup.different-world");
            return null;
        }
        return new Cuboid(selection[0].getWorld().getName(),
                selection[0].getBlockX(), selection[0].getBlockY(), selection[0].getBlockZ(),
                selection[1].getBlockX(), selection[1].getBlockY(), selection[1].getBlockZ());
    }

    // ==================================================================
    // Tamamlama
    // ==================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(PLAYER_SUBCOMMANDS);
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                options.addAll(ADMIN_SUBCOMMANDS);
            }
            return filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("addgate")
                && sender.hasPermission(ADMIN_PERMISSION)) {
            return filter(COMMON_GATE_BLOCKS, args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>(options.size());
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
