package me.twister915.perms.model;

import rx.functions.Func1;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

interface _IDataSource extends PreDataSource {
    void onEnable(ResourceFaucet faucet) throws Exception;
    void onDisable() throws Exception;
    Map<UUID, PPlayer> getPlayers(DataManager loaded, Collection<UUID> uuids, Func1<UUID, Boolean> allowCreate) throws Exception;
    PPlayer getPlayer(DataManager loaded, UUID player, boolean allowCreate) throws Exception;
    PGroup getGroupByName(DataManager loaded, String name) throws Exception;
    Map<String, PGroup> getGroups() throws Exception;

    void writePlayerMeta(Collection<PPlayer> players) throws Exception;
    void writeGroupMeta(Collection<PGroup> groups) throws Exception;

    void writeMeta(PPlayer player) throws Exception;
    void writeMeta(PGroup group) throws Exception;

    void deletePermission(PEntity entity, String perm, Boolean value) throws Exception;
    void addPermission(PEntity entity, String perm, Boolean value) throws Exception;

    void addGroup(PPlayer player, PGroup group) throws Exception;
    void deleteGroup(PPlayer player, PGroup group) throws Exception;

    void addParent(PGroup child, PGroup parent) throws Exception;
    void deleteParent(PGroup child, PGroup parent) throws Exception;

    void purgeEntity(PEntity entity) throws Exception;
}
