package fucci.mysql.ast;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * @see <a href=
 *      "https://dev.mysql.com/doc/refman/8.0/en/comparison-operators.html#operator_in">Comparison
 *      Functions and
 *      Operators</a>
 */
@Slf4j
public class MySQLInOperation implements MySQLExpression {

    private MySQLExpression expr;
    private List<MySQLExpression> listElements;
    private boolean isTrue;

    public MySQLInOperation(MySQLExpression expr, List<MySQLExpression> listElements, boolean isTrue) {
        this.expr = expr;
        this.listElements = listElements;
        this.isTrue = isTrue;
    }

    public MySQLExpression getExpr() {
        return expr;
    }

    public List<MySQLExpression> getListElements() {
        return listElements;
    }

    public void setExpr(MySQLExpression newExpr) {
        this.expr = newExpr;
    }

    public void setListElements(List<MySQLExpression> newListElements) {
        this.listElements = listElements;
    }

    public void setIsTrue(boolean newIsTrue) {
        this.isTrue = newIsTrue;
    }

    @Override
    public MySQLConstant getExpectedValue(Map<String, Object> row) {
        MySQLConstant leftVal = expr.getExpectedValue(row);
        if (leftVal.isNull()) {
            return MySQLConstant.createNullConstant();
        }
        /* workaround for https://bugs.mysql.com/bug.php?id=95957 */
        // if (leftVal.isInt() && !leftVal.isSigned()) {
        // log.info("IgnoreMeException 11");
        // throw new IgnoreMeException();
        // }

        boolean isNull = false;
        for (MySQLExpression rightExpr : listElements) {
            MySQLConstant rightVal = rightExpr.getExpectedValue(row);

            /* workaround for https://bugs.mysql.com/bug.php?id=95957 */
            // if (rightVal.isInt() && !rightVal.isSigned()) {
            // log.info("IgnoreMeException 12");
            // throw new IgnoreMeException();
            // }
            MySQLConstant convertedRightVal = rightVal;
            MySQLConstant isEquals = leftVal.isEquals(convertedRightVal);
            if (isEquals.isNull()) {
                isNull = true;
            } else {
                if (isEquals.getInt() == 1) {
                    return MySQLConstant.createBoolean(isTrue);
                }
            }
        }
        if (isNull) {
            return MySQLConstant.createNullConstant();
        } else {
            return MySQLConstant.createBoolean(!isTrue);
        }

    }

    // isTrue is true if the IN operator is used, false if NOT IN is used
    public boolean isTrue() {
        return isTrue;
    }
}
