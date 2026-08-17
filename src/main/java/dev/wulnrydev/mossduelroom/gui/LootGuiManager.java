package dev.wulnrydev.mossduelroom.gui;

import dev.wulnrydev.mossduelroom.MossDuelRoom;
import dev.wulnrydev.mossduelroom.arena.DuelSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ganimet sandigi (GUI) yoneticisi.
 * <p>
 * Sandik icerigi tek bir {@link Inventory} nesnesinde tutulur; oyuncu sandigi
 * kapatip {@code /1v1 sandik} ile tekrar actiginda ayni nesne acilir, bu yuzden
 * icerigi kaydetmek/geri yuklemek icin ek bir islem gerekmez.
 * <p>
 * Tum metotlar ana akista cagrilmalidir (Bukkit envanter API'si thread-safe degildir).
 */
public final class LootGuiManager {

    private final MossDuelRoom plugin;

    public LootGuiManager(MossDuelRoom plugin) {
        this.plugin = plugin;
    }

    /**
     * Olen oyuncunun esyalarindan sanal sandigi olusturur ve oturuma baglar.
     *
     * @param session oturum
     * @param loot    olen oyuncudan toplanan esyalar
     */
    public Inventory create(DuelSession session, List<ItemStack> loot) {
        int size = plugin.getConfigManager().getLootGuiRows() * 9;
        String title = plugin.getMessageManager().get("loot.gui-title",
                "%player%", session.getLoserName() == null ? "?" : session.getLoserName());

        LootHolder holder = new LootHolder(session);
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);

        // addItem esyalari birlestirerek yerlestirir; sigmayanlar geri doner.
        Map<Integer, ItemStack> overflow = inventory.addItem(loot.toArray(new ItemStack[0]));
        if (!overflow.isEmpty()) {
            plugin.getLogger().warning("Ganimet sandigina sigmayan " + overflow.size()
                    + " esya yigini var. settings.loot-gui-rows degerini arttirin.");
        }

        session.setLootInventory(inventory);
        return inventory;
    }

    /**
     * Sandigi oyuncuya acar.
     *
     * @return sandik mevcut degilse false
     */
    public boolean open(Player player, DuelSession session) {
        Inventory inventory = session.getLootInventory();
        if (inventory == null || player == null || !player.isOnline()) {
            return false;
        }
        player.openInventory(inventory);
        return true;
    }

    /**
     * Sandigi kapatir, icerigini siler ve referansi birakir.
     * Bellekte oturuma ait esya verisi kalmaz.
     */
    public void dispose(DuelSession session) {
        Inventory inventory = session.getLootInventory();
        if (inventory == null) {
            return;
        }
        // Kopya uzerinde donuyoruz: closeInventory viewer listesini degistirir.
        for (HumanEntity viewer : new ArrayList<>(inventory.getViewers())) {
            viewer.closeInventory();
        }
        inventory.clear();
        session.setLootInventory(null);
    }
}
