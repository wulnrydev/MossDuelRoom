package dev.wulnrydev.mossduelroom.listener;

import dev.wulnrydev.mossduelroom.MossDuelRoom;
import dev.wulnrydev.mossduelroom.arena.ArenaManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Baglanti olaylari.
 * <p>
 * Cikis olayinda oyuncuya ait tum bellek girdileri (oturum, bolge kaydi,
 * kose secimi, mesaj bekleme suresi) silinir. Uzun sureli calisan sunucularda
 * bellek sizintisini onlemedeki en kritik adim budur.
 */
public final class ConnectionListener implements Listener {

    private final MossDuelRoom plugin;
    private final ArenaManager arena;

    public ConnectionListener(MossDuelRoom plugin) {
        this.plugin = plugin;
        this.arena = plugin.getArenaManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Oyuncu arenanin icindeyken cikis yapmis olabilir.
        arena.handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        arena.handleQuit(event.getPlayer());
        plugin.getDuelCommand().clearSelection(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        // Duelloda olen oyuncu dogrudan duel spawn'ina doner.
        Location spawn = arena.consumeRespawnLocation(event.getPlayer().getUniqueId());
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }
}
