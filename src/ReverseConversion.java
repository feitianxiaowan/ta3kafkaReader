import NU.ETWRealTimeDetector.Input.ObjectConsumer;
import NU.ETWRealTimeDetector.SourceData;

import com.bbn.tc.schema.avro.cdm18.TCCDMDatum;

import javax.xml.transform.Source;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ReverseConversion {
    public static HashMap<String, Long> thread2EventSetBeginTime;
    public static HashMap<String, SourceData> thread2SourceData;

    public static final Long maxTimeInterval = 4000L;
    public static final int maxCountInterval = 400;


    public static EventRecord parse(TCCDMDatum datum){
        EventRecord tempRecord = new EventRecord();

        // reserve conversion here

        if (datum.getClass().getName().endsWith("Event")){
            parseEvent(datum);
        }
        else if(datum.getClass().getName().endsWith("Subject")){
            parseSubject(datum);
        }
        else if(datum.getClass().getName().endsWith("Object")){
            parseObject(datum);
        }

        // -----------------------

        return tempRecord;
    }

    public static void parseEvent(TCCDMDatum datum){

    }

    public static void parseSubject(TCCDMDatum datum){

    }

    public static void parseObject(TCCDMDatum datum){
    }

    public static void bufferEvent(EventRecord record, ObjectConsumer consumer){
        String threadKey = record.threadId + record.pcId + "";

        // when new thread appears!
        if(!thread2SourceData.containsKey(threadKey)){
            SourceData sData = new SourceData();
            sData.setPcid(record.pcId);
            sData.setProcessid(record.processId);
            sData.setThreadid(record.threadId);
            thread2SourceData.put(threadKey, new SourceData());
        }
        if(!thread2EventSetBeginTime.containsKey(threadKey)){
            thread2EventSetBeginTime.put(threadKey, record.timeStamp);
        }

        Long timeInterval = record.timeStamp - thread2EventSetBeginTime.get(threadKey);
        int countInterval = thread2SourceData.get(threadKey).getEvents().size();

        // time to handle event
        if(timeInterval >= maxTimeInterval || countInterval >= maxCountInterval){

            consumer.inputSourceData(thread2SourceData.get(threadKey));

            thread2SourceData.remove(threadKey);
            thread2EventSetBeginTime.remove(threadKey);
        }

        thread2SourceData.get(threadKey).getEvents().add(record.eventName + " @ " + record.parameter);
    }

}
