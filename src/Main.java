import org.apache.log4j.Logger;

public class Main {
    private static final Logger logger = Logger.getLogger("KafkaDemo");

    protected static String topic;
    protected static String kafkaServer = "localhost:9092";
    protected static String schemaFilename = "/etc/tc-hdfs-writer/TCCDMDatum.avsc";
    protected static String groupId = "tc-hdfs-writer";
    protected static String hdfsUrl = "hdfs://localhost:8020";
    protected static int pollPeriod = 100;

    public static void main(String [] args){
        final KafkaReader tcConsumer = new KafkaReader(kafkaServer, groupId, topic, schemaFilename, pollPeriod);
        tcConsumer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(){
           public void run(){
               try{
                   if(tcConsumer != null) {
                       logger.info("Shutting down consumer.");
                       tcConsumer.setShutdown();
                       tcConsumer.join();
                   }
               }catch (Exception e){
                   e.printStackTrace();
               }
           }
        });

        try{
            if(tcConsumer != null && tcConsumer.isAlive()) {
                tcConsumer.join();
            }
        }catch (InterruptedException e){
            logger.error(e);
        }
    }
}
