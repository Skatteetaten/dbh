ALTER TABLE SCHEMA_DATA
    ADD CREATED_DATE TIMESTAMP;

update SCHEMA_DATA sd
set CREATED_DATE =
        (SELECT CREATED_DATE
         FROM EXTERNAL_SCHEMA es
         WHERE sd.ID = es.SCHEMA_ID)
where SCHEMA_TYPE = 'EXTERNAL';
