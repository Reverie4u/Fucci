package fucci.mysql.ast;

import java.util.Map;

public class MySQLUnaryPostfixOperation implements MySQLExpression {

    private MySQLExpression expression;
    private UnaryPostfixOperator operator;
    private boolean negate;

    public enum UnaryPostfixOperator {
        IS_NULL, IS_TRUE, IS_FALSE;
    }

    public MySQLUnaryPostfixOperation(MySQLExpression expr, UnaryPostfixOperator op, boolean negate) {
        this.expression = expr;
        this.operator = op;
        this.setNegate(negate);
    }

    public MySQLExpression getExpression() {
        return expression;
    }

    public UnaryPostfixOperator getOperator() {
        return operator;
    }

    public boolean isNegated() {
        return negate;
    }

    public void setExpression(MySQLExpression newEXpression) {
        this.expression = newEXpression;
    }

    public void setOperator(UnaryPostfixOperator newOperator) {
        this.operator = newOperator;
    }

    public void setNegate(boolean negate) {
        this.negate = negate;
    }

    @Override
    public MySQLConstant getExpectedValue(Map<String, Object> row) {
        boolean val;
        MySQLConstant expectedValue = expression.getExpectedValue(row);
        switch (operator) {
            case IS_NULL:
                val = expectedValue.isNull();
                break;
            case IS_FALSE:
                val = !expectedValue.isNull() && !expectedValue.asBooleanNotNull();
                break;
            case IS_TRUE:
                val = !expectedValue.isNull() && expectedValue.asBooleanNotNull();
                break;
            default:
                throw new AssertionError(operator);
        }
        if (negate) {
            val = !val;
        }
        return MySQLConstant.createIntConstant(val ? 1 : 0);
    }

}
