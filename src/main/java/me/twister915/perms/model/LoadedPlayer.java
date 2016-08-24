package me.twister915.perms.model;

import java.util.UUID;

public interface LoadedPlayer {
    void attach();
    void swap(PPlayer player);
    void sever();

    UUID getUUID();
}
