import json

if __name__ == "__main__":
    inFile = open("remotedesktop_localResults_filtered.txt","r");
    outFile = open("remotedesktop_localResults_filtered1.txt","w");
    uniqueSyscall = set()

    sig = inFile.read();
    for eachSig in json.loads(sig):
        for eachSys in eachSig["sig"]:
            uniqueSyscall.add(eachSys)

    for eachSys in uniqueSyscall:
        outFile.write(eachSys+"\n")
