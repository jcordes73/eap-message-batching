## Goal
The goal of this project is to demonstrate on how to improve transactional performance when a message and a datasource are involved by batching multiple messages.
## Preparation
###Create VM
First create a VM (I have used KVM for that purpose) with 4 vCPUs and 4 GB of RAM using a RHEL 7.2 ISO.
###Subscribe
Now register the machine and subscribe it.

	subscription-manager register
	Registering to: subscription.rhn.redhat.com:443/subscription
	Username: <your user>
	Password: <your password>
	The system has been registered with ID: <your ID>

	subscription-manager attach
	Installed Product Current Status:
	Product Name: Red Hat Enterprise Linux Server
	Status:       Subscribed
### Install additional packages
	yum install -y java-1.8.0-openjdk-devel.x86_64
	yum install -y unzip
	yum install -y net-tools
Now check the java version

	java -version
	openjdk version "1.8.0_111"
	OpenJDK Runtime Environment (build 1.8.0_111-b15)
	OpenJDK 64-Bit Server VM (build 25.111-b15, mixed mode)

### Configure firewall
	firewall-cmd --zone=public --add-port=8080/tcp --permanent
	firewall-cmd --zone=public --add-port=9990/tcp --permanent
	firewall-cmd --reload
### Install postgres (9.2.13)
	yum install -y postgresql-server postgresql-jdbc
	service postgresql initdb
	service postgresql start
	su - postgres

	psql
	ALTER USER postgres PASSWORD 'password';
	CREATE USER jbossperf PASSWORD 'jbossperf';
	CREATE DATABASE jbossperf OWNER jbossperf;
	ALTER USER jbossperf SET statement_timeout=0;
	\q

In **/var/lib/pgsql/data/pg_hba.conf** set the following

	local   all             all                                     md5
	# IPv4 local connections:
	host    all             all             127.0.0.1/32            md5
	host    all             all             192.168.122.155/24      md5
	# IPv6 local connections:
	host    all             all             ::1/128                 md5

In **/var/lib/pgsql/data/postgresql.conf** set the following

	listen_addresses = '*'
	shared_buffers = 256MB # default is 32MB
	work_mem = 1MB # default is 1MB
	checkpoint_segments = 10 # default is 3
	max_prepared_transactions = 10000
	effective_cache_size = 512MB
	random_page_cost=1.0
	effective_io_concurrency = 8

Now restart PostgreSQL

	service postgresql restart

### Install JBoss EAP 7

* Download JBoss EAP 7 from https://access.redhat.com/jbossnetwork/restricted/softwareDownload.html?softwareId=43891
* Download JBoss EAP 7 Update 02 from https://access.redhat.com/jbossnetwork/restricted/softwareDownload.html?softwareId=46431
* scp jboss-eap-7.0.0.zip jboss-eap-7.0.2-patch.zip root@**VM-IP**:~/
* unzip -d /opt/jboss jboss-eap-7.0.0.zip

In **JBOSS_HOME**/bin/standalone.conf add

	JAVA_OPTS="-Xmx3072m -XX:NewSize=1536m -XX:MaxNewSize=1536m -XX:+AggressiveHeap -Xss512k -XX:MetaspaceSize=96M -XX:MaxMetaspaceSize=256m -Djava.net.preferIPv4Stack=true"
	JAVA_OPTS="$JAVA_OPTS -XX:+PrintGC -Xloggc:gc.log -XX:+PrintGCDetails -XX:+PrintGCDateStamps"
	JAVA_OPTS="$JAVA_OPTS -Djava.security.egd=file:/dev/urandom"

Now start-up JBoss EAP

	bin/standalone.sh -c standalone-full.xml > nohup.out 2>&1 &
	bin/jboss-cli.sh -c
	patch apply --path ~/jboss-eap-7.0.2-patch.zip
	shutdown

	bin/add-user.sh -u admin -p 'redhat2016!' -g admin
	bin/add-user.sh -a -u artemis -p 'artemis2016!' -g guest
	bin/standalone.sh -Djboss.bind.address=**VM-IP**

	/subsystem=ee/service=default-bindings:undefine-attribute(name=datasource)
	module add --name=org.postgresql --resources=/usr/share/java/postgresql-jdbc.jar --dependencies=javax.api,javax.transaction.api
	/subsystem=datasources/jdbc-driver=postgresql:add(driver-name=postgresql,driver-module-name=org.postgresql,driver-xa-datasource-class-name=org.postgresql.xa.PGXADataSource)
	xa-data-source add --name=jboss-perf-test --jndi-name="java:jboss/datasources/jboss-perf-testDS" --driver-name=postgresql --min-pool-size=10 --max-pool-size=60 --user-name=jbossperf --password=jbossperf --valid-connection-checker-class-name=org.jboss.jca.adapters.jdbc.extensions.postgres.PostgreSQLValidConnectionChecker --exception-sorter-class-name=org.jboss.jca.adapters.jdbc.extensions.postgres.PostgreSQLExceptionSorter --xa-datasource-properties={["ServerName"="<VM-IP>"]}
	/subsystem=datasources/xa-data-source=jboss-perf-test/xa-datasource-properties=PortNumber:add(value=5432)
	/subsystem=datasources/xa-data-source=jboss-perf-test/xa-datasource-properties=DatabaseName:add(value=jbossperf)
	/subsystem=datasources/xa-data-source=jboss-perf-test:write-attribute(name=statistics-enabled, value=true)
	/subsystem=datasources/xa-data-source=jboss-perf-test:write-attribute(name=prepared-statements-cache-size,value=100)

