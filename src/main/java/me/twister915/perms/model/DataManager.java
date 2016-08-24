package me.twister915.perms.model;

import java.util.Optional;
import java.util.UUID;

public interface DataManager {
    Optional<PPlayer> getPlayer(UUID uuid);
    Optional<PGroup> getGroup(String name);
    Optional<PGroup> getGroup(long id);
}
