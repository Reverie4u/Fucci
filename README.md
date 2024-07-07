# Fucci

This is the artifact for the paper "Fucci: Database Transaction Fuzzing via Random Conflict Construction and Multilevel Constraint Solving". 

Fucci is a tool to automatically detect transaction bugs in DBMSs.

## Overview

The artifact contains two structure:

* cases/: Contains the test cases that can trigger the bugs found by Fucci.
* src/main/java/fucci/: Contains the source code of Fucci.
G
## Requirements

[REQUIREMENTS.md](REQUIREMENTS.md) describes the required hardware and software of the artifact.

## Setup

[INSTALL.md](INSTALL.md) provides information on how to run the artifact and verify that it works correctly.

# Running Fucci

## Compile

The following commands put all dependencies into the subdirectory target/lib/ and create a Jar for Fucci:
```bash
cd Fucci
mvn package -Dmaven.test.skip=true
```

## Usage
Important Parameters:
* `--dbms`: The type of the target DBMS. Default: `mysql`, can also be `mariadb` and `tidb`
* `--host`: The host used to log into the target DBMS. Default: `127.0.0.1`
* `--port`: The port used to log into the target DBMS. Default: `3306`
* `--username`: The username used to log into the target DBMS. Default: `"root"`
* `--password`: The password used to log into the target DBMS. Default: `""`
* `--db`: The database name to run tests. Default: `test`
* `--table`: The table name to run tests. Default: `Fucci`
* `--set-case`: Whether you use a predefined test case as test input. Default: `false`
* `--case-file`: The path of the text file that contains the predefined test case. Default: `""`

After following the instructions to install our test version of the three DBMSs (i.e., MySQL, MariaDB and TiDB),
you will get some parameters required for running Fucci:
* MySQL:
  * `host`: `127.0.0.1`
  * `port`: `10003`
  * `username`: `"root"`
  * `password`: `"root"`
* MariaDB:
  * `host`: `127.0.0.1`
  * `port`: `10004`
  * `username`: `"root"`
  * `password`: `"root"`
* TiDB:
  * `host`: `127.0.0.1`
  * `port`: `4000`
  * `username`: `"root"`
  * `password`: `""`

**NOTE:**
If you already have deployed these DBMSs, the relevant parameters need to be replaced with those of your deployed DBMSs.

## Fucci testing

The following commands automatically generate test cases for testing DBMSs.

For example, we connect to MariaDB (`--dbms mysql --host 127.0.0.1 --port 10004 --username root --password root`) and run Fucci in MariaDB. 
We specify that the name of the tested table is t (`--table t`).
```bash
cd target
java -jar Fucci*.jar --dbms mariadb --host 127.0.0.1 --port 10004 --username root --password root --table t
```

The outputs are recorded in `Fucci.log`.