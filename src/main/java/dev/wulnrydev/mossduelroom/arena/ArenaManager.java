package dev.wulnrydev.mossduelroom.arena;

import dev.wulnrydev.mossduelroom.MossDuelRoom;
import dev.wulnrydev.mossduelroom.config.ConfigManager;
import dev.wulnrydev.mossduelroom.config.MessageManager;
import dev.wulnrydev.mossduelroom.gui.LootGuiManager;
import dev.wulnrydev.mossduelroom.util.SoundEntry;
import dev.wulnrydev.mossduelroom.util.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Arena ve duello akisinin beynidir.
 * <p>
 * <b>Es zamanlilik modeli:</b> Tum sayaclar tek bir asenkron gorevle (saniyede
 * bir kez) islenir; bu gorev yalnizca eklentinin kendi veri yapilarina dokunur.
 * Dunya, oyuncu ve envanter ile ilgili her islem {@link TaskScheduler} uzerinden
 * ana akisa (Folia'da ilgili bolgeye) devredilir. Boylece ana akis hicbir zaman
 * mesgul edilmez, buna karsin Bukkit API'si daima dogru akista cagrilir.
 * <p>
 * <b>Bellek yonetimi:</b> Veritabani yoktur. Oturum verileri HashMap'lerde
 * tutulur; oturum bittiginde veya oyuncu cikis yaptiginda tum girdiler aninda
 * silinerek bellek sizintisi onlenir.
 */
public final class ArenaManager {

    /** Kapi kapatilmadan onceki blok durumunun kaydi. */
    private static final class BlockSnapshot {
        private final int x;
        private final int y;
        private final int z;
        private final BlockData data;

        private BlockSnapshot(int x, int y, int z, BlockData data) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.data = data;
        }
    }

    /** Arenaya giris engellendiginde mesaj tekrarini onleyen sure (ms). */
    private static final long BLOCK_MESSAGE_COOLDOWN = 3000L;

    private final MossDuelRoom plugin;
    private final ConfigManager config;
    private final MessageManager messages;
    private final TaskScheduler scheduler;
    private final LootGuiManager lootGui;

    /** Su anda arena bolgesinin icinde bulunan oyuncular. */
    private final Collection<UUID> inside = ConcurrentHashMap.newKeySet();

    /** Oyuncu -> aktif oturum. Hizli erisim icin bellekte tutulur. */
    private final Map<UUID, DuelSession> byPlayer = new ConcurrentHashMap<>();

    /** Olup da duel spawn'ina isinlanmasi gereken oyuncular. */
    private final Collection<UUID> pendingRespawn = ConcurrentHashMap.newKeySet();

    /** Giris engelleme mesajlari icin son gonderim zamani. */
    private final Map<UUID, Long> blockMessageCooldown = new ConcurrentHashMap<>();

    /** Tek arena oldugundan ayni anda yalnizca bir oturum bulunur. */
    private final AtomicReference<DuelSession> active = new AtomicReference<>();

    /** Kapi kapatilirken alinan blok kayitlari (yalnizca ana akistan erisilir). */
    private final List<BlockSnapshot> gateSnapshots = new ArrayList<>();

    private volatile boolean gatesClosed;
    private TaskScheduler.Task ticker;

    public ArenaManager(MossDuelRoom plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.messages = plugin.getMessageManager();
        this.scheduler = plugin.getTaskScheduler();
        this.lootGui = plugin.getLootGuiManager();
    }

    // ==================================================================
    // Yasam dongusu
    // ==================================================================

    /** Asenkron sayaci baslatir ve halihazirda arenada olan oyunculari algilar. */
    public void start() {
        this.ticker = scheduler.asyncTimer(this::tick, 20L, 20L);

        scheduler.sync(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (config.isInsideArena(player.getLocation())) {
                    inside.add(player.getUniqueId());
                }
            }
            tryStart();
        });
    }

    /** Kapatilirken sayaci durdurur, kapilari acar ve tum bellegi temizler. */
    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }

        DuelSession session = active.getAndSet(null);
        if (session != null) {
            session.markEnded();
            session.setState(DuelState.ENDING);
            lootGui.dispose(session);
        }

        openGates();

        byPlayer.clear();
        inside.clear();
        pendingRespawn.clear();
        blockMessageCooldown.clear();
    }

    // ==================================================================
    // Asenkron sayac
    // ==================================================================

    /**
     * Saniyede bir kez, ana akis disinda calisir.
     * Yalnizca sayac azaltir ve gerekli anlarda ana akisa is birakir.
     */
    private void tick() {
        DuelSession session = active.get();
        if (session == null || session.isEnded()) {
            return;
        }

        try {
            switch (session.getState()) {
                case COUNTDOWN: {
                    int left = session.decrementCountdown();
                    if (left > 0) {
                        scheduler.sync(() -> announceCountdown(session, left));
                    } else {
                        scheduler.sync(() -> startFight(session));
                    }
                    break;
                }
                case FIGHTING: {
                    // Suresi dolan beraberlik teklifini temizle.
                    if (session.isDrawRequestExpired(System.currentTimeMillis())) {
                        UUID requester = session.getDrawRequester();
                        session.clearDrawRequest();
                        scheduler.sync(() -> notifyDrawExpired(session, requester));
                    }
                    break;
                }
                case LOOT: {
                    int left = session.decrementLootCountdown();
                    if (left > 0) {
                        scheduler.sync(() -> announceLoot(session, left));
                    } else {
                        scheduler.sync(() -> expireLoot(session));
                    }
                    break;
                }
                default:
                    break;
            }
        } catch (Throwable throwable) {
            // Sayacin olmesini engelle: hatayi logla, dongu devam etsin.
            plugin.getLogger().log(Level.SEVERE, "Duel sayaci sirasinda hata olustu.", throwable);
        }
    }

    // ==================================================================
    // Bolgeye giris / cikis
    // ==================================================================

    /**
     * Hareket ve isinlanma olaylarindan cagrilir.
     * Yalnizca bolge degisimi (giris/cikis) anlarinda is yapar.
     *
     * @param player oyuncu
     * @param to     varilan konum
     * @param event  gerekirse iptal edilecek olay
     */
    public void handleMovement(Player player, Location to, Cancellable event) {
        if (!config.isConfigured()) {
            return;
        }

        UUID id = player.getUniqueId();
        boolean wasInside = inside.contains(id);
        boolean nowInside = config.isInsideArena(to);
        if (wasInside == nowInside) {
            return;
        }

        if (nowInside) {
            DuelSession session = active.get();
            // Devam eden duelloya yabanci oyuncu giremez.
            if (session != null && !session.isParticipant(id)
                    && config.isBlockSpectators()
                    && !player.hasPermission("mossduelroom.bypass")) {
                event.setCancelled(true);
                sendBlockedMessage(player);
                return;
            }
            inside.add(id);
            tryStart();
        } else {
            inside.remove(id);
            handleRegionExit(player);
        }
    }

    /** Oyuncu giris yaptiginda arenada dogmus olabilir. */
    public void handleJoin(Player player) {
        if (config.isConfigured() && config.isInsideArena(player.getLocation())) {
            inside.add(player.getUniqueId());
            tryStart();
        }
    }

    /** Bolgeden cikan oyuncu geri sayimdaysa duello iptal edilir. */
    private void handleRegionExit(Player player) {
        DuelSession session = active.get();
        if (session == null || !session.isParticipant(player.getUniqueId())) {
            return;
        }
        if (session.getState() == DuelState.COUNTDOWN) {
            cancelCountdown(session);
        }
    }

    private void sendBlockedMessage(Player player) {
        long now = System.currentTimeMillis();
        Long last = blockMessageCooldown.get(player.getUniqueId());
        if (last != null && now - last < BLOCK_MESSAGE_COOLDOWN) {
            return;
        }
        blockMessageCooldown.put(player.getUniqueId(), now);
        messages.send(player, "arena.spectator-blocked");
    }

    // ==================================================================
    // Duello baslatma
    // ==================================================================

    /** Arenada tam olarak iki oyuncu varsa geri sayimi baslatir. */
    private void tryStart() {
        if (active.get() != null || !config.isConfigured() || inside.size() != 2) {
            return;
        }

        Iterator<UUID> iterator = inside.iterator();
        if (!iterator.hasNext()) {
            return;
        }
        Player first = Bukkit.getPlayer(iterator.next());
        if (!iterator.hasNext()) {
            return;
        }
        Player second = Bukkit.getPlayer(iterator.next());
        if (first == null || second == null || !first.isOnline() || !second.isOnline()) {
            return;
        }

        DuelSession session = new DuelSession(first, second,
                config.getCountdownSeconds(), config.getLootSeconds());
        if (!active.compareAndSet(null, session)) {
            return;
        }

        byPlayer.put(session.getFirstId(), session);
        byPlayer.put(session.getSecondId(), session);

        messages.send(first, "countdown.started", "%opponent%", second.getName());
        messages.send(second, "countdown.started", "%opponent%", first.getName());
        announceCountdown(session, config.getCountdownSeconds());
    }

    /** Geri sayimin her saniyesinde ekran bildirimi gonderir. */
    private void announceCountdown(DuelSession session, int seconds) {
        if (session.isEnded()) {
            return;
        }
        String value = String.valueOf(seconds);
        boolean announce = config.shouldAnnounceCountdown(seconds);

        for (Player player : onlineParticipants(session)) {
            messages.sendTitle(player, "countdown.title", "countdown.subtitle", 0, 25, 5, "%seconds%", value);
            messages.sendActionBar(player, "countdown.actionbar", "%seconds%", value);
            if (announce) {
                messages.send(player, "countdown.chat", "%seconds%", value);
                playSound(player, config.getCountdownSound());
            }
        }
    }

    /** Geri sayim bitti: kapilar kapanir, savas baslar. */
    private void startFight(DuelSession session) {
        if (session.isEnded() || session.getState() != DuelState.COUNTDOWN) {
            return;
        }

        Player first = Bukkit.getPlayer(session.getFirstId());
        Player second = Bukkit.getPlayer(session.getSecondId());
        if (first == null || second == null
                || !inside.contains(session.getFirstId()) || !inside.contains(session.getSecondId())) {
            cancelCountdown(session);
            return;
        }

        // Blok yerlestirme dunya durumunu degistirdigi icin daima ana akista yapilir.
        closeGates();
        session.setState(DuelState.FIGHTING);

        prepareFighter(first);
        prepareFighter(second);

        messages.sendTitle(first, "countdown.fight-title", "countdown.fight-subtitle", 0, 40, 10,
                "%opponent%", second.getName());
        messages.sendTitle(second, "countdown.fight-title", "countdown.fight-subtitle", 0, 40, 10,
                "%opponent%", first.getName());
        messages.send(first, "countdown.fight-chat");
        messages.send(second, "countdown.fight-chat");
        playSound(first, config.getFightStartSound());
        playSound(second, config.getFightStartSound());
    }

    /** Savas oncesi oyuncuyu esitler: can, aclik, efekt ve yanma durumu. */
    @SuppressWarnings("deprecation") // getMaxHealth: tum 1.20+ turevlerinde mevcut ve kararli
    private void prepareFighter(Player player) {
        if (config.isHealOnStart()) {
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
        }
        if (config.isClearEffectsOnStart()) {
            for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                player.removePotionEffect(effect.getType());
            }
        }
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
    }

    /** Geri sayim iptali (oyuncu ayrildi/cikti). Kapilar zaten aciktir. */
    private void cancelCountdown(DuelSession session) {
        for (Player player : onlineParticipants(session)) {
            messages.send(player, "countdown.cancelled");
            player.sendTitle(" ", " ", 0, 1, 0);
        }
        endSession(session, Collections.emptyList());
    }

    // ==================================================================
    // Kapilar
    // ==================================================================

    /** Kapi bolgelerini doldurur; onceki blok durumlari kaydedilir. */
    private void closeGates() {
        World world = config.getWorld();
        if (world == null || gatesClosed) {
            return;
        }

        gateSnapshots.clear();
        for (GateRegion gate : config.getGates()) {
            BlockData data = gate.getMaterial().createBlockData();
            gate.getRegion().forEach((x, y, z) -> {
                Block block = world.getBlockAt(x, y, z);
                gateSnapshots.add(new BlockSnapshot(x, y, z, block.getBlockData()));
                // applyPhysics = false: komsu blok guncellemesi tetiklenmez, cok daha hizlidir.
                block.setBlockData(data, config.isGatePhysics());
            });
        }
        gatesClosed = true;
        plugin.debug("Kapilar kapatildi (" + gateSnapshots.size() + " blok).");
    }

    /** Kapi bloklarini kayitli eski hallerine dondurur. */
    private void openGates() {
        if (!gatesClosed) {
            return;
        }
        World world = config.getWorld();
        if (world != null) {
            for (BlockSnapshot snapshot : gateSnapshots) {
                world.getBlockAt(snapshot.x, snapshot.y, snapshot.z)
                        .setBlockData(snapshot.data, config.isGatePhysics());
            }
        }
        gateSnapshots.clear();
        gatesClosed = false;
        plugin.debug("Kapilar acildi.");
    }

    // ==================================================================
    // Beraberlik
    // ==================================================================

    /** {@code /1v1 berabere} komutunun tek giris noktasi. */
    public void handleDrawCommand(Player player) {
        DuelSession session = byPlayer.get(player.getUniqueId());
        if (session == null || session.isEnded()) {
            messages.send(player, "draw.not-in-duel");
            return;
        }
        if (session.getState() != DuelState.FIGHTING) {
            messages.send(player, "draw.not-fighting");
            return;
        }

        UUID id = player.getUniqueId();
        UUID opponentId = session.getOpponent(id);
        long now = System.currentTimeMillis();

        if (session.hasDrawRequest(now)) {
            if (id.equals(session.getDrawRequester())) {
                messages.send(player, "draw.already-sent",
                        "%seconds%", String.valueOf(session.getDrawSecondsLeft(now)));
                return;
            }
            // Rakip zaten teklif etmis: onay.
            acceptDraw(session);
            return;
        }

        int timeout = config.getDrawRequestTimeout();
        session.setDrawRequest(id, now + timeout * 1000L);

        messages.send(player, "draw.sent",
                "%opponent%", session.getName(opponentId),
                "%seconds%", String.valueOf(timeout));

        Player opponent = Bukkit.getPlayer(opponentId);
        if (opponent != null) {
            messages.sendClickable(opponent, "draw.received", "draw.received-hover", "draw.received-command",
                    "%player%", player.getName());
            playSound(opponent, config.getDrawSound());
        }
    }

    /** Beraberlik onaylandi: savas biter, iki oyuncu da spawn'a doner. */
    private void acceptDraw(DuelSession session) {
        session.clearDrawRequest();
        for (Player player : onlineParticipants(session)) {
            messages.send(player, "draw.accepted");
            messages.sendTitle(player, "draw.accepted-title", "draw.accepted-subtitle", 5, 40, 10);
            playSound(player, config.getDrawSound());
        }
        endSession(session, Arrays.asList(session.getFirstId(), session.getSecondId()));
    }

    private void notifyDrawExpired(DuelSession session, UUID requester) {
        if (requester == null || session.isEnded()) {
            return;
        }
        for (Player player : onlineParticipants(session)) {
            messages.send(player, "draw.expired");
        }
    }

    // ==================================================================
    // Olum ve ganimet
    // ==================================================================

    /**
     * Savas sirasinda bir oyuncu oldu.
     *
     * @param victim olen oyuncu
     * @param loot   olen oyuncudan toplanan esyalar (yere dusmedi)
     */
    public void handleDuelDeath(Player victim, List<ItemStack> loot) {
        DuelSession session = byPlayer.get(victim.getUniqueId());
        if (session == null || session.isEnded() || session.getState() != DuelState.FIGHTING) {
            return;
        }
        pendingRespawn.add(victim.getUniqueId());
        startLootPhase(session, victim.getUniqueId(), loot);
    }

    /** Ganimet suresi devam ederken kazanan oldu: oturum kapatilir. */
    public void handleWinnerDeath(Player winner) {
        DuelSession session = byPlayer.get(winner.getUniqueId());
        if (session == null || session.getState() != DuelState.LOOT) {
            return;
        }
        pendingRespawn.add(winner.getUniqueId());
        endSession(session, Collections.emptyList());
    }

    /**
     * Ganimet asamasini baslatir: sandik olusturulur, kazanana aninda acilir,
     * kaybeden oturumdan cikarilir.
     */
    private void startLootPhase(DuelSession session, UUID loserId, List<ItemStack> loot) {
        UUID winnerId = session.getOpponent(loserId);
        session.setResult(winnerId, loserId);
        session.clearDrawRequest();
        session.setState(DuelState.LOOT);

        lootGui.create(session, loot);

        // Kaybeden artik oturumun bir parcasi degil; verisi hemen temizlenir.
        byPlayer.remove(loserId);
        inside.remove(loserId);

        String winnerName = session.getWinnerName();
        String loserName = session.getLoserName();
        String lootSeconds = String.valueOf(config.getLootSeconds());

        Player loser = Bukkit.getPlayer(loserId);
        if (loser != null) {
            messages.send(loser, "duel.lose-chat", "%opponent%", winnerName);
            messages.sendTitle(loser, "duel.lose-title", "duel.lose-subtitle", 5, 50, 10,
                    "%opponent%", winnerName);
            playSound(loser, config.getLoseSound());
        }

        Player winner = Bukkit.getPlayer(winnerId);
        if (winner != null) {
            messages.send(winner, "duel.win-chat", "%opponent%", loserName);
            messages.sendTitle(winner, "duel.win-title", "duel.win-subtitle", 5, 50, 10,
                    "%opponent%", loserName, "%seconds%", lootSeconds);
            playSound(winner, config.getWinSound());
        }

        // Olum olayinin icinde envanter acmak guvenli degildir: bir tick sonra acilir.
        scheduler.syncLater(() -> {
            if (session.isEnded() || session.getState() != DuelState.LOOT) {
                return;
            }
            Player target = Bukkit.getPlayer(winnerId);
            if (target == null || !lootGui.open(target, session)) {
                return;
            }
            messages.send(target, "loot.opened", "%seconds%", String.valueOf(session.getLootCountdown()));
            messages.send(target, "loot.reopen-hint", "%seconds%", String.valueOf(session.getLootCountdown()));
            playSound(target, config.getLootOpenSound());
        }, 1L);
    }

    /** {@code /1v1 sandik} komutu: ganimet sandigini yeniden acar. */
    public void handleLootCommand(Player player) {
        DuelSession session = byPlayer.get(player.getUniqueId());
        if (session == null || session.isEnded()
                || session.getState() != DuelState.LOOT
                || !session.isWinner(player.getUniqueId())) {
            messages.send(player, "loot.no-access");
            return;
        }
        if (!lootGui.open(player, session)) {
            messages.send(player, "loot.no-access");
            return;
        }
        messages.send(player, "loot.reopened", "%seconds%", String.valueOf(session.getLootCountdown()));
        playSound(player, config.getLootOpenSound());
    }

    /** Ganimet sayacinin her saniyesinde kazanani bilgilendirir. */
    private void announceLoot(DuelSession session, int seconds) {
        if (session.isEnded()) {
            return;
        }
        UUID winnerId = session.getWinnerId();
        if (winnerId == null) {
            return;
        }
        Player winner = Bukkit.getPlayer(winnerId);
        if (winner == null) {
            return;
        }

        String value = String.valueOf(seconds);
        messages.sendActionBar(winner, "loot.actionbar", "%seconds%", value);
        if (seconds == 10 || seconds == 5 || seconds <= 3) {
            messages.send(winner, "loot.warning", "%seconds%", value);
        }
    }

    /** 30 saniye doldu: erisim kesilir, esyalar silinir, arena sifirlanir. */
    private void expireLoot(DuelSession session) {
        if (session.isEnded()) {
            return;
        }
        UUID winnerId = session.getWinnerId();
        Player winner = winnerId == null ? null : Bukkit.getPlayer(winnerId);
        if (winner != null) {
            messages.send(winner, "loot.expired");
            playSound(winner, config.getLootExpireSound());
        }
        endSession(session, winnerId == null ? Collections.emptyList() : Collections.singletonList(winnerId));
    }

    // ==================================================================
    // Cikis / oturum kapatma
    // ==================================================================

    /** Oyuncu sunucudan ciktiginda cagrilir; tum bellek girdileri silinir. */
    public void handleQuit(Player player) {
        UUID id = player.getUniqueId();
        inside.remove(id);
        pendingRespawn.remove(id);
        blockMessageCooldown.remove(id);

        DuelSession session = byPlayer.get(id);
        if (session == null || session.isEnded()) {
            return;
        }

        UUID opponentId = session.getOpponent(id);
        Player opponent = opponentId == null ? null : Bukkit.getPlayer(opponentId);

        switch (session.getState()) {
            case COUNTDOWN:
                if (opponent != null) {
                    messages.send(opponent, "countdown.cancelled");
                }
                endSession(session, Collections.emptyList());
                break;

            case FIGHTING:
                if (opponent != null) {
                    messages.send(opponent, "duel.opponent-quit");
                }
                if (config.isPunishCombatLog() && opponentId != null) {
                    // Cikis yapanin envanteri rakibine ganimet olarak devredilir.
                    List<ItemStack> loot = new ArrayList<>();
                    for (ItemStack item : player.getInventory().getContents()) {
                        if (item != null && item.getType() != org.bukkit.Material.AIR) {
                            loot.add(item.clone());
                        }
                    }
                    player.getInventory().clear();
                    startLootPhase(session, id, loot);
                } else {
                    endSession(session, opponentId == null
                            ? Collections.emptyList() : Collections.singletonList(opponentId));
                }
                break;

            case LOOT:
                // Kazanan cikti: ganimet silinir, arena sifirlanir.
                endSession(session, Collections.emptyList());
                break;

            default:
                break;
        }
    }

    /**
     * Oturumu kapatir: sandigi imha eder, oyunculari spawn'a isinlar,
     * kapilari acar ve tum bellek girdilerini siler.
     *
     * @param teleportIds spawn'a gonderilecek oyuncular
     */
    private void endSession(DuelSession session, Collection<UUID> teleportIds) {
        if (!session.markEnded()) {
            return;
        }
        session.setState(DuelState.ENDING);

        // Sanal sandik ve icindeki esyalar tamamen birakilir.
        lootGui.dispose(session);

        Location spawn = config.getSpawn();
        for (UUID id : teleportIds) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || spawn == null) {
                continue;
            }
            scheduler.teleport(player, spawn);
            messages.send(player, "duel.teleported");
            messages.send(player, "arena.reset");
        }

        byPlayer.remove(session.getFirstId());
        byPlayer.remove(session.getSecondId());
        inside.remove(session.getFirstId());
        inside.remove(session.getSecondId());

        openGates();
        active.compareAndSet(session, null);

        // Arena yeni oyunculara hazir: kisa bir gecikmeyle tekrar degerlendirilir.
        scheduler.syncLater(this::tryStart, 40L);
    }

    // ==================================================================
    // Disaridan sorgular (dinleyiciler icin)
    // ==================================================================

    public DuelSession getSession(UUID id) {
        return byPlayer.get(id);
    }

    /** Oyuncu su anda savasiyor mu? */
    public boolean isFighting(UUID id) {
        DuelSession session = byPlayer.get(id);
        return session != null && session.getState() == DuelState.FIGHTING;
    }

    /** Ganimet suresi boyunca kazanan korunuyor mu? */
    public boolean isProtectedWinner(UUID id) {
        if (!config.isInvulnerableWinner()) {
            return false;
        }
        DuelSession session = byPlayer.get(id);
        return session != null && session.getState() == DuelState.LOOT && session.isWinner(id);
    }

    /**
     * Olen duello oyuncusunun dogacagi konumu dondurur ve kaydi temizler.
     *
     * @return duello ile ilgisi yoksa null
     */
    public Location consumeRespawnLocation(UUID id) {
        if (!pendingRespawn.remove(id)) {
            return null;
        }
        return config.getSpawn();
    }

    // ==================================================================
    // Yardimcilar
    // ==================================================================

    /** Oturumun cevrimici katilimcilarini dondurur. */
    private List<Player> onlineParticipants(DuelSession session) {
        List<Player> players = new ArrayList<>(2);
        Player first = Bukkit.getPlayer(session.getFirstId());
        if (first != null) {
            players.add(first);
        }
        Player second = Bukkit.getPlayer(session.getSecondId());
        if (second != null) {
            players.add(second);
        }
        return players;
    }

    private void playSound(Player player, SoundEntry sound) {
        if (config.isSoundsEnabled()) {
            sound.play(player);
        }
    }
}
