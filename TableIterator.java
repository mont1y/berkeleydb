/*
 * TableIterator.java
 *
 * DBMS Implementation
 */

import java.io.*;
import com.sleepycat.je.*;

/**
 * A class that serves as an iterator over some or all of the rows in
 * a stored table.  For a given table, there may be more than one
 * TableIterator open at the same time -- for example, when performing the
 * cross product of a table with itself.
 */
public class TableIterator {
    private Table table;
    private Cursor cursor;
    private DatabaseEntry key;
    private DatabaseEntry value;
    private ConditionalExpression where;
    private int numTuples;
    
    /**
     * Constructs a TableIterator object for the subset of the specified
     * table that is defined by the given SQLStatement.  If the
     * SQLStatement has a WHERE clause and the evalWhere parameter has a
     * value of true, the iterator will only visit rows that satisfy the
     * WHERE clause.
     *
     * @param  stmt  the SQL statement that defines the subset of the table
     * @param  table the table to iterate over
     * @param  evalWhere should the WHERE clause in stmt be evaluated by this
     *         iterator?  If this iterator is being used by a higher-level
     *         iterator, then we can specify false so that the WHERE clause 
     *         will not be evaluated at this level.
     * @throws IllegalStateException if the specified Table object has not
     *         already been opened
     * @throws DatabaseException if Berkeley DB encounters a problem
     *         while accessing one of the underlying database(s)
     */
    public TableIterator(SQLStatement stmt, Table table, boolean evalWhere)
        throws DatabaseException
    {
        this.table = table;
        
        // Make sure the table is open.
        if (table.getDB() == null) {
            throw new IllegalStateException("table " + table + " must be " +
              "opened before attempting to create an iterator for it");
        }
        
        /* 
         * Find all columns from the SQL statement whose values will
         * be obtained using this table iterator, and update their
         * state so that we can get their values as needed.
         */
        Column tableCol, stmtCol;
        for (int i = 0; i < table.numColumns(); i++) {
            tableCol = table.getColumn(i);
            // check for a match in the SELECT clause
            for (int j = 0; j < stmt.numColumns(); j++) {
                stmtCol = stmt.getColumn(j);
                if (stmtCol.nameMatches(tableCol, table)) {
                    stmtCol.useColInfo(tableCol);
                    stmtCol.setTableIterator(this);
                }
            }
            // check for a match in the WHERE clause
            for (int j = 0; j < stmt.numWhereColumns(); j++) {
                stmtCol = stmt.getWhereColumn(j);
                if (stmtCol.nameMatches(tableCol, table)) {
                    stmtCol.useColInfo(tableCol);
                    stmtCol.setTableIterator(this);
                }
            }
        }
        
        this.cursor = table.getDB().openCursor(null, null);
        this.key = new DatabaseEntry();
        this.value = new DatabaseEntry();
        
        this.where = (evalWhere ? stmt.getWhere() : null);
        if (this.where == null) {
            this.where = new TrueExpression();
        }
        
        this.numTuples = 0;
    }
    
    /**
     * Closes the iterator, which closes any BDB handles that it is using.
     *
     * @throws DatabaseException if Berkeley DB encounters a problem
     *         while closing a handle
     */
    public void close() throws DatabaseException {
        if (this.cursor != null) {
            this.cursor.close();
        }
        this.cursor = null;
    }
    
    /**
     * Positions the iterator on the first tuple in the relation, without
     * taking the a WHERE clause (if any) into effect.
     *
     * Because this method ignores the WHERE clause, it should
     * ordinarily be used only when you need to reposition the cursor
     * at the start of the relation after having completed a previous
     * iteration.
     *
     * @return true if the iterator was advanced to the first tuple, and false
     *         if there are no tuples to visit
     * @throws DeadlockException if deadlock occurs while accessing the
     *         underlying BDB database(s)
     * @throws DatabaseException if Berkeley DB encounters another problem
     *         while accessing the underlying database(s)
     */
    public boolean first() throws DeadlockException, DatabaseException {
        if (this.cursor == null) {
            throw new IllegalStateException("this iterator has been closed");
        }
        
        OperationStatus ret = this.cursor.getFirst(this.key, this.value, null); 
        if (ret == OperationStatus.NOTFOUND) {
            return false;
        }
        
        /* Only increment num_tuples if the WHERE clause isn't violated. */
        if (this.where.isTrue()) {
            this.numTuples++;
        }
        
        return true;
    }
    
