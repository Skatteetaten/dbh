CREATE TABLE SCHEMA_DATA
(
  id          VARCHAR(255) NOT NULL,
  name        varchar(255),
  active      integer      not null default 1,
  schema_type varchar(255) not null
);
CREATE TABLE USERS
(
  id        VARCHAR(255) NOT NULL,
  schema_id VARCHAR(255) NOT NULL,
  type      VARCHAR(255) NOT NULL,
  username  VARCHAR(255) NOT NULL,
  password  varchar(255)
);
CREATE TABLE LABELS
(
  id        VARCHAR(255) NOT NULL,
  schema_id VARCHAR(255) NOT NULL,
  name      TEXT         NOT NULL,
  value     TEXT
);
ALTER TABLE LABELS
  ADD CONSTRAINT UNIQUE_NAME_PR_SCHEMA UNIQUE (schema_id, name);

CREATE TABLE EXTERNAL_SCHEMA
(
  id           VARCHAR(255) NOT NULL,
  created_date DATE,
  schema_id    VARCHAR(255),
  jdbc_url     TEXT
);