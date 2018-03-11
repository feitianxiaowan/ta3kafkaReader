import NU.ETWRealTimeDetector.SysConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Properties;

public class Main {
    private static final Logger logger = Logger.getLogger("KafkaDemo");

    protected static String topic;
    protected static String kafkaServer = "localhost:9092";
    protected static String schemaFilename = "/etc/tc-hdfs-writer/TCCDMDatum.avsc";
    protected static String groupId = "tc-hdfs-writer";
    protected static String hdfsUrl = "hdfs://localhost:8020";
    protected static int pollPeriod = 100;

    public static void main(String[] args) {

        try {
            SysConfig.load("DebugConfig.xml");
        } catch (IOException e) {
            e.printStackTrace();
        }

        final KafkaReader tcConsumer = new KafkaReader(kafkaServer, groupId, topic, schemaFilename, pollPeriod);
        tcConsumer.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    if (tcConsumer != null) {
                        logger.info("Shutting down consumer.");
                        tcConsumer.setShutdown();
                        tcConsumer.join();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        try {
            if (tcConsumer != null && tcConsumer.isAlive()) {
                tcConsumer.join();
            }
        } catch (InterruptedException e) {
            logger.error(e);
        }
    }

}


