# Database Hotel API

## About the Database Hotel API

The Database Hotel API (dbh from now on) is a simple API that can be used for creating an managing schemas on one or
more Oracle database servers. The API is designed for both creating schemas and providing connection information 
(like usernames and passwords) at a later point in time. The consequence of this is that the API will store user names
and unhashed passwords since it will need to provide both a upon request. It is therefore critical that you are able
to restrict access to the database schema where the connection information is stored, or only use this application in
environments where where keeping passwords secret is non-critical (like in a development environment).


## Setting up a development environment

Dbh can easily be run locally either from your ide (IntellJ will be assumed) or
from gradle. Before you can run or build the application, however, there are a couple of prerequisites that must be
handled.

### Setup
 
 In order to use this project you must set repositories in your `~/.gradle/init.gradle` file
 
     allprojects {
       ext {
         springBootDevtools = true
         repos = {
           maven { url "http://aurora/nexus/content/groups/public" }
           mavenCentral()
         }
       }
       repositories repos
       buildscript {
         repositories repos
       }
     }

We use a local repository for distributionUrl in our gradle-wrapper.properties, you need to change it to a public repo in order to use the gradlew command. `../gradle/wrapper/gradle-wrapper.properties`

    <...>
    distributionUrl=https\://services.gradle.org/distributions/gradle-<version>-bin.zip
    <...>

### Running postgres locally

    docker-compose up
    
### Connecting to postgres in the aurora-build namespace (for unit tests)

    oc port-forward (oc get pods | grep postgresql | awk '{print $1;}') 35432:5432

or

    oc port-forward (oc get pods -o=custom-columns=NAME:.metadata.name --no-headers -l deploymentconfig=postgresql) 35432:5432
    
Will forward port 5432 of the postgresql pod to localhost:35432. Connect using 
* Jdbc url: ```jdbc:postgresql://localhost:35432/postgres```.
* Username: ```postgres```
* Password: ```postgres```

### Oracle JDBC driver

You will need to provide the Oracle JDBC driver as this dependency does not exist (legally) in any public maven repository.
Please read the readme file in the ```lib``` folder for more information. Once this jar file is in place, you will be
able to compile and build the application.

