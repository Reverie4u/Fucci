package fucci;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import fucci.common.IgnoreMeException;
import fucci.mysql.ast.MySQLConstant;
import fucci.mysql.ast.MySQLConstant.MySQLNullConstant;
import fucci.reducer.TestCase;

@Slf4j
public class FucciChecker {

    protected Transaction tx1;
    protected Transaction tx2;
    private String bugInfo;
    private HashMap<Integer, ArrayList<Version>> vData;
    private boolean isDeadlock;

    public FucciChecker(Transaction tx1, Transaction tx2) {
        this.tx1 = tx1;
        this.tx2 = tx2;
    }

    public void checkSchedule(String scheduleStr, TestCase testCase) {
        String[] schedule = scheduleStr.split("-");
        int len1 = tx1.statements.size();
        int len2 = tx2.statements.size();
        if (schedule.length != len1 + len2) {
            throw new RuntimeException("Invalid Schedule");
        }
        ArrayList<StatementCell> submittedOrder = new ArrayList<>();
        int idx1 = 0, idx2 = 0;
        for (String txId : schedule) {
            if (txId.equals("1")) {
                submittedOrder.add(tx1.statements.get(idx1++));
            } else if (txId.equals("2")) {
                submittedOrder.add(tx2.statements.get(idx2++));
            } else {
                throw new RuntimeException("Invalid Schedule");
            }
        }
        testCase.submittedOrder = submittedOrder;
        if (!oracleCheck(submittedOrder) && TableTool.reducerSwitchOn) {
            log.info("--------------------------Find a bug, start reducer--------------------------");
            String reducedCase = "";
            switch (TableTool.reducerType) {
                case "random":
                    reducedCase = TableTool.randomReducer.reduce(testCase.toString());
                    log.info("Random reducer result: \n{}", reducedCase);
                    break;
                case "probability-table":
                    reducedCase = TableTool.probabilityTableReducer.reduce(testCase.toString());
                    log.info("Probability-table reducer result: \n{}", reducedCase);
                    break;
                case "epsilon-greedy":
                    reducedCase = TableTool.epsilonGreedyReducer.reduce(testCase.toString());
                    log.info("Epsilon-greedy reducer result: \n{}", reducedCase);
                    break;
                case "all":
                    reducedCase = TableTool.randomReducer.reduce(testCase.toString());
                    log.info("Random reducer result: \n{}", reducedCase);
                    reducedCase = TableTool.probabilityTableReducer.reduce(testCase.toString());
                    log.info("Probability-table reducer result: \n{}", reducedCase);
                    reducedCase = TableTool.epsilonGreedyReducer.reduce(testCase.toString());
                    log.info("Epsilon-greedy reducer result: \n{}", reducedCase);
                default:
                    break;
            }
        }
        log.info(
                "randomAllReduceCount:{}, randomVaildReduceCount:{}, probabilityTableAllReduceCount:{}, probabilityTableVaildReduceCount:{},epsilonGreedyAllReduceCount:{}, epsilonGreedyVaildReduceCount:{}",
                TableTool.randomReducer.getAllReduceCount(), TableTool.randomReducer.getVaildReduceCount(),
                TableTool.probabilityTableReducer.getAllReduceCount(),
                TableTool.probabilityTableReducer.getVaildReduceCount(),
                TableTool.epsilonGreedyReducer.getAllReduceCount(),
                TableTool.epsilonGreedyReducer.getVaildReduceCount());
    }

    public void checkRandom(TestCase testCase) {
        checkRandom(TableTool.submittedOrderSampleCount, testCase);
    }

