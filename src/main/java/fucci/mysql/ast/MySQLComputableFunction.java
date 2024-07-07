package fucci.mysql.ast;

import java.util.Map;

import fucci.mysql.MySQLDataType;
import fucci.mysql.ast.MySQLCastOperation.CastType;

public class MySQLComputableFunction implements MySQLExpression {

    public static MySQLConstant castToMostGeneralType(MySQLConstant cons, Map<String, Object> row,
            MySQLExpression... typeExpressions) {
        if (cons.isNull()) {
            return cons;
        }
        MySQLDataType type = getMostGeneralType(row, typeExpressions);
        switch (type) {
            case INT:
                if (cons.isInt()) {
                    return cons;
                } else {
                    return MySQLConstant.createIntConstant(cons.castAs(CastType.SIGNED).getInt());
                }
            case VARCHAR:
                return MySQLConstant.createStringConstant(cons.castAsString());
            default:
                throw new AssertionError(type);
        }
    }

    public static MySQLDataType getMostGeneralType(Map<String, Object> row, MySQLExpression... expressions) {
        MySQLDataType type = null;
        for (MySQLExpression expr : expressions) {
            MySQLDataType exprType;
            if (expr instanceof MySQLColumnReference) {
                exprType = (MySQLDataType) ((MySQLColumnReference) expr).getColumn().getDataType();
            } else {
                exprType = expr.getExpectedValue(row).getType();
            }
            if (exprType == MySQLDataType.VARCHAR || exprType == MySQLDataType.TEXT
                    || exprType == MySQLDataType.CHAR) {
                exprType = MySQLDataType.VARCHAR;
            }

            if (type == null) {
                type = exprType;
            } else if (exprType == MySQLDataType.VARCHAR) {
                // 如果至少有一个字符串类型，都转成字符串类型；否则都转成整数形式
                type = MySQLDataType.VARCHAR;
            }

        }
        return type;
    }

}
