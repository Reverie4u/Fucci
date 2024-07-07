package fucci.mysql;

import java.util.ArrayList;
import java.util.List;

import fucci.Randomly;
import fucci.TableTool;
import fucci.common.ExprGen;
import fucci.mysql.ast.MySQLBetweenOperation;
import fucci.mysql.ast.MySQLBinaryComparisonOperation;
import fucci.mysql.ast.MySQLBinaryComparisonOperation.BinaryComparisonOperator;
import fucci.mysql.ast.MySQLBinaryLogicalOperation;
import fucci.mysql.ast.MySQLBinaryLogicalOperation.MySQLBinaryLogicalOperator;
import fucci.mysql.ast.MySQLBinaryOperation;
import fucci.mysql.ast.MySQLBinaryOperation.MySQLBinaryOperator;
import fucci.mysql.ast.MySQLCastOperation;
import fucci.mysql.ast.MySQLColumnReference;
import fucci.mysql.ast.MySQLConstant.MySQLIntConstant;
import fucci.mysql.ast.MySQLConstant.MySQLNullConstant;
import fucci.mysql.ast.MySQLExpression;
import fucci.mysql.ast.MySQLInOperation;
import fucci.mysql.ast.MySQLUnaryPostfixOperation;
import fucci.mysql.ast.MySQLUnaryPrefixOperation;
import fucci.mysql.ast.MySQLUnaryPrefixOperation.MySQLUnaryPrefixOperator;

public class MySQLExpressionGenerator extends ExprGen {
    private enum Actions {
        COLUMN, CONSTANT, UNARY_PREFIX_OPERATION, UNARY_POSTFIX, BINARY_LOGICAL_OPERATOR, BINARY_BIT_OPERATION,
        BINARY_COMPARISON_OPERATION, IN_OPERATION, BETWEEN_OPERATOR, CAST;
    }

    @Override
    public MySQLExpression genPredicate() {
        return generateExpression(0);
    }

    public MySQLExpression generateExpression(int depth) {
        if (depth > depthLimit) {
            return generateLeafNode();
        }
        switch (Randomly.fromOptions(Actions.values())) {
            case COLUMN:
                return generateColumn();
            case CONSTANT:
                return generateConstant();
            case UNARY_PREFIX_OPERATION:
                MySQLExpression subExpr = generateExpression(depth + 1);
                MySQLUnaryPrefixOperator random = MySQLUnaryPrefixOperator.getRandom();
                return new MySQLUnaryPrefixOperation(subExpr, random);
            case UNARY_POSTFIX:
                return new MySQLUnaryPostfixOperation(generateExpression(depth + 1),
                        Randomly.fromOptions(MySQLUnaryPostfixOperation.UnaryPostfixOperator.values()),
                        Randomly.getBoolean());
            case BINARY_LOGICAL_OPERATOR:
                return new MySQLBinaryLogicalOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                        MySQLBinaryLogicalOperator.getRandom());
            case BINARY_COMPARISON_OPERATION:
                return new MySQLBinaryComparisonOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                        BinaryComparisonOperator.getRandom());
            case CAST:
                return new MySQLCastOperation(generateExpression(depth + 1), MySQLCastOperation.CastType.getRandom());
            case IN_OPERATION:
                MySQLExpression expr = generateExpression(depth + 1);
                List<MySQLExpression> rightList = new ArrayList<>();
                for (int i = 0; i < 1 + Randomly.smallNumber(); i++) {
                    rightList.add(generateExpression(depth + 1));
                }
                return new MySQLInOperation(expr, rightList, Randomly.getBoolean());
            case BINARY_BIT_OPERATION:
                return new MySQLBinaryOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                        MySQLBinaryOperator.getRandom());
            case BETWEEN_OPERATOR:
                return new MySQLBetweenOperation(generateExpression(depth + 1), generateExpression(depth + 1),
                        generateExpression(depth + 1));
            default:
                throw new AssertionError();
        }
    }

    public MySQLExpression generateLeafNode() {
        if (Randomly.getBoolean() && !columns.isEmpty()) {
            return generateColumn();
        } else {
            return generateConstant();
        }
    }

    private enum ConstantType {
        // INT, NULL, STRING, DOUBLE;
        INT, NULL;
    }

    public MySQLExpression generateConstant() {
        ConstantType[] values;
        values = ConstantType.values();
        // 较大概率生成INT，较小概率（1/10）生成NULL。
        if (Randomly.getBooleanWithRatherLowProbability()) {
            return new MySQLNullConstant();
        }
        long num = TableTool.rand.getInteger();
        return new MySQLIntConstant(num);
        // switch (Randomly.fromOptions(values)) {
        // case INT:
        // long num = TableTool.rand.getInteger();
        // return new MySQLIntConstant(num);
        // case NULL:
        // return new MySQLNullConstant();
        // // case STRING:
        // // String string = "\"" + TableTool.rand.getString() + "\"";
        // // return new MySQLStringConstant(string);
        // // case DOUBLE:
        // // double val;
        // // do {
        // // val = TableTool.rand.getDouble();
        // // } while (Double.isInfinite(val) || Double.isNaN(val));
        // // return new MySQLDoubleConstant(val);
        // default:
        // throw new AssertionError();
    }

    protected MySQLExpression generateColumn() {
        MySQLColumn c = (MySQLColumn) Randomly.fromList(new ArrayList<>(columns.values()));
        return MySQLColumnReference.create(c, null);
    }
}