    /**
     * Advances the iterator to the next tuple in the relation.  If
     * there is a WHERE clause that limits which tuples should be
     * included in the relation, this method will advance the iterator
     * to the next tuple that satisfies the WHERE clause.  If the
     * iterator is newly created, this method will position it on the
     * first tuple in the relation (that satisfies the WHERE clause).
     * Provided that the iterator can be positioned on a tuple, the
     * count of the number of tuples visited by the iterator is
     * incremented.
     *
     * @return true if the iterator was advanced to a new tuple, and false
     *         if there are no more tuples to visit
     * @throws DeadlockException if deadlock occurs while accessing the
     *         underlying BDB database(s)
     * @throws DatabaseException if Berkeley DB encounters another problem
     *         while accessing the underlying database(s)
     */
    public boolean next() throws DeadlockException, DatabaseException {
        if (this.cursor == null) {
            throw new IllegalStateException("this iterator has been closed");
        }
        
        do {
            OperationStatus ret = this.cursor.getNext(this.key, this.value,
                                                      null);
            if (ret == OperationStatus.NOTFOUND) {
                return false;
            }
        } while (!this.where.isTrue());

        this.numTuples++;
        return true;
    }
    
    /**
     * Gets the column at the specified index in the relation that
     * this iterator iterates over.  The leftmost column has an index of 0.
     *
     * @return  the column
     * @throws  IndexOutOfBoundsException if the specified index is invalid
     */
    public Column getColumn(int colIndex) {
        return this.table.getColumn(colIndex);
    }
    
