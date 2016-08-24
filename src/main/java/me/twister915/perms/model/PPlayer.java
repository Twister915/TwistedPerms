package me.twister915.perms.model;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.Synchronized;
import rx.functions.Func1;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@EqualsAndHashCode(of = {"uuid"}, callSuper = true)
@Setter(AccessLevel.NONE)
public final class PPlayer extends PEntity {
    private String username;
    private UUID uuid;
    private List<PGroup> groups;

    PPlayer(IDataSource origin, UUID uuid, String username, String prefix, String suffix, String color, Map<String, Boolean> ownPermissions, Collection<PGroup> groups) {
        super(origin, prefix, suffix, color, ownPermissions);
        this.username = username;
        this.uuid = uuid;
        this.groups = new GroupsList(groups) {
            @Override
            protected void onAdd(PGroup group) {
                origin.addGroup(PPlayer.this, group);
            }

            @Override
            protected void onRemove(PGroup group) {
                origin.deleteGroup(PPlayer.this, group);
            }
        };

        reload();
    }

    @Override
    @Synchronized
    protected void reload() {
        super.reload();

        dumpAll(computedPermissions, ownPermissions);
        for (PGroup group : groups)
            dumpAll(computedPermissions, group.getComputedPermissions());
    }

    public void updateUsername(String username) {
        origin.writeMeta(this);
        this.username = username;
    }

    @Override
    public String getPrefix() {
        return getDeferToGroup(entity -> entity.prefix);
    }

    @Override
    public String getSuffix() {
        return getDeferToGroup(entity -> entity.suffix);
    }

    @Override
    public String getColor() {
        return getDeferToGroup(entity -> entity.color);
    }

    private <T> T getDeferToGroup(Func1<PEntity, T> func) {
        T call = func.call(this);
        if (call != null)
            return call;

        if (groups.size() == 0)
            return null;

        return func.call(groups.get(0));
    }
}
