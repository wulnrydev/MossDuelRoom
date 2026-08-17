package dev.wulnrydev.mossduelroom.listener;

import dev.wulnrydev.mossduelroom.MossDuelRoom;
import dev.wulnrydev.mossduelroom.arena.DuelSession;
import dev.wulnrydev.mossduelroom.arena.DuelState;
import dev.wulnrydev.mossduelroom.config.MessageManager;
import dev.wulnrydev.mossduelroom.gui.LootHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Ganimet sandigi kurallari.
 * <p>
 * Sandiktan esya <b>alinabilir</b>, sandiga esya <b>konulamaz</b>. Boylece
 * oyuncu kendi esyalarini yanlislikla sandikta birakip sure dolunca
 * kaybetmez.
 */
public final class LootGuiListener implements Listener {

    private final MessageManager messages;

    public LootGuiListener(MossDuelRoom plugin) {
        this.messages = plugin.getMessageManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof LootHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        DuelSession session = holder.getSession();

        // Sure dolduysa veya sandik sahibi degilse hicbir etkilesime izin verilmez.
        if (session.isEnded() || session.getState() != DuelState.LOOT
                || !session.isWinner(player.getUniqueId())) {
            event.setCancelled(true);
            player.closeInventory();
            messages.send(player, "loot.no-access");
            return;
        }

        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            // Pencerenin disina tiklama: esya dusurulmesin.
            event.setCancelled(true);
            return;
        }

        InventoryAction action = event.getAction();

        if (clicked.equals(top)) {
            // Sandiga esya koymaya calisan islemler engellenir.
            switch (action) {
                case PLACE_ONE:
                case PLACE_SOME:
                case PLACE_ALL:
                case SWAP_WITH_CURSOR:
                case HOTBAR_SWAP:
                case HOTBAR_MOVE_AND_READD:
                    event.setCancelled(true);
                    messages.send(player, "loot.cannot-insert");
                    break;
                default:
                    // PICKUP_*, MOVE_TO_OTHER_INVENTORY, COLLECT_TO_CURSOR: alma islemi, serbest.
                    break;
            }
            return;
        }

        // Oyuncunun kendi envanterinden shift + tik ile sandiga gonderme denemesi.
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
            messages.send(player, "loot.cannot-insert");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof LootHolder)) {
            return;
        }
        // Surukleme ile sandiga esya dagitilmasi engellenir.
        int topSize = top.getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    messages.send(player, "loot.cannot-insert");
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof LootHolder holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        DuelSession session = holder.getSession();
        if (session.isEnded() || session.getState() != DuelState.LOOT) {
            return;
        }
        // Sandik icerigi ayni Inventory nesnesinde kalir; kaydetmeye gerek yoktur.
        messages.send(player, "loot.reopen-hint",
                "%seconds%", String.valueOf(session.getLootCountdown()));
    }
}
