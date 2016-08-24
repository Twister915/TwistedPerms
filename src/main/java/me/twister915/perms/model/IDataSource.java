package me.twister915.perms.model;

import rx.Single;
import rx.functions.Func1;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface IDataSource {
    Single<Map<UUID, PPlayer>> getPlayers(DataManager loaded, Collection<UUID> uuids, Func1<UUID, Boolean> allowCreate);
    Single<PPlayer> getPlayer(DataManager loaded, UUID player, boolean allowCreate);
    Single<PGroup> getGroupByName(DataManager loaded, String name);
    Single<Map<String, PGroup>> getGroups();

    void writePlayerMeta(Collection<PPlayer> players);
    void writeGroupMeta(Collection<PGroup> groups);

    default void writeMeta(PEntity entity) {
        if (entity instanceof PPlayer)
            writeMeta((PPlayer) entity);
        
        if (entity instanceof PGroup)
            writeMeta((PGroup) entity);
    }

    void writeMeta(PPlayer player);
    void writeMeta(PGroup group);

    void deletePermission(PEntity entity, String perm, Boolean value);
    void addPermission(PEntity entity, String perm, Boolean value);

    void addGroup(PPlayer player, PGroup group);
    void deleteGroup(PPlayer player, PGroup group);

    void addParent(PGroup child, PGroup parent);
    void deleteParent(PGroup child, PGroup parent);

    void purgeEntity(PEntity entity);
    
    _IDataSource unsafe();
}
