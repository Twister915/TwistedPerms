package me.twister915.perms.model;

import rx.Observable;
import rx.Single;

import java.util.Set;
import java.util.UUID;

public interface PlayerSource {
    Observable<UUID> loadStream();
    Observable<UUID> unloadStream();
    Set<UUID> getOnline();
    Single<LoadedPlayer> load(UUID uuid, DataManager manager, IDataSource source);
}