## Build, Deploy & Test

### Build

To build the software run

	mvn clean install

### Deploy

To deploy the application do the following

	cp jboss-perf-test-ear/target/jboss-perf-test-ear.ear <JBOSS_HOME>/standalone/deployments

### Test

To do load-testing execute

**JMETER_HOME**/bin/jmeter jmeter/queue_db_test.jmx

## Optimization

### LRCO (Least Recent Commit Optimization)

"Used when a single non-xa capable resource is used in 2pc" (https://access.redhat.com/documentation/en/red-hat-jboss-enterprise-application-platform/7.0/single/development-guide/#about_the_lrco_optimization_for_single_phase_commit_1pc)

For this optimization , remove the datasource jboss-perf-test created earlier and replace it via the following CLI:

	/subsystem=datasources/data-source=jboss-perf-test:add(jndi-name="java:jboss/datasources/jboss-perf-testDS", driver-name=postgresql, min-pool-size=10, max-pool-size=60, user-name=jbossperf, password=jbossperf, connection-url="jdbc:postgresql://<VM-IP>/jbossperf", connectable="true")
	/subsystem=datasources/data-source=jboss-perf-test:write-attribute(name=prepared-statements-cache-size, value=100)
	/subsystem=datasources/data-source=jboss-perf-test:write-attribute(name=valid-connection-checker-class-name, value=org.jboss.jca.adapters.jdbc.extensions.postgres.PostgreSQLValidConnectionChecker)
	/subsystem=datasources/data-source=jboss-perf-test:write-attribute(name=exception-sorter-class-name,value=org.jboss.jca.adapters.jdbc.extensions.postgres.PostgreSQLExceptionSorter)
	/subsystem=datasources/data-source=jboss-perf-test:write-attribute(name=statistics-enabled,value=true)
	/subsystem=transactions/commit-markable-resource="java:jboss/datasources/jboss-javaee-multiDS":add(name=xids,batch-size=100,immediate-cleanup=false)

You also need to modify the database like this

	CREATE TABLE xids (xid bytea, transactionManagerID varchar(64), actionuid bytea);
	CREATE UNIQUE INDEX index_xid ON xids (xid);

## Messaging
	/system-property=clusterUser:add(value="admin")
	/system-property=clusterPassword:add(value="password")
	/subsystem=messaging-activemq/server=default:write-attribute(name=cluster-user,value=${clusterUser})
	/subsystem=messaging-activemq/server=default:write-attribute(name=cluster-password,value=${clusterPassword})
	/subsystem=messaging-activemq/server=default/address-setting=#:write-attribute(name=max-size-bytes,value=167772160)
	jms-queue add --queue-address=member --entries=jms/queue/member,java:jboss/exported/jms/queue/member
	/subsystem=messaging-activemq/server=default/connection-factory=RemoteConnectionFactory:write-attribute(name=block-on-durable-send,value=false)

	/subsystem=ejb3/strict-max-bean-instance-pool=slsb-strict-max-pool:undefine-attribute(name=derive-size) 
	/subsystem=ejb3/strict-max-bean-instance-pool=slsb-strict-max-pool:write-attribute(name=max-pool-size,value=60)

	/subsystem=ejb3/strict-max-bean-instance-pool=mdb-strict-max-pool:undefine-attribute(name=derive-size)
	/subsystem=ejb3/strict-max-bean-instance-pool=mdb-strict-max-pool:write-attribute(name=max-pool-size,value=60)

	/subsystem=messaging-activemq/server=default/pooled-connection-factory=activemq-ra:write-attribute(name=min-pool-size,value=10)
	/subsystem=messaging-activemq/server=default/pooled-connection-factory=activemq-ra:write-attribute(name=max-pool-size,value=70)

## Checking the database
	psql -d jbossperf -U jbossperf
	jbossperf=> \dt
	public | registrant | table | jbossperf
	public | xids       | table | jbossperf