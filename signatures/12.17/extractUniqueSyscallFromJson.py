import json

if __name__ == "__main__":
    inFile = open("remoteshell_localResults_filtered.txt","r");
    outFile = open("remoteshell_localResults_filtered_output.txt","w");
    uniqueSyscall = set()

    sig = inFile.read();
    for eachSig in json.loads(sig):
        for eachSys in eachSig["sig"]:
            uniqueSyscall.add(eachSys)

    for eachSys in uniqueSyscall:
        outFile.write(eachSys+"\n")
