# prod-odl-kafka
##Overview
`odl-kafka-plugin` is an Opendaylight (ODL)  northbound plugin that allows real-time or near real-time event or telemetry data streaming into such a big-data platform as PaNDA. The key design goal of this plugin is to provide a genenric and configurable data connector that subscribes to southbound event source(s) via ODL's Event Topic Broker (ETB) on one side, and forward notifications to a Kafka endpoint.

The `odl-kafka-plugin` has been development using Lithium maven artetype and tested using ODL Lithium 0.3.0 container (distribution-karaf-0.3.0-Lithium). 

##Quick Start
######Step 1: Clone source code

######Step 2: Build from source

```
https://wiki.opendaylight.org/view/GettingStarted:Development_Environment_Setup#Edit_your_.7E.2F.m2.2Fsettings.xml
```

```
$cd hweventsource/
hweventsource$mvn clean install -Dcheckstyle.skip=true -DskipTests=true
$cd ../kafka-agent 
kafka-agent$mvn clean install -Dcheckstyle.skip=true -DskipTests=true
```
######Step 3: Start ODL container
```
$../hweventsource/karaf/target/assembly/bin/karaf
```
You can verify the installation of `hweventsource` modules by running the command from ODL console as follows:
```
opendaylight-user@root>feature:list | grep 'hweventsource'
odl-hweventsource-api             | 1.0-Lithium      | x         | odl-hweventsource-1.0-Lithium        | OpenDaylight :: hweventsource :: api              
odl-hweventsource                 | 1.0-Lithium      | x         | odl-hweventsource-1.0-Lithium        | OpenDaylight :: hweventsource                     
odl-hweventsource-rest            | 1.0-Lithium      | x         | odl-hweventsource-1.0-Lithium        | OpenDaylight :: hweventsource :: REST             
odl-hweventsource-ui              | 1.0-Lithium      | x         | odl-hweventsource-1.0-Lithium        | OpenDaylight :: hweventsource :: UI               
```
######Step 4: Deploy kafka agent plugin
```
$cp kafka-agent/features/target/kafka-agent-features-1.0.0-Lithium.kar ./hweventsource/karaf/target/assembly/deploy/
```

`kafka-agent` should then be automatically deployed to the ODL container. To verify the success of the deployment, run `feature-list` command from ODL console, and you should see outputs as below.

```
opendaylight-user@root>feature:list | grep 'kafka-agent'
odl-kafka-agent-api               | 1.0.0-Lithium    | x         | odl-kafka-agent-1.0.0-Lithium        | OpenDaylight :: kafka-agent :: api                
odl-kafka-agent                   | 1.0.0-Lithium    | x         | odl-kafka-agent-1.0.0-Lithium        | OpenDaylight :: kafka-agent                       
odl-kafka-agent-rest              | 1.0.0-Lithium    | x         | odl-kafka-agent-1.0.0-Lithium        | OpenDaylight :: kafka-agent :: REST               
odl-kafka-agent-ui                | 1.0.0-Lithium    | x         | odl-kafka-agent-1.0.0-Lithium        | OpenDaylight :: kafka-agent :: UI 
```
######Step 5: Start Zookeeper and Kafka Server
```
$cd $KAFKA_HOME
$bin/zookeeper-server-start.sh config/zookeeper.properties
$bin/kafka-server-start.sh config/server.properties
```
Create a 'test' topic
```
$bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic test
```
Start a consumer and listens to the topic
```
$bin/kafka-console-consumer.sh --zookeeper localhost:2181 --topic odlmsg --from-beginning
```
######Step 6: Congifure `kafka-agent`
`kafka-agent` is configured using RESTCONF api, and requires four mandatory properties to start kafka message producer:
 * metadata-broker-list: a list of zookeeper endpoints
 * topic: kafka message topic
 * produce-type: sync|async
 * compression-codec: none|gzip|snappy|lz4 (NOTE: lz4 is yet supported, as no appropriate bundle repository found.)
 * message-serialization: raw|avro

```
$ curl --user admin:admin 
       --request PUT http://localhost:8080/restconf/config/kafka-agent:kafka-producer-config 
       --data '{kafka-producer-config: {metadata-broker-list: "127.0.0.1:9092",topic: "test",producer-type: "sync", compression-codec: "none", message-serialization: "raw"}}' 
       --header "Content-Type:application/yang.data+json"
```
To verify configurations are set properly, run:

```
$ curl --user admin:admin --request GET http://localhost:8080/restconf/config/kafka-agent:kafka-producer-config --header "Content-Type:application/yang.data+json"
```
You should see the output as:

```
{"kafka-producer-config":{"compression-codec":"none","topic":"test","metadata-broker-list":"127.0.0.1:9092","message-serialization":"raw","producer-type":"sync"}}
```

######Step 7: Start event source to generate messages.
Run the following curl command to trigger the sample event source. 
```
$curl --user admin:admin --request POST http://localhost:8181/restconf/operations/event-aggregator:create-topic --header "Content-Type:application/json" --data '{ "event-aggregator:input": {"notification-pattern": "**", "node-id-pattern":"*"}}'
```
If successful, you should be able to see a topic-id is generated, for exmaple:
```
{"output":{"topic-id":"d48ac918-d8d3-4425-8945-0c7d98aa29a0"}}
```

Meanwhile keep an eye on the Kafka consumer console, you should see messages streamed from both sources (SampleEventSource01 and SampleEventSource00) continuously. 

```
...
<?xml version="1.0" encoding="UTF-8"?><payload xmlns="urn:cisco:params:xml:ns:yang:messagebus:eventaggregator"><SampleEventSourceNotification><Source>EventSourceSample01</Source><Message>Hello World [Mon Sep 28 14:37:55 BST 2015]</Message></SampleEventSourceNotification></payload>
<?xml version="1.0" encoding="UTF-8"?><payload xmlns="urn:cisco:params:xml:ns:yang:messagebus:eventaggregator"><SampleEventSourceNotification><Source>EventSourceSample00</Source><Message>Hello World [Mon Sep 28 14:37:58 BST 2015]</Message></SampleEventSourceNotification></payload>
<?xml version="1.0" encoding="UTF-8"?><payload xmlns="urn:cisco:params:xml:ns:yang:messagebus:eventaggregator"><SampleEventSourceNotification><Source>EventSourceSample01</Source><Message>Hello World [Mon Sep 28 14:37:58 BST 2015]</Message></SampleEventSourceNotification></payload>
...
```


##Sending data to PaNDA
If you would like to share data to PaNDA, Cisco big data analytics plaform, you will need to contact PaNDA team and request connectivity settings for configurating Kafka-plugin.

   


