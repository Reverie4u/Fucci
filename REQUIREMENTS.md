# Requirements

## Hardware
* Supported OS: Ubuntu 20.04
* Memory: 8 GB
* CPUs: 2

## Software
* [Java 11](https://www.oracle.com/java/technologies/downloads/#java11) or above (`sudo apt install openjdk-11-jdk` on Ubuntu)
* [Maven](https://maven.apache.org/) (`sudo apt install maven` on Ubuntu)
* [Docker](https://www.docker.com/) for deploying tested DBMSs if necessary
* Supported DBMSs: 
  * [MySQL](https://hub.docker.com/_/mysql) (tested version: 8.0.25, using Docker container)
  * [MariaDB](https://hub.docker.com/_/mariadb) (tested version: 10.5.12, using Docker container)
  * [TiDB](https://docs.pingcap.com/tidb/stable/quick-start-with-tidb) (tested version: 5.2.0, with 1 TiDB instance, 1 TiKV instances and 1 PD instances)