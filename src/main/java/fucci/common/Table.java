package fucci.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fucci.DBMS;
import fucci.IsolationLevel;
import fucci.Randomly;
import fucci.SQLConnection;
import fucci.StatementCell;
import fucci.TableTool;
import fucci.Transaction;
import fucci.mysql.ast.MySQLExpression;
import fucci.mysql.visitor.MySQLVisitor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class Table {
    protected final String tableName;
    protected final boolean allPrimaryKey;
    protected boolean hasPrimaryKey;
    protected String createTableSql;
    protected final ArrayList<String> initializeStatements;
    protected final ArrayList<String> columnNames;
    protected final HashMap<String, Column> columns;
    protected int indexCnt = 0;
    protected ExprGen exprGenerator;
    protected int initRowCount = 0;

    public void setCreateTableSql(String createTableSql) {
        this.createTableSql = createTableSql;
    }

    public Table(String tableName) {
        this.tableName = tableName;
        this.allPrimaryKey = Randomly.getBoolean();
        this.hasPrimaryKey = false;
        createTableSql = "";
        initializeStatements = new ArrayList<>();
        columnNames = new ArrayList<>();
        columns = new HashMap<>();
    }

    public int getInitRowCount() {
        return initRowCount;
    }

    public String getCreateTableSql() {
        return createTableSql;
    }

    public ArrayList<String> getInitializeStatements() {
        return initializeStatements;
    }

    public boolean create() {
        this.drop();
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(tableName).append("(");
        int columnCnt = 1 + Randomly.getNextInt(0, 6);
        for (int i = 0; i < columnCnt; i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(getColumn(i));
        }
        sb.append(") ");
        sb.append(getTableOption());
        createTableSql = sb.toString();
        exprGenerator.setColumns(columns);
        return TableTool.executeOnTable(createTableSql);
    }

    protected abstract String getTableOption();

    protected abstract String getColumn(int idx);

    public void drop() {
        TableTool.executeOnTable("DROP TABLE IF EXISTS " + tableName);
    }

    public void initialize() {
        while (!this.create()) {
            log.info("Create table failed, {}", getCreateTableSql());
        }
        // 随机生成创建索引/插入语句
        for (int i = 0; i < Randomly.getNextInt(5, 15); i++) {
            String initSQL;
            if (Randomly.getNextInt(0, 15) == 10) {
                initSQL = genAddIndexStatement();
            } else {
                initSQL = genInsertStatement(new Transaction(0), 0).getStatement();
            }
            initializeStatements.add(initSQL);
            TableTool.executeOnTable(initSQL);
        }
        String query = "SELECT COUNT(*) FROM " + tableName;
        log.info("query: {}", query);
        initRowCount = TableTool.executeQueryReturnInteger(query);
        log.info("Rowcount: {}", initRowCount);
    }

    public StatementCell genSelectStatement(Transaction tx, int statementId) {
        MySQLExpression predicate = (MySQLExpression) exprGenerator.genPredicate();
        List<String> selectedColumns = Randomly.nonEmptySubset(columnNames);
        String postfix = "";
        if (Randomly.getBoolean()) {
            if (Randomly.getBoolean()) {
                postfix = " FOR UPDATE";
            } else {
                if (TableTool.dbms != DBMS.TIDB) {
                    postfix = " LOCK IN SHARE MODE";
                }
            }
        }
        String whereClause = MySQLVisitor.asString(predicate);
        if (whereClause.isEmpty()) {
            whereClause = "TRUE";
        }
        String stmtText = "SELECT " + String.join(", ", selectedColumns) + " FROM "
                + tableName + " WHERE " + whereClause + postfix;
        return new StatementCell(tx, statementId, stmtText, predicate, selectedColumns);
    }

    public StatementCell genInsertStatement(Transaction tx, int statementId) {
        List<String> insertedCols = Randomly.nonEmptySubset(columnNames);
        for (String colName : columns.keySet()) {
            Column column = columns.get(colName);
            if ((column.isPrimaryKey() || column.isNotNull()) && !insertedCols.contains(colName)) {
                insertedCols.add(colName);
            }
        }
        Map<String, String> insertMap = new HashMap<>();
        List<String> insertedVals = new ArrayList<>();
        for (String colName : insertedCols) {
            Column column = columns.get(colName);
            String val = column.getRandomVal();
            insertedVals.add(val);
            insertMap.put(colName, val);
        }
        String ignore = "";
        if (Randomly.getBoolean()) {
            ignore = "IGNORE ";
        }
        String stmtText = "INSERT " + ignore + "INTO " + tableName + "(" + String.join(", ", insertedCols)
                + ") VALUES (" + String.join(", ", insertedVals) + ")";
        return new StatementCell(tx, statementId, stmtText, insertMap);

    }

    public StatementCell genUpdateStatement(Transaction tx, int statementId) {
        MySQLExpression predicate = (MySQLExpression) exprGenerator.genPredicate();
        String whereClause = MySQLVisitor.asString(predicate);
        if (whereClause.isEmpty()) {
            whereClause = "TRUE";
        }
        List<String> updatedCols = Randomly.nonEmptySubset(columnNames);
        List<String> setPairs = new ArrayList<>();
        Map<String, String> setMap = new HashMap<>();
        for (String colName : updatedCols) {
            String val = columns.get(colName).getRandomVal();
            setPairs.add(colName + "=" + val);
            setMap.put(colName, val);
        }
        String stmtText = "UPDATE " + tableName + " SET " + String.join(", ", setPairs) + " WHERE " + whereClause;
        return new StatementCell(tx, statementId, stmtText, predicate, setMap);
    }

    public StatementCell genDeleteStatement(Transaction tx, int statementId) {
        MySQLExpression predicate = (MySQLExpression) exprGenerator.genPredicate();
        String whereClause = MySQLVisitor.asString(predicate);
        if (whereClause.isEmpty()) {
            whereClause = "TRUE";
        }
        String stmtText = "DELETE FROM " + tableName + " WHERE " + whereClause;
        return new StatementCell(tx, statementId, stmtText, predicate);
    }

    public String genAddIndexStatement() {
        List<String> candidateColumns = Randomly.nonEmptySubset(columnNames);
        List<String> indexedColumns = new ArrayList<>();
        for (String colName : candidateColumns) {
            Column column = columns.get(colName);
            if (column.getDataType().isNumeric()) {
                indexedColumns.add(colName);
            } else if (column.getDataType().isString()) {
                if (TableTool.dbms == DBMS.MYSQL || TableTool.dbms == DBMS.MARIADB || TableTool.dbms == DBMS.TIDB) {
                    indexedColumns.add(colName + "(5)");
                } else {
                    indexedColumns.add(colName);
                }
            }
        }
        String indexName = "i" + (indexCnt++);
        String unique = "";
        if (Randomly.getBoolean()) {
            unique = "UNIQUE ";
        }
        return "CREATE " + unique + "INDEX " + indexName + " ON " + tableName
                + " (" + String.join(", ", indexedColumns) + ")";
    }

    public void setIsolationLevel(SQLConnection conn, IsolationLevel isolationLevel) {
        String sql = "SET SESSION TRANSACTION ISOLATION LEVEL " + isolationLevel.getName();
        TableTool.executeWithConn(conn, sql);
    }

    public Transaction genTransaction(int txId) {
        IsolationLevel isolationLevel = Randomly.fromList(TableTool.possibleIsolationLevels);
        return genTransaction(txId, isolationLevel);
    }

    public Transaction genTransaction(int txId, IsolationLevel isolationLevel) {
        SQLConnection refConn = null;
        if (TableTool.oracle.equals("DT") || TableTool.oracle.equals("ALL")) {
            // 需要启动参照数据库
            refConn = TableTool.genAnotherConnection();
            setIsolationLevel(refConn, isolationLevel);
        }
        SQLConnection txConn = TableTool.genConnection();
        Transaction tx = new Transaction(txId, isolationLevel, txConn, refConn);
        setIsolationLevel(txConn, isolationLevel);
        int n = Randomly.getNextInt(TableTool.TxSizeMin, TableTool.TxSizeMax);
        ArrayList<StatementCell> statementList = new ArrayList<>();
        StatementCell cell = new StatementCell(tx, 0, "BEGIN");
        statementList.add(cell);
        for (int i = 1; i <= n; i++) {
            cell = genStatement(tx, i);
            statementList.add(cell);
        }
        String lastStmt = "COMMIT";
        if (Randomly.getBoolean()) {
            lastStmt = "ROLLBACK";
        }
        cell = new StatementCell(tx, n + 1, lastStmt);
        statementList.add(cell);
        tx.setStatements(statementList);
        return tx;
    }

    /**
     * 生成随机的SQL语句
     * 
     * @return
     */
    public StatementCell genStatement(Transaction tx, int statementId) {
        StatementCell statementCell;
        // 重复以生成合法的语句
        do {
            while (true) {
                if (Randomly.getBooleanWithRatherLowProbability()) {
                    statementCell = genSelectStatement(tx, statementId);
                    break;
                }
                if (Randomly.getBooleanWithRatherLowProbability()) {
                    if (Randomly.getBooleanWithRatherLowProbability()) {
                        statementCell = genInsertStatement(tx, statementId);
                    } else {
                        statementCell = genUpdateStatement(tx, statementId);
                    }
                    break;
                }
                if (Randomly.getBooleanWithSmallProbability()) {
                    statementCell = genDeleteStatement(tx, statementId);
                    break;
                }
            }
        } while (!TableTool.checkSyntax(statementCell.getStatement()));
        return statementCell;
    }

    @Override
    public String toString() {
        return String.format("[Table %s in DB %s Column:%s]", tableName, TableTool.DatabaseName,
                columnNames);
    }
}
