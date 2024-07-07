package fucci.mysql.ast;

import java.util.Map;

import fucci.Randomly;

public class MySQLBinaryLogicalOperation implements MySQLExpression {

    private MySQLExpression left;
    private MySQLExpression right;
    private MySQLBinaryLogicalOperator op;
    private String textRepresentation;

    public enum MySQLBinaryLogicalOperator {
        AND("AND", "&&") {
            @Override
            public MySQLConstant apply(MySQLConstant left, MySQLConstant right) {
                if (left.isNull() && right.isNull()) {
                    return MySQLConstant.createNullConstant();
                } else if (left.isNull()) {
                    if (right.asBooleanNotNull()) {
                        return MySQLConstant.createNullConstant();
                    } else {
                        return MySQLConstant.createFalse();
                    }
                } else if (right.isNull()) {
                    if (left.asBooleanNotNull()) {
                        return MySQLConstant.createNullConstant();
                    } else {
                        return MySQLConstant.createFalse();
                    }
                } else {
                    return MySQLConstant.createBoolean(left.asBooleanNotNull() && right.asBooleanNotNull());
                }
            }
        },
        OR("OR", "||") {
            @Override
            public MySQLConstant apply(MySQLConstant left, MySQLConstant right) {
                if (!left.isNull() && left.asBooleanNotNull()) {
                    return MySQLConstant.createTrue();
                } else if (!right.isNull() && right.asBooleanNotNull()) {
                    return MySQLConstant.createTrue();
                } else if (left.isNull() || right.isNull()) {
                    return MySQLConstant.createNullConstant();
                } else {
                    return MySQLConstant.createFalse();
                }
            }
        },
        XOR("XOR") {
            @Override
            public MySQLConstant apply(MySQLConstant left, MySQLConstant right) {
                if (left.isNull() || right.isNull()) {
                    return MySQLConstant.createNullConstant();
                }
                boolean xorVal = left.asBooleanNotNull() ^ right.asBooleanNotNull();
                return MySQLConstant.createBoolean(xorVal);
            }
        };

        private final String[] textRepresentations;

        MySQLBinaryLogicalOperator(String... textRepresentations) {
            this.textRepresentations = textRepresentations.clone();
        }

        String getTextRepresentation() {
            return Randomly.fromOptions(textRepresentations);
        }

        public abstract MySQLConstant apply(MySQLConstant left, MySQLConstant right);

        public static MySQLBinaryLogicalOperator getRandom() {
            // return Randomly.fromOptions(values());
            return values()[0];
        }
    }

    public MySQLBinaryLogicalOperation(MySQLExpression left, MySQLExpression right, MySQLBinaryLogicalOperator op) {
        this.left = left;
        this.right = right;
        this.op = op;
        this.textRepresentation = op.getTextRepresentation();
    }

    public MySQLExpression getLeft() {
        return left;
    }

    public MySQLBinaryLogicalOperator getOp() {
        return op;
    }

    public MySQLExpression getRight() {
        return right;
    }

    public String getTextRepresentation() {
        return textRepresentation;
    }

    public void setLeft(MySQLExpression newLeft) {
        this.left = newLeft;
    }

    public void setOp(MySQLBinaryLogicalOperator newOp) {
        this.op = newOp;
    }

    public void setRight(MySQLExpression newRight) {
        this.right = newRight;
    }

    public void setTextRepresentation(String newTextRepresentation) {
        this.textRepresentation = newTextRepresentation;
    }

    @Override
    public MySQLConstant getExpectedValue(Map<String, Object> row) {
        MySQLConstant leftExpected = left.getExpectedValue(row);
        MySQLConstant rightExpected = right.getExpectedValue(row);
        if (left.getExpectedValue(row) == null || right.getExpectedValue(row) == null) {
            return null;
        }
        return op.apply(leftExpected, rightExpected);
    }

}
