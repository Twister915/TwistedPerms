package me.twister915.perms.model;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.Synchronized;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@EqualsAndHashCode(of = {"name"}, callSuper = true)
@Setter(AccessLevel.NONE)
public final class PGroup extends PEntity implements Comparable<PGroup> {
    private String name;
    private int priority;
    private List<PGroup> parents;

    PGroup(IDataSource origin, String name, String prefix, String suffix, String color, Map<String, Boolean> ownPermissions, int priority, Set<PGroup> parents) {
        super(origin, prefix, suffix, color, ownPermissions);
        this.name = name;
        this.priority = priority;
        this.parents = new GroupsList(parents) {
            @Override
            protected void onAdd(PGroup group) {
                origin.addParent(PGroup.this, group);
            }

            @Override
            protected void onRemove(PGroup group) {
                origin.deleteParent(PGroup.this, group);
            }
        };

        reload();
    }

    @Override
    public int compareTo(PGroup o) {
        return priority - o.getPriority();
    }

    @Synchronized
    public void setPriority(int priority) {
        if (priority == this.priority)
            return;

        this.priority = priority;
        origin.writeMeta(this);
    }

    @Override
    @Synchronized
    protected void reload() {
        super.reload();

        dumpAll(computedPermissions, ownPermissions);
        for (PGroup parent : parents)
            dumpAll(computedPermissions, parent.getComputedPermissions());
    }
}
