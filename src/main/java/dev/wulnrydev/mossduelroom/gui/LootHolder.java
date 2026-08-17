package dev.wulnrydev.mossduelroom.gui;

import dev.wulnrydev.mossduelroom.arena.DuelSession;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Ganimet sandigini isaretleyen tasiyici.
 * <p>
 * Envanterin bize ait olup olmadigi baslik metni karsilastirarak degil, bu
 * tasiyici uzerinden anlasilir; boylece oyuncu adi/baslik degisse bile
 * dinleyici dogru calisir ve karsilastirma maliyeti sifira yakindir.
 */
public final class LootHolder implements InventoryHolder {

    private final DuelSession session;
    private Inventory inventory;

    public LootHolder(DuelSession session) {
        this.session = session;
    }

    public DuelSession getSession() {
        return session;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
