package fucci.mysql.ast;

import java.util.Map;

import fucci.mysql.ast.MySQLBinaryComparisonOperation.BinaryComparisonOperator;
import fucci.mysql.ast.MySQLBinaryLogicalOperation.MySQLBinaryLogicalOperator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MySQLBetweenOperation implements MySQLExpression {

    private MySQLExpression expr;
    private MySQLExpression left;
    private MySQLExpression right;

    public MySQLBetweenOperation(MySQLExpression expr, MySQLExpression left, MySQLExpression right) {
        this.expr = expr;
        this.left = left;
        this.right = right;
    }

    public MySQLExpression getExpr() {
        return expr;
    }

    public MySQLExpression getLeft() {
        return left;
    }

    public MySQLExpression getRight() {
        return right;
    }

    public void setExpr(MySQLExpression newExpr) {
        this.expr = newExpr;
    }

    public void setLeft(MySQLExpression newLeft) {
        this.left = newLeft;
    }

    public void setRight(MySQLExpression newRight) {
        this.right = newRight;
    }

    @Override
    public MySQLConstant getExpectedValue(Map<String, Object> row) {
        MySQLExpression[] arr = { left, right, expr };
        MySQLConstant convertedExpr = MySQLComputableFunction.castToMostGeneralType(expr.getExpectedValue(row), row,
                arr);
        MySQLConstant convertedLeft = MySQLComputableFunction.castToMostGeneralType(left.getExpectedValue(row), row,
                arr);
        MySQLConstant convertedRight = MySQLComputableFunction.castToMostGeneralType(right.getExpectedValue(row), row,
                arr);

        /* workaround for https://bugs.mysql.com/bug.php?id=96006 */
        // if (convertedLeft.isInt() && convertedLeft.getInt() < 0 ||
        // convertedRight.isInt() && convertedRight.getInt() < 0
        // || convertedExpr.isInt() && convertedExpr.getInt() < 0) {
        // log.info("IgnoreMeException 1");
        // throw new IgnoreMeException();
        // }
        MySQLBinaryComparisonOperation leftComparison = new MySQLBinaryComparisonOperation(convertedLeft, convertedExpr,
                BinaryComparisonOperator.LESS_EQUALS);
        MySQLBinaryComparisonOperation rightComparison = new MySQLBinaryComparisonOperation(convertedExpr,
                convertedRight, BinaryComparisonOperator.LESS_EQUALS);
        MySQLBinaryLogicalOperation andOperation = new MySQLBinaryLogicalOperation(leftComparison, rightComparison,
                MySQLBinaryLogicalOperator.AND);
        return andOperation.getExpectedValue(row);
    }

}
