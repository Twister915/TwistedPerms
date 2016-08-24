package me.twister915.perms.bukkit;

import lombok.Data;
import me.twister915.perms.model.ThreadModel;
import org.bukkit.Bukkit;
import rx.Scheduler;
import tech.rayline.core.plugin.RedemptivePlugin;

@Data
public final class BukkitThreadModel implements ThreadModel {
    private final RedemptivePlugin plugin;

    @Override
    public Scheduler getSync() {
        return plugin.getSyncScheduler();
    }

    @Override
    public Scheduler getAsync() {
        return plugin.getAsyncScheduler();
    }

    @Override
    public boolean isPrimaryThread() {
        return Bukkit.isPrimaryThread();
    }
}
