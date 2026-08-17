package dev.wulnrydev.mossduelroom.arena;

/**
 * Bir duello oturumunun yasam dongusu.
 */
public enum DuelState {

    /** Iki oyuncu arenaya girdi, geri sayim isliyor. Kapilar hala acik. */
    COUNTDOWN,

    /** Kapilar kapandi, savas devam ediyor. */
    FIGHTING,

    /** Bir oyuncu oldu; kazanan icin ganimet sandigi erisim suresi isliyor. */
    LOOT,

    /** Oturum kapatiliyor; sayac artik bu oturumu islemez. */
    ENDING
}
