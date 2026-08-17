package dev.wulnrydev.mossduelroom.arena;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tek bir duellonun bellek ici durumu.
 * <p>
 * Nesne asenkron sayac (okuma/azaltma) ile ana akis (Bukkit islemleri)
 * arasinda paylasildigindan tum degisken alanlar {@code volatile} veya
 * atomiktir. Veritabani kullanilmaz; oturum bittiginde referans birakilir ve
 * cop toplayiciya devredilir.
 */
public final class DuelSession {

    private final UUID firstId;
    private final UUID secondId;
    private final String firstName;
    private final String secondName;

    private volatile DuelState state = DuelState.COUNTDOWN;

    private final AtomicInteger countdown;
    private final AtomicInteger lootCountdown;

    /** Oturumun yalnizca bir kez sonlandirilmasini garanti eder. */
    private final AtomicBoolean ended = new AtomicBoolean(false);

    private volatile UUID winnerId;
    private volatile UUID loserId;
    private volatile String winnerName;
    private volatile String loserName;

    /** Ganimet sandigi. Kalan esyalarin tek kaynagi bu nesnedir. */
    private volatile Inventory lootInventory;

    /** Beraberlik teklifini gonderen oyuncu ve teklifin bitis zamani (ms). */
    private volatile UUID drawRequester;
    private volatile long drawExpiresAt;

    public DuelSession(Player first, Player second, int countdownSeconds, int lootSeconds) {
        this.firstId = first.getUniqueId();
        this.secondId = second.getUniqueId();
        this.firstName = first.getName();
        this.secondName = second.getName();
        this.countdown = new AtomicInteger(countdownSeconds);
        this.lootCountdown = new AtomicInteger(lootSeconds);
    }

    // ------------------------------------------------------------------
    // Katilimcilar
    // ------------------------------------------------------------------

    public UUID getFirstId() {
        return firstId;
    }

    public UUID getSecondId() {
        return secondId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getSecondName() {
        return secondName;
    }

    public boolean isParticipant(UUID id) {
        return firstId.equals(id) || secondId.equals(id);
    }

    /** Verilen oyuncunun rakibini dondurur; katilimci degilse null. */
    public UUID getOpponent(UUID id) {
        if (firstId.equals(id)) {
            return secondId;
        }
        if (secondId.equals(id)) {
            return firstId;
        }
        return null;
    }

    public String getName(UUID id) {
        if (firstId.equals(id)) {
            return firstName;
        }
        if (secondId.equals(id)) {
            return secondName;
        }
        return "?";
    }

    // ------------------------------------------------------------------
    // Durum ve sayaclar
    // ------------------------------------------------------------------

    public DuelState getState() {
        return state;
    }

    public void setState(DuelState state) {
        this.state = state;
    }

    /** Geri sayimi bir azaltir ve kalan saniyeyi dondurur. */
    public int decrementCountdown() {
        return countdown.decrementAndGet();
    }

    public int getCountdown() {
        return countdown.get();
    }

    /** Ganimet sayacini bir azaltir ve kalan saniyeyi dondurur. */
    public int decrementLootCountdown() {
        return lootCountdown.decrementAndGet();
    }

    public int getLootCountdown() {
        return lootCountdown.get();
    }

    /** Oturumu sonlandirilmis olarak isaretler; ilk cagrida true doner. */
    public boolean markEnded() {
        return ended.compareAndSet(false, true);
    }

    public boolean isEnded() {
        return ended.get();
    }

    // ------------------------------------------------------------------
    // Sonuc
    // ------------------------------------------------------------------

    public void setResult(UUID winnerId, UUID loserId) {
        this.winnerId = winnerId;
        this.loserId = loserId;
        this.winnerName = getName(winnerId);
        this.loserName = getName(loserId);
    }

    public UUID getWinnerId() {
        return winnerId;
    }

    public UUID getLoserId() {
        return loserId;
    }

    public String getWinnerName() {
        return winnerName;
    }

    public String getLoserName() {
        return loserName;
    }

    public boolean isWinner(UUID id) {
        return winnerId != null && winnerId.equals(id);
    }

    // ------------------------------------------------------------------
    // Ganimet
    // ------------------------------------------------------------------

    public Inventory getLootInventory() {
        return lootInventory;
    }

    public void setLootInventory(Inventory lootInventory) {
        this.lootInventory = lootInventory;
    }

    // ------------------------------------------------------------------
    // Beraberlik teklifi
    // ------------------------------------------------------------------

    public UUID getDrawRequester() {
        return drawRequester;
    }

    public void setDrawRequest(UUID requester, long expiresAt) {
        this.drawRequester = requester;
        this.drawExpiresAt = expiresAt;
    }

    public void clearDrawRequest() {
        this.drawRequester = null;
        this.drawExpiresAt = 0L;
    }

    public boolean hasDrawRequest(long now) {
        return drawRequester != null && now < drawExpiresAt;
    }

    /** Teklifin suresi dolmus ama hala temizlenmemisse true. */
    public boolean isDrawRequestExpired(long now) {
        return drawRequester != null && now >= drawExpiresAt;
    }

    /** Teklifin bitmesine kalan saniye. */
    public int getDrawSecondsLeft(long now) {
        if (drawRequester == null) {
            return 0;
        }
        return (int) Math.max(0L, (drawExpiresAt - now + 999L) / 1000L);
    }
}
