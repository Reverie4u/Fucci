package fucci.mysql.ast;

import java.util.Map;

import fucci.mysql.MySQLColumn;
import fucci.mysql.MySQLDataType;
import fucci.mysql.ast.MySQLConstant.MySQLDoubleConstant;

public class MySQLColumnReference implements MySQLExpression {

    private MySQLColumn column;
    private MySQLConstant value;

    public MySQLColumnReference(MySQLColumn column, MySQLConstant value) {
        this.column = column;
        this.value = value;
    }

    public static MySQLColumnReference create(MySQLColumn column, MySQLConstant value) {
        return new MySQLColumnReference(column, value);
    }

    public MySQLColumn getColumn() {
        return column;
    }

    public MySQLConstant getValue() {
        return value;
    }

    public void setColumn(MySQLColumn newColumn) {
        this.column = newColumn;
    }

    public void setValue(MySQLConstant newValue) {
        this.value = newValue;
    }

    @Override
    public MySQLConstant getExpectedValue(Map<String, Object> row) {
        // 根据列类型，构造MySQLConstant
        switch ((MySQLDataType) column.getDataType()) {
            case TINYINT:
            case SMALLINT:
            case MEDIUMINT:
            case INT:
            case BIGINT:
                Integer intVal = (Integer) row.get(column.getColumnName());
                if (intVal == null) {
                    return MySQLConstant.createNullConstant();
                }
                return MySQLConstant.createIntConstant(intVal.intValue());
            case FLOAT:
            case DOUBLE:
            case DECIMAL:
                return new MySQLDoubleConstant((double) row.get(column.getColumnName()));
            case CHAR:
            case VARCHAR:
            case TINYTEXT:
            case TEXT:
            case MEDIUMTEXT:
            case LONGTEXT:
            case BLOB:
            case MEDIUMBLOB:
            case LONGBLOB:
                String str = (String) row.get(column.getColumnName());
                if (str == null) {
                    return MySQLConstant.createNullConstant();
                }
                return MySQLConstant.createStringConstant((String) row.get(column.getColumnName()));
            default:
                throw new IllegalStateException("Unexpected value: " + this);
        }
    }

}
