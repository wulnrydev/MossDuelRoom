package dev.wulnrydev.mossduelroom.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Yapilandirmadan okunan tek bir ses tanimi: {@code SES_ADI:sesSeviyesi:perde}.
 * <p>
 * Ses adi eklenti acilisinda bir kez cozulur; oyun icinde her calisimda
 * {@code Sound.valueOf} cagrilmaz. Gecersiz tanimlar sessizce devre disi kalir.
 */
public final class SoundEntry {

    private static final SoundEntry DISABLED = new SoundEntry(null, 0.0F, 0.0F);

    private final Sound sound;
    private final float volume;
    private final float pitch;

    private SoundEntry(Sound sound, float volume, float pitch) {
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
    }

    /**
     * "ENTITY_PLAYER_LEVELUP:1.0:1.2" bicimindeki tanimi cozer.
     * Bos/gecersiz girdilerde devre disi bir tanim doner.
     */
    public static SoundEntry parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return DISABLED;
        }
        String[] parts = raw.split(":");
        Sound resolved;
        try {
            resolved = Sound.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return DISABLED;
        }
        float volume = parts.length > 1 ? parseFloat(parts[1], 1.0F) : 1.0F;
        float pitch = parts.length > 2 ? parseFloat(parts[2], 1.0F) : 1.0F;
        return new SoundEntry(resolved, volume, pitch);
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /** Sesi oyuncuya calar. Tanim gecersizse hicbir sey yapmaz. */
    public void play(Player player) {
        if (sound == null || player == null || !player.isOnline()) {
            return;
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
