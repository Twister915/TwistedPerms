package me.twister915.perms.model;

import lombok.Data;
import lombok.Synchronized;
import rx.Scheduler;
import rx.Single;
import rx.Subscription;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Data public final class PermissionsManager implements DataManager {
    private final Scheduler.Worker asyncWorker;
    private final Scheduler syncScheduler;
    private final PlayerSource playerSource;
    //data management
    private final IDataSource dataSource;

    //actual data
    //  groups
    private final Map<String, PGroup> groupsLoadedByName = new HashMap<>();
    private final Map<Long, PGroup> groupsLoadedById = new HashMap<>();
    //  users
    private final Map<UUID, PPlayer> playersLoadedByUUID = new HashMap<>();
    private final Map<UUID, LoadedPlayer> loadedPlayers = new HashMap<>();

    private Subscription subscription;

    public PermissionsManager(PlayerSource playerSource, ThreadModel threadModel, _IDataSource dataSource, ResourceFaucet faucet) throws Exception {
        this.dataSource = DataSourceProxyUtil.proxy(dataSource, threadModel, this::handleError);
        this.asyncWorker = threadModel.getAsync().createWorker();
        this.syncScheduler = threadModel.getSync();
        this.playerSource = playerSource;

        dataSource.onEnable(faucet);
        doBlockingLoad();
    }

    public void onDisable() throws Exception {
        dataSource.unsafe().onDisable();
    }

    public Single<Void> reload() {
        return Single.create(subscriber -> {
           asyncWorker.schedule(() -> {
               try {
                   doBlockingReload();
               } catch (Throwable throwable) {
                   subscriber.onError(throwable);
               }
               subscriber.onSuccess(null);
           });
        });
    }

    @Synchronized
    private void doBlockingReload() throws Exception {
        doBlockingUnload();
        doBlockingLoad();
    }

    private void doBlockingUnload() throws Exception {
        dataSource.writePlayerMeta(playersLoadedByUUID.values());

        groupsLoadedByName.clear();
        groupsLoadedById.clear();
        playersLoadedByUUID.clear();

        subscription.unsubscribe();
    }

    private void doBlockingLoad() throws Exception {
        _IDataSource unsafe = dataSource.unsafe();
        groupsLoadedByName.putAll(unsafe.getGroups());
        for (PGroup pGroup : groupsLoadedByName.values())
            groupsLoadedById.put(pGroup.getId(), pGroup);

        playersLoadedByUUID.putAll(unsafe.getPlayers(this, playerSource.getOnline(), p -> false));
        for (Map.Entry<UUID, PPlayer> uuidpPlayerEntry : playersLoadedByUUID.entrySet())
            loadedPlayers.get(uuidpPlayerEntry.getKey()).swap(uuidpPlayerEntry.getValue());

        playerSource.loadStream()
                .flatMap(uuid -> playerSource.load(uuid, this, dataSource).toObservable())
                .observeOn(syncScheduler)
                .subscribe(player -> {
                    //jumps threads here
                    loadedPlayers.put(player.getUUID(), player);
                    player.attach();
                }, this::handleError);

        playerSource.unloadStream().subscribe(uuid -> {
            LoadedPlayer loadedPlayer = loadedPlayers.get(uuid);
            if (loadedPlayer == null)
                return;

            loadedPlayer.sever();
            loadedPlayers.remove(uuid);
        }, this::handleError);
    }

    private void handleError(Throwable t) {
        t.printStackTrace();
    }

    @Override
    public Optional<PPlayer> getPlayer(UUID uuid) {
        return grab(uuid, playersLoadedByUUID);
    }

    @Override
    public Optional<PGroup> getGroup(String name) {
        return grab(name, groupsLoadedByName);
    }

    @Override
    public Optional<PGroup> getGroup(long id) {
        return grab(id, groupsLoadedById);
    }

    private <T, V> Optional<V> grab(T input, Map<T, V> data) {
        return Optional.ofNullable(data.get(input));
    }
}
