/*
 * InsertRow.java
 *
 * DBMS Implementation
 */

import java.io.*;
import java.util.Arrays;

/**
 * A class that represents a row that will be inserted in a table in a
 * relational database.
 *
 * This class contains the code used to marshall the values of the
 * individual columns to a single key-value pair.
 */
public class InsertRow {
    private Table table;           // the table in which the row will be inserted
    private Object[] columnVals;   // the column values to be inserted
    private RowOutput keyBuffer;   // buffer for the marshalled row's key
    private RowOutput valueBuffer; // buffer for the marshalled row's value
    private int[] offsets;         // offsets for header of marshalled row's value
    
    /** Constants for special offsets **/
    /** The field with this offset has a null value. */
    public static final int IS_NULL = -1;
    
    /** The field with this offset is a primary key. */
    public static final int IS_PKEY = -2;
    
    /**
     * Constructs an InsertRow object for a row containing the specified
     * values that is to be inserted in the specified table.
     *
     * @param  t  the table
     * @param  values  the column values for the row to be inserted
     */
    public InsertRow(Table table, Object[] values) {
        this.table = table;
        this.columnVals = values;
        this.keyBuffer = new RowOutput();
        this.valueBuffer = new RowOutput();
        
        // Note that we need one more offset than value,
        // so that we can store the offset of the end of the record.
        this.offsets = new int[values.length + 1];
    }
    
