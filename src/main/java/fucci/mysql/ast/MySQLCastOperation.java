package fucci.mysql.ast;

import java.util.Map;

public class MySQLCastOperation implements MySQLExpression {
    private MySQLExpression expr;
    private CastType type;

    public enum CastType {
        UNSIGNED, SIGNED;

        // public static CastType getRandom() {
        // return Randomly.fromOptions(CastType.values());
        // }
        public static CastType getRandom() {
            return SIGNED;
            // return Randomly.fromOptions(CastType.values());
        }

    }

    public MySQLCastOperation(MySQLExpression expr, CastType type) {
        this.expr = expr;
        this.type = type;
    }

    public MySQLExpression getExpr() {
        return expr;
    }

    public CastType getType() {
        return type;
    }

    public void setExpr(MySQLExpression newExpr) {
        this.expr = newExpr;
    }

    public void setType(CastType newType) {
        this.type = newType;
    }

    @Override
    public MySQLConstant getExpectedValue(Map<String, Object> row) {
        return expr.getExpectedValue(row).castAs(type);
    }

}