    public void checkRandom(int count, TestCase testCase) {
        // 随机抽样count种提交顺序
        ArrayList<ArrayList<StatementCell>> submittedOrderList = ShuffleTool.sampleSubmittedTrace(tx1, tx2, count);
        for (ArrayList<StatementCell> submittedOrder : submittedOrderList) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
            // check每一种提交顺序
            testCase.submittedOrder = submittedOrder;
            boolean res = oracleCheck(submittedOrder);
            if (!res && TableTool.reducerSwitchOn) {
                log.info("---------------------------Find a bug, start reducer ---------------------------");
                String reducedCase = "";
                String reducedCaseOfAllRandom = "";
                String reducedCaseOfAllProb = "";
                String reducedCaseOfAllEpsilon = "";
                switch (TableTool.reducerType) {
                    case "random":
                        reducedCase = TableTool.randomReducer.reduce(testCase.toString());
                        log.info("Random reducer result: \n{}", reducedCase);
                        break;
                    case "probability-table":
                        reducedCase = TableTool.probabilityTableReducer.reduce(testCase.toString());
                        log.info("Probability-table reducer result: \n{}", reducedCase);
                        break;
                    case "epsilon-greedy":
                        reducedCase = TableTool.epsilonGreedyReducer.reduce(testCase.toString());
                        log.info("Epsilon-greedy reducer result: \n{}", reducedCase);
                        break;
                    case "all":
                        reducedCaseOfAllRandom = TableTool.randomReducer.reduce(testCase.toString());
                        log.info("Random reducer result: \n{}", reducedCaseOfAllRandom);
                        reducedCaseOfAllProb = TableTool.probabilityTableReducer.reduce(testCase.toString());
                        log.info("Probability-table reducer result: \n{}", reducedCaseOfAllProb);
                        reducedCaseOfAllEpsilon = TableTool.epsilonGreedyReducer.reduce(testCase.toString());
                        log.info("Epsilon-greedy reducer result: \n{}", reducedCaseOfAllEpsilon);
                    default:
                        break;
                }
                if (TableTool.reducerType.equals("random") ||
                        TableTool.reducerType.equals("probability-table") ||
                        TableTool.reducerType.equals("epsilon-greedy")) {
                    // 输出原始bug case
                    saveTestCase(testCase.toString(),
                            TableTool.bugPath + File.separator + "bug_" + TableTool.bugFound + "_origin.txt");
                    // 输出简化后的bug case
                    saveTestCase(reducedCase,
                            TableTool.bugPath + File.separator + "bug_" + TableTool.bugFound + "_"
                                    + TableTool.reducerType + "_reduced.txt");
                } else if (TableTool.reducerType.equals("all")) {
                    // 输出原始bug case
                    saveTestCase(testCase.toString(),
                            TableTool.bugPath + File.separator + "bug_" + TableTool.bugFound + "_origin.txt");
                    // 分别输出简化后的bug case
                    saveTestCase(reducedCaseOfAllRandom,
                            TableTool.bugPath + File.separator + "bug_" + TableTool.bugFound + "_random_reduced.txt");
                    saveTestCase(reducedCaseOfAllProb,
                            TableTool.bugPath + File.separator + "bug_" + TableTool.bugFound
                                    + "_probability-table_reduced.txt");
                    saveTestCase(reducedCaseOfAllEpsilon,
                            TableTool.bugPath + File.separator + "bug_" + TableTool.bugFound
                                    + "_epsilon-greedy_reduced.txt");
                }
                TableTool.bugFound++;
            }
            if (TableTool.isFilterDuplicateBug && !res) {
                // 同一个测试用例检测到的BUG都是一样的，一旦检测到直接停止本轮检测
                break;
            }
            log.info(
                    "txPair:{}, conflictTxPair:{}, allCase:{}, conflictCase:{}, sematicCorrectCase:{}, DTTime:{}, CSTime:{}, MTTime:{}, DTbugCase:{}, CSbugCase:{}, MTbugCase:{}",
                    TableTool.txPair, TableTool.conflictTxPair, TableTool.allCase, TableTool.conflictCase,
                    TableTool.sematicCorrectCase, TableTool.DTTime, TableTool.CSTime, TableTool.MTTime,
                    TableTool.DTbugCase, TableTool.CSbugCase, TableTool.MTbugCase);
            log.info(
                    "randomAllReduceCount:{}, randomVaildReduceCount:{}, probabilityTableAllReduceCount:{}, probabilityTableVaildReduceCount:{},epsilonGreedyAllReduceCount:{}, epsilonGreedyVaildReduceCount:{}",
                    TableTool.randomReducer.getAllReduceCount(), TableTool.randomReducer.getVaildReduceCount(),
                    TableTool.probabilityTableReducer.getAllReduceCount(),
                    TableTool.probabilityTableReducer.getVaildReduceCount(),
                    TableTool.epsilonGreedyReducer.getAllReduceCount(),
                    TableTool.epsilonGreedyReducer.getVaildReduceCount());
        }
    }

    public static void saveTestCase(String testCase, String filename) {
        try {
            FileWriter writer = new FileWriter(filename);
            writer.write(testCase);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void checkAll() {
        ArrayList<ArrayList<StatementCell>> submittedOrderList = ShuffleTool.genAllSubmittedTrace(tx1, tx2);
        for (ArrayList<StatementCell> submittedOrder : submittedOrderList) {
            oracleCheck(submittedOrder);
        }
    }

    private boolean oracleCheckByDT(ArrayList<StatementCell> schedule) {
        String tmp = TableTool.oracle;
        TableTool.oracle = "DT";
        log.info("oracle check by DT");
        // 获取oracleCheck花费时间，秒
        long start = System.currentTimeMillis();
        boolean res = oracleCheckInternalDT(schedule);
        long end = System.currentTimeMillis();
        TableTool.DTTime += (end - start);
        // 还原oracle
        TableTool.oracle = tmp;
        if (!res) {
            TableTool.DTbugCase++;
        }
        return res;
    }

    private boolean oracleCheckByCS(ArrayList<StatementCell> schedule) {
        String tmp = TableTool.oracle;
        TableTool.oracle = "CS";
        log.info("oracle check by CS");
        // 获取oracleCheck花费时间，秒
        long start = System.currentTimeMillis();
        boolean res = oracleCheckInternal(schedule);
        long end = System.currentTimeMillis();
        TableTool.CSTime += (end - start);
        // 还原oracle
        TableTool.oracle = tmp;
        if (!res) {
            TableTool.CSbugCase++;
        }
        return res;
    }

    private boolean oracleCheckByMT(ArrayList<StatementCell> schedule) {
        String tmp = TableTool.oracle;
        TableTool.oracle = "MT";
        log.info("oracle check by MT");
        long start = System.currentTimeMillis();
        boolean res = oracleCheckInternal(schedule);
        long end = System.currentTimeMillis();
        TableTool.MTTime += (end - start);
        // 还原oracle
        TableTool.oracle = tmp;
        if (!res) {
            TableTool.MTbugCase++;
        }
        return res;
    }

    private boolean oracleCheckInternalDT(ArrayList<StatementCell> schedule) {
        // 修改语句使用的连接
        TableTool.allCase++;
        log.info("Check new schedule:{}", schedule);
        // 将origin表复制到Fucci表
        if (!TableTool.isReducer) {
            TableTool.recoverOriginalTable();
        } else {
            TableTool.recoverTableFromSnapshot("reducer");
        }
        bugInfo = "";
        // 1.正常执行的结果
        TxnPairExecutor executor1 = null;
        TxnPairResult execResult1 = null;

        executor1 = new TxnPairExecutor(scheduleClone(schedule), tx1, tx2, false);
        execResult1 = executor1.getResult();
        // 遍历actualschedule，判断本次执行是否出现阻塞或死锁
        boolean hasConflict = false;
        for (StatementCell stmt : execResult1.getOrder()) {
            if (stmt.blocked) {
                hasConflict = true;
                break;
            }
        }
        if (execResult1.isDeadBlock()) {
            hasConflict = true;
        }
        if (hasConflict) {
            TableTool.txPairHasConflict = true;
            TableTool.conflictCase++;
        }
        if (!execResult1.isSematicError()) {
            // 可以用来计算事务语义正确率。
            TableTool.sematicCorrectCase++;
        }

        // 2.通过参照数据库获取的结果
        TxnPairExecutor executor2 = new TxnPairExecutor(scheduleClone(schedule), tx1, tx2, true);
        TxnPairResult execResult2 = executor2.getResult();

        bugInfo = " -- DT Error \n";
        if (TableTool.options.isSetCase()) {
            if (!TableTool.isReducer) {
                log.info("Schedule: " + schedule);
                log.info("Input schedule: " + getScheduleInputStr(schedule));
                log.info("Get execute result: " + execResult1);
                log.info("DT oracle order: " + execResult2.getOrder());
                log.info("DT oracle result: " + execResult2);
            }
            return compareOracles(execResult1, execResult2);
        }
        if (compareOracles(execResult1, execResult2)) {
            // false代表有bug
            log.info("Schedule: " + schedule);
            log.info("Input schedule: " + getScheduleInputStr(schedule));
            log.info("Get execute result: " + execResult1);
            log.info("DT oracle order: " + execResult2.getOrder());
            log.info("DT oracle result: " + execResult2);
            return true;
        }
        TableTool.bugReport.setInputSchedule(getScheduleInputStr(schedule));
        TableTool.bugReport.setSubmittedOrder(schedule.toString());
        TableTool.bugReport.setExecRes(execResult1);
        TableTool.bugReport.setInferredRes(execResult2);
        log.info(TableTool.bugReport.toString());
        return false;
    }

    private boolean oracleCheckInternal(ArrayList<StatementCell> schedule) {
        TableTool.allCase++;
        log.info("Check new schedule:{}", schedule);
        // 将origin表复制到Fucci表
        if (!TableTool.isReducer) {
            TableTool.recoverOriginalTable();
        } else {
            TableTool.recoverTableFromSnapshot("reducer");
        }
        bugInfo = "";
        // 1.正常执行的结果
        TxnPairExecutor executor = new TxnPairExecutor(scheduleClone(schedule), tx1, tx2, false);
        TxnPairResult execResult = executor.getResult();
        // 遍历actualschedule，判断本次执行是否出现阻塞或死锁
        boolean hasConflict = false;
        for (StatementCell stmt : execResult.getOrder()) {
            if (stmt.blocked) {
                hasConflict = true;
                break;
            }
        }
        if (execResult.isDeadBlock()) {
            hasConflict = true;
        }
        if (hasConflict) {
            TableTool.txPairHasConflict = true;
            TableTool.conflictCase++;
        }
        if (!execResult.isSematicError()) {
            // 可以用来计算事务语义正确率。
            TableTool.sematicCorrectCase++;
        }

        // ArrayList<StatementCell> mvccSchedule =
        // inferOracleOrderMVCC(scheduleClone(schedule));
        // TxnPairResult mvccResult = obtainOracleResults(mvccSchedule);
        // 2.通过外部MVCC获取的结果
        TxnPairResult mvccResult;
        try {
            mvccResult = inferOracleMVCC(scheduleClone(schedule));
        } catch (IgnoreMeException e) {
            log.info("Ignore: {}", e.getMessage());
            bugInfo += " -- Ignore: " + e.getMessage();
            TableTool.skipCase++;
            return true;
        }

        bugInfo = " -- MVCC Error \n";
        if (TableTool.options.isSetCase()) {
            if (!TableTool.isReducer) {
                log.info("Schedule: " + schedule);
                log.info("Input schedule: " + getScheduleInputStr(schedule));
                log.info("Get execute result: " + execResult);
                log.info("MVCC-based oracle order: " + mvccResult.getOrder());
                log.info("MVCC-based oracle result: " + mvccResult);
            }
            return compareOracles(execResult, mvccResult);
        }
        if (compareOracles(execResult, mvccResult)) {
            // false代表有bug
            log.info("Schedule: " + schedule);
            log.info("Input schedule: " + getScheduleInputStr(schedule));
            log.info("Get execute result: " + execResult);
            log.info("MVCC-based oracle order: " + mvccResult.getOrder());
            log.info("MVCC-based oracle result: " + mvccResult);
            return true;
        }
        TableTool.bugReport.setInputSchedule(getScheduleInputStr(schedule));
        TableTool.bugReport.setSubmittedOrder(schedule.toString());
        TableTool.bugReport.setExecRes(execResult);
        TableTool.bugReport.setInferredRes(mvccResult);
        log.info(TableTool.bugReport.toString());
        return false;
    }

    public boolean oracleCheck(ArrayList<StatementCell> schedule) {
        if (TableTool.options.getOracle().equals("DT")) {
            return oracleCheckByDT(schedule);
        } else if (TableTool.options.getOracle().equals("CS")) {
            return oracleCheckByCS(schedule);
        } else if (TableTool.options.getOracle().equals("MT")) {
            return oracleCheckByMT(schedule);
        } else if (TableTool.options.getOracle().equals("ALL")) {
            // 各输出一次
            boolean res1 = oracleCheckByDT(schedule);
            boolean res2 = oracleCheckByCS(schedule);
            boolean res3 = oracleCheckByMT(schedule);
            return res1 && res2 && res3;
        } else {
            throw new RuntimeException("Unexpected oracle type: " + TableTool.options.getOracle());
        }
    }

    private String getScheduleInputStr(ArrayList<StatementCell> schedule) {
        ArrayList<String> order = new ArrayList<>();
        for (StatementCell stmt : schedule) {
            order.add(Integer.toString(stmt.tx.txId));
        }
        return String.join("-", order);
    }

    // schedule的一个备份，方便修改而不影响原始list
    private ArrayList<StatementCell> scheduleClone(ArrayList<StatementCell> schedule) {
        ArrayList<StatementCell> copied = new ArrayList<>();
        for (StatementCell stmt : schedule) {
            copied.add(stmt.copy());
        }
        return copied;
    }

    private TxnPairResult inferOracleMVCC(ArrayList<StatementCell> schedule) {
        // 将origin表复制到Fucci表
        // 这里设置一个标记，如果是reducer调用，就不用恢复了
        if (!TableTool.isReducer) {
            TableTool.recoverOriginalTable();
        } else {
            TableTool.recoverTableFromSnapshot("reducer");
        }
        isDeadlock = false;
        ArrayList<StatementCell> oracleOrder = new ArrayList<>();
        TableTool.firstTxnInSerOrder = null;
        tx1.clearStates();
        tx2.clearStates();
        vData = TableTool.initVersionData();
        // log.info("init mvcc: {}", vData);
        // 初始状态每一行只有一个版本
        for (StatementCell stmt : schedule) {

            Transaction curTx = stmt.tx;
            Transaction otherTx = curTx == tx1 ? tx2 : tx1;
            if (curTx.blocked) {
                curTx.blockedStatements.add(stmt);
                continue;
            }
            boolean blocked = analyzeStmt(stmt, curTx, otherTx);
            if (blocked) {
                StatementCell blockPoint = stmt.copy();
                blockPoint.blocked = true;
                oracleOrder.add(blockPoint);
                curTx.blockedStatements.add(stmt);
            } else {
                oracleOrder.add(stmt);
                if (stmt.type == StatementType.COMMIT || stmt.type == StatementType.ROLLBACK) {
                    // 当前事务提交的时候, 把另一个事务的阻塞语句重新分析一遍。
                    otherTx.blocked = false;
                    for (StatementCell blockedStmt : otherTx.blockedStatements) {
                        analyzeStmt(blockedStmt, otherTx, curTx);
                        oracleOrder.add(blockedStmt);
                        // log.info("after blockedStmt: {}, mvcc: {}", blockedStmt, vData);
                    }
                }
            }
            // log.info("after stmt: {}, mvcc: {}", stmt, vData);
            if (curTx.blocked && otherTx.blocked) {
                isDeadlock = true;
                tx1.clearStates();
                tx2.clearStates();
                break;
            }
        }
        TableTool.viewToTable(newestView());
        // 这里也需要改成约束求解
        ArrayList<Object> finalState = null;
        if ("MT".equals(TableTool.oracle)) {
            finalState = TableTool.getFinalStateAsList();
        } else {
            View view = newestView();
            finalState = new ArrayList<>();
            for (int rowId : view.data.keySet()) {
                Object[] row = view.data.get(rowId);
                HashMap<String, Object> tupleMap = new HashMap<>();
                for (int i = 0; i < TableTool.colNames.size(); i++) {
                    tupleMap.put(TableTool.colNames.get(i), row[i]);
                }
                for (String columnName : TableTool.getColNames()) {
                    finalState.add(tupleMap.get(columnName));
                }
            }
        }

        TxnPairResult result = new TxnPairResult();
        result.setOrder(oracleOrder);
        result.setFinalState(finalState);
        result.setDeadBlock(isDeadlock);
        tx1.clearStates();
        tx2.clearStates();
        return result;
    }

    private boolean analyzeStmt(StatementCell stmt, Transaction curTx, Transaction otherTx) {
        if (curTx.aborted) {
            if (stmt.type != StatementType.COMMIT && stmt.type != StatementType.ROLLBACK) {
                stmt.aborted = true;
            }
            return false;
        }
        // stmt.view仅用于锁分析，对于需要加锁的语句来说都是当前读，例外是RC, RR下的update语句是半一致性读
        // if (curTx.isolationlevel == IsolationLevel.READ_UNCOMMITTED
        // || curTx.isolationlevel == IsolationLevel.READ_COMMITTED) {
        // // 半一致性读，读取到的是最新的提交数据
        // stmt.view = buildTxView(curTx, otherTx, false);
        // } else {
        // stmt.view = newestView();
        // }
        // log.info("vData: {}", vData.toString());
        stmt.view = newestView();
        // log.info("stmt {}, view: {}", stmt, stmt.view);
        // 锁分析
        Lock lock = TableTool.getLock(stmt);
        // log.info("lock: {}", lock.type);
        if (lock.isConflict(otherTx) && !otherTx.aborted && !otherTx.committed) {
            curTx.blocked = true;
            TableTool.firstTxnInSerOrder = otherTx;
            return true;
        }
        if (curTx.aborted) {
            stmt.aborted = true;
            curTx.locks.clear();
            deleteVersion(curTx);
            return false;
        }
        if (lock.type != LockType.NONE) {
            curTx.locks.add(lock);
        }
        if (curTx.snapTxs.isEmpty() && isSnapshotPoint(stmt)) {
            curTx.snapTxs.addAll(Arrays.asList(TableTool.txInit, curTx));
            if (otherTx.committed) {
                curTx.snapTxs.add(otherTx);
            }
            curTx.snapView = snapshotView(curTx);
        }

        // 在多版本链上执行语句
        View view;
        if (stmt.type == StatementType.BEGIN || stmt.type == StatementType.COMMIT
                || stmt.type == StatementType.ROLLBACK) {
            // curTx.locks.clear();
            if (stmt.type == StatementType.BEGIN) {
                curTx.locks.clear();
            }
            if (stmt.type == StatementType.COMMIT) {
                curTx.committed = true;
            }
            if (stmt.type == StatementType.ROLLBACK) {
                // 回滚的时候把该事务在多版本链中的数据全部删掉
                curTx.aborted = true;
                curTx.finished = true;
                curTx.locks.clear();
                deleteVersion(curTx);
            }
        } else if (stmt.type == StatementType.SELECT) {
            if (curTx.isolationlevel == IsolationLevel.REPEATABLE_READ) {
                // 读快照
                view = snapshotView(curTx);
            } else if (curTx.isolationlevel == IsolationLevel.READ_UNCOMMITTED) {
                // 读最新数据
                view = newestView();
            } else {
                // 读已提交数据
                view = buildTxView(curTx, otherTx, false);
            }
            stmt.result = queryOnView(stmt, view);
        } else if (stmt.type == StatementType.SELECT_SHARE || stmt.type == StatementType.SELECT_UPDATE) {
            // 任何隔离级别下都应该读最新数据
            view = newestView();
            stmt.result = queryOnView(stmt, view);
        } else if (stmt.type == StatementType.UPDATE || stmt.type == StatementType.INSERT
                || stmt.type == StatementType.DELETE) {
            // 更新多版本链
            updateVersion(stmt, curTx, otherTx);
            if (stmt.type == StatementType.INSERT && stmt.newRowId > 0) {
                lock.lockObject.rowIds.add(stmt.newRowId);
            }
        } else {
            log.info("Weird Statement: {}", stmt);
        }
        return false;
    }

    boolean isSnapshotPoint(StatementCell stmt) {
        switch (TableTool.dbms) {
            case MYSQL:
            case MARIADB:
                return stmt.type == StatementType.SELECT;
            case TIDB:
                return stmt.type == StatementType.BEGIN;
            default:
                throw new RuntimeException("Unexpected switch case: " + TableTool.dbms.name());
        }
    }

    void deleteVersion(Transaction curTx) {
        for (int rowId : vData.keySet()) {
            vData.get(rowId).removeIf(version -> version.tx == curTx);
        }
    }

    View newestView() {
        View view = new View();
        for (int rowid : vData.keySet()) {
            ArrayList<Version> versions = vData.get(rowid);
            if (versions == null || versions.isEmpty()) {
                continue;
            }
            Version latest = versions.get(versions.size() - 1);
            if (!latest.deleted) {
                view.data.put(rowid, latest.data);
            }
        }
        return view;
    }

    View newestView(Transaction curTx, boolean useDel) {
        View view = new View(useDel);
        for (int rowid : vData.keySet()) {
            ArrayList<Version> versions = vData.get(rowid);
            if (versions == null || versions.isEmpty()) {
                continue;
            }
            Version latest = versions.get(versions.size() - 1);
            if (!latest.deleted) {
                view.data.put(rowid, latest.data);
                if (useDel) {
                    view.deleted.put(rowid, false);
                }
            } else if (curTx.snapView.data.containsKey(rowid) && latest.tx != curTx && useDel) {
                // 如果被其他事务删除了，就读取当前事务快照过的版本
                view.data.put(rowid, curTx.snapView.data.get(rowid));
                view.deleted.put(rowid, true);
            }
        }
        return view;
    }

    View snapshotView(Transaction curTx) {
        View view = new View();
        for (int rowId : vData.keySet()) {
            ArrayList<Version> versions = vData.get(rowId);
            for (int i = versions.size() - 1; i >= 0; i--) {
                Version version = versions.get(i);
                if (curTx.snapTxs.contains(version.tx)) {
                    if (!version.deleted) {
                        view.data.put(rowId, version.data);
                    }
                    break;
                }
            }
        }
        return view;
    }

    View buildTxView(Transaction curTx, Transaction otherTx, boolean useDel) {
        // 可读事务包括事务0及当前事务
        ArrayList<Transaction> readTxs = new ArrayList<>(Arrays.asList(TableTool.txInit, curTx));
        if (otherTx.committed) {
            readTxs.add(otherTx);
        }
        View view = new View(useDel);
        for (int rowId : vData.keySet()) {
            // 每一行的多个版本
            ArrayList<Version> versions = vData.get(rowId);
            for (int i = versions.size() - 1; i >= 0; i--) {
                Version version = versions.get(i);
                if (readTxs.contains(version.tx)) {
                    if (!version.deleted) {
                        view.data.put(rowId, version.data);
                        if (useDel) {
                            view.deleted.put(rowId, false);
                        }
                    } else if (curTx.snapView.data.containsKey(rowId) && version.tx != curTx && useDel) {
                        // 如果被其他事务删除了，就读取当前事务快照过的版本
                        view.data.put(rowId, curTx.snapView.data.get(rowId));
                        view.deleted.put(rowId, true);
                    }
                    break;
                }
            }
        }
        return view;
    }

    void updateVersionByExecOnTable(StatementCell stmt, Transaction curTx, Transaction otherTx) {
        // curview表示已提交的数据
        // 当成功执行到这里时，一定不存在冲突/冲突事务已提交，此处buildTxView和newView效果一样了。
        View curView = newestView();
        View allView = curView;
        if (curTx.isolationlevel == IsolationLevel.REPEATABLE_READ) {
            // 可重复读下，事务1删除过的数据事务2可以再次删除/修改
            allView = newestView(curTx, true);
        } else if (curTx.isolationlevel == IsolationLevel.READ_COMMITTED
                || curTx.isolationlevel == IsolationLevel.READ_UNCOMMITTED) {
            // UPDATE的时候采用已提交的最新数据进行评估
            allView = buildTxView(curTx, otherTx, false);
        }
        // 获取影响行数的时候需要包含已删除的行
        HashSet<Integer> rowIds = getAffectedRows(stmt, allView);
        String snapshotName = "update_version";
        TableTool.takeSnapshotForTable(snapshotName);
        TableTool.viewToTable(curView);
        boolean success = TableTool.executeOnTable(stmt.statement);
        int newRowId = TableTool.fillOneRowId();
        View newView = TableTool.tableToView(); // 这是修改之后的数据
        if (success) {
            if (stmt.type == StatementType.INSERT) {
                assert newRowId > 0;
                rowIds.add(newRowId);
                stmt.newRowId = newRowId;
            }
            for (int rowId : rowIds) {
                boolean deleted = allView.deleted != null && allView.deleted.containsKey(rowId)
                        && allView.deleted.get(rowId) || stmt.type == StatementType.DELETE;
                Object[] data;
                if (deleted) {
                    data = allView.data.get(rowId);
                } else {
                    data = newView.data.get(rowId);
                }
                if (data == null) {
                    continue;
                }
                if (!vData.containsKey(rowId)) {
                    vData.put(rowId, new ArrayList<>());
                }
                // 可能出现空指针异常
                vData.get(rowId).add(new Version(data.clone(), curTx, deleted));
            }
        }
        TableTool.recoverTableFromSnapshot(snapshotName);
    }

    void updateVersion(StatementCell stmt, Transaction curTx, Transaction otherTx) {
        if (TableTool.oracle.equals("MT")) {
            // log.info("Update version use MT oracle"); // 使用变形测试
            updateVersionByExecOnTable(stmt, curTx, otherTx);
        } else {
            // log.info("Update version use CS oracle"); // 使用约束求解预言机
            updateVersionByCostraintSolver(stmt, curTx, otherTx);
        }
    }

    void updateVersionByCostraintSolver(StatementCell stmt, Transaction curTx, Transaction otherTx) {
        // curview表示已提交的数据
        // 当成功执行到这里时，一定不存在冲突/冲突事务已提交，此处buildTxView和newView效果一样了。
        View curView = newestView();
        View allView = curView;
        if (curTx.isolationlevel == IsolationLevel.REPEATABLE_READ) {
            // 可重复读下，事务1删除过的数据事务2可以再次删除/修改
            allView = newestView(curTx, true);
        }
        // 根据类型来判断是插入/删除/修改
        if (stmt.type == StatementType.INSERT) {
            // 如果是插入语句，则需要获取新插入的行号，在多版本链中新增记录
            int newRowId = TableTool.getNewRowId();
            stmt.newRowId = newRowId;
            if (!vData.containsKey(newRowId)) {
                vData.put(newRowId, new ArrayList<>());
            }
            // 根据insertMap构造行数据
            Object[] newRow = new Object[TableTool.colNames.size()];
            for (int i = 0; i < TableTool.colNames.size(); i++) {
                String value = stmt.values.get(TableTool.colNames.get(i));
                // 将String类型的value转化成对应类型。
                newRow[i] = TableTool.convertStringToType(value, TableTool.colTypeNames.get(i));
            }
            vData.get(newRowId).add(new Version(newRow, curTx, false));
        } else if (stmt.type == StatementType.UPDATE || stmt.type == StatementType.DELETE) {
            // 根据allView获取受影响的行
            Set<Integer> rowIds = new HashSet<>();
            for (int rowId : allView.data.keySet()) {
                Object[] row = allView.data.get(rowId);
                HashMap<String, Object> tupleMap = new HashMap<>();
                for (int i = 0; i < TableTool.colNames.size(); i++) {
                    tupleMap.put(TableTool.colNames.get(i), row[i]);
                }
                MySQLConstant constant;
                if (stmt.predicate == null) {
                    constant = new MySQLNullConstant();
                } else {
                    constant = stmt.predicate.getExpectedValue(tupleMap);
                    // log.info("expression: {}", stmt.whereClause);
                    // log.info("tuple: {}", tupleMap);
                    // log.info("constant: {}", constant);
                }
                if (!constant.isNull() && constant.asBooleanNotNull()) {
                    // 加入结果集
                    rowIds.add(rowId);
                }
            }
            for (int rowId : rowIds) {
                boolean deleted = allView.deleted != null && allView.deleted.containsKey(rowId)
                        && allView.deleted.get(rowId) || stmt.type == StatementType.DELETE;
                Object[] newData = null;
                if (deleted) {
                    newData = allView.data.get(rowId);
                } else {
                    newData = allView.data.get(rowId).clone();
                    for (int i = 0; i < TableTool.colNames.size(); i++) {
                        String colName = TableTool.colNames.get(i);
                        if (stmt.values.containsKey(colName)) {
                            String value = stmt.values.get(colName);
                            newData[i] = TableTool.convertStringToType(value, TableTool.colTypeNames.get(i));
                        }
                    }
                }
                if (newData == null) {
                    continue;
                }
                if (!vData.containsKey(rowId)) {
                    vData.put(rowId, new ArrayList<>());
                }
                vData.get(rowId).add(new Version(newData.clone(), curTx, deleted));
            }
        }
    }

    HashSet<Integer> getAffectedRows(StatementCell stmt, View view) {
        // 这个函数需要改成约束求解的形式
        HashSet<Integer> res = new HashSet<>();
        String snapshotName = "affected_rows";
        TableTool.takeSnapshotForTable(snapshotName);
        TableTool.viewToTable(view);
        if (stmt.type == StatementType.DELETE || stmt.type == StatementType.UPDATE) {
            res.addAll(TableTool.getRowIdsFromWhere(stmt.whereClause));
        }
        if (stmt.type == StatementType.INSERT || stmt.type == StatementType.UPDATE) {
            HashSet<String> indexObjs = TableTool.getIndexObjs(stmt.values);
            String query = "SELECT * FROM " + TableTool.TableName;
            TableTool.executeQueryWithCallback(query, rs -> {
                try {
                    while (rs.next()) {
                        HashMap<String, String> rowValues = new HashMap<>();
                        for (String colName : TableTool.colNames) {
                            Object obj = rs.getObject(colName);
                            if (obj != null) {
                                if (obj instanceof byte[]) {
                                    rowValues.put(colName, TableTool.byteArrToHexStr((byte[]) obj));
                                } else {
                                    rowValues.put(colName, obj.toString());
                                }
                            }
                        }
                        HashSet<String> rowIndexObjs = TableTool.getIndexObjs(rowValues);
                        for (String indexObj : rowIndexObjs) {
                            if (indexObjs.contains(indexObj)) {
                                res.add(rs.getInt(TableTool.RowIdColName));
                                break;
                            }
                        }
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Get lock object failed: ", e);
                }
            });
        }
        TableTool.recoverTableFromSnapshot(snapshotName);
        return res;
    }

    ArrayList<Object> queryOnView(StatementCell stmt, View view) {
        if (TableTool.oracle.equals("MT")) {
            // log.info("Query on view use MT oracle"); // 使用变形测试
            return queryOnViewByExecOnTable(stmt, view);
        } else {
            // log.info("Query on view use CS oracle"); // 使用约束求解预言机
            ArrayList<Object> res = queryOnViewByCostraintSolver(stmt, view);
            return res;
        }
    }

    ArrayList<Object> queryOnViewByExecOnTable(StatementCell stmt, View view) {
        TableTool.backupCurTable();
        TableTool.viewToTable(view);
        ArrayList<Object> res = TableTool.getQueryResultAsList(stmt.statement);
        TableTool.recoverCurTable();
        return res;
    }

    ArrayList<Object> queryOnViewByCostraintSolver(StatementCell stmt, View view) {
        ArrayList<Object> res = new ArrayList<>();
        for (int rowId : view.data.keySet()) {
            Object[] row = view.data.get(rowId);
            HashMap<String, Object> tupleMap = new HashMap<>();
            for (int i = 0; i < TableTool.colNames.size(); i++) {
                tupleMap.put(TableTool.colNames.get(i), row[i]);
            }
            MySQLConstant constant;
            if (stmt.predicate == null) {
                constant = new MySQLNullConstant();
            } else {
                constant = stmt.predicate.getExpectedValue(tupleMap);
                // log.info("expression: {}", stmt.whereClause);
                // log.info("tuple: {}", tupleMap);
                // log.info("constant: {}", constant);
            }
            if (!constant.isNull() && constant.asBooleanNotNull()) {
                // 加入结果集
                for (String columnName : stmt.selectedColumns) {
                    res.add(tupleMap.get(columnName));
                }
            }
        }
        return res;
    }

    private boolean compareOracles(TxnPairResult execRes, TxnPairResult oracleRes) {
        log.info("txp: {}, all case: {}, skip: {}", TableTool.txPair, TableTool.allCase, TableTool.skipCase);
        ArrayList<StatementCell> execOrder = execRes.getOrder();
        ArrayList<StatementCell> oracleOrder = oracleRes.getOrder();
        int minLen = Math.min(execOrder.size(), oracleOrder.size());
        if (execRes.isSyntaxError()) {
            log.info("Ignore: Syntax Error");
            bugInfo += " -- Ignore: Syntax Error";
            TableTool.skipCase++;
            return true;
        }
        if (execRes.isSematicError()) {
            log.info("Ignore: Sematic Error");
            bugInfo += " -- Ignore: Sematic Error";
            TableTool.skipCase++;
            return true;
        }
        if (execRes.isDeadBlock() && !oracleRes.isDeadBlock()) {
            log.info("Ignore: Undecided");
            bugInfo += " -- Ignore: Undecided";
            TableTool.skipCase++;
            return true;
        }
        if (execRes.isDeadBlock() && oracleRes.isDeadBlock()) {
            // 两个都阻塞的情况，只比较第一个阻塞点前的语句
            int blockIndex = 0;
            for (int i = 0; i < oracleOrder.size(); i++) {
                if (oracleOrder.get(i).blocked) {
                    blockIndex = i;
                }
            }
            minLen = Math.min(minLen, blockIndex);
        }
        for (int i = 0; i < minLen; i++) {
            StatementCell oStmt = oracleOrder.get(i);
            StatementCell eStmt = execOrder.get(i);
            if (oStmt.aborted && eStmt.aborted)
                continue;
            if (oStmt.aborted) {
                log.info("Error: Missing abort");
                bugInfo += " -- Error: Missing abort";
                return false;
            }
            if (eStmt.aborted) {
                if (shouldNotAbort(eStmt)) {
                    log.info("Error: Unnecessary abort");
                    bugInfo += " -- Error: Unnecessary abort";
                    return false;
                } else {
                    log.info("Ignore: Undecided (because abort)");
                    bugInfo += " -- Ignore: Undecided (because abort)";
                    return true;
                }
            }
            if (!oStmt.blocked && !eStmt.blocked) {
                if ((oStmt.type == StatementType.SELECT || oStmt.type == StatementType.SELECT_SHARE
                        || oStmt.type == StatementType.SELECT_UPDATE)
                        && oStmt.equals(eStmt)) {
                    if (!compareResultSets(oStmt.result, eStmt.result)) {
                        log.info("Error: Inconsistent query result");
                        log.info("query: " + oStmt.statement);
                        bugInfo += " -- Error: Inconsistent query result \n";
                        bugInfo += " -- query: " + oStmt.statement;
                        return false;
                    }
                }
            }
            if (oStmt.blocked && !eStmt.blocked) {
                // log.info("Error: Missing lock");
                // bugInfo += " -- Error: Missing lock";
                // TableTool.skipCase++;
                // return false;
                // 锁不一样统一忽略
                log.info("Ignore: Undecided (because lock)");
                bugInfo += " -- Ignore: Undecided (because lock)";
                return true;
            }
            if (!oStmt.blocked && eStmt.blocked) {
                if (shouldNotBlock(eStmt)) {
                    log.info("Error: Unnecessary lock");
                    bugInfo += " -- Error: Unnecessary lock";
                    return false;
                } else {
                    log.info("Ignore: Undecided (because lock)");
                    bugInfo += " -- Ignore: Undecided (because lock)";
                    return true;
                }
            }
        }
        if (!execRes.isDeadBlock() && !oracleRes.isDeadBlock()) {
            if (!compareResultSets(execRes.getFinalState(), oracleRes.getFinalState())) {
                log.info("Error: Inconsistent final database state");
                bugInfo += " -- Error: Inconsistent final database state";
                return false;
            }
        }
        return true;
    }

    private boolean shouldNotBlock(StatementCell stmt) {
        return false;
    }

    private boolean shouldNotAbort(StatementCell stmt) {
        return false;
    }

    private boolean compareResultSets(ArrayList<Object> resultSet1, ArrayList<Object> resultSet2) {
        if (resultSet1 == null && resultSet2 == null) {
            return true;
        } else if (resultSet1 == null || resultSet2 == null) {
            bugInfo += " -- One result is NULL\n";
            return false;
        }
        if (resultSet1.size() != resultSet2.size()) {
            bugInfo += " -- Number Of Data Different\n";
            return false;
        }
        List<String> rs1 = preprocessResultSet(resultSet1);
        List<String> rs2 = preprocessResultSet(resultSet2);
        for (int i = 0; i < rs1.size(); i++) {
            String result1 = rs1.get(i);
            String result2 = rs2.get(i);
            if (result1 == null && result2 == null) {
                continue;
            }
            if (result1 == null || result2 == null) {
                bugInfo += " -- (" + i + ") Values Different [" + result1 + ", " + result2 + "]\n";
                return false;
            }
            if (!result1.equals(result2)) {
                bugInfo += " -- (" + i + ") Values Different [" + result1 + ", " + result2 + "]\n";
                return false;
            }
        }
        return true;
    }

    private static List<String> preprocessResultSet(ArrayList<Object> resultSet) {
        return resultSet.stream().map(o -> {
            if (o == null) {
                return "[NULL]";
            } else {
                return o.toString();
            }
        }).sorted().collect(Collectors.toList());
    }
}
