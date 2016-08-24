package me.twister915.perms.model;

import rx.Scheduler;

public interface ThreadModel {
    Scheduler getSync();
    Scheduler getAsync();
    boolean isPrimaryThread();
}
