/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.opendaylight.panda.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventaggregator.rev141202.TopicId;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventaggregator.rev141202.TopicNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.kafka.agent.rev150922.KafkaProducerConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.kafka.agent.rev150922.KafkaProducerConfig.CompressionCodec;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.kafka.agent.rev150922.KafkaProducerConfig.MessageSerialization;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.kafka.agent.rev150922.KafkaProducerConfig.ProducerType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyXmlNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author williscc
 */
public class KafkaUserAgentImpl implements DOMNotificationListener, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaUserAgentImpl.class);

    private final ListenerRegistration<KafkaUserAgentImpl> notificationReg;

    private final NodeIdentifier EVENT_SOURCE_NODE = new NodeIdentifier(QName.create(TopicNotification.QNAME, "node-id"));
    private final NodeIdentifier PAYLOAD_NODE = new NodeIdentifier(QName.create(TopicNotification.QNAME, "payload"));
    private static final NodeIdentifier TOPIC_ID_ARG = new NodeIdentifier(QName.create(TopicNotification.QNAME, "topic-id"));
    
    private final SchemaPath TOPIC_NOTIFICATION_PATH = SchemaPath.create(true, TopicNotification.QNAME);

    private final static String DEFAULT_AVRO_SCHEMA = "dataplatform-raw.avsc";
    
    private final String DEFAULT_HOST_IP;

    private final Producer producer;
    
    private final String timestampXPath;
    
    private final String hostIpXPath;
    
    private final String messageSourceXPath;
    
    private final String defaultMessageSource;
    
    private final String topic;
    
    private final Set<String> registeredTopics = new HashSet<>();
    
    private static Schema schema;

    public static KafkaUserAgentImpl create(final DOMNotificationService notificationService, final KafkaProducerConfig configuration) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("in create()");
        }
        return new KafkaUserAgentImpl(notificationService, configuration);
    }

    @Override
    public void onNotification(DOMNotification notification) {
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("in onNotification()");
        }

        LOG.info("Notification received.");
        
        boolean isAcceptable = false;
        
        if (registeredTopics.isEmpty())
        {
            isAcceptable=true;
        }
        
        if (!isAcceptable)
        {
            LOG.info("topic filters are not empty; applying them now...");
            //check topic against filter list
            if(notification.getBody().getChild(TOPIC_ID_ARG).isPresent()){
                TopicId topicId = (TopicId) notification.getBody().getChild(TOPIC_ID_ARG).get().getValue();
                if (topicId != null)
                {
                    LOG.info("topic is parsed: " + topicId.getValue());
                    if(registeredTopics.contains(topicId.getValue())){
                        isAcceptable = true;
                        LOG.info("Topic accepted.");
                    }
                }
            }
        }
        
        try{
            
            if (producer!=null && isAcceptable)
            {
                String messageSource=null;
                Long timestamp = null;
                String hostIp=null;

                //processing message
                
                final String rawdata = this.parsePayLoad(notification);
                
                
                if (messageSourceXPath != null)
                {
                    LOG.info("evaluating " + messageSourceXPath + " against message payload...");
                    messageSource = this.evaluate(rawdata, messageSourceXPath);
                }
                
                if (messageSource == null)
                {
                    messageSource = this.defaultMessageSource;
                }
                
                if (messageSource == null)
                {
                    LOG.info("no message source xpath specified or invalid xpath statement. Use the node-id by default.");
                    final String nodeId = notification.getBody().getChild(EVENT_SOURCE_NODE).get().getValue().toString();
                    messageSource = nodeId;
                }
                
                LOG.info("src = " + messageSource);

                if (timestampXPath != null)
                {
                    LOG.info("evaluating " + timestampXPath + " against message payload ...");
                    timestamp = Long.valueOf(this.evaluate(rawdata, timestampXPath));
                    
                }
                
                if (timestamp == null)
                {
                    LOG.info("no timestampe xpath specified or invalid xpath statement. Use the system time by default");
                    timestamp = System.currentTimeMillis();
                }
                
                LOG.info("timestamp = " + timestamp);

                
                if (hostIpXPath != null)
                {
                    LOG.info("evaluating " + hostIpXPath + " against message payload ...");
                    hostIp = this.evaluate(rawdata, hostIpXPath);
                }
                
                if (hostIp == null)
                {
                    LOG.info("not host ip xpath specified, use the ODL host ip by default");
                    hostIp = DEFAULT_HOST_IP;
                    
                }
                LOG.info("host-ip = " + hostIp);
                

                LOG.info("about to send message to Kafka ...");
                KeyedMessage<String, byte[]> keyedMessage=null;
                if (schema == null)
                {
                    LOG.info("sending data without serialization.");
                    keyedMessage = new KeyedMessage<>(topic, rawdata.getBytes());
                }
                else
                {
                    LOG.info("sending data using avro serialisation.");
                    keyedMessage = new KeyedMessage<>(topic, encode(timestamp, hostIp, messageSource, rawdata));
                    /*
                    GenericRecord srec = new GenericData.Record(schema);
                    srec.put("src", messageSource);
                    srec.put("timestamp", timestamp);
                    srec.put("host_ip", hostIp);
                    srec.put("rawdata", (ByteBuffer) ByteBuffer.wrap(rawdata.getBytes()));
                    keyedMessage = new KeyedMessage<String, Object>(topic, srec);
                    */
                }
                producer.send(keyedMessage);
                LOG.info("message sent.");
            }
        }catch(Exception ex)
        {
            LOG.error("Error while sending message to Kafka: ", ex);
            
        }
    }

    @Override
    public void close() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("in close()");
        }
        notificationReg.close();
        
        producer.close();
        
        
    }
    
    //Private methods --
    
    /**
     * Constructor
     * @param notificationService
     * @param configuration 
     */
    private KafkaUserAgentImpl(final DOMNotificationService notificationService, final KafkaProducerConfig configuration) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("in KafkaUserAgentImpl()");
        }

        LOG.info("registering to Notification Service broker");
        //register this as a listener to notification service and listens all messages published to message-bus.
        notificationReg = notificationService.registerNotificationListener(this, TOPIC_NOTIFICATION_PATH);

        LOG.info("Creating kafka producer instance using the following configuration");
        LOG.info("metadata.broker.list -> " + configuration.getMetadataBrokerList());
        LOG.info("topic -> " + configuration.getTopic());
        
        try{
            String topicSubscriptions = configuration.getEventSubscriptions();
            if (topicSubscriptions !=null && !topicSubscriptions.isEmpty())
            {
                LOG.info("adding topic subscriptions : " + topicSubscriptions);
                registeredTopics.addAll(Arrays.asList(topicSubscriptions.split(", ")));
            }
            topic = configuration.getTopic();
            if (topic ==null)
            {
                throw new Exception ("topic is a mandatory configuration. Kafka producer is not initialised.");
            }
            timestampXPath = configuration.getDpTimestampXpath();
            hostIpXPath = configuration.getDpMessageHostIpXpath();
            messageSourceXPath = configuration.getDpMessageSourceXpath();
            defaultMessageSource = configuration.getDefaultMessageSource();
            
            if (configuration.getDefaultHostIp()!=null)
            {
                DEFAULT_HOST_IP = configuration.getDefaultHostIp();
            }else
            {
                DEFAULT_HOST_IP = "0.0.0.0";
            }
            LOG.info("default host ip is set: " + DEFAULT_HOST_IP);
            producer = new Producer<>(createProducerConfig(configuration));
            
        }catch(Exception ex)
        {
            LOG.error(ex.getMessage());
            throw new RuntimeException(ex);
            
        }
    }
    
    //Private methods --
    private byte[] encode(Long timestamp, String hostIp, String src, String payload) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
        GenericRecord datum = new GenericData.Record(schema);
        datum.put("src", src);
        datum.put("timestamp", timestamp);
        datum.put("host_ip", hostIp);
        datum.put("rawdata", ByteBuffer.wrap(payload.getBytes("UTF-8")));
        GenericDatumWriter writer = new GenericDatumWriter<GenericRecord>(schema);
        writer.write(datum, encoder);
        encoder.flush();
        out.close();
        return out.toByteArray();
    }
    
    private static ProducerConfig createProducerConfig(KafkaProducerConfig configuration) throws Exception {
        if (LOG.isDebugEnabled())
        {
            LOG.debug("in createProducerConfig()");
        }
        
        Properties props = new Properties();

        //check existence of mandatory configurations
        String brokerList = configuration.getMetadataBrokerList();
        ProducerType producerType = configuration.getProducerType();
        CompressionCodec compCodec = configuration.getCompressionCodec();
        MessageSerialization ser = configuration.getMessageSerialization();
        
        String avroSchema = configuration.getAvroSchema();
        //Long avroSchemaId = configuration.getAvroSchemaId();
        
        // Mandatory ones
        //try {
            if (brokerList == null)
            {
                throw new Exception("metadata-broker-list is a mandatory configuration. Kafka producer is not intialised.");
            }
            
            
            if (producerType == null)
            {
                throw new Exception("producer-type is a mandatory configuration. Kafka producer is not initialised.");
            }
            
            if(compCodec == null)
            {
                throw new Exception("compression-codec is a mandatory configuration. Kafka producer is not initialised.");
            }
            
            if (ser == null)
            {
                throw new Exception ("message-serialization is a mandatory configuration. Kafka producer is not initialised.");
            }
            
             
            if (avroSchema == null)
            {
                avroSchema = DEFAULT_AVRO_SCHEMA;
                
            }
            LOG.info("avroSchema = " + avroSchema);
            
            
            //LOG.info("avroSchemaId (obtained from configuration) : " + avroSchemaId);
            /*if (avroSchemaId == null)
            {
                avroSchemaId = DEFAULT_AVRO_SCHEMA_ID;
            }
            LOG.info("avroSchemaId = " + avroSchemaId);
            */
            
            //set mandatory properties
            props.put(""+Constants.PROP_MD_BROKER_LIST,  brokerList);
            
            LOG.info("setting producer type property ...");
            switch (producerType.getIntValue()){
                case 0: props.put(""+Constants.PROP_PRODUCER_TYPE, "sync");
                        LOG.info("producer.type is set as sync");
                        break;
                case 1: props.put(""+Constants.PROP_PRODUCER_TYPE, "async");
                        LOG.info("producer.type is set as async");
                        break;
                default: throw new Exception("Unrecognised producer type " + producerType.getIntValue()+". Kafka producer is not intialised.");
            }
            
            LOG.info("setting compression codec property ...");
            switch(compCodec.getIntValue())
            {
                case 0: props.put(""+Constants.PROP_PRODUCER_COMPRESSION_CODEC, "none");
                        break;
                case 1: props.put(""+Constants.PROP_PRODUCER_COMPRESSION_CODEC, "gzip");
                        break;
                case 2: props.put(""+Constants.PROP_PRODUCER_COMPRESSION_CODEC, "snappy");
                        break;
                //case 3: props.put(""+Constants.PROP_PRODUCER_COMPRESSION_CODEC, "lz4");
                //        break;
                default: throw new Exception("Unrecognised compression encode type " + compCodec.getIntValue()+". Kafka producer is not intialised.");
            }
            
            
            LOG.info("setting serialization property ...");
            props.put(""+Constants.PROP_PRODUCER_MSG_ENCODER,Constants.KAFKA_DEFAULT_MSG_ENCODER);
            switch(ser.getIntValue())
            {
                case 0: //props.put(""+Constants.PROP_PRODUCER_MSG_ENCODER,Constants.KAFKA_DEFAULT_MSG_ENCODER);
                        LOG.info("No serialization set.");
                        schema = null;
                        break;
                case 1: //props.put(""+Constants.PROP_PRODUCER_MSG_ENCODER, DataplatformEncoder.class.getName());
                        //Constants.KAFKA_DATAPLATFORM_MSG_ENCODER);
                        //LOG.info("set dataplatform message encoder.");
                        //configure producer with addtional information
                        
                        //props.put(""+Constants.PROP_PRI_ENC_SCHEMA, avroSchema);
                        
                        //LOG.info("avro schema set as: " + avroSchema);
                        //props.put(""+Constants.PROP_PRI_ENC_SCHEMAID, avroSchemaId);
                        //LOG.info("avro schema id set as: " + avroSchemaId);
                        //props.put(""+Constants.PROP_PRI_ENC_EXTRAHEADER,(String) "false"); //set default value to false;
                        //load schema
                        LOG.info("load schema from classpath  ...");
                        schema = new Schema.Parser().parse(KafkaUserAgentImpl.class.getClassLoader().getResourceAsStream(avroSchema));
                        LOG.info("schema loaded.");
                        break;
                 default: throw new Exception("Unrecognised message serialization type " + ser.getIntValue()+". Kafka producer is not intialised.");
            }
            
            /*Enumeration keys = props.keys();
            while(keys.hasMoreElements())
            {
                String key = (String) keys.nextElement();
                LOG.info(key + " -> " + props.get(key).toString());
            }*/
            
            LOG.info("creating ProducerConfig instance...");
            return new ProducerConfig(props);
            /*
            String  msgSerialization=mainConfig.getProperty(Constants.PROP_SERIALIZATION);
            if (msgSerialization.equalsIgnoreCase(Constants.STR_SERIALIZATION_RAW)) {
                logger.debug("producer setting: no serialization required");
                props.put(""+Constants.PROP_PRODUCER_MSG_ENCODER,Constants.KAFKA_DEFAULT_MSG_ENCODER);
                        //Use the default binary encoder
            } else {
                logger.debug("producer setting: serialization required");
                String configExtra=mainConfig.getProperty(Constants.PROP_AVRO_EXTRA_HEADER);
                props.put(""+Constants.PROP_PRODUCER_MSG_ENCODER,Constants.KAFKA_DATAPLATFORM_MSG_ENCODER);
                props.put(""+Constants.PROP_PRI_ENC_SCHEMA,mainConfig.getProperty(Constants.PROP_AVRO_SCHEMA));
                props.put(""+Constants.PROP_PRI_ENC_SCHEMAID,mainConfig.getProperty(Constants.PROP_AVRO_SCHEMAID));
          
                if ( configExtra.equalsIgnoreCase(Constants.MSG_HEADER_CONFLUENT_IO )) {
                    props.put(""+Constants.PROP_PRI_ENC_EXTRAHEADER,(String) "true");
                }
                else {
                    props.put(""+Constants.PROP_PRI_ENC_EXTRAHEADER,(String) "false");
                }
            
            }*/
            
            /*
            props.put(""+Constants.PROP_KAFKA_GROUPID,                  mainConfig.getProperty(Constants.PROP_KAFKA_GROUPID ));

            if (mainConfig.getProperty(Constants.PROP_PARTITIONER_CLASS) != null) {
                props.put(""+Constants.PROP_PARTITIONER_CLASS,          mainConfig.getProperty(Constants.PROP_PARTITIONER_CLASS));
            }
            if (mainConfig.getProperty(Constants.PROP_PRODUCER_COMPRESSED_TOPIC) != null) {
                props.put(""+Constants.PROP_PRODUCER_COMPRESSED_TOPIC,  mainConfig.getProperty(Constants.PROP_PRODUCER_COMPRESSED_TOPIC));
            }*/
        
        
        // Aynsc producer only
        /*
        String asyncValue=Constants.STR_PRODUCER_TYPE_ASYNC;
        String producerType=mainConfig.getProperty(Constants.PROP_PRODUCER_TYPE);
        if (asyncValue.equalsIgnoreCase(producerType)) {
            props.put(""+Constants.PROP_PRODUCER_AS_Q_MAX_MS,       mainConfig.getProperty(Constants.PROP_PRODUCER_AS_Q_MAX_MS));
            props.put(""+Constants.PROP_PRODUCER_AS_Q_MAX_MSG,      mainConfig.getProperty(Constants.PROP_PRODUCER_AS_Q_MAX_MSG));
            props.put(""+Constants.PROP_PRODUCER_AS_Q_TIMEOUT,      mainConfig.getProperty(Constants.PROP_PRODUCER_AS_Q_TIMEOUT));
            props.put(""+Constants.PROP_PRODUCER_AS_Q_BATCH_MSG,    mainConfig.getProperty(Constants.PROP_PRODUCER_AS_Q_BATCH_MSG));
        }*/
        //} 
        //catch (Exception ex) {
        //    LOG.error(ex.getMessage());
        //    throw new RuntimeException("Unable to instantiate kafka producer", ex);
        //}
        
    }
    
    
    private String parsePayLoad(DOMNotification notification){

        if (LOG.isDebugEnabled())
        {
            LOG.debug("in parsePayLoad");
        }
        final AnyXmlNode encapData = (AnyXmlNode) notification.getBody().getChild(PAYLOAD_NODE).get();
        
        final StringWriter writer = new StringWriter();
        final StreamResult result = new StreamResult(writer);
        final TransformerFactory tf = TransformerFactory.newInstance();
        try {
        final Transformer transformer = tf.newTransformer();
            transformer.transform(encapData.getValue(), result);
        } catch (TransformerException e) {
            LOG.error("Can not parse PayLoad data", e);
            return null;
        }
        writer.flush();
        return writer.toString();
    }
    
    private static Document loadXmlFromString (String xml) throws Exception
    {
        DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
        fac.setNamespaceAware(false);
        DocumentBuilder builder = fac.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes()));
        return doc;
    }
    
    private String evaluate (String payload, String xpathStmt) throws Exception
    {
        String result = null;
        
        Document doc = loadXmlFromString(payload);

        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList nodeList = (NodeList) xpath.evaluate(xpathStmt, doc, XPathConstants.NODESET);
        System.out.println(nodeList.getLength());
        if (nodeList.getLength()>0)
        {
            Node node = nodeList.item(0);
            result = node.getTextContent();
        }
        
        return result;
    }
    
    
}
