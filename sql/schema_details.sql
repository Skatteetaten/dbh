select u.schema_id, dba.username, dba.created, dba.last_login, LISTAGG(l.NAME || ':' || l.VALUE, ';') WITHIN GROUP (ORDER BY l.NAME) as labels
  from dba_users dba
  join DATABASEHOTEL_INSTANCE_DATA.USERS u on u.USERNAME=dba.username
  left outer join DATABASEHOTEL_INSTANCE_DATA.LABELS l on l.SCHEMA_ID=u.SCHEMA_ID
  GROUP BY u.SCHEMA_ID, dba.username, dba.created, dba.last_login
  ORDER BY labels;