As an alternative you may also use [https://docs.gradle.org/current/userguide/init_scripts.html](Gradle Init Scrips) if 
you want to register your own artifact repository (like an on prem Nexus) that already has the Oracle JDBC driver
installed. That will likely be required if you want to build this on a CI server, for instance. You may also be able to
use the Nexus registration features of the [https://github.com/Skatteetaten/aurora-gradle-plugin](Aurora Gradle Plugin)
directly for registering an on-prem Nexus.

### Configuring database access

You will need access to an Oracle database server both for running the unit tests and for local development. It is
recommended that you use a dedicated server for these purposes and not reuse any existing server that is used for
other services as well. Especially running the unit tests will create and drop schemas, and the cleanup mechanism
may interfere with or delete existing schemas in the database.

#### Configuring development and tests to use an external server

The two configuration files that handle general configuration (including config for development) and for tests are
```src/main/resources/application.yml``` and ```src/test/resources/application.yml```. They both have default config
that will try to access a database on localhost:1521. This can be overridden for both development and test.

The most convenient way to override settings for development and test is to use the "Home folder properties file"
feature of Spring Boot. By creating a file ```~/.spring-boot-devtools.properties``` and setting a few properties you
can control which database will be used.

    dbh.dev.db.host = dbhost.example.com
    dbh.dev.db.service = dbhotel
    dbh.dev.db.instanceName = test-dev
    dbh.dev.db.username = aos_api_user
    dbh.dev.db.password = dbh
    dbh.dev.db.clientService = dbhotel
    dbh.dev.db.oracleScriptRequired = false
    
    dbh.test.db.host = dbhost.example.com
    dbh.test.db.service = dbhotel
    dbh.test.db.instanceName = test-dev
    dbh.test.db.username = aos_api_user
    dbh.test.db.password = dbh
    dbh.test.db.clientService = dbhotel
    dbh.test.db.oracleScriptRequired = false

The same properties can also be set using system properties or environment variables as per the spring boot
conventions. See [https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html](Externalized Configuration)
for more details on all options spring provides for setting and overriding configuration.

#### Prepare a Oracle database server

A convenient way to get started with development is setting up your own Oracle database running from
a Docker image on your own development machine. There are no official Oracle database images available for download
from DockerHub, but there are, however, official and detailed instructions from Oracle on how you can build your own image.
Though it takes a little time to complete, the process is pretty easy and well worth it unless you already have a
dedicated server you can use for development or some other means (like a VM) to access a server.

The official instructions can be found here: https://github.com/oracle/docker-images/tree/master/OracleDatabase

You will need to build the Enterprise Edition image for the instructions below to work.

Once you have completed the image build process, run

    docker run --name=oracle-ee -p 1521:1521 -p 5500:5500 -e ORACLE_PWD=dbh -v /opt/oracle/oradata:/opt/oracle/oradata oracle/database:12.2.0.1-ee

to start the server. This takes quite a while the first time you run it, so be patient.

Once the server is running log in to create the objects that are required to support the dbh application
    
    docker exec -ti oracle-ee sqlplus sys/dbh@ORCLCDB as sysdba

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


### Running the application

From IntelliJ import the project from external model (gradle) and run the Main class.

Or, from the command line run

    ./gradlew bootRun


## Objects that are required to exist in the database

All users are given the role ```APP_USER``` when created. This will need to exist before creating new schemas.

    create profile APP_USER limit FAILED_LOGIN_ATTEMPTS 5;

All schemas are registered in a table called RESIDENTS.RESIDENTS. This table and schema need to exist and the user
used to connect to the database needs to have access to this table;

    create user RESIDENTS identified by dbh default tablespace "USERS" temporary tablespace "TEMP" quota unlimited on "USERS" profile APP_USER account unlock;
    alter session set CURRENT_SCHEMA=RESIDENTS;
    CREATE TABLE RESIDENTS
    (
        RESIDENT_NAME VARCHAR2(100) NOT NULL,
        RESIDENT_EMAIL VARCHAR2(100) NOT NULL,
        RESIDENT_SERVICE VARCHAR2(100) NOT NULL
    );
    grant select, insert, update on RESIDENTS.RESIDENTS to aos_api_user;

This table is only used for internal management purposes external to dbh itself. As such, it should probably be optional
to use it, but as of now it is not.


## Controlling access for the database user

Since the user dbh connects to the server with needs to be able to create and drop schemas, the user needs more
privileges than ordinary schema users.

**Roles**
 - Connect
 - Resource
 
**System Privileges**
 - ALTER USER
 - CREATE TABLESPACE
 - CREATE USER
 
**Object Privileges**
 - INSERT into RESIDENTS
 - SELECT from RESIDENTS
 - UPDATE from RESIDENTS
 - SELECT from SYS.DBA_DATA_FILES
 - SELECT from SYS.DBA_SEGMENTS
 - SELECT from SYS.DBA_USERS


## Configuration options

The following environment variables can be set to control the behaviour of dbh

Config field | Legal values | Description
--- | --- | ---
DATABASECONFIG_SCHEMALISTINGALLOWED | true/false | Enables or disables listing of schemas (```GET /api/v1/schema```)
DATABASECONFIG_DROPALLOWED | true/false | Enables or disables deletion of schemas (```DELETE /api/v1/schema/{id}```)
DATABASE_CONFIG_DATABASES_0_host | Example: dbhost.example.com | Host name of the first database server to manage.
DATABASE_CONFIG_DATABASES_0_service | Example: dbhotel | The service of the first database server to connect to.
DATABASE_CONFIG_DATABASES_0_instanceName | Example: test-dev | The unique logical name of this database.
DATABASE_CONFIG_DATABASES_0_username | Example: aos_api_user | The username to connect with
DATABASE_CONFIG_DATABASES_0_password | Example: dbh | 
DATABASE_CONFIG_DATABASES_0_clientService | Example: dbhotel | The service for generated jdbc urls for external clients to use when connecting to a generated schema.
DATABASE_CONFIG_DATABASES_0_oracleScriptRequired | true/false | Whether or not oracle script is required for this database instance 
DATABASE_CONFIG_DATABASES_n_host | | The host name of the nth database to mange
... | | Config options for the nth database are the same as for the first.

**Note** The first database instance configured will be used for storage of connection info for external schemas.

## Notes to self

Below are a few random notes not part of the actual documentation.

### How to disable password expiration

    ssh root@dbhost

    su - oracle
    sqlplus / as sysdba

then run

    ALTER PROFILE "DEFAULT" LIMIT PASSWORD_VERIFY_FUNCTION NULL;
    ALTER USER DATABASEHOTEL_INSTANCE_DATA identified by password account unlock;
    ALTER USER AOS_API_USER identified by dbh account unlock;
