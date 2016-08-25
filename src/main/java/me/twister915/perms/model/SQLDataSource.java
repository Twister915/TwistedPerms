package me.twister915.perms.model;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.pool.HikariPool;
import lombok.Data;
import lombok.EqualsAndHashCode;
import rx.functions.Func1;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public final class SQLDataSource implements _IDataSource, PreDataSource {
    private final static String META_SELECT = "entities.prefix AS prefix, " +
            "entities.suffix AS suffix, " +
            "entities.color AS color, ";

    private final static String GROUP_SELECT = "SELECT groups.entity_id AS entity_id, " +
            "groups.name AS name, " +
            "groups.priority AS name, " +
            META_SELECT +
            "GROUP_CONCAT(DISTINCT entity_permissions.permission, ':', entity_permissions.state) AS permissions " +
            "GROUP_CONCAT(DISTINCT group_parents.parent_id) AS parents " +
            "FROM groups " +
            "JOIN entities ON entities.id = groups.entity_id " +
            "JOIN entity_permissions ON entity_permissions.entity_id = groups.entity_id " +
            "JOIN group_parents ON groups.entity_id = group_parents.child_id ";

    private final static String USER_SELECT = "SELECT players.entity_id AS entity_id, " +
            "players.uuid AS uuid, " +
            "players.username AS username, " +
            META_SELECT +
            "GROUP_CONCAT(DISTINCT groups.entity_id) AS groups, " +
            "GROUP_CONCAT(DISTINCT entity_permissions.permission, ':', entity_permissions.state) AS permissions " +
            "FROM players " +
            "JOIN player_groups ON player_groups.player_id = players.id " +
            "JOIN groups ON player_groups.group_id = groups.entity_id " +
            "JOIN entities ON entities.id = players.entity_id " +
            "JOIN entity_permissions ON entity_permissions.entity_id = players.entity_id ";

    private final HikariPool pool;
    private IDataSource proxied;

    @Data public static class SQLConfig {
        private final String host, database, username, password;
        private final int port;

        public HikariPool toPool() {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:mysql://" + this.host + ":" + this.port + "/" + this.database);
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);

            return new HikariPool(hikariConfig);
        }
    }

    public SQLDataSource(SQLConfig config) {
        this.pool = config.toPool();
    }

    @Override
    public void onEnable(ResourceFaucet faucet) throws Exception {
        List<String> collect = faucet.readResource("schema.sql").stream().filter(s -> s.trim().length() > 0).collect(Collectors.toList());
        doSQL(connection -> {
            if (connection.getMetaData().getTables(null, null, "players", null).first())
                return;

            for (String s : collect)
                connection.prepareStatement(s).executeUpdate();
        });
    }

    @Override
    public void onDisable() {
        pool.suspendPool();
    }

    @Override
    public Map<UUID, PPlayer> getPlayers(DataManager loaded, Collection<UUID> uuids, Func1<UUID, Boolean> allowCreate) throws Exception {
        return doSQL(connection -> {
            //first, get everyone who we need to load out of the database
            //this comes out with a set of groups and player data
            List<UUID> neededUUIDS = new ArrayList<>(uuids);
            Set<Long> groupsNeeded;
            Map<UUID, RawSQLUser> rawSQLUsers = new HashMap<>();
            {
                PreparedStatement statement = connection.prepareStatement(USER_SELECT + "WHERE player.uuid IN " + questionMarks(uuids.size()) + " GROUP BY players.uuid;");

                for (int i = 1; i <= uuids.size(); i++)
                    statement.setString(i, neededUUIDS.get(i).toString());

                rawSQLUsers.putAll(usersFrom(statement.executeQuery()));

                groupsNeeded = new HashSet<>();
                for (RawSQLUser rawSQLUser : rawSQLUsers.values()) {
                    neededUUIDS.remove(rawSQLUser.getUuid());

                    groupsNeeded.addAll(rawSQLUser.getGroups());
                }
            }

            //now for those that didn't appear
            {
                Set<UUID> create = neededUUIDS.stream().filter(allowCreate::call).collect(Collectors.toSet());
                int count = create.size();
                if (count > 0) {
                    Set<Long> entityIdsAvaliable = new HashSet<>();
                    {
                        PreparedStatement statement = connection.prepareStatement("INSERT INTO entities () VALUES " + insertParens(0, count) + ";", Statement.RETURN_GENERATED_KEYS);
                        int l = statement.executeUpdate();
                        assert l == count;
                        ResultSet generatedKeys = statement.getGeneratedKeys();
                        while (generatedKeys.next())
                            entityIdsAvaliable.add(generatedKeys.getLong(1));
                        if (entityIdsAvaliable.size() != count)
                            throw new SQLException("Did not generate enough rows! " + count + " needed and " + entityIdsAvaliable.size() + " generated!");
                    }

                    {
                        PreparedStatement statement = connection.prepareStatement("INSERT INTO players (entity_id, uuid) VALUES " + insertParens(2, count) + ";");
                        Iterator<Long> ids = entityIdsAvaliable.iterator();
                        Iterator<UUID> uuidItr = create.iterator();
                        for (int i = 0, pos = 0; i < count; i++) {
                            long id = ids.next();
                            UUID uuid = uuidItr.next();
                            statement.setLong(++pos, id);
                            statement.setString(++pos, uuid.toString());
                            rawSQLUsers.put(uuid, new RawSQLUser(id, null, null, null, null, "", "", uuid));
                        }
                        int i = statement.executeUpdate();
                        assert i == count;
                    }
                }
            }

            Map<Long, PGroup> groupsLoaded = new GroupIdSearcher().from(loaded, connection, groupsNeeded);
            //flaten it
            ImmutableMap.Builder<UUID, PPlayer> output = ImmutableMap.builder();
            Collection<RawSQLUser> users = rawSQLUsers.values();
            for (RawSQLUser user : users) {
                Set<PGroup> groups = groupsLoaded.entrySet().stream().filter(entry -> user.getGroups().contains(entry.getKey())).map(Map.Entry::getValue).collect(Collectors.toSet());
                output.put(user.getUuid(), user.toPlayer(groups));
            }
            return output.build();
        });
    }

    private abstract class GroupSearcher<T> {
        protected Map<T, PGroup> from(DataManager loaded, Connection connection, Set<T> groupsNeeded) throws Exception {
            Map<T, PGroup> groupsLoaded = new HashMap<>();
            Map<Long, PGroup> groupsLoadedLong = new HashMap<>();
            for (Iterator<T> itr = groupsNeeded.iterator(); itr.hasNext(); ) {
                T data = itr.next();
                getFromLoaded(loaded, data).ifPresent(group -> {
                    itr.remove();
                    groupsLoaded.put(data, group);
                    groupsLoadedLong.put(group.getId(), group);
                });
            }

            Map<Long, RawSQLGroup> gottenRawLong = new HashMap<>();
            Map<T, RawSQLGroup> gottenRaw = new HashMap<>();

            //there's a lot of flow going on here, let me try to explain it
            /*
             * First, while we need more groups
             * Construct a list of groups that we need to query with
             *      if we don't have any group loaded, then we add it to the groupsQuerying collection
             *      And if that list turns out to be longer than 0 elements, we send it to the database
             *
             *      Any time we query the database, we put it in gottenRawLong and gottenRaw
             *
             *      Then we go through all the groups in gottenRawLong
             *      we check what parents we need for that group
             *      if we don't have a parent then we mark it's ID as something we need
             *      otherwise we add it to the colleciton
             *
             *      If we find all the parents we need, we construct the group and update all the collections
             *      This will remove raw groups from the raw collections
             *
             *      If the raw collections have unconstructed groups, that means that there are more parents to find
             *
             */
                    // "we need more groups"
            while (groupsNeeded.size() > 0) {
                //loads init from Ts
                Set<Long> parentsNeeded = new HashSet<>();
                HashSet<T> groupsQuerying = new HashSet<>(groupsNeeded);
                for (Iterator<T> itr = groupsQuerying.iterator(); itr.hasNext(); ) {
                    if (gottenRaw.containsKey(itr.next()))
                        itr.remove();
                }

                if (groupsQuerying.size() > 0) {
                    PreparedStatement statement = prepareStatement(connection, groupsQuerying);

                    gottenRawLong.putAll(groupsFrom(statement.executeQuery()));
                    for (Map.Entry<Long, RawSQLGroup> longRawSQLGroupEntry : gottenRawLong.entrySet())
                        gottenRaw.put(keyFor(longRawSQLGroupEntry.getValue()), longRawSQLGroupEntry.getValue());
                }

                for (Iterator<RawSQLGroup> itr = gottenRawLong.values().iterator(); itr.hasNext(); ) {
                    RawSQLGroup rawSQLGroup = itr.next();
                    Set<PGroup> parents = new HashSet<>();
                    for (Long idParent : rawSQLGroup.getParents()) {
                        PGroup parent = loaded.getGroup(idParent).orElse(groupsLoadedLong.get(idParent));
                        if (parent == null) {
                            parentsNeeded.add(idParent);
                            continue;
                        }
                        parents.add(parent);
                    }
                    if (parents.size() == rawSQLGroup.getParents().size()) {
                        T t = keyFor(rawSQLGroup);
                        PGroup pGroup = rawSQLGroup.toGroup(parents);
                        groupsLoaded.put(t, pGroup);
                        groupsLoadedLong.put(pGroup.getId(), pGroup);
                        groupsNeeded.remove(t);
                        itr.remove();
                        gottenRaw.remove(t);
                    }
                }

                //there are still more parents or groups unconstructed
                while (gottenRawLong.size() > 0) {
                    assert Collections.disjoint(parentsNeeded, gottenRawLong.keySet());
                    Set<Long> lastQuery = new HashSet<>(parentsNeeded);

                    if (parentsNeeded.size() > 0) {
                        PreparedStatement statement = connection.prepareStatement(GROUP_SELECT + " WHERE groups.entiity_id IN " + questionMarks(parentsNeeded.size()) + " GROUP BY groups.entity_id;");
                        int i = 0;
                        for (Long aLong : parentsNeeded)
                            statement.setLong(i, aLong);

                        gottenRawLong.putAll(groupsFrom(statement.executeQuery()));
                        for (Map.Entry<Long, RawSQLGroup> longRawSQLGroupEntry : gottenRawLong.entrySet())
                            gottenRaw.put(keyFor(longRawSQLGroupEntry.getValue()), longRawSQLGroupEntry.getValue());
                    }

                    PARENT_LOADER:
                    for (Iterator<Long> itr = parentsNeeded.iterator(); itr.hasNext(); ) { //for everything we've still got to load
                        //for every group we needed to load,
                        Long id = itr.next(); //get the id
                        RawSQLGroup rawSQLGroup = gottenRawLong.get(id); //then get the group that was loaded
                        if (rawSQLGroup == null)
                            throw new SQLException("Could not load group " + id + "!");

                        Set<PGroup> parents = new HashSet<>(); //now start to put together the parents of this parent group
                        for (Long idParent : rawSQLGroup.getParents()) { //go through all their parents
                            PGroup parent = loaded.getGroup(idParent).orElse(groupsLoadedLong.get(idParent)); //try to get it by ID
                            if (parent == null) { //if we don't have it already loaded AND converted then continue
                                //but first check....
                                if (!gottenRawLong.containsKey(idParent)) { //if we haven't even queried for it
                                    if (lastQuery.contains(idParent)) //if we did just now and didn't get it, then that's shit
                                        throw new SQLException("Invalid parent specification- ID " + idParent + " on group " + rawSQLGroup.getName());
                                    parentsNeeded.add(idParent); //add it to the next cycle's query queue
                                }
                                continue PARENT_LOADER; //we continue so that other groups that *can* be loaded are loaded, then this will start over again (highest while loop)
                            }
                            parents.add(parent);
                        }
                        PGroup pGroup = rawSQLGroup.toGroup(parents);

                        groupsLoadedLong.put(id, pGroup);
                        gottenRawLong.remove(id);
                        gottenRaw.remove(keyFor(rawSQLGroup));
                        itr.remove();
                    }
                }
            }

            return groupsLoaded;
        }

        public abstract PreparedStatement prepareStatement(Connection connection, Set<T> groupsQuerying) throws Exception;
        public abstract Optional<PGroup> getFromLoaded(DataManager loaded, T obj) throws Exception;
        public abstract T keyFor(RawSQLGroup id);
    }

    private final class GroupNameSearcher extends GroupSearcher<String> {
        @Override
        public PreparedStatement prepareStatement(Connection connection, Set<String> groupsQuerying) throws Exception {
            PreparedStatement statement = connection.prepareStatement(GROUP_SELECT + "WHERE groups.name IN " + questionMarks(groupsQuerying.size()) + " GROUP BY groups.entity_id;");
            int i = 0;
            for (String name : groupsQuerying)
                statement.setString(++i, name);

            return statement;
        }

        @Override
        public Optional<PGroup> getFromLoaded(DataManager loaded, String obj) {
            return loaded.getGroup(obj);
        }

        @Override
        public String keyFor(RawSQLGroup id) {
            return id.getName();
        }
    }

    private final class GroupIdSearcher extends GroupSearcher<Long> {
        @Override
        public PreparedStatement prepareStatement(Connection connection, Set<Long> groupsQuerying) throws Exception {
            PreparedStatement statement = connection.prepareStatement(GROUP_SELECT + "WHERE groups.id IN" + questionMarks(groupsQuerying.size()) + " GROUP BY groups.entity_id;");
            int i = 0;
            for (Long id : groupsQuerying)
                statement.setLong(++i, id);

            return statement;
        }

        @Override
        public Optional<PGroup> getFromLoaded(DataManager loaded, Long obj) throws Exception {
            return loaded.getGroup(obj);
        }

        @Override
        public Long keyFor(RawSQLGroup id) {
            return id.getId();
        }
    }

    /*
     * CREATE TABLE ...
     */
    @Data private static class RawSQLEntity {
        protected final Long id;
        protected final String prefix, suffix, color;
        protected final String permissionsRaw; //GROUP_CONCAT(DISTINCT entity_permissions.permission, ':', entity_permissions.value) AS permissions

        public Map<String, Boolean> getPermissions() {
            ImmutableMap.Builder<String, Boolean> perms = ImmutableMap.builder();
            for (String s : permissionsRaw.split(",")) {
                String[] split = s.split(":", 2);
                perms.put(split[0], Boolean.valueOf(split[1]));
            }
            return perms.build();
        }
    }

    private static List<Long> parseCSL(String list) {
        String[] split = list.split(",");
        List<Long> out = new ArrayList<>(split.length);
        for (int i = 0; i < split.length; i++)
            out.add(i, Long.parseLong(split[i]));
        return out;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data private class RawSQLUser extends RawSQLEntity {
        private final String groupsRaw, username;
        private final UUID uuid;

        public RawSQLUser(Long id, String username, String prefix, String suffix, String color, String permissionsRaw, String groupsRaw, UUID uuid) {
            super(id, prefix, suffix, color, permissionsRaw);
            this.groupsRaw = groupsRaw;
            this.uuid = uuid;
            this.username = username;
        }

        public List<Long> getGroups() {
            return parseCSL(groupsRaw);
        }

        public PPlayer toPlayer(Set<PGroup> groups) {
            PPlayer pPlayer = new PPlayer(proxied, uuid, username, prefix, suffix, color, getPermissions(), groups);
            pPlayer.setId(id);
            return pPlayer;
        }
    }

    private Map<UUID, RawSQLUser> usersFrom(ResultSet set) throws Exception {
        ImmutableMap.Builder<UUID, RawSQLUser> out = ImmutableMap.builder();
        while (set.next()) {
            UUID uuid = UUID.fromString(set.getString("uuid"));
            out.put(uuid, new RawSQLUser(set.getLong("entity_id"), set.getString("username"), set.getString("prefix"),
                    set.getString("suffix"), set.getString("color"),
                    set.getString("permissions"), set.getString("groups"), uuid));
        }
        return out.build();
    }

    @Data @EqualsAndHashCode(callSuper = true)
    private class RawSQLGroup extends RawSQLEntity {
        private final String name, parentsRaw;
        private final int priority;

        public RawSQLGroup(Long id, String prefix, String suffix, String color, String permissionsRaw, String name, String parentsRaw, int priority) {
            super(id, prefix, suffix, color, permissionsRaw);
            this.name = name;
            this.parentsRaw = parentsRaw;
            this.priority = priority;
        }

        public List<Long> getParents() {
            return parseCSL(parentsRaw);
        }

        public PGroup toGroup(Set<PGroup> parents) {
            return new PGroup(proxied, name, prefix, suffix, color, getPermissions(), priority, parents);
        }
    }

    private Map<Long, RawSQLGroup> groupsFrom(ResultSet set) throws Exception {
        ImmutableMap.Builder<Long, RawSQLGroup> out = ImmutableMap.builder();
        while (set.next()) {
            Long id = set.getLong("entity_id");
            out.put(id, new RawSQLGroup(id, set.getString("prefix"),
                    set.getString("suffix"), set.getString("color"),
                    set.getString("permissions"), set.getString("name"),
                    set.getString("parents"), set.getInt("priority")));
        }
        return out.build();
    }

    @Override
    public PPlayer getPlayer(DataManager loaded, UUID player, boolean allowCreate) throws Exception {
        return getPlayers(loaded, Collections.singleton(player), p -> allowCreate).get(player);
    }

    @Override
    public PGroup getGroupByName(DataManager loaded, String name) throws Exception {
        return doSQL(connection -> {
            Map<String, PGroup> map = new GroupNameSearcher().from(loaded, connection, Collections.singleton(name));
            assert map.size() == 1;
            return map.get(name);
        });
    }

    @Override
    public Map<String, PGroup> getGroups() throws Exception {
        return doSQL(connection -> {
            PreparedStatement statement = connection.prepareStatement(GROUP_SELECT + ";");
            Map<Long, RawSQLGroup> rawGroups = groupsFrom(statement.executeQuery());
            Map<Long, PGroup> loadedGroups = new HashMap<>();
            while (loadedGroups.size() != rawGroups.size()) {
                GROUP_CONSTRUCTOR:
                for (RawSQLGroup next : rawGroups.values()) {
                    Set<PGroup> parents = new HashSet<>();
                    for (Long aLong : next.getParents()) {
                        PGroup pGroup = loadedGroups.get(aLong);
                        if (pGroup == null)
                            continue GROUP_CONSTRUCTOR;
                        parents.add(pGroup);
                    }
                    loadedGroups.put(next.getId(), next.toGroup(parents));
                }
            }
            ImmutableMap.Builder<String, PGroup> outMap = ImmutableMap.builder();
            for (PGroup pGroup : loadedGroups.values())
                outMap.put(pGroup.getName(), pGroup);
            return outMap.build();
        });
    }

    @Override
    public void writePlayerMeta(Collection<PPlayer> players) throws Exception {
        int size = players.size();
        doSQL(connection -> {
            writeEntities(connection, players);
            PreparedStatement statement = connection.prepareStatement("INSERT IGNORE INTO players (entity_id, uuid, username) VALUES " + insertParens(3, size) + " ON DUPLICATE KEY UPDATE username = VALUES(username)");
            int i = 0;
            for (PPlayer player : players) {
                statement.setLong(++i, player.getId());
                statement.setString(++i, player.getUuid().toString());
                statement.setString(++i, player.getUsername());
            }
            int i1 = statement.executeUpdate();
            assert i1 == size;
        });
    }

    private void writeEntities(Connection connection, Collection<? extends PEntity> entities) throws Exception {
        int size = entities.size();
        PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO entities (id, prefix, suffix, color) VALUES " +
                        insertParens(4, size) +
                        " ON DUPLICATE KEY UPDATE " +
                        "prefix = VALUES(prefix), suffix = VALUES(suffix), color = VALUES(color)");

        int i = 0;
        for (PEntity entity : entities) {
            statement.setLong(++i, entity.getId());
            statement.setString(++i, entity.getPrefix());
            statement.setString(++i, entity.getSuffix());
            statement.setString(++i, entity.getColor());
        }

        int i1 = statement.executeUpdate();
        assert i1 == size;
    }

    @Override
    public void writeGroupMeta(Collection<PGroup> groups) throws Exception {
        int size = groups.size();
        doSQL(connection -> {
            writeEntities(connection, groups);
            PreparedStatement statement = connection.prepareStatement("INSERT IGNORE INTO groups (entity_id, `name`, priority) VALUES " + insertParens(3, size) + " ON DUPLICATE KEY UPDATE priority = VALUES(`name`)");
            int i = 0;
            for (PGroup group : groups) {
                statement.setLong(++i, group.getId());
                statement.setString(++i, group.getName());
                statement.setInt(++i, group.getPriority());
            }
            int i1 = statement.executeUpdate();
            assert i1 == size;
        });
    }

    @Override
    public void writeMeta(PPlayer player) throws Exception {
        writePlayerMeta(Collections.singleton(player));
    }

    @Override
    public void writeMeta(PGroup group) throws Exception {
        writeGroupMeta(Collections.singleton(group));
    }

    @Override
    public void deletePermission(PEntity entity, String perm, Boolean value) throws Exception {
        doSQL(connection -> {
            PreparedStatement statement = connection.prepareStatement("DELETE FROM entity_permissions WHERE entity_id = ? AND permission = ? AND state = ? LIMIT 1");
            statement.setLong(1, entity.getId());
            statement.setString(2, perm);
            statement.setBoolean(3, value);
            statement.executeUpdate();
        });
    }

    @Override
    public void addPermission(PEntity entity, String perm, Boolean value) throws Exception {
        doSQL(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT IGNORE INTO entity_permissions (entity_id, permission, state) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE state = VALUE(state)");
            statement.setLong(1, entity.getId());
            statement.setString(2, perm);
            statement.setBoolean(3, value);
            statement.executeUpdate();
        });
    }

    @Override
    public void addGroup(PPlayer player, PGroup group) throws Exception {
        doSQL(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT IGNORE INTO player_groups (player_id, group_id) VALUES (?, ?)");
            statement.setLong(1, player.getId());
            statement.setLong(2, group.getId());
            statement.executeUpdate();
        });
    }

    @Override
    public void deleteGroup(PPlayer player, PGroup group) throws Exception {
        doSQL(connection -> {
            PreparedStatement statement = connection.prepareStatement("DELETE FROM player_groups WHERE player_id = ? AND group_id = ? LIMIT 1");
            statement.setLong(1, player.getId());
            statement.setLong(2, group.getId());
            statement.executeUpdate();
        });
    }

    @Override
    public void addParent(PGroup child, PGroup parent) throws Exception {
        doSQL(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO group_parents (parent_id, child_id) VALUES (?, ?)");
            statement.setLong(1, parent.getId());
            statement.setLong(2, child.getId());
            statement.executeUpdate();
        });
    }

    @Override
    public void deleteParent(PGroup child, PGroup parent) throws Exception {
        doSQL(connection -> {
            PreparedStatement statement = connection.prepareStatement("DELETE FROM group_parents WHERE parent_id = ? AND child_id = ? LIMIT 1");
            statement.setLong(1, parent.getId());
            statement.setLong(2, child.getId());
            statement.executeUpdate();
        });
    }

    @Override
    public void purgeEntity(PEntity entity) throws Exception {
        doSQL(connection -> {
            PreparedStatement statement = connection.prepareStatement("DELETE FROM entities WHERE id = ? LIMIT 1");
            statement.setLong(1, entity.getId());
            statement.executeUpdate();
        });
    }

    private static String questionMarks(int marks) {
        String[] questionMarks = new String[marks];
        Arrays.fill(questionMarks, "?");
        return "(" + Joiner.on(',').join(questionMarks) + ")";
    }

    private static String insertParens(int marksPer, int parens) {
        String base = questionMarks(marksPer);
        StringBuilder builder = new StringBuilder(base.length() * parens - 1);
        for (int i = 0; i < parens; i++)
            builder.append(base).append(",");
        String finParens = builder.substring(0, builder.length() - 1);
        assert !finParens.endsWith(",");
        assert finParens.endsWith(")");
        assert finParens.startsWith("(");
        assert finParens.length() == (base.length() * parens) - 1;
        return finParens;
    }

    public interface ExFunc1<T, R> {
        R call(T val) throws Exception;
    }

    public interface ExFunc0<T> {
        void call(T val) throws Exception;
    }

    protected void doSQL(ExFunc0<Connection> func) throws Exception {
        doSQL(val -> {
            func.call(val);
            return null;
        });
    }

    protected <T> T doSQL(ExFunc1<Connection, T> func) throws Exception {
        try (Connection c = pool.getConnection()) {
            return func.call(c);
        }
    }
}
