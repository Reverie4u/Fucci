package fucci.reducer;

import java.sql.SQLException;

import fucci.FucciChecker;
import fucci.StatementCell;
import fucci.TableTool;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MTOracleChecker implements OracleChecker {
    @Override
    public boolean hasBug(TestCase tc) {
        // 给testCase的两个事务设置连接
        tc.tx1.setConn(TableTool.genConnection());
        tc.tx2.setConn(TableTool.genConnection());
        FucciChecker checker = new FucciChecker(tc.tx1, tc.tx2);
        // 对表进行处理
        TableTool.executeOnTable("DROP TABLE IF EXISTS " + TableTool.TableName);
        TableTool.executeOnTable(tc.createStmt.getStatement());
        for (StatementCell stmt : tc.prepareTableStmts) {
            TableTool.executeOnTable(stmt.getStatement());
        }
        TableTool.isReducer = true;
        TableTool.preProcessTable();
        boolean res = checker.oracleCheck(tc.submittedOrder);
        TableTool.isReducer = false;
        try {
            tc.tx1.getConn().close();
            tc.tx2.getConn().close();
        } catch (SQLException e) {
            log.info("Close connection failed.");
            e.printStackTrace();
        }
        return !res;
    }

    @Override
    public boolean hasBug(String tc) {
        TestCase testCase = Reducer.parse(tc);
        return hasBug(testCase);
    }
}