    /**
     * Takes the collection of values for this InsertRow
     * and marshalls them into a key/value pair.
     * 
     * (Note: We include a throws clause because this method will use 
     * methods like writeInt() that the RowOutput class inherits from 
     * DataOutputStream, and those methods could in theory throw that 
     * exception. In reality, an IOException should *not* occur in the
     * context of our RowOutput class.)
     */
    public void marshall() throws IOException {
        /* 
         * PS 3: Implement this method. 
         * 
         * Feel free to also add one or more private helper methods
         * to do some of the work (e.g., to fill in the offsets array
         * with the appropriate offsets).
         * 
         * When inserting a row, we need to turn a collection of fields into a key/value pair
         * e.g. ('9876543', 'psych', 125) into key 9876543, value -2 8 13 17 psych 125
         * key and values are each represented by a DatabaseEntry object
         * Based on a byte array that we need to create
         * the primay key becomes the key in the key/value pair
         * Classes for manipulating byte arrays:
         * Inherits from Java's DataOutputStream:
         * writeBytes(String val)
         * writeShort(int val) - this will be the type of the offset
         * writeInt(int val)
         * writeDouble(double val)
         * methods for obtaining the results of the writes
         * getBufferBytes()
         * getBufferLength()
         * includes a toString() method that shows the current contents of the byte array
         * RowInput: an input stream that reads from a byte array
         * readBytesAtOffset(int offset, int length)
         * readIntAtOffset(int offset) etc
         * methods that read from current offset
         * readNextBytes(int length)
         * readNextInt() etc
         * includes a toString() method that shows the byte array and the current offset
         * e.g. RowOutput keyBuffer = new RowOutput();
         * keyBuffer.writeBytes("1234567");
         * RowOutput valueBuffer = newRowOutput();
         * valueBuffer.writeShort(-2);
         * valueBuffer.writeShort(8);
         * valueBuffer.writeShort(16);
         * valueBuffer.writeShort(20);
         * valueBuffer.writeBytes("comp sci");
         * valueBuffer.writeInt(200);
         */

        System.out.println("hello????????????");
        // get primary key
        int numColumns = table.numColumns();
        Column pkColumn = table.primaryKeyColumn();
        if (pkColumn == null) {
            System.out.println("There is no primary key column");
        }
        // get the index of columbn of primary key
        int pkIndex = -1;
        for (int i = 0; i < numColumns; i++) {
            if (table.getColumn(i) == pkColumn) {
                pkIndex = i;
                break;
            }
        }
        if (pkIndex == -1) {
            // if still can't find anything
            throw new IOException("error at pkIndex");
        }
        
        // Calculate the size of the offset table
        int offsetTableSize = (numColumns + 1) * 2;
        // System.out.println("getTbaleSizr" + offsetTableSize);
        // set currentOffset to the start of the value after offsets
        int currentOffset = offsetTableSize;

        // Determine offsets for each value column
        for (int i = 0; i < numColumns; i++) {
            if (i == pkIndex) {
                // if this is the primary key column
                // offset is IS_PKEY which is -2
                offsets[i] = IS_PKEY; 
            } else if (columnVals[i] == null) {
                // NULL value set -1
                offsets[i] = IS_NULL;
            } else {
                // Non-null value
                // start from the end of the space the offset takes - currenOffset
                offsets[i] = currentOffset;
                Column col = table.getColumn(i);
                int length = 0;

                // Determine the length of the column value
                switch (col.getType()) {
                    case Column.INTEGER:
                        // 4 byte int
                        length = 4; 
                        break;
                    case Column.REAL:
                        // 8 bytes real
                        length = 8; 
                        break;
                    case Column.CHAR:
                        // Fixed length for char
                        length = col.getLength(); 
                        break;
                    case Column.VARCHAR:
                        // Variable
                        String strVal = (String) columnVals[i];
                        length = strVal.length(); 
                        break;
                    default:
                        throw new IOException("somthing's wrong in columns offsets");
                }
                currentOffset += length;
            }
        }
        // last offset
        offsets[numColumns] = currentOffset;

        // write primary key using keyBuffer
        Object pkValue = columnVals[pkIndex];
        Column pkCol = table.getColumn(pkIndex);
        switch (pkCol.getType()) {
            case Column.INTEGER:
                keyBuffer.writeInt(((Integer) pkValue).intValue());
                break;
            case Column.REAL:
                keyBuffer.writeDouble(((Double) pkValue).doubleValue());
                break;
            case Column.CHAR:
                String strPkVal = (String) pkValue;
                int pkLen = pkCol.getLength();

                // add spaces if it's shorter than pkLen
                // basically pad it with spaces since it's fixed length
                if (strPkVal.length() < pkLen) {
                    int paddingLength = pkLen - strPkVal.length();
                    StringBuilder sb = new StringBuilder(strPkVal);
                    for (int j = 0; j < paddingLength; j++) {
                        sb.append(' ');
                    }
                    strPkVal = sb.toString();
                } else if (strPkVal.length() > pkLen) {
                    // it it exceeds length, we substring it
                    strPkVal = strPkVal.substring(0, pkLen);
                }

                keyBuffer.writeBytes(strPkVal);
                break;
            case Column.VARCHAR:
                // if exceeds the varchar limit, truncate
                // System.out.println("varcahr limit is: " + pkCol.getLength());
                // System.out.println("cur length is: " + ((String)pkValue).length());
                if (((String)pkValue).length() > pkCol.getLength()) {
                    keyBuffer.writeBytes(((String)pkValue).substring(0, ((String)pkValue).length()));
                } else {
                    keyBuffer.writeBytes((String) pkValue);
                }
                break;
            default:
                throw new IOException("something's wrong at primary key column");
        }

        // Write offsets into valueBuffer 
        for (int i = 0; i < offsets.length; i++) { 
            valueBuffer.writeShort(offsets[i]);
            // System.out.println("writing valuebuffer offet: " + offsets[i]);
        }

        // Write column values into valueBuffer
        for (int i = 0; i < numColumns; i++) {
            // Skip primary key and null value
            if (i == pkIndex || columnVals[i] == null) {
                continue;
            }

            Column col = table.getColumn(i);
            switch (col.getType()) {
                case Column.INTEGER:
                    valueBuffer.writeInt(((Integer) columnVals[i]).intValue());
                    break;
                case Column.REAL:
                    valueBuffer.writeDouble(((Double) columnVals[i]).doubleValue());
                    break;
                case Column.CHAR:
                    String strVal = (String) columnVals[i];
                    int len = col.getLength();

                    // add spaces if it's shorter than pkLen
                    if (strVal.length() < len) {
                        int paddingLength = len - strVal.length();
                        StringBuilder sb = new StringBuilder(strVal);
                        for (int j = 0; j < paddingLength; j++) {
                            sb.append(' ');
                        }
                        strVal = sb.toString();
                    } else if (strVal.length() > len) {
                        // Truncate the string if it's longer than allocated size
                        strVal = strVal.substring(0, len);
                    }
                    valueBuffer.writeBytes(strVal);
                    break;
                case Column.VARCHAR:
                    String strVal2 = (String) columnVals[i];
                    // if exceeds the varchar limit, truncate
                    // System.out.println("varchar limit is: " + col.getLength());
                    // System.out.println("cur length is: " + ((String)strVal2).length());
                    if (strVal2.length() > col.getLength()) {
                        keyBuffer.writeBytes((strVal2).substring(0, strVal2.length()));
                    } else {
                        keyBuffer.writeBytes(strVal2);
                    }
                    break;
                default:
                    throw new IOException("something's wrong at column of values: " + col.getType());
            }
        }
        
    }
        
    /**
     * Returns the RowOutput used for the key portion of the marshalled row.
     *
     * @return  the key's RowOutput
     */
    public RowOutput getKeyBuffer() {
        return this.keyBuffer;
    }
    
    /**
     * Returns the RowOutput used for the value portion of the marshalled row.
     *
     * @return  the value's RowOutput
     */
    public RowOutput getValueBuffer() {
        return this.valueBuffer;
    }
    
    /**
     * Returns a String representation of this InsertRow object. 
     *
     * @return  a String for this InsertRow
     */
    public String toString() {
        return "offsets: " + Arrays.toString(this.offsets)
             + "\nkey buffer: " + this.keyBuffer
             + "\nvalue buffer: " + this.valueBuffer;
    }
}
