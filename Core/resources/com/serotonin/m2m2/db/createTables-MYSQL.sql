--
--    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
--    @author Matthew Lohbihler
--

-- Make sure that everything get created with utf8 as the charset.
alter database default character set utf8;

--
-- System settings
create table systemSettings (
  settingName varchar(64) not null,
  settingValue longtext,
  primary key (settingName)
) engine=InnoDB;

--
-- Users
create table users (
  id int not null auto_increment,
  username varchar(40) not null,
  password varchar(255) not null,
  email varchar(255) not null,
  phone varchar(40),
  disabled char(1) not null,
  lastLogin bigint,
  homeUrl varchar(255),
  receiveAlarmEmails int not null,
  receiveOwnAuditEvents char(1) not null,
  timezone varchar(50),
  muted char(1),
  name varchar(255),
  locale varchar(50),
  tokenVersion int not null,
  passwordVersion int not null,
  passwordChangeTimestamp bigint NOT NULL,
  sessionExpirationOverride char(1),
  sessionExpirationPeriods int,
  sessionExpirationPeriodType varchar(25),
  organization varchar(80),
  organizationalRole varchar(80),
  createdTs bigint NOT NULL,
  emailVerifiedTs bigint,
  data JSON,
  primary key (id)
) engine=InnoDB;
ALTER TABLE users ADD CONSTRAINT username_unique UNIQUE(username);
ALTER TABLE users ADD CONSTRAINT email_unique UNIQUE(email);

create table userComments (
  id int not null auto_increment,
  xid varchar(100) not null,
  userId int,
  commentType int not null,
  typeKey int not null,
  ts bigint not null,
  commentText varchar(1024) not null,
  primary key (id)
) engine=InnoDB;
alter table userComments add constraint userCommentsFk1 foreign key (userId) references users(id);
alter table userComments add constraint userCommentsUn1 unique (xid);
ALTER TABLE userComments ADD INDEX userComments_performance1 (`commentType` ASC, `typeKey` ASC);

--
--
-- Roles
--
CREATE TABLE roles (
	id int not null auto_increment,
	xid varchar(100) not null,
	name varchar(255) not null,
  	primary key (id)
) engine=InnoDB;
ALTER TABLE roles ADD CONSTRAINT rolesUn1 UNIQUE (xid);

--
-- Role Inheritance Mappings
-- 
CREATE TABLE roleInheritance (
	roleId INT NOT NULL,
	inheritedRoleId INT NOT NULL
);
ALTER TABLE roleInheritance ADD CONSTRAINT roleInheritanceUn1 UNIQUE (roleId,inheritedRoleId);
ALTER TABLE roleInheritance ADD CONSTRAINT roleInheritanceFk1 FOREIGN KEY (roleId) REFERENCES roles(id) ON DELETE CASCADE;
ALTER TABLE roleInheritance ADD CONSTRAINT roleInheritanceFk2 FOREIGN KEY (inheritedRoleId) REFERENCES roles(id) ON DELETE CASCADE;

--
--
-- User Role Mappings
--
CREATE TABLE userRoleMappings (
	roleId int not null,
	userId int not null
) engine=InnoDB;
ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsFk1 FOREIGN KEY (roleId) REFERENCES roles(id) ON DELETE CASCADE;
ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsFk2 FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsUn1 UNIQUE (roleId,userId);

CREATE TABLE `minterms` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB;

CREATE TABLE `minterms_roles` (
  `minterm_id` int(11) NOT NULL,
  `role_id` int(11) NOT NULL,
  UNIQUE KEY `minterms_roles_idx1` (`minterm_id`,`role_id`),
  KEY `minterms_roles_fk1_idx` (`minterm_id`),
  KEY `minterms_roles_fk2_idx` (`role_id`),
  CONSTRAINT `minterms_roles_fk1` FOREIGN KEY (`minterm_id`) REFERENCES `minterms` (`id`) ON DELETE CASCADE ON UPDATE NO ACTION,
  CONSTRAINT `minterms_roles_fk2` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE NO ACTION
) ENGINE=InnoDB;

CREATE TABLE `permissions` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB;

