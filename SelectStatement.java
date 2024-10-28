/*
 * SelectStatement.java
 *
 * DBMS Implementation
 */

import java.util.*;
import com.sleepycat.je.*;

/**
 * A class that represents a SELECT statement.
 */
public class SelectStatement extends SQLStatement {
    /* 
     * Used in the selectList for SELECT * statements.
     */
    public static final String STAR = "*";
    
    private ArrayList<Object> selectList;
    private Limit limit;
    private boolean distinctSpecified;
    
    /** 
     * Constructs a SelectStatement object involving the specified
     * columns and other objects from the SELECT clause, the specified
     * tables from the FROM clause, the specified conditional
     * expression from the WHERE clause (if any), the specified Limit
     * object summarizing the LIMIT clause (if any), and the specified
     * value indicating whether or not we should eliminate duplicates.
     *
     * @param  selectList  the columns and other objects from the SELECT clause
     * @param  fromList  the list of tables from the FROM clause
     * @param  where  the conditional expression from the WHERE clause (if any)
     * @param  limit  summarizes the info in the LIMIT clause (if any)
     * @param  distinctSpecified  should duplicates be eliminated?
     */
    public SelectStatement(ArrayList<Object> selectList, 
                           ArrayList<Table> fromList, ConditionalExpression where,
                           Limit limit, Boolean distinctSpecified)
    {
        super(fromList, new ArrayList<Column>(), where);
        this.selectList = selectList;
        this.limit = limit;
        this.distinctSpecified = distinctSpecified.booleanValue();
        
        /* add the columns in the select list to the list of columns */
        for (int i = 0; i < selectList.size(); i++) {
            Object selectItem = selectList.get(i);
            if (selectItem instanceof Column) {
                this.addColumn((Column)selectItem);
            }
        }
    }
    
    /**
     * Returns a boolean value indicating whether duplicates should be
     * eliminated in the result of this statement -- i.e., whether the
     * user specified SELECT DISTINCT.
     */
    public boolean distinctSpecified() {
        return this.distinctSpecified;
    }
    
    public void execute() throws DatabaseException, DeadlockException {
        TableIterator iter = null;
        
        try {

            // System.out.println("numTables: " + this.numTables());
            if (this.numTables() != 1) {
                throw new Exception("Specifying multiple table names in the FROM clause is not supported.");
            }

            // System.out.println("selectList.size " + this.selectList.size());
            // System.out.println("selectList.get(0) " + this.selectList.get(0));
            if (!(this.selectList.size() == 1 && this.selectList.get(0).equals(STAR))) {
                throw new Exception("Specifying column names in the SELECT clause is not supported.");
            }

            // Get the table from the FROM clause in the SQLstaement
            Table table = this.getTable(0);
            table.open();

            // Create a TableIterator for the table
            iter = new TableIterator(this, table, true);

            // Use printAll() with system.out to print table
            iter.printAll(System.out);

            // Print format
            System.out.println("Selected " + iter.numTuples() + " tuples.");
            
        } catch (Exception e) {
            if (DBMS.DEBUG) {
                e.printStackTrace();
            }
            String errMsg = e.getMessage();
            if (errMsg != null) {
                System.err.println(errMsg + ".");
            }
        }
        
        if (iter != null) {
            iter.close();
        }
    }
}
