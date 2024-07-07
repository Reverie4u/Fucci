package fucci.mysql.ast;

import java.util.Map;

public class MySQLDummyExpression implements MySQLExpression {
    private MySQLExpression node;

    public MySQLDummyExpression(MySQLExpression exprNode) {
        this.node = exprNode;
    }

    public void setNode(MySQLExpression newNode) {
        this.node = newNode;
    }

    public MySQLExpression getNode() {
        return this.node;
    }

    @Override
    public MySQLConstant getExpectedValue(Map<String, Object> row) {
        throw new AssertionError("Not supported for this operator");
    }

}
