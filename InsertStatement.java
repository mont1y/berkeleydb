/*
 * InsertStatement.java
 *
 * DBMS Implementation
 */

import java.util.*;
import com.sleepycat.je.*;

/**
 * A class that represents an INSERT statement.
 */
public class InsertStatement extends SQLStatement {
    /** 
     * Constructs an InsertStatement object involving the specified table,
     * list of columns (if any), and list of values.  The columns and 
     * their associated values must be specified in the same order.
     * If no list of columns is specified, we will assume that values are 
     * being specified for all columns.
     *
     * @param  t  the table in which the values should be inserted
     * @param  colList  the list of columns for which values are specified
     * @param  valList  the list of values to be inserted
     */
    public InsertStatement(Table t, ArrayList<Column> colList,
                           ArrayList<Object> valList) {
        super(t, colList, valList);
    }
    
    public void execute() throws DatabaseException, DeadlockException {
        try {
            Table table = this.getTable(0);
            if (table.open() != OperationStatus.SUCCESS) {
                throw new Exception();  // error msg was printed in open()
            }
            
            // Preliminary error checking.
            if (this.numColumns() != 0) {
                throw new Exception("INSERT commands with column names " +
                                    "are not supported");
            }
            if (this.numColumnVals() != table.numColumns()) {
                throw new Exception("Must specify a value for each column");
            }
            
            // Make any necessary adjustments (type conversions, 
            // truncations, etc.) to the values to be inserted.
            // This will throw an exception if a value is invalid.
            Object[] adjustedValues = new Object[table.numColumns()];
            for (int i = 0; i < table.numColumns(); i++) {
                Column col = table.getColumn(i);
                adjustedValues[i] = col.adjustValue(this.getColumnVal(i));
            }
            
            // Create an InsertRow object for the row to be inserted,
            // and use that object to marshall the row.
            InsertRow row = new InsertRow(table, adjustedValues);
            row.marshall();
            if (DBMS.DEBUG) {
                System.out.println(row);
            }
            
            /* 
             * PS 3: Add code below to perform the actual insertion, 
             * and to print the appropriate message after it has occurred.
             * 
             * Inserting Data into BDB
             * byte[] bytes = keyBuffer.getBufferBytes();
             * int numBytes = keyBuffer.getBufferLength();
             * DatabaseEntry key = new DatabaseEntry(bytes, 0, numBytes)
             * 
             * bytes = valueBuffer.getBufferBytes
             * numBytes = valueBuffer.getBufferLength();
             * DatabaseEntry value = new DatabaseEntry(bytes, 0, numBytes)
             * 
             * Use the Database putNoOverwrite Method:
             * Database db; //assume it has been opened
             * OperationStatus ret = db.putNoOverwrite(null, key, value);
             * null because we are not using transactions
             * if there is an existing key/value pair with the specified key:
             * the insertion fails
             * the method returns OperationStatus.KEYEXIST
             * if the insertion succeeds, returns OperationStatus.SUCCESS
             */
            // get databaseentry object of key value pair
            byte[] keyBytes = row.getKeyBuffer().getBufferBytes();
            int keyLength = row.getKeyBuffer().getBufferLength();
            DatabaseEntry key = new DatabaseEntry(keyBytes, 0, keyLength);

            byte[] valueBytes = row.getValueBuffer().getBufferBytes();
            int valueLength = row.getValueBuffer().getBufferLength();
            DatabaseEntry value = new DatabaseEntry(valueBytes, 0, valueLength);
            
            Database db = table.getDB();
            // System.out.println("databsename: " + table.getName());
            OperationStatus ret = db.putNoOverwrite(null, key, value);
        
            // output the different scenarios of the insert
            if (ret == OperationStatus.KEYEXIST) {
                System.out.println("There is an existing row with the specified primary key.\nCould not insert row.");
            } else if (ret == OperationStatus.SUCCESS) {
                // Insertion was successful
                System.out.println("Added 1 row to " + table.getName() + ".");
            } else {
                System.out.println("BEEP BOOP SOMETHING WEALLY BAD HAPPENED");
            }
            
        } catch (Exception e) {
            if (DBMS.DEBUG) {
                e.printStackTrace();
            }
            String errMsg = e.getMessage();
            if (errMsg != null) {
                System.err.println(errMsg + ".");
            }
            System.err.println("Could not insert row.");
        }
    }
}
