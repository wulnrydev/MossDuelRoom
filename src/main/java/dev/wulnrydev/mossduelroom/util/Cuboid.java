package dev.wulnrydev.mossduelroom.util;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Iki capraz kose ile tanimlanan, degismez (immutable) kup bolge.
 * <p>
 * Bolge kontrolu tamamen ilkel (primitive) int karsilastirmasi ile yapilir;
 * Location nesnesi olusturulmaz. Bu sayede PlayerMoveEvent gibi saniyede
 * binlerce kez tetiklenen olaylarda dahi olculebilir bir maliyeti yoktur.
 */
public final class Cuboid {

    /**
     * Bolgedeki her blok koordinati icin cagrilan hafif geri cagirim.
     * Location/Block listesi olusturmadan iterasyon saglar.
     */
    @FunctionalInterface
    public interface BlockVisitor {
        void visit(int x, int y, int z);
    }

    private final String world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public Cuboid(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    /**
     * Verilen konumun bolge icinde olup olmadigini dondurur.
     * Dunya karsilastirmasi isim uzerinden yapilir (World nesnesi cozulmez).
     */
    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getName().equals(world)) {
            return false;
        }
        return contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    /** Bolgedeki tum blok koordinatlarini gezer. */
    public void forEach(BlockVisitor visitor) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    visitor.visit(x, y, z);
                }
            }
        }
    }

    /** Bolgedeki toplam blok sayisi. */
    public int volume() {
        return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public String getWorld() {
        return world;
    }

    /** Yapilandirmada gosterilecek okunakli ozet. */
    public String describe() {
        return "(" + minX + ", " + minY + ", " + minZ + ") -> (" + maxX + ", " + maxY + ", " + maxZ + ")";
    }

    // ------------------------------------------------------------------
    // Yapilandirma donusumleri
    // ------------------------------------------------------------------

    /**
     * {@code pos1} ve {@code pos2} alt bolumlerini iceren bir bolumden Cuboid okur.
     *
     * @return okunamazsa null
     */
    public static Cuboid fromSection(ConfigurationSection section, String world) {
        if (section == null || world == null) {
            return null;
        }
        ConfigurationSection first = section.getConfigurationSection("pos1");
        ConfigurationSection second = section.getConfigurationSection("pos2");
        if (first == null || second == null) {
            return null;
        }
        return new Cuboid(world,
                first.getInt("x"), first.getInt("y"), first.getInt("z"),
                second.getInt("x"), second.getInt("y"), second.getInt("z"));
    }

    /** Cuboid'i verilen bolume {@code pos1}/{@code pos2} olarak yazar. */
    public void writeTo(ConfigurationSection section) {
        section.set("pos1.x", minX);
        section.set("pos1.y", minY);
        section.set("pos1.z", minZ);
        section.set("pos2.x", maxX);
        section.set("pos2.y", maxY);
        section.set("pos2.z", maxZ);
    }
}
