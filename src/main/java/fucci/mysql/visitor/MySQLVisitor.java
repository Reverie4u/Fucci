package fucci.mysql.visitor;

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

public interface MySQLVisitor {

    void visit(MySQLConstant constant);

    void visit(MySQLColumnReference column);

    void visit(MySQLUnaryPostfixOperation column);

    void visit(MySQLBinaryLogicalOperation op);

    void visit(MySQLBinaryComparisonOperation op);

    void visit(MySQLCastOperation op);

    void visit(MySQLInOperation op);

    void visit(MySQLBinaryOperation op);

    void visit(MySQLStringExpression op);

    void visit(MySQLBetweenOperation op);

    void visit(MySQLUnaryPrefixOperation op);

    default void visit(MySQLExpression expr) {
        if (expr instanceof MySQLConstant) {
            visit((MySQLConstant) expr);
        } else if (expr instanceof MySQLColumnReference) {
            visit((MySQLColumnReference) expr);
        } else if (expr instanceof MySQLUnaryPostfixOperation) {
            visit((MySQLUnaryPostfixOperation) expr);
        } else if (expr instanceof MySQLBinaryLogicalOperation) {
            visit((MySQLBinaryLogicalOperation) expr);
        } else if (expr instanceof MySQLBinaryComparisonOperation) {
            visit((MySQLBinaryComparisonOperation) expr);
        } else if (expr instanceof MySQLCastOperation) {
            visit((MySQLCastOperation) expr);
        } else if (expr instanceof MySQLInOperation) {
            visit((MySQLInOperation) expr);
        } else if (expr instanceof MySQLBinaryOperation) {
            visit((MySQLBinaryOperation) expr);
        } else if (expr instanceof MySQLStringExpression) {
            visit((MySQLStringExpression) expr);
        } else if (expr instanceof MySQLBetweenOperation) {
            visit((MySQLBetweenOperation) expr);
        } else if (expr instanceof MySQLUnaryPrefixOperation) {
            visit((MySQLUnaryPrefixOperation) expr);
        } else {
            throw new AssertionError(expr);
        }
    }

    static String asString(MySQLExpression expr) {
        MySQLToStringVisitor visitor = new MySQLToStringVisitor();
        visitor.visit(expr);
        return visitor.get();
    }

    static String asExpectedValues(MySQLExpression expr) {
        return "";
    }

}
