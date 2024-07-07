package fucci.mysql.visitor;

import fucci.Randomly;
import fucci.common.visitor.ToStringVisitor;
import fucci.mysql.ast.MySQLBetweenOperation;
import fucci.mysql.ast.MySQLBinaryComparisonOperation;
import fucci.mysql.ast.MySQLBinaryLogicalOperation;
import fucci.mysql.ast.MySQLBinaryOperation;
import fucci.mysql.ast.MySQLCastOperation;
import fucci.mysql.ast.MySQLColumnReference;
import fucci.mysql.ast.MySQLConstant;
import fucci.mysql.ast.MySQLExpression;
import fucci.mysql.ast.MySQLInOperation;
import fucci.mysql.ast.MySQLStringExpression;
import fucci.mysql.ast.MySQLUnaryPostfixOperation;
import fucci.mysql.ast.MySQLUnaryPrefixOperation;

public class MySQLToStringVisitor extends ToStringVisitor<MySQLExpression> implements MySQLVisitor {

    int ref;

    @Override
    public void visitSpecific(MySQLExpression expr) {
        MySQLVisitor.super.visit(expr);
    }

    @Override
    public void visit(MySQLConstant constant) {
        sb.append(constant.getTextRepresentation());
    }

    @Override
    public String get() {
        return sb.toString();
    }

    @Override
    public void visit(MySQLColumnReference column) {
        sb.append(column.getColumn().getColumnName());
    }

    @Override
    public void visit(MySQLUnaryPostfixOperation op) {
        sb.append("(");
        visit(op.getExpression());
        sb.append(")");
        sb.append(" IS ");
        if (op.isNegated()) {
            sb.append("NOT ");
        }
        switch (op.getOperator()) {
            case IS_FALSE:
                sb.append("FALSE");
                break;
            case IS_NULL:
                if (Randomly.getBoolean()) {
                    sb.append("UNKNOWN");
                } else {
                    sb.append("NULL");
                }
                break;
            case IS_TRUE:
                sb.append("TRUE");
                break;
            default:
                throw new AssertionError(op);
        }
    }

    @Override
    public void visit(MySQLBinaryLogicalOperation op) {
        sb.append("(");
        visit(op.getLeft());
        sb.append(")");
        sb.append(" ");
        sb.append(op.getTextRepresentation());
        sb.append(" ");
        sb.append("(");
        visit(op.getRight());
        sb.append(")");
    }

    @Override
    public void visit(MySQLBinaryComparisonOperation op) {
        sb.append("(");
        visit(op.getLeft());
        sb.append(") ");
        sb.append(op.getOp().getTextRepresentation());
        sb.append(" (");
        visit(op.getRight());
        sb.append(")");
    }

    @Override
    public void visit(MySQLCastOperation op) {
        sb.append("CAST(");
        visit(op.getExpr());
        sb.append(" AS ");
        sb.append(op.getType());
        sb.append(")");
    }

    @Override
    public void visit(MySQLInOperation op) {
        sb.append("(");
        visit(op.getExpr());
        sb.append(")");
        if (!op.isTrue()) {
            sb.append(" NOT");
        }
        sb.append(" IN ");
        sb.append("(");
        for (int i = 0; i < op.getListElements().size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            visit(op.getListElements().get(i));
        }
        sb.append(")");
    }

    @Override
    public void visit(MySQLBinaryOperation op) {
        sb.append("(");
        visit(op.getLeft());
        sb.append(") ");
        sb.append(op.getOp().getTextRepresentation());
        sb.append(" (");
        visit(op.getRight());
        sb.append(")");
    }

    @Override
    public void visit(MySQLStringExpression op) {
        sb.append(op.getStr());
    }

    @Override
    public void visit(MySQLBetweenOperation op) {
        sb.append("(");
        visit(op.getExpr());
        sb.append(") BETWEEN (");
        visit(op.getLeft());
        sb.append(") AND (");
        visit(op.getRight());
        sb.append(")");
    }

    @Override
    public void visit(MySQLUnaryPrefixOperation op) {
        sb.append("(");
        sb.append(op.getOperatorRepresentation());
        sb.append("(");
        visit(op.getExpression());
        sb.append("))");

    }

}
