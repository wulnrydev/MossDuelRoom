package dev.wulnrydev.mossduelroom.arena;

import dev.wulnrydev.mossduelroom.util.Cuboid;
import org.bukkit.Material;

/**
 * Savas basladiginda doldurulacak, bittiginde eski haline dondurulecek
 * tek bir kapi bolgesi.
 */
public final class GateRegion {

    private final String name;
    private final Cuboid region;
    private final Material material;

    public GateRegion(String name, Cuboid region, Material material) {
        this.name = name;
        this.region = region;
        this.material = material;
    }

    public String getName() {
        return name;
    }

    public Cuboid getRegion() {
        return region;
    }

    public Material getMaterial() {
        return material;
    }
}
