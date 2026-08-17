package dev.wulnrydev.mossduelroom.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Zamanlayici koprusu.
 * <p>
 * Sunucu Folia ise Folia'nin bolge tabanli zamanlayicilari (GlobalRegionScheduler,
 * AsyncScheduler, EntityScheduler), degilse klasik BukkitScheduler kullanilir.
 * Folia siniflari derleme zamaninda mevcut olmadigi icin yansima (reflection) ile
 * bir kez cozulur ve Method referanslari onbelleklenir; calisma anindaki maliyeti
 * normal bir metot cagrisina cok yakindir.
 * <p>
 * Kural: dunya/oyuncu ile ilgili tum islemler {@link #sync(Runnable)} veya
 * {@link #atEntity(Entity, Runnable)} icinden yapilir. Sadece kendi veri
 * yapilarimiza dokunan sayaclar {@link #asyncTimer(Runnable, long, long)} ile
 * ana akisin disinda calisir.
 */
public final class TaskScheduler {

    /** Iptal edilebilir gorev referansi. */
    @FunctionalInterface
    public interface Task {
        void cancel();
    }

    private final Plugin plugin;
    private final boolean folia;

    // --- Folia yansima onbellegi -------------------------------------------
    private Object globalScheduler;
    private Method globalExecute;
    private Method globalRunDelayed;
    private Object asyncScheduler;
    private Method asyncRunAtFixedRate;
    private Method entityGetScheduler;
    private Method entitySchedulerRun;
    private Method scheduledTaskCancel;

    // --- Paper teleportAsync onbellegi -------------------------------------
    private static final Method TELEPORT_ASYNC = resolveTeleportAsync();

    public TaskScheduler(Plugin plugin) {
        this.plugin = plugin;

        boolean foliaDetected = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            foliaDetected = true;
        } catch (ClassNotFoundException ignored) {
            // Folia degil, klasik Bukkit zamanlayicisi kullanilacak.
        }

        if (foliaDetected) {
            try {
                setupFolia();
            } catch (Throwable throwable) {
                foliaDetected = false;
                plugin.getLogger().log(Level.WARNING,
                        "Folia zamanlayicisi baglanamadi, Bukkit zamanlayicisina donuluyor.", throwable);
            }
        }
        this.folia = foliaDetected;
    }

    /** Folia arayuz siniflarini ve metotlarini bir kez cozer. */
    private void setupFolia() throws Exception {
        Class<?> globalClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
        Class<?> asyncClass = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
        Class<?> entitySchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
        Class<?> scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");

        globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
        globalExecute = globalClass.getMethod("execute", Plugin.class, Runnable.class);
        globalRunDelayed = globalClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);

        asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
        asyncRunAtFixedRate = asyncClass.getMethod("runAtFixedRate",
                Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class);

        entityGetScheduler = Entity.class.getMethod("getScheduler");
        entitySchedulerRun = entitySchedulerClass.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
        scheduledTaskCancel = scheduledTaskClass.getMethod("cancel");
    }

    public boolean isFolia() {
        return folia;
    }

    // ------------------------------------------------------------------
    // Senkron (ana akis / global bolge) islemler
    // ------------------------------------------------------------------

    /**
     * Gorevi ana akista calistirir. Zaten ana akistaysak dogrudan calistirilir,
     * boylece gereksiz bir tick gecikmesi olusmaz.
     */
    public void sync(Runnable task) {
        if (folia) {
            invoke(() -> globalExecute.invoke(globalScheduler, plugin, task), "sync");
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    /** Gorevi belirtilen tick sonra ana akista calistirir. */
    public void syncLater(Runnable task, long delayTicks) {
        long delay = Math.max(1L, delayTicks);
        if (folia) {
            invoke(() -> globalRunDelayed.invoke(globalScheduler, plugin, wrap(task), delay), "syncLater");
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }

    /**
     * Gorevi ilgili varligin (oyuncunun) bolgesinde calistirir.
     * Folia'da isinlanma / envanter acma gibi islemler bu yolla yapilmalidir.
     */
    public void atEntity(Entity entity, Runnable task) {
        if (folia) {
            invoke(() -> {
                Object scheduler = entityGetScheduler.invoke(entity);
                entitySchedulerRun.invoke(scheduler, plugin, wrap(task), null);
                return null;
            }, "atEntity");
            return;
        }
        sync(task);
    }

    // ------------------------------------------------------------------
    // Asenkron sayaclar
    // ------------------------------------------------------------------

    /**
     * Tekrarlayan asenkron gorev baslatir. Yalnizca eklentiye ait veri
     * yapilarina dokunan sayaclar icin kullanilmalidir.
     *
     * @param delayTicks  ilk calisma gecikmesi (tick)
     * @param periodTicks tekrar araligi (tick)
     */
    public Task asyncTimer(Runnable task, long delayTicks, long periodTicks) {
        long delay = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);

        if (folia) {
            Object handle = invoke(() -> asyncRunAtFixedRate.invoke(asyncScheduler, plugin, wrap(task),
                    delay * 50L, period * 50L, TimeUnit.MILLISECONDS), "asyncTimer");
            if (handle == null) {
                return () -> {
                };
            }
            return () -> invoke(() -> scheduledTaskCancel.invoke(handle), "cancel");
        }

        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
        return bukkitTask::cancel;
    }

    // ------------------------------------------------------------------
    // Isinlanma
    // ------------------------------------------------------------------

    /**
     * Oyuncuyu guvenli bicimde isinlar.
     * Paper/Folia uzerinde teleportAsync kullanilir (chunk yuklemesi ana akisi
     * kilitlemez), klasik Spigot'ta normal teleport'a duser.
     */
    public void teleport(Entity entity, Location location) {
        if (entity == null || location == null) {
            return;
        }
        atEntity(entity, () -> {
            if (TELEPORT_ASYNC != null) {
                try {
                    TELEPORT_ASYNC.invoke(entity, location);
                    return;
                } catch (Throwable ignored) {
                    // Asagidaki klasik teleport'a dusulur.
                }
            }
            entity.teleport(location);
        });
    }

    private static Method resolveTeleportAsync() {
        try {
            return Entity.class.getMethod("teleportAsync", Location.class);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Yardimcilar
    // ------------------------------------------------------------------

    /** Folia zamanlayicilari Consumer<ScheduledTask> bekler; Runnable'i sarmalar. */
    private static Consumer<Object> wrap(Runnable task) {
        return ignored -> task.run();
    }

    @FunctionalInterface
    private interface ReflectiveCall {
        Object call() throws Exception;
    }

    private Object invoke(ReflectiveCall call, String action) {
        try {
            return call.call();
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Zamanlayici islemi basarisiz: " + action, exception);
            return null;
        }
    }
}
