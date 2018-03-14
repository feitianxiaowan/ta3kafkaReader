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

    public static void init(){
        tempRecord = new EventRecord();

        thread2SourceData = new HashMap<>();
        thread2EventSetBeginTime = new HashMap<>();

        subject2ThreadId = new HashMap<>();
        subject2process = new HashMap<>();
        process2CmdLine = new HashMap<>();
        threadId2ProcessId = new HashMap<>();

        fileObject2FilePath = new HashMap<>();
        registryObject2Path = new HashMap<>();
    }


    public static EventRecord parse(TCCDMDatum CDMdatum){
        Object datum = CDMdatum.getDatum();
        System.out.println(CDMdatum.getDatum().getClass().getName());
        // reserve conversion here
        if (datum instanceof  Event){
            parseEvent(CDMdatum);
        }
        else if(datum instanceof Subject){
            parseSubject(CDMdatum);
        }
        else if(datum instanceof FileObject){
            parseObject(CDMdatum);
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
            case "RegistryEnumerateKey": tempRecord.eventName = record.getName().toString(); tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
            case "RegistryDeleteValue": tempRecord.eventName = record.getName().toString(); tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
            case "RegistrySetValue": tempRecord.eventName = record.getName().toString(); tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
            case "RegistrySetInformation": tempRecord.eventName = record.getName().toString(); tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
            case "RegistryQueryValue": tempRecord.eventName = record.getName().toString(); tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
            case "RegistryQuery": tempRecord.eventName = record.getName().toString(); tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
            case "RegistryOpen": tempRecord.eventName = record.getName().toString(); tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
            case "RegistryKCBDelete": tempRecord.eventName = record.getName().toString(); tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
            case "RegistryKCBCreate": tempRecord.eventName = record.getName().toString(); tempRecord.parameter = record.getPredicateObjectPath().toString(); break;

            case "FileIoCleanup": tempRecord.eventName = record.getName().toString(); tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
            case "FileIoClose": tempRecord.eventName = record.getName().toString(); tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
            case "FileIoDirEnum": tempRecord.eventName = record.getName().toString(); tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
            case "FileIoFileDelete": tempRecord.eventName = record.getName().toString(); tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
            case "FileIoQueryInfo": tempRecord.eventName = record.getName().toString(); tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
            case "FileIoRead": tempRecord.eventName = record.getName().toString(); tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
            case "FileIoSetInfo": tempRecord.eventName = record.getName().toString(); tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
            case "FileIoWrite": tempRecord.eventName = record.getName().toString(); tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
            case "FileIoFSControl": tempRecord.eventName = record.getName().toString(); tempRecord.parameter = record.getPredicateObjectPath().toString(); break;
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
        else if(registryObject2Path.containsKey(record.getUuid())) {
            tempRecord.eventName = "RegistryCreate";
            tempRecord.parameter = record.getPredicateObjectPath().toString();
        }
    }

    private static void parseEventWrite(Event record){
        tempRecord.eventName = "DiskIoWrite";
        tempRecord.parameter = record.getPredicateObjectPath().toString();
    }



    public static void parseSubject(TCCDMDatum datum){
        Subject record = (com.bbn.tc.schema.avro.cdm18.Subject) datum.getDatum();

        if(record.getType() == SubjectType.SUBJECT_PROCESS){
            try {
                subject2process.put(record.getUuid(),record.getCid());
                process2CmdLine.put(record.getUuid(),record.getCmdLine().toString());
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
        else if(record.getType() == SubjectType.SUBJECT_THREAD){
            try {
                subject2ThreadId.put(record.getUuid(), record.getCid());
                threadId2ProcessId.put(record.getCid(), subject2process.get(record.getParentSubject()));
            }
            catch(Exception e){
                e.printStackTrace();
            }
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
        String threadKey;
        if(record.eventName == "")
            return;
        try {
            threadKey = record.threadId + record.pcId + "";
        }
        catch (Exception e){
            e.printStackTrace();
            return;
        }

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
