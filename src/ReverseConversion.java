import NU.ETWRealTimeDetector.Input.ObjectConsumer;
import NU.ETWRealTimeDetector.SourceData;

import com.bbn.tc.schema.avro.cdm18.*;

import javax.xml.transform.Source;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ReverseConversion {
    // buffer Event
    private static HashMap<String, Long> thread2EventSetBeginTime;
    private static HashMap<String, SourceData> thread2SourceData;

    private static final Long maxTimeInterval = 4000L;
    private static final int maxCountInterval = 400;


    // resever conversion
    // subjects
    private static HashMap<UUID, Integer> subject2ThreadId; // thread;
    private static HashMap<UUID, Integer> subject2process;
    private static HashMap<Integer, Integer> threadId2ProcessId;
    private static HashMap<UUID, String> object2Parameter;
    // objects
    private static HashMap<UUID, String> process2CmdLine;

    private static EventRecord tempRecord;


    public static EventRecord parse(TCCDMDatum datum){

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
        Event record = (com.bbn.tc.schema.avro.cdm18.Event) datum.getDatum();

        // unified operation
        tempRecord.pcId = record.getHostId().hashCode();
        tempRecord.timeStamp = record.getTimestampNanos();
//        tempRecord.eventName = record.getName().toString();

        tempRecord.threadId = subject2ThreadId.get(record.getSubject());
        tempRecord.processId = threadId2ProcessId.get(tempRecord.threadId);


        switch (record.getType()){
            case EVENT_EXECUTE: parseEventEXECUTE(record); break; // ProcessStart
            case EVENT_EXIT: parseEventExit(record); break;  // ProcessEnd

            case EVENT_LOADLIBRARY: parseEventLoadLibrary(record); break; // ImageLoad
            default: tempRecord.eventName = "";
        }
    }

    private static void parseEventEXECUTE(Event record) {
        tempRecord.eventName = "ProcessEnd";
        tempRecord.parameter = "ImageFileName:" + record.getPredicateObjectPath() + ", CommandLine:" + process2CmdLine.get(record.getPredicateObject());
    }

    private static void parseEventExit(Event record) {
        tempRecord.eventName = "ProcessStart";
        tempRecord.parameter = "ImageFileName:" + record.getPredicateObjectPath() + ", CommandLine:" + process2CmdLine.get(record.getPredicateObject());
    }

    private  static void  parseEventLoadLibrary(Event record){
        tempRecord.eventName = "ImageLoad";
        tempRecord.parameter = record.getPredicateObjectPath().toString();
    }

    public static void parseSubject(TCCDMDatum datum){
        Subject record = (com.bbn.tc.schema.avro.cdm18.Subject) datum.getDatum();

        if(record.getType() == SubjectType.SUBJECT_PROCESS){
            subject2process.put(record.getUuid(),record.getCid());
            process2CmdLine.put(record.getUuid(),record.getCmdLine().toString());
        }
        else if(record.getType() == SubjectType.SUBJECT_THREAD){
            subject2ThreadId.put(record.getUuid(), record.getCid());
            threadId2ProcessId.put(record.getCid(), subject2process.get(record.getParentSubject()));
        }

    }

    public static void parseObject(TCCDMDatum datum){
        AbstractObject record = (com.bbn.tc.schema.avro.cdm18.AbstractObject) datum.getDatum();

        switch (record.getClass().getName()){
            case "FileObject":
                break;
            case "RegistryKeyObject":
                break;
        }
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
