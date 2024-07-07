package fucci.mysql;

import fucci.Randomly;
import fucci.TableTool;
import fucci.common.Column;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MySQLColumn extends Column {

    public MySQLColumn(MySQLTable table, String columnName, MySQLDataType dataType, boolean primaryKey, boolean unique,
            boolean notNull, int size) {
        super(table, columnName, dataType, primaryKey, unique, notNull, size);
    }

    @Override
    public String getRandomVal() {
        switch ((MySQLDataType) this.dataType) {
            case TINYINT:
                return Integer.toString(TableTool.rand.getInteger(-128, 127));
            case SMALLINT:
                return Integer.toString(TableTool.rand.getInteger(-32768, 32767));
            case MEDIUMINT:
                return Integer.toString(TableTool.rand.getInteger(-8388608, 8388607));
            case INT:
                return Integer.toString(TableTool.rand.getInteger(Integer.MIN_VALUE, Integer.MAX_VALUE));
            case BIGINT:
                return Long.toString(TableTool.rand.getInteger());
            case FLOAT:
            case DOUBLE:
            case DECIMAL:
                return Double.toString(TableTool.rand.getDouble());
            case CHAR:
            case VARCHAR:
            case TINYTEXT:
            case TEXT:
            case MEDIUMTEXT:
            case LONGTEXT:
                if (size == 0)
                    size = 20;
                String str = TableTool.rand.getString();
                if (str.length() > size) {
                    str = str.substring(0, size);
                }
                return "\"" + str + "\"";
            case BLOB:
            case MEDIUMBLOB:
            case LONGBLOB:
                return randomHexStr();
            default:
                throw new IllegalStateException("Unexpected value: " + this);
        }
    }

    private String randomHexStr() {
        final int size = 8;
        final String HEX = "0123456789ABCDEF";
        StringBuilder sb = new StringBuilder(size);
        sb.append("0x");
        for (int i = 0; i < size; i++) {
            sb.append(HEX.charAt(Randomly.getNextInt(0, 16)));
        }
        return sb.toString();
    }

}
