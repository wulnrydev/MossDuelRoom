package dev.wulnrydev.mossduelroom.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Konum <-> yapilandirma donusumleri.
 */
public final class LocationUtil {

    private LocationUtil() {
    }

    /**
     * Yapilandirma bolumunden konum okur.
     *
     * @return dunya yuklu degilse veya bolum eksikse null
     */
    public static Location read(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world");
        if (worldName == null || worldName.isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch"));
    }

    /** Konumu yapilandirmaya yazar (kaydetme islemi cagirana aittir). */
    public static void write(ConfigurationSection section, Location location) {
        section.set("world", location.getWorld() == null ? null : location.getWorld().getName());
        section.set("x", round(location.getX()));
        section.set("y", round(location.getY()));
        section.set("z", round(location.getZ()));
        section.set("yaw", round(location.getYaw()));
        section.set("pitch", round(location.getPitch()));
    }

    /** Mesajlarda gosterilecek kisa ozet. */
    public static String describe(Location location) {
        if (location == null) {
            return null;
        }
        return (int) location.getX() + ", " + (int) location.getY() + ", " + (int) location.getZ();
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
