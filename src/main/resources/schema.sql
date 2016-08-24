CREATE TABLE entities (
  id               INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  prefix           VARCHAR(64)           DEFAULT NULL,
  suffix           VARCHAR(64)           DEFAULT NULL,
  color            VARCHAR(2)            DEFAULT NULL,
  last_updated     TIMESTAMP    NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  created          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE entity_permissions (
  entity_id    INT UNSIGNED NOT NULL,
  permission   VARCHAR(64)  NOT NULL,
  state        BOOLEAN      NOT NULL,
  last_updated TIMESTAMP    NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  created      TIMESTAMP    NOT NULL DEFAULT NOW(),
  PRIMARY KEY (entity_id, permission)
);

CREATE INDEX permissions_by_id ON entity_permissions (entity_id);

CREATE TABLE groups (
  entity_id    INT UNSIGNED NOT NULL,
  name         VARCHAR(64)  NOT NULL,
  priority     INT UNSIGNED NOT NULL DEFAULT 0,
  last_updated TIMESTAMP    NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  created      TIMESTAMP    NOT NULL DEFAULT NOW(),
  PRIMARY KEY (entity_id, name)
);

CREATE UNIQUE INDEX group_by_name ON groups (`name`);

CREATE TABLE group_parents (
  parent_id    INT UNSIGNED NOT NULL,
  child_id     INT UNSIGNED NOT NULL,
  last_updated TIMESTAMP    NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  created      TIMESTAMP    NOT NULL DEFAULT NOW(),
  PRIMARY KEY (parent_id, child_id)
);

CREATE INDEX parents_by_child ON group_parents (child_id);
CREATE INDEX parents_by_parent ON group_parents (parent_id);

CREATE TABLE players (
  entity_id    INT UNSIGNED NOT NULL,
  uuid         VARCHAR(36)  NOT NULL,
  username     VARCHAR(16)  NOT NULL DEFAULT NULL,
  last_updated TIMESTAMP    NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  created      TIMESTAMP    NOT NULL DEFAULT NOW(),
  PRIMARY KEY (entity_id, uuid)
);

CREATE UNIQUE INDEX player_by_uuid ON players (uuid);
CREATE INDEX player_by_username ON players (username);

CREATE TABLE player_groups (
  player_id    INT UNSIGNED NOT NULL,
  group_id     INT UNSIGNED NOT NULL,
  last_updated TIMESTAMP    NOT NULL DEFAULT NOW() ON UPDATE NOW(),
  created      TIMESTAMP    NOT NULL DEFAULT NOW(),
  PRIMARY KEY (player_id, group_id)
);

CREATE INDEX groups_by_player ON player_groups (player_id);
CREATE INDEX groups_by_group ON player_groups (group_id);