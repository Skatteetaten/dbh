# Database Hotel API

## How to use containerized Oracle database

    docker run -p 1521:1521 -p 5500:5500 -v /opt/oracle/oradata:/opt/oracle/oradata oracle/database:12.1.0.2-ee
    
    sqlplus sys/pass@//localhost:1521/ORCLCDB as sysdba
    exec DBMS_SERVICE.CREATE_SERVICE('dbhotel','dbhotel');
    exec DBMS_SERVICE.START_SERVICE('dbhotel');
    
    alter session set "_ORACLE_SCRIPT"=true;
    create profile APP_USER limit FAILED_LOGIN_ATTEMPTS 50000;
    create user aos_api_user identified by dbh default tablespace "USERS" temporary tablespace "TEMP" quota unlimited on "USERS" profile APP_USER account unlock;
    
    grant connect,resource,sysdba to aos_api_user;
    alter user aos_api_user quota unlimited on USERS;
    
    create user RESIDENTS identified by dbh default tablespace "USERS" temporary tablespace "TEMP" quota unlimited on "USERS" profile APP_USER account unlock;
    alter session set CURRENT_SCHEMA=RESIDENTS;
    CREATE TABLE RESIDENTS
    (
        RESIDENT_NAME VARCHAR2(100) NOT NULL,
        RESIDENT_EMAIL VARCHAR2(100) NOT NULL,
        RESIDENT_SERVICE VARCHAR2(100) NOT NULL
    );
    grant select, insert, update on RESIDENTS.RESIDENTS to aos_api_user;
    
    
## How to disable password expiration

    ssh root@dbhost

    su - oracle
    sqlplus / as sysdba

then run

    ALTER PROFILE "DEFAULT" LIMIT PASSWORD_VERIFY_FUNCTION NULL;
    ALTER USER DATABASEHOTEL_INSTANCE_DATA identified by password account unlock;
    ALTER USER AOS_API_USER identified by dbh account unlock;