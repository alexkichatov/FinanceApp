package com.databases.example.data;

//An Object Class used to hold the data of each transaction record
public class TransactionRecord {

    public final int id;
    public final int acctId;
    public final int planId;
    public final String name;
    public final String value;
    public final String type;
    public final String category;
    public final String checknum;
    public final String memo;
    public final String time;
    public final String date;
    public final String cleared;

    public TransactionRecord(int id, int acctId, int planId, String name, String value, String type, String category, String checknum, String memo, String time, String date, String cleared) {
        this.id = id;
        this.acctId = acctId;
        this.planId = planId;
        this.name = name;
        this.value = value;
        this.type = type;
        this.category = category;
        this.checknum = checknum;
        this.memo = memo;
        this.time = time;
        this.date = date;
        this.cleared = cleared;
    }
}
