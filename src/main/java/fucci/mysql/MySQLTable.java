package fucci.mysql;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import fucci.DBMS;
import fucci.Randomly;
import fucci.TableTool;
import fucci.common.Table;

public class MySQLTable extends Table {

    public MySQLTable(String tableName) {
        super(tableName);
        this.exprGenerator = new MySQLExpressionGenerator();
    }

    @Override
    protected String getColumn(int idx) {
        String columnName = "c" + idx;
        this.columnNames.add(columnName);
        MySQLDataType dataType = MySQLDataType.getRandomDataType();
        String typeLen = "";
        int size = 0;
        if (dataType.hasLen()) {
            size = 1 + Randomly.getNextInt(0, 20);
            typeLen = "(" + size + ")";
        }
        boolean isPrimaryKey = false;
        String primaryKey = "";
        if (allPrimaryKey && !hasPrimaryKey && dataType.isNumeric() && Randomly.baseInt() == 1) {
            primaryKey = " PRIMARY KEY";
            hasPrimaryKey = true;
            isPrimaryKey = true;
        }
        boolean isUnique = false;
        String unique = "";
        if (primaryKey.equals("") && dataType.isNumeric() && Randomly.baseInt() == 1) {
            unique = " UNIQUE";
            isUnique = true;
        }
        boolean isNotNull = false;
        String notNull = "";
        if (Randomly.baseInt() == 1) {
            notNull = " NOT NULL";
            isNotNull = true;
        }
        columns.put(columnName, new MySQLColumn(this, columnName, dataType,
                isPrimaryKey, isUnique, isNotNull, size));
        return columnName + " " + dataType.name() + typeLen + primaryKey + unique + notNull;
    }

    private enum MySQLTableOptions {
        AUTO_INCREMENT, AVG_ROW_LENGTH, CHECKSUM, COMPRESSION, DELAY_KEY_WRITE, INSERT_METHOD,
        KEY_BLOCK_SIZE, MAX_ROWS, MIN_ROWS, PACK_KEYS, STATS_AUTO_RECALC, STATS_PERSISTENT,
        STATS_SAMPLE_PAGES, COMMENT;

        public static List<MySQLTableOptions> getRandomTableOptions() {
            List<MySQLTableOptions> allowedOptions = Arrays.asList(MySQLTableOptions.values());
            if (TableTool.dbms.equals(DBMS.TIDB) || TableTool.oracle.equals("ALL") || TableTool.oracle.equals("DT")) {
                allowedOptions = Arrays.asList(AUTO_INCREMENT, COMMENT);
            }
            List<MySQLTableOptions> options;
            if (Randomly.getBooleanWithSmallProbability()) {
                options = Randomly.subset(allowedOptions);
            } else {
                if (Randomly.getBoolean()) {
                    options = Collections.emptyList();
                } else {
                    int count;
                    if (TableTool.dbms.equals(DBMS.TIDB) || TableTool.oracle.equals("ALL")
                            || TableTool.oracle.equals("DT")) {
                        count = Randomly.getNextInt(0, allowedOptions.size() + 1);
                    } else {
                        count = Randomly.smallNumber();
                    }
                    options = Randomly.nonEmptySubset(allowedOptions, count);
                }
            }
            return options;
        }
    }

    @Override
    protected String getTableOption() {
        StringBuilder sb = new StringBuilder();
        List<MySQLTableOptions> tableOptions = MySQLTableOptions.getRandomTableOptions();
        int i = 0;
        for (MySQLTableOptions o : tableOptions) {
            if (i++ != 0) {
                sb.append(", ");
            }
            switch (o) {
                case AUTO_INCREMENT:
                    sb.append("AUTO_INCREMENT = ");
                    sb.append(TableTool.rand.getPositiveInteger());
                    break;
                case AVG_ROW_LENGTH:
                    sb.append("AVG_ROW_LENGTH = ");
                    sb.append(TableTool.rand.getPositiveInteger());
                    break;
                case CHECKSUM:
                    sb.append("CHECKSUM = 1");
                    break;
                case COMPRESSION:
                    sb.append("COMPRESSION = '");
                    sb.append(Randomly.fromOptions("ZLIB", "LZ4", "NONE"));
                    sb.append("'");
                    break;
                case DELAY_KEY_WRITE:
                    sb.append("DELAY_KEY_WRITE = ");
                    sb.append(Randomly.fromOptions(0, 1));
                    break;
                case INSERT_METHOD:
                    sb.append("INSERT_METHOD = ");
                    sb.append(Randomly.fromOptions("NO", "FIRST", "LAST"));
                    break;
                case KEY_BLOCK_SIZE:
                    sb.append("KEY_BLOCK_SIZE = ");
                    sb.append(TableTool.rand.getPositiveInteger());
                    break;
                case MAX_ROWS:
                    sb.append("MAX_ROWS = ");
                    sb.append(TableTool.rand.getLong(0, Long.MAX_VALUE));
                    break;
                case MIN_ROWS:
                    sb.append("MIN_ROWS = ");
                    sb.append(TableTool.rand.getLong(1, Long.MAX_VALUE));
                    break;
                case PACK_KEYS:
                    sb.append("PACK_KEYS = ");
                    sb.append(Randomly.fromOptions("1", "0", "DEFAULT"));
                    break;
                case STATS_AUTO_RECALC:
                    sb.append("STATS_AUTO_RECALC = ");
                    sb.append(Randomly.fromOptions("1", "0", "DEFAULT"));
                    break;
                case STATS_PERSISTENT:
                    sb.append("STATS_PERSISTENT = ");
                    sb.append(Randomly.fromOptions("1", "0", "DEFAULT"));
                    break;
                case STATS_SAMPLE_PAGES:
                    sb.append("STATS_SAMPLE_PAGES = ");
                    sb.append(TableTool.rand.getInteger(1, Short.MAX_VALUE));
                    break;
                case COMMENT:
                    sb.append("COMMENT = 'comment info'");
                    break;
                default:
                    throw new AssertionError(o);
            }
        }
        return sb.toString();
    }
}
