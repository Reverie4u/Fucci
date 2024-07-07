package fucci;

import java.util.List;

import lombok.Data;

@Data
public class BugReport {
    private boolean bugFound = false;

    private String createTableSQL;
    private List<String> initializeStatements;
    private String initialTable;
    private Transaction tx1, tx2;
    private String inputSchedule;
    private String submittedOrder;
    private TxnPairResult execRes;
    private TxnPairResult inferredRes;

    public String toString() {
        StringBuilder sb = new StringBuilder("=============================");
        sb.append("BUG REPORT\n")
                .append(" -- Oracle: ").append(TableTool.oracle).append("\n")
                .append(createTableSQL).append("\n");
        for (String stmt : initializeStatements) {
            sb.append(stmt).append(";\n");
        }
        sb.append("\n");
        sb.append(tx1.getIsolationlevel().getAlias()).append("\n");
        if (tx1.statements != null) {
            for (StatementCell stmt : tx1.statements) {
                sb.append(stmt.statement).append(";\n");
            }
        }
        sb.append("\n");
        sb.append(tx2.getIsolationlevel().getAlias()).append("\n");
        if (tx2.statements != null) {
            for (StatementCell stmt : tx2.statements) {
                sb.append(stmt.statement).append(";\n");
            }
        }
        sb.append("\n");
        sb.append(inputSchedule).append("\n").append("END\n");
        // sb.append(" -- Submitted Order: ").append(submittedOrder).append("\n");
        // sb.append(" -- Execution Result: ").append(execRes).append("\n");
        // sb.append(" -- Inferred Result: ").append(inferredRes).append("\n");
        return sb.toString();
    }
}