    /**
     * Gets the value of the column at the specified index in the row
     * on which this iterator is currently positioned.  The leftmost
     * column has an index of 0.
     *
     * This method will unmarshall the relevant bytes from the
     * key/data pair and return the corresponding Object -- i.e.,
     * an object of type String for CHAR and VARCHAR values, an object
     * of type Integer for INTEGER values, or an object of type Double
     * for REAL values.
     *
     * @return  the value of the column
     * @throws  IllegalStateException if the iterator has not yet been
     *          been positioned on a tuple using first() or next()
     * @throws  IndexOutOfBoundsException if the specified index is invalid
     */
    public Object getColumnVal(int colIndex) {
        /*
         * Get the specified column. If colIndex is invalid
         * getColumn() will throw an IndexOutOfBoundsException for us.
         */
        Column col = this.table.getColumn(colIndex);

        /* 
         * Check to make sure the underlying cursor has been positioned 
         * on a key/value pair.
         */
        if (this.value.getSize() == 0) {
            throw new IllegalStateException("cursor has not yet been positioned "
                                             + "on a valid tuple");
        }

        /* 
         * PS 3: replace the following return statement with your 
         * implementation of the rest of this method.
         * 
         * Cursor - iterate over records in a database file
         * the key/value pairs are return in "empty" DatabseEntrys
         * that are passed as parameters to the cursor's getNext method
         * 
         * OperationStatus ret = curs.getNext(key, value, null);
         * 
         * takes an index n and returns the value of the nth column in the tuple on 
         * which the iterator is currently positioned. To do so, it will need to 
         * unmarshall the appropriate value from the BDB key/value pair associated with 
         * that tuple, and it should use the metadata that you included when you 
         * marshalled the tuple to efficiently access the value of the specified column. 
         * 
         * A TableIterator has 
         * fields for the current key/value pair accessed by the cursor
         * methods for advacing/resetting the cursor
         * this method which gets a column's value
         * 
         * First, create necessary RowInput objects:
         * RowInput keyIn = new RowInput(this.key.getData());
         * RowInput valueIn = new RowInput(this.value.getData());
         * Then, use RowInput methods to access the necessary offsets and value
         * 
         * You should not unmarshall the entire record but only the portions that are needed
         * to get the value of the specified column
         * 
         * Mostly use the at offset versions of the RowInput methods such as
         * readBytesAtOffset, readIntAtOffset, etc
         * 
         * Example:
         * Movie(id CHAR(7), name VARCHAR(64), runtime INT, rating VARCHAR(5), earnings_rank INT)
         * Didn't specify primary key - id is the primary key
         * 
         * The curosr is currently positioned on this key/value pair
         * to retrieve the movie's name, determine the offset, which is 2 bytes from the start
         * read at offset of 2 is 12
         * because name is a VARCHAR, read offset at 3 which is 21
         * 21 - 12 = 9
         * read 9 bytes at an offset of 12 bytes -> 'Moonlight'
         * 
         * To retrieve the movie's earnings_rank, we determine that the offset is 4*2 = 8 bytes
         * from the start
         * perform a read at an offset of 8 to obtain offset of 4, which is -1
         * we conclude that the value is null
         * 
         * To retrieve the movie's rating, we determine that the offset at column 3 is 
         * 3*2 = 6 bytes from the start
         * we perform a read at an offset of 6 to obtain the offset of the column which is 25
         * because rating is a VARCHAR, read offset at column 4 is -1, so we keep going
         * we read offset at column 5, is 26
         * compute length 26-25 = 1, read one byte at offset of 25 -> 'R'
         * 
         */
        try {
            // Create RowInput objects for key and value buffers.
            RowInput keyIn = new RowInput(this.key.getData());
            RowInput valIn = new RowInput(this.value.getData());
    
            int numColumns = this.table.numColumns();
    
            // Calculate the position of the offset for the specified column.
            int offsetPosition = colIndex * 2; // Each offset is 2 bytes.
    
            // Read the offset for the specified column.
            int offset = valIn.readShortAtOffset(offsetPosition);
    
            // Handle special cases.
            if (offset == InsertRow.IS_NULL) {
                // Column value -1, which is NULL
                return null;
            } else if (offset == InsertRow.IS_PKEY) {
                // Column value is key
                return readValueFromKeyBuffer(col, keyIn);
            } else {
                // Column value is value
                return readValueFromValueBuffer(colIndex, col, valIn, offset);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Error has happened! " + e);
        }
    }

    private Object readValueFromKeyBuffer(Column col, RowInput keyIn) {
        try {
            switch (col.getType()) {
                case Column.INTEGER:
                    // Read 4-byte integer
                    return keyIn.readIntAtOffset(0);
                case Column.REAL:
                    // Read 8-byte double
                    return keyIn.readDoubleAtOffset(0);
                case Column.CHAR:
                    // Read fixed-length CHAR
                    String str = keyIn.readBytesAtOffset(0, col.getLength());
                    return str.trim(); 
                    // Trim to remove front and back and maybe spaces
                case Column.VARCHAR:
                    // Read variable-length VARCHAR from offset 0 to the end of key.
                    int keyLength = this.key.getSize();
                    String varcharStr = keyIn.readBytesAtOffset(0, keyLength);
                    return varcharStr;
                default:
                    throw new IOException("error appeared in key type unmarshalling");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error appeared in key read for column value", e);
        }
    }

    private Object readValueFromValueBuffer(int colIndex, Column col, RowInput valIn, int offset) {
        try {
            int numColumns = this.table.numColumns();
            // max length of offsets
            int offsetsLength = (numColumns + 1) * 2; 
    
            // Find the next that is not -1 (if it is we just ignore)
            int nextOffset = -1;
            int startposition = colIndex + 1;
            for (int i = startposition; i <= numColumns; i++) {
                int nextOffsetPosition = i * 2;
                if (nextOffsetPosition >= offsetsLength) {
                    // no more offsets for me to read, we're at the end
                    // break
                    break;
                }
                int tempOffset = valIn.readShortAtOffset(nextOffsetPosition);
                // if it is not null or primary key/-1 or -2
                if (tempOffset >= 0) {
                    nextOffset = tempOffset;
                    break;
                }
            }
    
            int length;
            if (nextOffset != -1) {
                length = nextOffset - offset;
            } else {
                // If no next offset found
                // Length is just from this to the end
                length = this.value.getSize() - offset;
            }
    
            switch (col.getType()) {
                case Column.INTEGER:
                    // Read 4-byte integer
                    return valIn.readIntAtOffset(offset);
                case Column.REAL:
                    // Read 8-byte double
                    return valIn.readDoubleAtOffset(offset);
                case Column.CHAR:
                    // Read fixed-length char
                    int charLength = col.getLength();
                    String charStr = valIn.readBytesAtOffset(offset, charLength);
                    return charStr.trim();
                case Column.VARCHAR:
                    // Read variable-length VARCHAR
                    String varcharStr = valIn.readBytesAtOffset(offset, length);
                    return varcharStr;
                default:
                    throw new IOException("error at value column type");
            }
        } catch (IOException e) {
            throw new IllegalStateException("error at value column read", e);
        }
    }    
    
    
    /**
     * Gets the number of tuples that the iterator has visited.
     *
     * @return  the number of tuples visited
     */
    public int numTuples() {
        return this.numTuples;
    }
    
    /**
     * Gets the number of columns in the table being iterated over
     *
     * @return  the number of columns
     */
    public int numColumns() {
        return this.table.numColumns();
    }  
    
    /**
     * Iterates over all rows in the relation and prints them to the
     * specified PrintStream (e.g., System.out).
     *
     * @throws DeadlockException if deadlock occurs while accessing the
     *         underlying BDB database(s)
     * @throws DatabaseException if Berkeley DB encounters another problem
     *         while accessing the underlying database(s)
     */
    public void printAll(PrintStream out)
        throws DeadlockException, DatabaseException
    {
        // Display column names -- and compute the length of the separator.
        int separatorLen = 0;
        
        out.println();
        for (int i = 0; i < this.numColumns(); i++) {
            Column col = this.getColumn(i);
            out.print(" | " + col.getName());
            
            int colWidth = col.printWidth();
            for (int j = col.getName().length(); j < colWidth; j++) {
                out.print(" ");
            }
            separatorLen += (colWidth + 3);
        }
        out.println(" | ");
        separatorLen += 3;
        
        // Display the separator.
        for (int i = 0; i < separatorLen; i++) {
            out.print("-");
        }
        out.println();
        
        // Print the tuples.
        while (this.next()) {
            for (int i = 0; i < this.numColumns(); i++) {
                Object val = this.getColumnVal(i);
                String valString =
                    (val == null ? "null" : val.toString());
                
                out.print(" | " + valString);
                
                int valWidth = valString.length();
                int colWidth = this.getColumn(i).printWidth();
                for (int j = valWidth; j < colWidth; j++) {
                    out.print(" ");
                }
            }
            out.println(" | ");
        }
        out.println();
    }
}
