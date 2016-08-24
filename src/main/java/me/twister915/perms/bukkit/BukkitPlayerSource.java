package me.twister915.perms.bukkit;

import me.twister915.perms.model.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import rx.Observable;
import rx.Scheduler;
import rx.Single;
import tech.rayline.core.plugin.RedemptivePlugin;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class BukkitPlayerSource implements PlayerSource {
    private final RedemptivePlugin plugin;
    private final Scheduler.Worker async;

    public BukkitPlayerSource(RedemptivePlugin plugin) {
        this.plugin = plugin;
        async = plugin.getAsyncScheduler().createWorker();
    }

    @Override
    public Observable<UUID> loadStream() {
        return plugin.observeEvent(AsyncPlayerPreLoginEvent.class).map(AsyncPlayerPreLoginEvent::getUniqueId);
    }

    @Override
    public Observable<UUID> unloadStream() {
        return plugin.observeEvent(PlayerQuitEvent.class).map(event -> event.getPlayer().getUniqueId());
    }

    @Override
    public Set<UUID> getOnline() {
        return Bukkit.getOnlinePlayers().stream().map(Entity::getUniqueId).collect(Collectors.toSet());
    }

    private Player getPlayer(UUID uuid) {
        return Bukkit.getPlayer(uuid);
    }

    @Override
    public Single<LoadedPlayer> load(UUID uuid, DataManager manager, IDataSource source) {
        return Single.create(subscriber -> {
            async.schedule(() -> {
                try {
                    subscriber.onSuccess(new BukkitLoadedPlayer(plugin, getPlayer(uuid), manager, source));
                } catch (Exception e) {
                    subscriber.onError(e);
                }
            });
        });
    }
}
