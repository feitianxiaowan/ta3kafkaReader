import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import NU.ETWRealTimeDetector.Input.ObjectConsumer;
import NU.ETWRealTimeDetector.Manager;
import NU.ETWRealTimeDetector.Match.Detector;
import NU.ETWRealTimeDetector.Match.TraceKey;
import NU.ETWRealTimeDetector.Match.WFADetector;
import NU.ETWRealTimeDetector.Output.Publisher;
import NU.ETWRealTimeDetector.Output.StdPublisher;
import com.bbn.tc.schema.avro.cdm18.TCCDMDatum;
import org.apache.avro.generic.GenericContainer;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.bbn.tc.schema.serialization.AvroConfig;

public class KafkaReader extends Thread{
    private final Logger logger = Logger.getLogger(this.getClass().getCanonicalName());

    protected final KafkaConsumer<String, GenericContainer> consumer;
    protected long recordCounter = 0;
    protected AtomicBoolean shutdown = new AtomicBoolean(false);
    protected int pollPeriod = 100;

    private boolean setSpecificOffset = true;
    private long forcedOffset = 0;
    private String topic;


    private ObjectConsumer objectConsumer;
    private Manager manager;
    private Detector detector;
    private Publisher publisher;


    public KafkaReader(String kafkaServer, String groupId, String topic, String schemaFilename, int pollPeriod) {

        logger.setLevel(Level.DEBUG);
        this.pollPeriod = pollPeriod;
        this.topic = topic;

        Properties properties = new Properties();
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        //add some other properties
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaServer);
        properties.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 20000);
        properties.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, "0"); //ensure no temporal batching
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // latest or earliestautoOffset

        //serialization properties
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");

        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                com.bbn.tc.schema.serialization.kafka.KafkaAvroGenericDeserializer.class);

        properties.put(AvroConfig.SCHEMA_READER_FILE, schemaFilename);
        properties.put(AvroConfig.SCHEMA_WRITER_FILE, schemaFilename);
        properties.put(AvroConfig.SCHEMA_SERDE_IS_SPECIFIC, false);
        consumer = new KafkaConsumer<>(properties);

        if (setSpecificOffset) {
            logger.info("Going to set specific offset for each partition to " + forcedOffset);
            consumer.subscribe(Arrays.asList(topic.split(",")), new ForceOffsetConsumerRebalanceListener());
        } else {
            consumer.subscribe(Arrays.asList(topic.split(",")));
        }

        //Initialize.
        manager = new Manager();
        objectConsumer = new ObjectConsumer();
        detector = new WFADetector(new TraceKey(),false,false,false);
        publisher = new StdPublisher();
        manager.setConsumer(objectConsumer);
        manager.setDetector(detector);
        manager.setPublisher(publisher);
        manager.start();
    }

    public void setShutdown(){
        this.shutdown.set(true);
    }

    public void run(){
        logger.info("Started KafkaReader");
        recordCounter = 0;

        boolean receivedSomethingYet = false;
        ConsumerRecords<String, GenericContainer> records = null;

        try{
            while (!shutdown.get()) {
                // =================== <KAFKA consumer> ===================
                records = consumer.poll(pollPeriod);

                Iterator recIter = records.iterator();

                while (recIter.hasNext()){
                    GenericContainer record = (GenericContainer) recIter.next();
                    TCCDMDatum datum = (TCCDMDatum) record;
                    EventRecord tempRecord = ReverseConversion.parse(datum);
                    ReverseConversion.bufferEvent(tempRecord,objectConsumer);
                }
                // =================== </KAFKA consumer> ===================
            }
            closeConsumer();
            logger.info("Done.");
        } catch (Exception e){
            logger.error("Error while consuming from Kafka", e);
            e.printStackTrace();
        }
    }

    protected void logRecord(String key, GenericContainer tmpRecord) throws Exception {
        if (tmpRecord == null) {
            if (logger.isDebugEnabled()) logger.debug("Received null record ");
            return;

        }
        if (tmpRecord.getSchema() == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Received record with unknown schema " + tmpRecord.toString());
            }
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Consumed record of type " + tmpRecord.getSchema().getFullName() +
                    " {" + recordCounter + " records}");
        }
    }

    protected void closeConsumer() {
        if(consumer != null) {
            logger.info("Closing consumer session ...");
            consumer.commitSync();
            logger.info("Committed");
            consumer.unsubscribe();
            logger.info("Unsubscribed");
            consumer.close();
            logger.info("Consumer session closed.");
        }
    }

    public class ForceOffsetConsumerRebalanceListener implements ConsumerRebalanceListener {

        @Override
        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            // Only force the offsets to a specific value after the first assignment
            if (!setSpecificOffset) {
                return;
            }
            setSpecificOffset = false;

            Set<TopicPartition> consumer_assignment = consumer.assignment();
            logger.info("Setting a specific offset: "+forcedOffset+" for "+consumer_assignment.size()+" TopicPartitions");
            if (forcedOffset < 0) {
                logger.info("Seeing to the end of assigned partitions");
                consumer.seekToEnd(consumer_assignment.toArray(new TopicPartition[consumer_assignment.size()]));
            }
            for (TopicPartition partition : consumer_assignment) {
                if (forcedOffset < 0) {
                    long partitionOffset = consumer.position(partition);
                    logger.info("Last offset for "+partition+" is "+partitionOffset);
                    if (partitionOffset > 0) {
                        partitionOffset = partitionOffset + forcedOffset;
                        consumer.seek(partition, partitionOffset);
                        logger.info("Setting offset for "+partition+" to "+partitionOffset);
                    }
                } else {
                    consumer.seek(partition, forcedOffset);
                    logger.info("Setting offset for "+partition+" to "+forcedOffset);
                }
            }
        }

        @Override
        public void onPartitionsRevoked(Collection<TopicPartition> partitions) {}

    }
}
