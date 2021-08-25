#!/bin/bash
######Author : Swati Sharma
######Script to make file exported from Derby compatible for MySQL import

source /opt/derby/derby-script-files/derby.properties
/opt/derby/db-derby-10.14.2.0-bin/bin/dblook -d "jdbc:derby:$derbyDbPath;" -o  '/opt/derby/derby-metadata/kilda_db.sql'

sed -i s/\"/\'/g /opt/derby/derby-metadata/kilda_db.sql

sed -i s/"CREATE SCHEMA '"$oldDB"'"/"CREATE SCHEMA IF NOT EXISTS '"$newDB"'"/g /opt/derby/derby-metadata/kilda_db.sql

sed -i s/"'"$oldDB"'"/"'"$newDB"'"/g /opt/derby/derby-metadata/kilda_db.sql
sed -i s/\'/''/g /opt/derby/derby-metadata/kilda_db.sql
sed -i s/"GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1)"/""/g /opt/derby/derby-metadata/kilda_db.sql
sed -i s/"GENERATED BY DEFAULT AS IDENTITY (START WITH 100, INCREMENT BY 1)"/""/g /opt/derby/derby-metadata/kilda_db.sql
sed -i s/","/" PRIMARY KEY auto_increment,"/1 /opt/derby/derby-metadata/kilda_db.sql
###Remove Sequence and mapping tables as mappings will be added later
sed -i /"CREATE SEQUENCE"/,/";"/d /opt/derby/derby-metadata/kilda_db.sql
sed -i "/CREATE TABLE $newDB.SAML_USER_ROLES/d" /opt/derby/derby-metadata/kilda_db.sql
sed -i "/CREATE TABLE $newDB.USER_ROLE/d" /opt/derby/derby-metadata/kilda_db.sql
sed -i "/CREATE TABLE $newDB.ROLE_PERMISSION/d" /opt/derby/derby-metadata/kilda_db.sql

###Add Mapping tables without defining primary keys
sed -i "/^CREATE TABLE $newDB.KILDA_SWITCH_STORE_URLS*/i CREATE TABLE $newDB.SAML_USER_ROLES (ID BIGINT NOT NULL, ROLE_ID BIGINT NOT NULL );" /opt/derby/derby-metadata/kilda_db.sql
sed -i "/^CREATE TABLE $newDB.KILDA_SWITCH_STORE_URLS*/i CREATE TABLE $newDB.USER_ROLE (USER_ID BIGINT NOT NULL, ROLE_ID BIGINT NOT NULL);" /opt/derby/derby-metadata/kilda_db.sql
sed -i "/^CREATE TABLE $newDB.KILDA_SWITCH_STORE_URLS*/i CREATE TABLE $newDB.ROLE_PERMISSION (PERMISSION_ID BIGINT NOT NULL, ROLE_ID BIGINT NOT NULL);" /opt/derby/derby-metadata/kilda_db.sql
###Update SQLs to make it compatible with MYSQL
sed -i s/TIMESTAMP/DATETIME/g /opt/derby/derby-metadata/kilda_db.sql
sed -i s/"BOOLEAN"/"TINYINT"/g /opt/derby/derby-metadata/kilda_db.sql
sed -i s/"CLOB"/"BLOB"/g /opt/derby/derby-metadata/kilda_db.sql

###Update VERSION table to VERSION_ENTITY since Version is a reserved keyword in MySQL
sed -i "s;$newDB.VERSION;$newDB.VERSION_ENTITY;g" /opt/derby/derby-metadata/kilda_db.sql

###Remove Primary key statements as we are adding it to update
sed -i /"-- PRIMARY\/"/,/"-- FOREIGN"/{//!d} /opt/derby/derby-metadata/kilda_db.sql

echo "ALTER TABLE $newDB.ROLE_PERMISSION ADD CONSTRAINT SQL210528141756230 PRIMARY KEY (ROLE_ID, PERMISSION_ID);" >> /opt/derby/derby-metadata/kilda_db.sql
echo "ALTER TABLE $newDB.USER_ROLE ADD CONSTRAINT SQL210528141756380 PRIMARY KEY (ROLE_ID, USER_ID);" >> /opt/derby/derby-metadata/kilda_db.sql
echo "ALTER TABLE $newDB.SAML_USER_ROLES ADD CONSTRAINT SQL210528141756280 PRIMARY KEY (ROLE_ID, ID);" >> /opt/derby/derby-metadata/kilda_db.sql

echo "ALTER TABLE $newDB.KILDA_ROLE AUTO_INCREMENT = 100;" >> /opt/derby/derby-metadata/kilda_db.sql
echo "ALTER TABLE $newDB.KILDA_PERMISSION AUTO_INCREMENT = 100;" >> /opt/derby/derby-metadata/kilda_db.sql

mysql -u root -p < /opt/derby/derby-metadata/kilda_db.sql;
mysql -u root --database $newDB -p < /opt/derby/derby-script-files/derby-import.sql;