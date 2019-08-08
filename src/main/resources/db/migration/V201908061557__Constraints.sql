alter table schema_data add constraint schema_data_pk primary key (id);

alter table users add constraint users_schema_data_id_fk foreign key (schema_id) references schema_data (id) on delete cascade;
alter table users add constraint users_pk primary key (id);

alter table labels add constraint labels_schema_data_id_fk foreign key (schema_id) references schema_data (id) on delete cascade;
alter table labels add constraint labels_pk primary key (id);

alter table external_schema add constraint external_schema_schema_data_id_fk foreign key (schema_id) references schema_data (id) on delete cascade;
alter table external_schema add constraint external_schema_pk primary key (id);