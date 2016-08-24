package me.twister915.perms.model;

import com.google.common.collect.ImmutableMap;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.Synchronized;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@Data
@EqualsAndHashCode(of = {"id"})
@Setter(AccessLevel.NONE)
public abstract class PEntity {
    @Setter(AccessLevel.PACKAGE) private long id;

    protected final IDataSource origin;

    protected String prefix, suffix, color;
    protected final Map<String, Boolean> ownPermissions;
    protected final Map<String, Boolean> computedPermissions = new HashMap<>();

    PEntity(IDataSource origin, String prefix, String suffix, String color, Map<String, Boolean> ownPermissions) {
        this.origin = origin;
        this.prefix = prefix;
        this.suffix = suffix;
        this.color = color;
        this.ownPermissions = new PermissionsMap(ownPermissions);
    }

    @Synchronized
    public final void setPrefix(String prefix) {
        origin.writeMeta(this);
        this.prefix = prefix;
    }

    @Synchronized
    public final void setSuffix(String suffix) {
        origin.writeMeta(this);
        this.suffix = suffix;
    }

    @Synchronized
    public final void setColor(String color) {
        origin.writeMeta(this);
        this.color = color;
    }

    public Map<String, Boolean> getComputedPermissions() {
        return ImmutableMap.copyOf(computedPermissions);
    }

    public static <K, V> void dumpAll(Map<K, V> target, Map<K, V> other) {
        dumpAll(false, target, other);
    }

    public static <K, V> void dumpAll(boolean overwrite, Map<K, V> target, Map<K, V> other) {
        for (Map.Entry<K, V> kvEntry : other.entrySet()) {
            if (target.containsKey(kvEntry.getKey()) && !overwrite)
                continue;

            target.put(kvEntry.getKey(), kvEntry.getValue());
        }
    }

    protected void reload() {
        computedPermissions.clear();
    }

    private final class PermissionsMap extends AbstractMap<String, Boolean> {
        private final Map<String, Boolean> ownPermissions;

        public PermissionsMap(Map<String, Boolean> ownPermissions) {
            this.ownPermissions = new HashMap<>(ownPermissions);
        }

        @Override
        public Set<Entry<String, Boolean>> entrySet() {
            Set<Entry<String, Boolean>> entries = ownPermissions.entrySet();

            return new AbstractSet<Entry<String, Boolean>>() {
                @Override
                public Iterator<Entry<String, Boolean>> iterator() {
                    Iterator<Entry<String, Boolean>> entryIterator = entries.iterator();
                    return new Iterator<Entry<String, Boolean>>() {
                        Entry<String, Boolean> last;
                        @Override
                        public boolean hasNext() {
                            return entryIterator.hasNext();
                        }

                        @Override
                        public Entry<String, Boolean> next() {
                            Entry<String, Boolean> next = entryIterator.next();
                            last = next;
                            return new SimpleEntry<String, Boolean>(next) {
                                @Override
                                public Boolean setValue(Boolean value) {
                                    Boolean aBoolean = next.setValue(value);
                                    origin.addPermission(PEntity.this, next.getKey(), next.getValue());
                                    return aBoolean;
                                }
                            };
                        }

                        @Override
                        public void remove() {
                            origin.deletePermission(PEntity.this, last.getKey(), last.getValue());
                            entryIterator.remove();
                        }
                    };
                }

                @Override
                public int size() {
                    return ownPermissions.size();
                }
            };
        }

        @Override
        public Boolean put(String key, Boolean value) {
            Boolean put = ownPermissions.put(key, value);
            origin.addPermission(PEntity.this, key, value);
            return put;
        }
    }

    public void purge() {
        origin.purgeEntity(this);
    }
}
