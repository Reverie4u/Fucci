# Installation Instructions

This document contains information on how to run the artifact and verify that it works correctly.

## Deploying MySQL

1. Download MySQL

This command downloads MySQL (version 8.0.25) from Docker locally:
```bash
docker pull mysql:8.0.25
```

2. Starting MySQL

This command starts MySQL:
```bash
docker run -p 127.0.0.1:10003:3306 --name mysql-8.0.25 -e MYSQL_ROOT_PASSWORD=root -d mysql:8.0.25
```

**NOTE:**
The `host` of MySQL is `127.0.0.1`. The `port` of MySQL is `10003`. The `username` and `password` of MySQL is `"root"`.

## Deploying MariaDB

1. Download MariaDB

This command downloads MariaDB (version 10.5.12) from Docker locally:
```bash
docker pull mariadb:10.5.12
```

2. Starting MariaDB

This command starts MariaDB:
```bash
docker run -p 127.0.0.1:10004:3306 --name mariadb-10.5.12 -e MARIADB_ROOT_PASSWORD=root -d mariadb:10.5.12
```

**NOTE:**
The `host` of MariaDB is `127.0.0.1`. The `port` of MariaDB is `10004`. The `username` and `password` of MariaDB is `"root"`.

## Deploying TiDB

1. Download TiDB

This command downloads and installs TiDB (version 5.2.0) locally:
```bash
curl --proto '=https' --tlsv1.2 -sSf https://tiup-mirrors.pingcap.com/install.sh | sh
```

2. Declaring the global environment variable

**NOTE:**
After the installation, TiUP displays the absolute path of the corresponding Shell profile file. 
You need to modify `${your_shell_profile}` in the following `source` command according to the path.


This command declares the global environment variable:
```bash
source ${your_shell_profile}
```

3. Starting TiDB

This command starts TiDB with 1 TiDB instance, 1 TiKV instance, 1 PD instance, and 1 TiFlash instance:

```bash
tiup playground v5.2.0
```
**NOTE:**
The default `host` of TiDB is `127.0.0.1`. The default `port` of TiDB is `4000`. The default `username` of TiDB is `"root"`, with empty `password`.

## Testing the setup

We take MariaDB as an example to show how to use Fucci locally.

1. Compile

The following commands put all dependencies into the subdirectory target/lib/ and create a Jar for Fucci:
```bash
cd Fucci
mvn package -Dmaven.test.skip=true
```

2. Run with a test case

The following commands use predefined test case in text file as input:
```bash
cd target
java -jar Fucci*.jar --dbms mariadb --host 127.0.0.1 --port 10004 --username root --password root --db test --set-case --case-file ../cases/test.txt --table t
```

The outputs of these commands include five parts. All the outputs are recorded in `Fucci.log`.