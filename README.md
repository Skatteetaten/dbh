# Database Hotel API

## Setting up a development environment

The Database Hotel API (dbh from now on) can easilly be run locally eather from your ide (IntellJ will be assumed) or
from gradle.

From IntelliJ import the project from external model (gradle) and run the Main class.

Or, from the command line run

    ./gradlew boot:run


## How to use containerized Oracle database

A convenient way to get started with development is setting up your own Oracle database running from
a Docker image on your own development machine. There are no official Oracle database images available for download
from GitHub, but there are, however, official and detailed instructions from Oracle on how you can build your own image.
Though it takes a little time to complete, the process is pretty easy and well worth it unless you already have a
dedicated server you can use for development or some other means (like a VM) to accesss a server.

The official instructions can be found here: https://github.com/oracle/docker-images/tree/master/OracleDatabase

We have found that the Standard Edition 2 image works well.

Once you have completed the image build process, run

    docker run --name=oracle-se -p 1521:1521 -p 5500:5500 -e ORACLE_PWD=dbh -v /Users/bent/tmp/oracle:/opt/oracle/oradata oracle/database:12.2.0.1-se2

to start the server. This takes quite a while the first time you run it, so be patient.

Once the server is running log in to create the objects that are required to support the dbh application
    
    sqlplus sys/dbh@//localhost:1521/ORCLCDB as sysdba
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