-- MySQL 8; apply manually after importing reference schema and cleaning duplicates.
-- No historical account/log INSERT data is provided.
ALTER TABLE sys_account MODIFY username varchar(64) NOT NULL, MODIFY password varchar(100) NOT NULL,
 ADD UNIQUE KEY uk_account_username(username);
ALTER TABLE sys_role ADD UNIQUE KEY uk_role_code(code);
ALTER TABLE sys_menu ADD UNIQUE KEY uk_menu_code(code);
ALTER TABLE sys_account_role MODIFY account_id bigint NOT NULL, MODIFY role_id bigint NOT NULL,
 ADD UNIQUE KEY uk_account_role(account_id,role_id), ADD KEY idx_ar_role(role_id);
ALTER TABLE sys_role_menu MODIFY role_id bigint NOT NULL, MODIFY menu_id bigint NOT NULL,
 ADD UNIQUE KEY uk_role_menu(role_id,menu_id), ADD KEY idx_rm_menu(menu_id);
ALTER TABLE sys_menu ADD KEY idx_menu_parent(parent_id);
ALTER TABLE sys_login_log ADD KEY idx_login_created(created_time), ADD KEY idx_login_account(account);
CREATE TABLE sys_rbac_version (id bigint PRIMARY KEY, version bigint NOT NULL);
INSERT INTO sys_rbac_version VALUES (1,1);
UPDATE sys_menu SET method='PUT' WHERE method='UPDATE';
UPDATE sys_menu SET code='sys:role:add' WHERE code='sys:roleadd';
UPDATE sys_menu SET code='sys:menu:add' WHERE code='sys:menuadd';
-- New contract: enable=1 enabled; lock=1 locked (review historical accounts before use).
-- Populate a NEW administrator with a locally generated BCrypt hash; never reuse reference credentials.

-- Missing audit/concurrency fields; no database foreign keys.
ALTER TABLE sys_account ADD COLUMN version bigint NOT NULL DEFAULT 0, ADD COLUMN is_deleted tinyint NOT NULL DEFAULT 0;
ALTER TABLE sys_role ADD COLUMN version bigint NOT NULL DEFAULT 0, ADD COLUMN is_deleted tinyint NOT NULL DEFAULT 0;
ALTER TABLE sys_menu ADD COLUMN version bigint NOT NULL DEFAULT 0, ADD COLUMN is_deleted tinyint NOT NULL DEFAULT 0;