CREATE TABLE `permissions_minterms` (
  `permission_id` int(11) NOT NULL,
  `minterm_id` int(11) NOT NULL,
  UNIQUE KEY `permissions_minterms_idx1` (`permission_id`,`minterm_id`),
  KEY `permissions_minterms_fk1_idx` (`permission_id`),
  KEY `permissions_minterms_fk2_idx` (`minterm_id`),
  CONSTRAINT `permissions_minterms_fk1` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`) ON DELETE CASCADE ON UPDATE NO ACTION,
  CONSTRAINT `permissions_minterms_fk2` FOREIGN KEY (`minterm_id`) REFERENCES `minterms` (`id`) ON DELETE CASCADE ON UPDATE NO ACTION
) ENGINE=InnoDB;

--
-- Mailing lists
create table mailingLists (
  id int not null auto_increment,
  xid varchar(100) not null,
  name varchar(40) not null,
  receiveAlarmEmails int not null,
  primary key (id)
) engine=InnoDB;
alter table mailingLists add constraint mailingListsUn1 unique (xid);

create table mailingListInactive (
  mailingListId int not null,
  inactiveInterval int not null
) engine=InnoDB;
alter table mailingListInactive add constraint mailingListInactiveFk1 foreign key (mailingListId) 
  references mailingLists(id) on delete cascade;

create table mailingListMembers (
  mailingListId int not null,
  typeId int not null,
  userId int,
  address varchar(255)
) engine=InnoDB;
alter table mailingListMembers add constraint mailingListMembersFk1 foreign key (mailingListId) 
  references mailingLists(id) on delete cascade;

--
--
-- Data Sources
--
create table dataSources (
  id int not null auto_increment,
  xid varchar(100) not null,
  name varchar(255) not null,
  dataSourceType varchar(40) not null,
  data longblob not null,
  jsonData JSON,
  rtdata longblob,
  read_permission int default null,
  edit_permission int default null,
  primary key (id)
) engine=InnoDB;
alter table dataSources add constraint dataSourcesUn1 unique (xid);
ALTER TABLE dataSources ADD CONSTRAINT dataSourcesFk1 FOREIGN KEY (read_permission) REFERENCES permissions(id) ON DELETE SET NULL;
ALTER TABLE dataSources ADD CONSTRAINT dataSourcesFk2 FOREIGN KEY (edit_permission) REFERENCES permissions(id) ON DELETE SET NULL;
ALTER TABLE dataSources ADD INDEX nameIndex (name ASC);

--
--
-- Data Points
--
CREATE TABLE dataPoints (
  id int not null auto_increment,
  xid varchar(100) not null,
  dataSourceId int not null,
  name varchar(255),
  deviceName varchar(255),
  enabled char(1),
  loggingType int,
  intervalLoggingPeriodType int,
  intervalLoggingPeriod int,
  intervalLoggingType int,
  tolerance double,
  purgeOverride char(1),
  purgeType int,
  purgePeriod int,
  defaultCacheSize int,
  discardExtremeValues char(1),
  engineeringUnits int,
  data longblob not null,
  templateId int,
  rollup int,
  dataTypeId int not null,
  settable char(1),
  jsonData JSON,
  read_permission int default null,
  set_permission int default null,
  primary key (id)
) engine=InnoDB;
ALTER TABLE dataPoints ADD CONSTRAINT dataPointsUn1 UNIQUE (xid);
ALTER TABLE dataPoints ADD CONSTRAINT dataPointsFk1 FOREIGN KEY (dataSourceId) REFERENCES dataSources(id);
ALTER TABLE dataPoints ADD CONSTRAINT dataPointsFk2 FOREIGN KEY (read_permission) REFERENCES permissions(id) ON DELETE SET NULL;
ALTER TABLE dataPoints ADD CONSTRAINT dataPointsFk3 FOREIGN KEY (set_permission) REFERENCES permissions(id) ON DELETE SET NULL;
CREATE INDEX pointNameIndex on dataPoints (name ASC);
CREATE INDEX deviceNameIndex on dataPoints (deviceName ASC);
CREATE INDEX deviceNameNameIndex on dataPoints (deviceName ASC, name ASC);
CREATE INDEX deviceNameNameIdIndex ON dataPoints (deviceName ASC, name ASC, id ASC);
CREATE INDEX enabledIndex on dataPoints (enabled ASC);
CREATE INDEX xidNameIndex on dataPoints (xid ASC, name ASC);
CREATE INDEX dataSourceIdFkIndex ON dataPoints (dataSourceId ASC);

-- Data point tags
CREATE TABLE dataPointTags (
  dataPointId INT NOT NULL,
  tagKey VARCHAR(255) NOT NULL,
  tagValue VARCHAR(255) NOT NULL
) engine=InnoDB;
ALTER TABLE dataPointTags ADD CONSTRAINT dataPointTagsUn1 UNIQUE (dataPointId ASC, tagKey ASC);
ALTER TABLE dataPointTags ADD CONSTRAINT dataPointTagsFk1 FOREIGN KEY (dataPointId) REFERENCES dataPoints (id) ON DELETE CASCADE;
CREATE INDEX dataPointTagsIndex1 ON dataPointTags (tagKey ASC, tagValue ASC);


--
--
-- Point Values (historical data)
--
create table pointValues (
  id bigint not null auto_increment,
  dataPointId int not null,
  dataType int not null,
  pointValue double,
  ts bigint not null,
  primary key (id)
) engine=InnoDB;
create index pointValuesIdx1 on pointValues (dataPointId, ts);

create table pointValueAnnotations (
  pointValueId bigint not null,
  textPointValueShort varchar(128),
  textPointValueLong longtext,
  sourceMessage longtext,
  primary key (pointValueId)
) engine=InnoDB;

--
--
-- Event detectors
--
CREATE TABLE eventDetectors (
  id int NOT NULL auto_increment,
  xid varchar(100) NOT NULL,
  sourceTypeName varchar(32) NOT NULL,
  typeName varchar(32) NOT NULL,
  dataPointId int,
  data longtext NOT NULL,
  jsonData JSON,
  PRIMARY KEY (id)
)engine=InnoDB;
ALTER TABLE eventDetectors ADD CONSTRAINT eventDetectorsUn1 UNIQUE (xid);
ALTER TABLE eventDetectors ADD CONSTRAINT dataPointIdFk FOREIGN KEY (dataPointId) REFERENCES dataPoints(id);

--
--
-- Events
--
create table events (
  id int not null auto_increment,
  typeName varchar(32) not null,
  subtypeName varchar(32),
  typeRef1 int not null,
  typeRef2 int not null,
  activeTs bigint not null,
  rtnApplicable char(1) not null,
  rtnTs bigint,
  rtnCause int,
  alarmLevel int not null,
  message longtext,
  ackTs bigint,
  ackUserId int,
  alternateAckSource longtext,
  primary key (id)
) engine=InnoDB;
alter table events add constraint eventsFk1 foreign key (ackUserId) references users(id);
alter table events add index performance1 (activeTs ASC);
ALTER TABLE events ADD INDEX events_performance2 (`rtnApplicable` ASC, `rtnTs` ASC);
ALTER TABLE events ADD INDEX events_performance3 (`typeName` ASC, `subTypeName` ASC, `typeRef1` ASC);

--
--
-- Event handlers
--
create table eventHandlers (
  id int not null auto_increment,
  xid varchar(100) not null,
  alias varchar(255) not null,
  eventHandlerType varchar(40) NOT NULL,
  data longblob not null,
  primary key (id)
) engine=InnoDB;
alter table eventHandlers add constraint eventHandlersUn1 unique (xid);

CREATE TABLE eventHandlersMapping (
  eventHandlerId int not null,
  
  -- Event type, see events
  eventTypeName varchar(32) NOT NULL,
  eventSubtypeName varchar(32) NOT NULL DEFAULT '',
  eventTypeRef1 int NOT NULL,
  eventTypeRef2 int NOT NULL
);
ALTER TABLE eventHandlersMapping ADD CONSTRAINT eventHandlersFk1 FOREIGN KEY (eventHandlerId) REFERENCES eventHandlers(id) ON DELETE CASCADE;
ALTER TABLE eventHandlersMapping ADD CONSTRAINT handlerMappingUniqueness UNIQUE(eventHandlerId, eventTypeName, eventSubtypeName, eventTypeRef1, eventTypeRef2);

--
--
-- Audit Table
-- 
CREATE TABLE audit (
  id int NOT NULL auto_increment,
  typeName varchar(32) NOT NULL,
  alarmLevel int NOT NULL,
  userId int NOT NULL,
  changeType int NOT NULL,
  objectId int NOT NULL,
  ts bigint NOT NULL,
  context longtext,
  message varchar(255),
  PRIMARY KEY (id)
)engine=InnoDB;
CREATE INDEX tsIndex ON audit (ts ASC);
CREATE INDEX userIdIndex ON audit (userId ASC);
CREATE INDEX typeNameIndex ON audit (typeName ASC);
CREATE INDEX alarmLevelIndex ON audit (alarmLevel ASC);

--
--
-- Publishers
--
create table publishers (
  id int not null auto_increment,
  xid varchar(100) not null,
  publisherType varchar(40) not null,
  data longblob not null,
  rtdata longblob,
  primary key (id)
) engine=InnoDB;
alter table publishers add constraint publishersUn1 unique (xid);

--
--
-- JsonData
--
CREATE TABLE jsonData (
  	id int not null auto_increment,
	xid varchar(100) not null,
	name varchar(255) not null,
  	publicData char(1),
  	data longtext,
    primary key (id)
) engine=InnoDB;
ALTER TABLE jsonData ADD CONSTRAINT jsonDataUn1 UNIQUE (xid);

--
--
-- InstalledModules
--  Thirty character restriction is from the store
CREATE TABLE installedModules (
	name varchar(30) not null,
	version varchar(255) not null
) engine=InnoDB;
ALTER TABLE installedModules ADD CONSTRAINT installModulesUn1 UNIQUE (name);

--
--
-- FileStores
--
CREATE TABLE fileStores (
	id int not null auto_increment, 
	storeName varchar(100) not null, 
	PRIMARY KEY (id)
) engine=InnoDB;
ALTER TABLE fileStores ADD CONSTRAINT fileStoresUn1 UNIQUE (storeName);

--
--
-- Role Mappings
--
CREATE TABLE roleMappings (
	roleId int not null,
	voId int,
	voType varchar(255),
	permissionType varchar(255) not NULL,
	mask BIGINT NOT NULL
) engine=InnoDB;
ALTER TABLE roleMappings ADD CONSTRAINT roleMappingsFk1 FOREIGN KEY (roleId) REFERENCES roles(id) ON DELETE CASCADE;
ALTER TABLE roleMappings ADD CONSTRAINT roleMappingsUn1 UNIQUE (roleId,voId,voType,permissionType);
CREATE INDEX roleMappingsPermissionTypeIndex ON roleMappings (permissionType ASC);
CREATE INDEX roleMappingsVoTypeIndex ON roleMappings (voType ASC);
CREATE INDEX roleMappingsVoIdIndex ON roleMappings (voId ASC);
CREATE INDEX roleMappingsRoleIdIndex ON roleMappings (roleId ASC);
CREATE INDEX roleMappingsVoTypeVoIdPermissionTypeIndex ON roleMappings (voType ASC, voId ASC, permissionType ASC);
--
--
-- Persistent session data
--
CREATE TABLE mangoSessionData (
	sessionId VARCHAR(120),
	contextPath VARCHAR(60),
	virtualHost VARCHAR(60),
	lastNode VARCHAR(60),
	accessTime BIGINT,
	lastAccessTime BIGINT,
	createTime BIGINT,
	cookieTime BIGINT,
	lastSavedTime BIGINT,
	expiryTime BIGINT,
	maxInterval BIGINT,
	userId INT,
	primary key (sessionId, contextPath, virtualHost)
)engine=InnoDB;
CREATE INDEX mangoSessionDataExpiryIndex ON mangoSessionData (expiryTime);
CREATE INDEX mangoSessionDataSessionIndex ON mangoSessionData (sessionId, contextPath);


--
--
-- Mango Default Data
--
-- Insert admin user
INSERT INTO users (id, name, username, password, email, phone, disabled, lastLogin, homeUrl, receiveAlarmEmails, receiveOwnAuditEvents, muted, locale, tokenVersion, passwordVersion, passwordChangeTimestamp, sessionExpirationOverride, createdTs) VALUES 
	(1, 'Administrator', 'admin', '{BCRYPT}$2a$10$L6Jea9zZ79Hc82trIesw0ekqH0Q8hTGOBqSGutoi17p2UZ.j3vzWm', 'admin@mango.example.com', '', 'N', 0, '/ui/administration/home', -3, 'N', 'Y', '', 1, 1, UNIX_TIMESTAMP(NOW()) * 1000, 'N', UNIX_TIMESTAMP(NOW()) * 1000);      
-- Insert default roles
INSERT INTO roles (id, xid, name) VALUES (1, 'superadmin', 'Superadmin role');
INSERT INTO roles (id, xid, name) VALUES (2, 'user', 'User role');
INSERT INTO roles (id, xid, name) VALUES (3, 'anonymous', 'Anonymous role');
-- Add admin user role mappings
INSERT INTO userRoleMappings (roleId, userId) VALUES (1, 1);
INSERT INTO userRoleMappings (roleId, userId) VALUES (2, 1);
