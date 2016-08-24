package me.twister915.perms.bukkit;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import me.twister915.perms.model.DataManager;
import me.twister915.perms.model.IDataSource;
import me.twister915.perms.model.LoadedPlayer;
import me.twister915.perms.model.PPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import tech.rayline.core.plugin.RedemptivePlugin;

import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.UUID;

@EqualsAndHashCode(of = {"data"})
public final class BukkitLoadedPlayer implements LoadedPlayer {
    private final RedemptivePlugin plugin;
    @Getter private final Player player;

    @Getter private PPlayer data;
    private PermissionAttachment attachment;

    public BukkitLoadedPlayer(RedemptivePlugin plugin, Player player, DataManager manager, IDataSource source) throws Exception {
        this.plugin = plugin;
        this.data = source.unsafe().getPlayer(manager, player.getUniqueId(), true);
        this.player = player;
    }

    public void swap(PPlayer newData) {
        this.data = newData;
        sever();
        attach();
    }

    public void attach() {
        if (!Bukkit.isPrimaryThread())
            throw new ConcurrentModificationException("You must modify permissions on Bukkit's main thread!");

        if (attachment != null)
            throw new IllegalStateException("You cannot attach again!");

        attachment = player.addAttachment(this.plugin);
        for (Map.Entry<String, Boolean> stringBooleanEntry : data.getComputedPermissions().entrySet())
            attachment.setPermission(stringBooleanEntry.getKey(), stringBooleanEntry.getValue());
    }

    public void sever() {
        if (!Bukkit.isPrimaryThread())
            throw new ConcurrentModificationException("You must modify permissions on Bukkit's main thread!");

        if (attachment == null)
            throw new IllegalStateException("The permission attachment has already been removed!");

        attachment.remove();
        attachment = null;
    }

    @Override
    public UUID getUUID() {
        return player.getUniqueId();
    }
}
