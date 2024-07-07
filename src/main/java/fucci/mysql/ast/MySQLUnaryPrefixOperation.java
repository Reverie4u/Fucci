package fucci.mysql.ast;

import java.util.Map;

import fucci.Randomly;
import fucci.common.BinaryOperatorNode.Operator;
import fucci.common.IgnoreMeException;
import fucci.common.UnaryOperatorNode;
import fucci.mysql.ast.MySQLUnaryPrefixOperation.MySQLUnaryPrefixOperator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MySQLUnaryPrefixOperation extends UnaryOperatorNode<MySQLExpression, MySQLUnaryPrefixOperator>
        implements MySQLExpression {

    public enum MySQLUnaryPrefixOperator implements Operator {
        NOT("!", "NOT") {
            @Override
            public MySQLConstant applyNotNull(MySQLConstant expr) {
                return MySQLConstant.createIntConstant(expr.asBooleanNotNull() ? 0 : 1);
            }
        },
        PLUS("+") {
            @Override
            public MySQLConstant applyNotNull(MySQLConstant expr) {
                return expr;
            }
        },
        MINUS("-") {
            @Override
            public MySQLConstant applyNotNull(MySQLConstant expr) {
                if (expr.isString()) {
                    // TODO: implement floating points
                    log.info("IgnoreMeException 13");
                    throw new IgnoreMeException();
                } else if (expr.isInt()) {
                    if (!expr.isSigned()) {
                        // // TODO
                        // // 在无符号整数前面加负号
                        // log.info("IgnoreMeException 14");
                        // throw new IgnoreMeException();
                    }
                    return MySQLConstant.createIntConstant(-expr.getInt());
                } else {
                    throw new AssertionError(expr);
                }
            }
        };

        private String[] textRepresentations;

        MySQLUnaryPrefixOperator(String... textRepresentations) {
            this.textRepresentations = textRepresentations.clone();
        }

        public abstract MySQLConstant applyNotNull(MySQLConstant expr);

        public static MySQLUnaryPrefixOperator getRandom() {
            return Randomly.fromOptions(values());
        }

        @Override
        public String getTextRepresentation() {
            return Randomly.fromOptions(textRepresentations);
        }
    }

    public MySQLUnaryPrefixOperation(MySQLExpression expr, MySQLUnaryPrefixOperator op) {
        super(expr, op);
    }

    @Override
    public MySQLConstant getExpectedValue(Map<String, Object> row) {
        MySQLConstant subExprVal = expr.getExpectedValue(row);
        if (subExprVal.isNull()) {
            return MySQLConstant.createNullConstant();
        } else {
            return op.applyNotNull(subExprVal);
        }
    }

    @Override
    public OperatorKind getOperatorKind() {
        return OperatorKind.PREFIX;
    }

}
