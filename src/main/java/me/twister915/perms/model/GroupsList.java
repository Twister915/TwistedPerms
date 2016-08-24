package me.twister915.perms.model;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class GroupsList extends AbstractList<PGroup> {
    private final List<PGroup> groups;

    public GroupsList(Collection<PGroup> groups) {
        this.groups = new ArrayList<>(groups);
    }

    @Override
    public PGroup get(int index) {
        return groups.get(index);
    }

    @Override
    public PGroup remove(int index) {
        PGroup remove = groups.remove(index);
        onRemove(remove);
        Collections.sort(this);
        return remove;
    }

    @Override
    public void add(int index, PGroup element) {
        if (groups.contains(element))
            throw new UnsupportedOperationException("The groups collection cannot support multiple of the same item!");

        groups.add(index, element);
        onAdd(element);
        Collections.sort(this);
    }

    @Override
    //only used when sorting, so no notification is made
    public PGroup set(int index, PGroup element) {
        return groups.set(index, element);
    }

    protected void onRemove(PGroup group) {}
    protected void onAdd(PGroup group) {}

    @Override
    public int size() {
        return groups.size();
    }
}
