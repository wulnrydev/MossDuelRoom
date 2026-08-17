package dev.wulnrydev.mossduelroom.listener;

import dev.wulnrydev.mossduelroom.MossDuelRoom;
import dev.wulnrydev.mossduelroom.arena.ArenaManager;
import dev.wulnrydev.mossduelroom.arena.DuelSession;
import dev.wulnrydev.mossduelroom.arena.DuelState;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Duello savas kurallari: olum, ganimet toplama ve hasar korumalari.
 */
public final class DuelCombatListener implements Listener {

    private final MossDuelRoom plugin;
    private final ArenaManager arena;

    public DuelCombatListener(MossDuelRoom plugin) {
        this.plugin = plugin;
        this.arena = plugin.getArenaManager();
    }

    /**
     * Olen duello oyuncusunun esyalari yere dusmez; sanal sandiga aktarilir.
     * Oncelik HIGHEST: diger eklentiler drop listesini duzenledikten sonra calisir.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        DuelSession session = arena.getSession(victim.getUniqueId());
        if (session == null) {
            return;
        }

        // Ganimet suresi devam ederken kazanan oldu: oturum kapanir,
        // esyalarina karisilmaz (normal sekilde duser).
        if (session.getState() == DuelState.LOOT && session.isWinner(victim.getUniqueId())) {
            arena.handleWinnerDeath(victim);
            return;
        }

        if (session.getState() != DuelState.FIGHTING) {
            return;
        }

        List<ItemStack> loot = new ArrayList<>(45);
        for (ItemStack item : event.getDrops()) {
            if (isValid(item)) {
                loot.add(item.clone());
            }
        }
        event.getDrops().clear();

        // Sunucuda keepInventory acikken esyalar drop listesine girmez;
        // bu durumda dogrudan envanterden alinip temizlenir.
        if (event.getKeepInventory()) {
            for (ItemStack item : victim.getInventory().getContents()) {
                if (isValid(item)) {
                    loot.add(item.clone());
                }
            }
            victim.getInventory().clear();
        }

        if (!plugin.getConfigManager().isDropExp()) {
            event.setDroppedExp(0);
        }

        arena.handleDuelDeath(victim, loot);
    }

    /**
     * Ganimet suresi boyunca kazanan oyuncu arenada savunmasiz kalmasin diye
     * hasar almaz (yapilandirmadan kapatilabilir).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (arena.isProtectedWinner(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Duello disindaki oyuncularin duellocularla etkilesimini engeller
     * (ok, iksir, yakin dovus dahil).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.getConfigManager().isProtectDuel()) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }

        boolean victimFighting = arena.isFighting(victim.getUniqueId());
        boolean attackerFighting = arena.isFighting(attacker.getUniqueId());

        // Yalnizca taraflardan biri duelloda ise etkilesime izin verilmez.
        if (victimFighting != attackerFighting) {
            event.setCancelled(true);
        }
    }

    /** Mermi ise atan oyuncuyu, degilse dogrudan saldiran oyuncuyu dondurur. */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private boolean isValid(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getAmount() > 0;
    }
}
