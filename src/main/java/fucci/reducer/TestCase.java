package fucci.reducer;

import java.util.ArrayList;

import fucci.StatementCell;
import fucci.Transaction;

public class TestCase {
    public StatementCell createStmt;
    public ArrayList<StatementCell> prepareTableStmts;
    public Transaction tx1;
    public Transaction tx2;
    public ArrayList<StatementCell> submittedOrder;

    public TestCase() {
        prepareTableStmts = new ArrayList<>();
        submittedOrder = new ArrayList<>();
    }

    // 需要重写toString方法
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(createStmt.getStatement()).append("\n");
        for (StatementCell stmt : prepareTableStmts) {
            sb.append(stmt.getStatement()).append("\n");
        }
        sb.append("\n").append(tx1.getIsolationlevel().getAlias()).append("\n");
        for (StatementCell stmt : tx1.getStatements()) {
            sb.append(stmt.getStatement()).append("\n");
        }
        sb.append("\n").append(tx2.getIsolationlevel().getAlias()).append("\n");
        for (StatementCell stmt : tx2.getStatements()) {
            sb.append(stmt.getStatement()).append("\n");
        }
        sb.append("\n");
        // 将submittedOrder中的语句的事务id使用'-'拼接起来，类似1-2-1-1-2-2-1，最后一个数字后面没有-
        submittedOrder.forEach(stmt -> sb.append(stmt.getTx().getTxId()).append("-"));
        sb.deleteCharAt(sb.length() - 1).append("\n").append("END\n");
        return sb.toString();
    }
}
