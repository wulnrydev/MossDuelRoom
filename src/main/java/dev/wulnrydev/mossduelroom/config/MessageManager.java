package dev.wulnrydev.mossduelroom.config;

import dev.wulnrydev.mossduelroom.MossDuelRoom;
import dev.wulnrydev.mossduelroom.util.ColorUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * messages.yml yoneticisi.
 * <p>
 * Tum metinler dosya okundugu anda renklendirilip onbellege alinir; oyun ici
 * her mesaj gonderiminde YAML agacina inilmez ve renk cozumlemesi tekrarlanmaz.
 * Yer tutucular (%player%, %seconds% ...) onbellekten sonra uygulanir.
 */
public final class MessageManager {

    private static final String FILE_NAME = "messages.yml";

    private final MossDuelRoom plugin;
    private final Map<String, String> stringCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> listCache = new ConcurrentHashMap<>();

    private YamlConfiguration messages;
    private String prefix = "";

    public MessageManager(MossDuelRoom plugin) {
        this.plugin = plugin;
    }

    /** Dil dosyasini diskten okur, eksik anahtarlar jar icindeki varsayilanlarla tamamlanir. */
    public void load() {
        stringCache.clear();
        listCache.clear();

        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }

        messages = YamlConfiguration.loadConfiguration(file);

        // Jar icindeki surum varsayilan olarak baglanir; boylece guncellemelerde
        // eklenen yeni anahtarlar kullanicinin dosyasi eski olsa bile calisir.
        try (InputStream stream = plugin.getResource(FILE_NAME)) {
            if (stream != null) {
                messages.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)));
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Varsayilan messages.yml okunamadi.", exception);
        }

        prefix = ColorUtil.colorize(messages.getString("prefix", ""));
    }

    // ------------------------------------------------------------------
    // Okuma
    // ------------------------------------------------------------------

    /** Renklendirilmis, yer tutuculari uygulanmis tek satirlik metin dondurur. */
    public String get(String path, String... placeholders) {
        String cached = stringCache.computeIfAbsent(path, key -> {
            String raw = messages == null ? null : messages.getString(key);
            if (raw == null) {
                return "";
            }
            return ColorUtil.colorize(raw.replace("%prefix%", prefix));
        });
        return applyPlaceholders(cached, placeholders);
    }

    /** Renklendirilmis satir listesi dondurur (yardim, bilgi menuleri icin). */
    public List<String> getList(String path, String... placeholders) {
        List<String> cached = listCache.computeIfAbsent(path, key -> {
            if (messages == null) {
                return Collections.emptyList();
            }
            List<String> raw = messages.getStringList(key);
            if (raw.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> colored = new ArrayList<>(raw.size());
            for (String line : raw) {
                colored.add(ColorUtil.colorize(line.replace("%prefix%", prefix)));
            }
            return Collections.unmodifiableList(colored);
        });

        if (placeholders.length == 0 || cached.isEmpty()) {
            return cached;
        }
        List<String> result = new ArrayList<>(cached.size());
        for (String line : cached) {
            result.add(applyPlaceholders(line, placeholders));
        }
        return result;
    }

    /** Ham (renklendirilmemis) degeri dondurur; komut metinleri icin kullanilir. */
    public String raw(String path, String fallback) {
        if (messages == null) {
            return fallback;
        }
        return messages.getString(path, fallback);
    }

    // ------------------------------------------------------------------
    // Gonderim
    // ------------------------------------------------------------------

    /** Mesaji gonderir. Deger bos ise (kullanici kapatmissa) hicbir sey yapmaz. */
    public void send(CommandSender sender, String path, String... placeholders) {
        if (sender == null) {
            return;
        }
        String message = get(path, placeholders);
        if (message.isEmpty()) {
            return;
        }
        sender.sendMessage(message);
    }

    /** Satir listesini gonderir. */
    public void sendList(CommandSender sender, String path, String... placeholders) {
        if (sender == null) {
            return;
        }
        for (String line : getList(path, placeholders)) {
            sender.sendMessage(line);
        }
    }

    /**
     * Ekran ortasi baslik gonderir. Iki metin de bos ise gonderim yapilmaz.
     *
     * @param fadeIn  giris suresi (tick)
     * @param stay    ekranda kalma suresi (tick)
     * @param fadeOut cikis suresi (tick)
     */
    public void sendTitle(Player player, String titlePath, String subtitlePath,
                          int fadeIn, int stay, int fadeOut, String... placeholders) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String title = titlePath == null ? "" : get(titlePath, placeholders);
        String subtitle = subtitlePath == null ? "" : get(subtitlePath, placeholders);
        if (title.isEmpty() && subtitle.isEmpty()) {
            return;
        }
        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }

    /** Envanter ustu (action bar) mesaji gonderir. */
    public void sendActionBar(Player player, String path, String... placeholders) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String message = get(path, placeholders);
        if (message.isEmpty()) {
            return;
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    /**
     * Uzerine gelince aciklama gosteren, tiklaninca komut calistiran mesaj gonderir.
     *
     * @param path        govde metni anahtari
     * @param hoverPath   fare uzerinde gosterilecek metin anahtari
     * @param commandPath calistirilacak komutun anahtari (ham deger)
     */
    public void sendClickable(Player player, String path, String hoverPath, String commandPath,
                              String... placeholders) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String body = get(path, placeholders);
        if (body.isEmpty()) {
            return;
        }

        TextComponent component = new TextComponent(TextComponent.fromLegacyText(body));

        String hover = hoverPath == null ? "" : get(hoverPath, placeholders);
        if (!hover.isEmpty()) {
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new Text(TextComponent.fromLegacyText(hover))));
        }

        String command = raw(commandPath, "");
        if (!command.isEmpty()) {
            component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        }

        player.spigot().sendMessage(component);
    }

    // ------------------------------------------------------------------
    // Yardimcilar
    // ------------------------------------------------------------------

    /** Yer tutucular ikili gelir: ("%player%", "wulnrydev", "%seconds%", "30") */
    private String applyPlaceholders(String message, String... placeholders) {
        if (message.isEmpty() || placeholders == null || placeholders.length < 2) {
            return message;
        }
        String result = message;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace(placeholders[i], placeholders[i + 1]);
        }
        return result;
    }
}
