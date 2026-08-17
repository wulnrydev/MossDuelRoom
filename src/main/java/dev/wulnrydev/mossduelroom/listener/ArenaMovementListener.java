package dev.wulnrydev.mossduelroom.listener;

import dev.wulnrydev.mossduelroom.arena.ArenaManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Arena bolgesine giris/cikis algilayicisi.
 * <p>
 * PlayerMoveEvent sunucunun en sik tetiklenen olayidir. Bu yuzden ilk kontrol
 * blok koordinati karsilastirmasidir: oyuncu ayni blogun icinde donuyorsa
 * (bakis acisi degisimi, kucuk adimlar) hicbir is yapilmadan cikilir.
 * <p>
 * PlayerTeleportEvent, PlayerMoveEvent'ten turese de kendi dinleyici listesine
 * sahiptir; bu yuzden ayrica kaydedilmistir (ender inci, komut vb. giriler).
 */
public final class ArenaMovementListener implements Listener {

    private final ArenaManager arena;

    public ArenaMovementListener(ArenaManager arena) {
        this.arena = arena;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        handle(event, event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        handle(event, event.getFrom(), event.getTo());
    }

    private void handle(PlayerMoveEvent event, Location from, Location to) {
        if (to == null) {
            return;
        }
        // Ayni blok icindeki hareketler yok sayilir (en sik durum, en ucuz kontrol).
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && from.getWorld() == to.getWorld()) {
            return;
        }
        arena.handleMovement(event.getPlayer(), to, event);
    }
}
