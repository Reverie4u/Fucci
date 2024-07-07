package fucci.mysql.visitor;

import java.util.List;
import java.util.stream.Collectors;

import fucci.MySQLExpressionBaseVisitor;
import fucci.MySQLExpressionParser;
import fucci.mysql.MySQLColumn;
import fucci.mysql.MySQLDataType;
import fucci.mysql.ast.MySQLBetweenOperation;
import fucci.mysql.ast.MySQLBinaryComparisonOperation;
import fucci.mysql.ast.MySQLBinaryComparisonOperation.BinaryComparisonOperator;
import fucci.mysql.ast.MySQLBinaryLogicalOperation;
import fucci.mysql.ast.MySQLBinaryLogicalOperation.MySQLBinaryLogicalOperator;
import fucci.mysql.ast.MySQLBinaryOperation;
import fucci.mysql.ast.MySQLBinaryOperation.MySQLBinaryOperator;
import fucci.mysql.ast.MySQLCastOperation;
import fucci.mysql.ast.MySQLCastOperation.CastType;
import fucci.mysql.ast.MySQLColumnReference;
import fucci.mysql.ast.MySQLConstant.MySQLIntConstant;
import fucci.mysql.ast.MySQLConstant.MySQLNullConstant;
import fucci.mysql.ast.MySQLExpression;
import fucci.mysql.ast.MySQLInOperation;
import fucci.mysql.ast.MySQLUnaryPostfixOperation;
import fucci.mysql.ast.MySQLUnaryPostfixOperation.UnaryPostfixOperator;
import fucci.mysql.ast.MySQLUnaryPrefixOperation;
import fucci.mysql.ast.MySQLUnaryPrefixOperation.MySQLUnaryPrefixOperator;

