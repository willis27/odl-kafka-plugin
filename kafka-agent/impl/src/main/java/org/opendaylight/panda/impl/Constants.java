/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.opendaylight.panda.impl;

/**
 *
 * @author williscc
 */
public interface Constants {
    
    public static final String PROP_SERIALIZATION               = "message.serialization";
    public static final String STR_SERIALIZATION_RAW            = "raw";
    public static final String STR_SERIALIZATION_AVRO           = "avro";
    public static final String PROP_KAFKA_TOPIC 				= "topic";


    // Specific section for PRODUCER
    // BEGIN Fields for reading propertie file
    //list of brokers used for bootstrapping knowledge about the rest of the cluster
    public static final String PROP_MD_BROKER_LIST              = "metadata.broker.list";
    public static final String PROP_PARTITIONER_CLASS           = "partitioner.class";
    public static final String PROP_PRODUCER_TYPE               = "producer.type";
    public static final String PROP_PRODUCER_COMPRESSION_CODEC  = "compression.codec";
    public static final String PROP_PRODUCER_MSG_ENCODER        = "serializer.class";
    public static final String PROP_PRODUCER_COMPRESSED_TOPIC   = "compressed.topic";
    // AS: Async
    public static final String PROP_PRODUCER_AS_Q_MAX_MS        = "queue.buffering.max.ms";
    public static final String PROP_PRODUCER_AS_Q_MAX_MSG       = "queue.buffering.max.messages";
    public static final String PROP_PRODUCER_AS_Q_TIMEOUT       = "queue.enqueue.timeout.ms";
    public static final String PROP_PRODUCER_AS_Q_BATCH_MSG     = "batch.num.messages";

    public static final String STR_PRODUCER_TYPE_SYNC           = "sync";
    public static final String STR_PRODUCER_TYPE_ASYNC          = "async";
    public static final String KAFKA_DEFAULT_MSG_ENCODER        = "kafka.serializer.DefaultEncoder";
                                                                                               
    public static final String KAFKA_DATAPLATFORM_MSG_DECODER   = "com.cisco.formatter.DataplatformDecoder";

    // Specific properties for reading properties for message stucture carried in databus
    public static final String  PROP_PRODUCER_MSG_SRC           = "dataplatform.message.src";
    public static final String  PROP_PRODUCER_NIC_4_HOST_IP     = "dataplatform.message.host_ip.fromnic";
    public static final String  PROP_PRODUCER_FORMAT_IP         = "dataplatform.message.host_ip.format";
    public static final String  PROP_PRODUCER_IP_DEFAULT        = "dataplatform.message.host_ip.default";



    // ConfluentIO
    public static final byte TEST_MAGIC_BYTE                    = 0x0;
    public static final String  MSG_HEADER_CONFLUENT_IO         = "confluent.io";

    // Internal code config
    public static final Integer MAIN_THREAD_SLEEP_INT           = 10000;
    public static final Integer TCP_THREAD_SLEEP_INT            = 1000;
    
}
