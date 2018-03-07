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
    private static HashMap<UUID, String> process2CmdLine;
    private static HashMap<Integer, Integer> threadId2ProcessId;
    // objects
    private static HashMap<UUID, String> fileObject2FilePath;
    private static HashMap<UUID, String> registryObject2Path;

    private static EventRecord tempRecord;


    public static EventRecord parse(TCCDMDatum datum){

        // reserve conversion here
        if (datum.getClass().getName().endsWith("Event")){
            parseEvent(datum);
        }
        else if(datum.getClass().getName().endsWith("Subject")){
            parseSubject(datum);
        }
        else if(datum.getClass().getName().endsWith("FileObject")){
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
            case EVENT_CLOSE: parseEventClose(record); break; // ImageUnLoad

            case EVENT_SENDMSG: parseEventSendMsg(record); break; // ALPCALPC-Send-Message
            case EVENT_RECVMSG: parseEventRecvMsg(record); break; // ALPCALPC-Receive-Message
            case EVENT_OTHER: parseEventOther(record); break; // ALPCALPC-Unwait, ALPCALPC-Wait-For-Reply, RegistryEnumerateKey, System call

            case EVENT_CREATE_OBJECT: parseEventCreateObject(record); break; // FileIoCreate

            case EVENT_READ: parseEventWrite(record); break; // DiskIoWrite
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

    private static void parseEventLoadLibrary(Event record){
        tempRecord.eventName = "ImageLoad";
        tempRecord.parameter = record.getPredicateObjectPath().toString();
    }

    private static void parseEventClose(Event record){
        tempRecord.eventName = "ImageUnLoad";
        tempRecord.parameter = record.getPredicateObjectPath().toString();
    }

    private static void parseEventSendMsg(Event record){
        if(process2CmdLine.containsKey(record.getPredicateObject())) {
            tempRecord.eventName = "ALPCALPC-Send-Message";
            tempRecord.parameter = process2CmdLine.get(record.getPredicateObject());
        }
        else{
            tempRecord.eventName = "";
        }
    }

    private static void parseEventRecvMsg(Event record){
        if(process2CmdLine.containsKey(record.getPredicateObject())) {
            tempRecord.eventName = "ALPCALPC-Receive-Message";
            tempRecord.parameter = process2CmdLine.get(record.getPredicateObject());
        }
        else{
            tempRecord.eventName = "";
        }
    }

    private static void parseEventOther(Event record){
        switch (record.getName().toString()){
            case "ALPC Wait For Reply": tempRecord.eventName = "ALPCALPC-Wait-For-Reply"; break;
            case "ALPC Unwait": tempRecord.eventName = "ALPCALPC-Unwait"; break;
            case "Delete File": tempRecord.eventName = "FileIoDelete"; tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
            case "Enumerate value key event": tempRecord.eventName = "RegistryEnumerateValueKey"; tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
            case "Delete Registry": tempRecord.eventName = "RegistryDelete"; tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
            default:
                tempRecord.eventName = "PerfInfoSysClEnter";
                tempRecord.parameter = record.getName().toString();

        }
    }

    private static void parseEventCreateObject(Event record){
        if(fileObject2FilePath.containsKey(record.getUuid())){
            tempRecord.eventName = "FileIoCreate";
            tempRecord.parameter = record.getPredicateObjectPath().toString();
        }
        else if(registryObject2Path.containsKey(record.getUuid())){
            tempRecord.eventName = "RegistryCreate";
            tempRecord.parameter = record.getPredicateObjectPath().toString();
        }
        }
    }

    private static void parseEventWrite(Event record){
        tempRecord.eventName = "DiskIoWrite";
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

        tempRecord.eventName = "";
    }

    public static void parseObject(TCCDMDatum datum){
        if(datum.getDatum().getClass().getName().endsWith("FileObject")) {
            FileObject record = (com.bbn.tc.schema.avro.cdm18.FileObject) datum.getDatum();
            fileObject2FilePath.put(record.getUuid(), "");
        }
        else if(datum.getDatum().getClass().getName().endsWith("RegistryObject")){
            RegistryKeyObject record = (com.bbn.tc.schema.avro.cdm18.RegistryKeyObject) datum.getDatum();
            registryObject2Path.put(record.getUuid(), "");
        }

        tempRecord.eventName = "";
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