public class MySQLExpressionVisitorImpl extends MySQLExpressionBaseVisitor<MySQLExpression> {
    @Override
    public MySQLExpression visitExpression(MySQLExpressionParser.ExpressionContext ctx) {
        // BETWEEN 运算
        if (ctx.BETWEEN() != null) {
            return new MySQLBetweenOperation(visit(ctx.expression(0)), visit(ctx.expression(1)),
                    visit(ctx.expression(2)));
        }
        // IN 运算
        else if (ctx.IN() != null) {
            boolean isTrue = ctx.NOT() == null;
            // 获取expressionList并转化成List<MySQLExpression>
            List<MySQLExpression> expressions = ctx.expressionList().expression().stream().map(this::visit)
                    .collect(Collectors.toList());
            return new MySQLInOperation(visit(ctx.expression(0)), expressions, isTrue);
        }
        // 一元后缀运算符
        else if (ctx.IS() != null && ctx.literal().TRUE_LITERAL() != null) {
            boolean isTrue = ctx.NOT() != null;
            return new MySQLUnaryPostfixOperation(visit(ctx.expression(0)), UnaryPostfixOperator.IS_TRUE, isTrue);
        } else if (ctx.IS() != null && ctx.literal().FALSE_LITERAL() != null) {
            boolean isTrue = ctx.NOT() != null;
            return new MySQLUnaryPostfixOperation(visit(ctx.expression(0)), UnaryPostfixOperator.IS_FALSE, isTrue);
        } else if (ctx.IS() != null && ctx.literal().NULL_LITERAL() != null) {
            boolean isTrue = ctx.NOT() != null;
            return new MySQLUnaryPostfixOperation(visit(ctx.expression(0)), UnaryPostfixOperator.IS_NULL, isTrue);
        } else if (ctx.IS() != null && ctx.literal().UNKNOWN_LITERAL() != null) {
            boolean isTrue = ctx.NOT() != null;
            return new MySQLUnaryPostfixOperation(visit(ctx.expression(0)), UnaryPostfixOperator.IS_NULL, isTrue);
        }
        // 一元前缀运算符
        else if (ctx.BITNOT() != null || ctx.NOT() != null) {
            return new MySQLUnaryPrefixOperation(visit(ctx.expression(0)), MySQLUnaryPrefixOperator.NOT);
        } else if (ctx.PLUS() != null) {
            return new MySQLUnaryPrefixOperation(visit(ctx.expression(0)), MySQLUnaryPrefixOperator.PLUS);
        } else if (ctx.MINUS() != null) {
            return new MySQLUnaryPrefixOperation(visit(ctx.expression(0)), MySQLUnaryPrefixOperator.MINUS);
        }
        // CAST 运算
        else if (ctx.CAST() != null) {
            CastType type = ("SIGNED").equals(ctx.TYPE().getText()) ? CastType.SIGNED : CastType.UNSIGNED;
            return new MySQLCastOperation(visit(ctx.expression(0)), type);
        }
        // 位运算符
        else if (ctx.AND_OP() != null) {
            return new MySQLBinaryOperation(visit(ctx.expression(0)), visit(ctx.expression(1)),
                    MySQLBinaryOperator.AND);
        } else if (ctx.XOR_OP() != null) {
            return new MySQLBinaryOperation(visit(ctx.expression(0)), visit(ctx.expression(1)),
                    MySQLBinaryOperator.XOR);
        } else if (ctx.OR_OP() != null) {
            return new MySQLBinaryOperation(visit(ctx.expression(0)), visit(ctx.expression(1)), MySQLBinaryOperator.OR);
        }
        // 二元比较运算符
        else if (ctx.EQUALS() != null) {
            return new MySQLBinaryComparisonOperation(visit(ctx.expression(0)), visit(ctx.expression(1)),
                    BinaryComparisonOperator.EQUALS);
        } else if (ctx.NOT_EQUALS() != null) {
            return new MySQLBinaryComparisonOperation(visit(ctx.expression(0)), visit(ctx.expression(1)),
                    BinaryComparisonOperator.NOT_EQUALS);
        } else if (ctx.LESS() != null) {
            return new MySQLBinaryComparisonOperation(visit(ctx.expression(0)), visit(ctx.expression(1)),
                    BinaryComparisonOperator.LESS);
        } else if (ctx.LESS_EQUALS() != null) {
            return new MySQLBinaryComparisonOperation(visit(ctx.expression(0)), visit(ctx.expression(1)),
                    BinaryComparisonOperator.LESS_EQUALS);
        } else if (ctx.GREATER() != null) {
            return new MySQLBinaryComparisonOperation(visit(ctx.expression(0)), visit(ctx.expression(1)),
                    BinaryComparisonOperator.GREATER);
        } else if (ctx.GREATER_EQUALS() != null) {
            return new MySQLBinaryComparisonOperation(visit(ctx.expression(0)), visit(ctx.expression(1)),
                    BinaryComparisonOperator.GREATER_EQUALS);
        } else if (ctx.LIKE() != null) {
            return new MySQLBinaryComparisonOperation(visit(ctx.expression(0)), visit(ctx.expression(1)),
                    BinaryComparisonOperator.LIKE);
        }
        // 括号运算
        else if (ctx.LEFT_BRACKET() != null) {
            return visit(ctx.expression(0));
        }
        // 二元逻辑运算符
        else if (ctx.AND() != null || ctx.BITAND() != null) {
            return new MySQLBinaryLogicalOperation(visit(ctx.expression(0)), visit(ctx.expression(1)),
                    MySQLBinaryLogicalOperator.AND);
        } else if (ctx.OR() != null || ctx.BITOR() != null) {
            return new MySQLBinaryLogicalOperation(visit(ctx.expression(0)), visit(ctx.expression(1)),
                    MySQLBinaryLogicalOperator.OR);
        } else if (ctx.XOR() != null) {
            return new MySQLBinaryLogicalOperation(visit(ctx.expression(0)), visit(ctx.expression(1)),
                    MySQLBinaryLogicalOperator.XOR);
        }

        // 常量
        else if (ctx.literal() != null) {
            // This is a literal
            return visitLiteral(ctx.literal());
        } else {
            throw new UnsupportedOperationException("Unsupported expression: " + ctx.getText());
        }
    }

    @Override
    public MySQLExpression visitLiteral(MySQLExpressionParser.LiteralContext ctx) {
        if (ctx.INTEGER_LITERAL() != null) {
            return new MySQLIntConstant(Long.parseLong(ctx.INTEGER_LITERAL().getText()));
        } else if (ctx.SIGNED_INTEGER_LITERAL() != null) {
            return new MySQLIntConstant(Long.parseLong(ctx.SIGNED_INTEGER_LITERAL().getText()));
        } else if (ctx.COLUMN_NAME() != null) {
            MySQLColumn column = new MySQLColumn(null, ctx.COLUMN_NAME().getText(), MySQLDataType.INT, false, false,
                    false, 0);
            return new MySQLColumnReference(column, null);
        } else if (ctx.TRUE_LITERAL() != null) {
            return new MySQLIntConstant(1);
        } else if (ctx.FALSE_LITERAL() != null) {
            return new MySQLIntConstant(0);
        } else if (ctx.NULL_LITERAL() != null) {
            return new MySQLNullConstant();
        } else if (ctx.UNKNOWN_LITERAL() != null) {
            return new MySQLNullConstant();
        } else {
            throw new UnsupportedOperationException("Unsupported literal: " + ctx.getText());
        }
    }
}