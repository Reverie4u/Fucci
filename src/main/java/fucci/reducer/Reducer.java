package fucci.reducer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import com.mysql.cj.conf.ConnectionUrlParser.Pair;

import fucci.ConstantStatementType;
import fucci.IsolationLevel;
import fucci.Randomly;
import fucci.SimplifyType;
import fucci.StatementCell;
import fucci.StatementType;
import fucci.TableTool;
import fucci.Transaction;
import fucci.WhereExprType;
import fucci.mysql.ast.MySQLBetweenOperation;
import fucci.mysql.ast.MySQLBinaryComparisonOperation;
import fucci.mysql.ast.MySQLBinaryLogicalOperation;
import fucci.mysql.ast.MySQLBinaryOperation;
import fucci.mysql.ast.MySQLCastOperation;
import fucci.mysql.ast.MySQLConstant.MySQLIntConstant;
import fucci.mysql.ast.MySQLDummyExpression;
import fucci.mysql.ast.MySQLExpression;
import fucci.mysql.ast.MySQLInOperation;
import fucci.mysql.ast.MySQLUnaryPostfixOperation;
import fucci.mysql.ast.MySQLUnaryPrefixOperation;
import fucci.mysql.visitor.MySQLVisitor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Reducer {
    // 全局只有一个Reducer
    // 第一层：语句删除层选择器
    OrderSelector<StatementType> stmtDelOrderSelector;
    // 第二层：语句简化层选择器
    OrderSelector<SimplifyType> stmtSimplifySelector;
    // 第三层：表达式简化层选择器
    OrderSelector<WhereExprType> exprSimplifySelector;
    // 第四层：常量简化层选择器
    OrderSelector<ConstantStatementType> constantSimplifySelector;
    // 第一层简化的map
    Map<StatementType, ArrayList<StatementCell>> stmtTypeMap;
    // 第一层简化失败的语句map
    Map<StatementType, ArrayList<StatementCell>> stmtTypeFailMap;
    // 分层简化模型每层最大简化次数
    int maxReduceCount = 0;
    // 所有简化次数
    int allReduceCount = 0;
    // 有效简化次数
    int vaildReduceCount = 0;
    // 每一层的成功简化次数
    int[] validReduceCountForEachLevel;
    // 每一层的简化次数
    int[] reduceCountForEachLevel;
    // 每层的有效简化率 = 每一层的成功简化次数/每一层的简化次数
    double[] reduceRateForEachLevel;
    // 每层的有效简化贡献率 = 每一层的成功简化次数/所有简化次数
    double[] reduceRateContribOfEachLevel;
    // 总共的有效简化率 = 有效简化次数/所有简化次数
    double validReduceRate = 0.0;

    // 每一层简化后的样本字符数
    int[] lengthOfCase;
    // 每一层的简化率
    double[] simplificationRate;
    // 每一层贡献的简化率
    double[] addedSimplificationRate;
    // 统计数据
    StringBuffer statistics;
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public int getVaildReduceCount() {
        return vaildReduceCount;
    }

    public int getAllReduceCount() {
        return allReduceCount;
    }

    public Reducer(int selectorType) {
        maxReduceCount = TableTool.maxReduceCount;
        stmtTypeMap = new HashMap<>();
        stmtTypeFailMap = new HashMap<>();
        lengthOfCase = new int[5];
        simplificationRate = new double[5];
        addedSimplificationRate = new double[5];
        validReduceCountForEachLevel = new int[5];
        reduceCountForEachLevel = new int[5];
        reduceRateForEachLevel = new double[5];
        reduceRateContribOfEachLevel = new double[5];
        statistics = new StringBuffer();
        for (int i = 0; i <= 4; i++) {
            lengthOfCase[i] = 0;
            simplificationRate[i] = 0.0;
            addedSimplificationRate[i] = 0.0;
            validReduceCountForEachLevel[i] = 0;
            reduceCountForEachLevel[i] = 0;
            reduceRateForEachLevel[i] = 0.0;
            reduceRateContribOfEachLevel[i] = 0.0;
        }
        List<StatementType> candidatesForDelStmt = new ArrayList<>();
        List<SimplifyType> candidatesForSimplifyStmt = new ArrayList<>();
        List<WhereExprType> candidatesForSimplifyExpr = new ArrayList<>();
        List<ConstantStatementType> candidatesForSimplifyCons = new ArrayList<>();
        StatementType[] typesForDelStmt = new StatementType[] {
                StatementType.SELECT, StatementType.UPDATE,
                StatementType.INSERT, StatementType.DELETE,
                StatementType.CREATE_INDEX, StatementType.SELECT_SHARE,
                StatementType.SELECT_UPDATE
        };

        SimplifyType[] typesForSimplifyStmt = new SimplifyType[] {
                SimplifyType.DEL_EXPRE, SimplifyType.DEL_INSERT_COL,
                SimplifyType.DEL_UPDATE_COL, SimplifyType.SIMPLIFY_TABLE,
                SimplifyType.DEL_SELECT_ROL
        };

        WhereExprType[] typesForSimplifyExpr = new WhereExprType[] {
                WhereExprType.MySQLBetweenOperation,
                WhereExprType.MySQLBinaryComparisonOperation,
                WhereExprType.MySQLBinaryLogicalOperation,
                WhereExprType.MySQLBinaryOperation,
                WhereExprType.MySQLCastOperation,
                WhereExprType.MySQLInOperation,
                WhereExprType.MySQLUnaryPostfixOperation,
                WhereExprType.MySQLUnaryPrefixOperation
        };
        ConstantStatementType[] typesForSimplifyCons = new ConstantStatementType[] {
                ConstantStatementType.SELECT,
                ConstantStatementType.INSERT, ConstantStatementType.DELETE,
                ConstantStatementType.SELECT_SHARE, ConstantStatementType.SELECT_UPDATE,
                ConstantStatementType.UPDATE_SET, ConstantStatementType.UPDATE_WHERE
        };
        for (StatementType type : typesForDelStmt) {
            stmtTypeMap.put(type, new ArrayList<>());
            stmtTypeFailMap.put(type, new ArrayList<>());
            candidatesForDelStmt.add(type);
        }
        for (SimplifyType type : typesForSimplifyStmt) {
            candidatesForSimplifyStmt.add(type);
        }
        for (WhereExprType type : typesForSimplifyExpr) {
            candidatesForSimplifyExpr.add(type);
        }
        for (ConstantStatementType type : typesForSimplifyCons) {
            candidatesForSimplifyCons.add(type);
        }

        switch (selectorType) {
            case 0:
                stmtDelOrderSelector = new RandomOrderSelector<>(candidatesForDelStmt);
                stmtSimplifySelector = new RandomOrderSelector<>(candidatesForSimplifyStmt);
                exprSimplifySelector = new RandomOrderSelector<>(candidatesForSimplifyExpr);
                constantSimplifySelector = new RandomOrderSelector<>(candidatesForSimplifyCons);
                break;
            case 1:
                stmtDelOrderSelector = new ProbabilityTableBasedOrderSelector<>(candidatesForDelStmt);
                stmtSimplifySelector = new ProbabilityTableBasedOrderSelector<>(candidatesForSimplifyStmt);
                exprSimplifySelector = new ProbabilityTableBasedOrderSelector<>(candidatesForSimplifyExpr);
                constantSimplifySelector = new ProbabilityTableBasedOrderSelector<>(candidatesForSimplifyCons);
                break;
            case 2:
                stmtDelOrderSelector = new EpsilonGreedyOrderSelector<>(candidatesForDelStmt);
                stmtSimplifySelector = new EpsilonGreedyOrderSelector<>(candidatesForSimplifyStmt);
                exprSimplifySelector = new EpsilonGreedyOrderSelector<>(candidatesForSimplifyExpr);
                constantSimplifySelector = new EpsilonGreedyOrderSelector<>(candidatesForSimplifyCons);
                break;
            default:
                break;
        }
    }

    private ArrayList<StatementCell> stmtListClone(List<StatementCell> stmts) {
        ArrayList<StatementCell> copied = new ArrayList<>();
        for (StatementCell stmt : stmts) {
            copied.add(stmt.copy());
        }
        return copied;
    }

    private TestCase testCaseClone(TestCase tc) {
        TestCase clonedTestCase = new TestCase();
        clonedTestCase.createStmt = tc.createStmt.copy();
        clonedTestCase.prepareTableStmts = stmtListClone(tc.prepareTableStmts);
        clonedTestCase.tx1 = new Transaction(1, tc.tx1.getIsolationlevel(), tc.tx1.getConn(), tc.tx1.getRefConn());
        clonedTestCase.tx1.setStatements(stmtListClone(tc.tx1.getStatements()));
        clonedTestCase.tx2 = new Transaction(2, tc.tx2.getIsolationlevel(), tc.tx2.getConn(), tc.tx2.getRefConn());
        clonedTestCase.tx2.setStatements(stmtListClone(tc.tx2.getStatements()));
        clonedTestCase.submittedOrder = stmtListClone(tc.submittedOrder);
        return clonedTestCase;
    }

    public String reduce(String tc) {
        // 第一层：*删除语句层*
        // stmtTypeMap和stmtTypeFailMap需要清空
        for (Map.Entry<StatementType, ArrayList<StatementCell>> entry : stmtTypeMap.entrySet()) {
            entry.getValue().clear();
        }
        for (Map.Entry<StatementType, ArrayList<StatementCell>> entry : stmtTypeFailMap.entrySet()) {
            entry.getValue().clear();
        }
        TestCase testCase = parse(tc);
        // 将语句按照类型划分
        for (StatementCell stmt : testCase.prepareTableStmts) {
            stmtTypeMap.get(stmt.getType()).add(stmt);
        }
        for (StatementCell stmt : testCase.tx1.getStatements()) {
            if (stmt.getType() != StatementType.BEGIN && stmt.getType() != StatementType.COMMIT
                    && stmt.getType() != StatementType.ROLLBACK) {
                stmtTypeMap.get(stmt.getType()).add(stmt);
            }
        }
        for (StatementCell stmt : testCase.tx2.getStatements()) {
            if (stmt.getType() != StatementType.BEGIN && stmt.getType() != StatementType.COMMIT
                    && stmt.getType() != StatementType.ROLLBACK) {
                stmtTypeMap.get(stmt.getType()).add(stmt);
            }
        }
        OracleChecker oracleChecker;
        if (TableTool.oracle.equals("MT") || TableTool.oracle.equals("CS")) {
            oracleChecker = new MTOracleChecker();
        } else {
            oracleChecker = new DTOracleChecker();
        }
        // 计算原样例的字符数
        lengthOfCase[0] += testCase.toString().length();

        log.info("-------------------------------First Level-------------------------------");

        // 选择一个语句类型
        for (int i = 0; i < maxReduceCount; i++) {
            // 首先克隆一份testcase
            TestCase clonedTestCase = testCaseClone(testCase);
            StatementCell delStmt = deleteStatement(clonedTestCase);
            if (delStmt == null) {
                break;
            }
            if (oracleChecker.hasBug(clonedTestCase.toString())) {
                log.info("Statement [{}] del success", delStmt.toString());
                stmtDelOrderSelector.updateWeight(delStmt.getType(), true);
                // 删除后仍能复现bug则更新测试用例
                testCase = clonedTestCase;
                reduceCountForEachLevel[1]++;
                validReduceCountForEachLevel[1]++;
            } else {
                log.info("Statement [{}] del failed", delStmt.toString());
                stmtTypeFailMap.get(delStmt.getType()).add(delStmt);
                stmtDelOrderSelector.updateWeight(delStmt.getType(), false);
                reduceCountForEachLevel[1]++;
            }
        }
        // 计算第一层之后的简化样例字符数
        lengthOfCase[1] += testCase.toString().length();
        log.info("-------------------------------Second Level-------------------------------");
        // 第二层：*语句简化层*
        for (int i = 1; i <= maxReduceCount; i++) {
            // SimplifyType typeForStmtSimplify = stmtSimplifySelector.selectNext();
            SimplifyType typeForStmtSimplify = SimplifyType.DEL_SELECT_ROL;
            switch (typeForStmtSimplify.toString()) {
                case "SIMPLIFY_TABLE":
                    // 简化表定义
                    testCase = simplifyTable(testCase, oracleChecker, typeForStmtSimplify);
                    break;
                case "DEL_INSERT_COL":
                    // 删除插入列
                    testCase = delInsertCol(testCase, oracleChecker, typeForStmtSimplify);
                    break;
                case "DEL_UPDATE_COL":
                    // 删除更新列
                    testCase = delUpdateCol(testCase, oracleChecker, typeForStmtSimplify);
                    break;
                case "DEL_SELECT_ROL":
                    // 删除选择列
                    testCase = delSelectCol(testCase, oracleChecker, typeForStmtSimplify);
                    break;
                case "DEL_EXPRE":
                    // 删除表达式
                    testCase = delWhereClause(testCase, oracleChecker, typeForStmtSimplify);
                    break;
            }
        }

        // 计算第二层之后的简化样例字符数
        lengthOfCase[2] += testCase.toString().length();
        log.info("-------------------------------Third Level-------------------------------");
        // 第三层：*表达式简化层*
        for (int i = 1; i <= maxReduceCount; i++)
            testCase = simplifyWhereExpr(testCase, oracleChecker);

        // System.out.println(testCase.toString());

        // 计算第三层之后的简化样例字符数
        lengthOfCase[3] += testCase.toString().length();
        log.info("-------------------------------Fourth Level-------------------------------");
        // 第四层：*常量简化层*
        for (int i = 1; i <= maxReduceCount; i++)
            testCase = simplifyConstant(testCase, oracleChecker);

        // 计算第四层之后的简化样例字符数
        lengthOfCase[4] += testCase.toString().length();

        statistics.append(testCase.toString() + "\n\n");
        if (TableTool.reducerType.equals("All") || TableTool.reducerType.equals("Epsilon"))
            statistics.append("Reducer Type:" + TableTool.reducerType + " Epsilon = 0.7\n");
        else
            statistics.append("Reducer Type:" + TableTool.reducerType + "\n");
        // 获取简化统计数据
        getStatistics();
        String res = statistics.toString();
        statistics.delete(0, statistics.length());
        return res;
    }

    public void getStatistics() {
        // 计算简化程度
        calculateSimplificationRate();
        // 打印简化程度
        printSimplificationRate();
        // 计算有效简化率
        calculateReduceCountRate();
        // 打印有效简化率
        printReduceCountRate();
    }

    public void calculateReduceCountRate() {
        allReduceCount = 0;
        vaildReduceCount = 0;
        for (int i = 1; i <= 4; i++) {
            allReduceCount += reduceCountForEachLevel[i];
            vaildReduceCount += validReduceCountForEachLevel[i];
            reduceRateForEachLevel[i] = (double) validReduceCountForEachLevel[i] / reduceCountForEachLevel[i];
        }
        for (int i = 1; i <= 4; i++) {
            reduceRateContribOfEachLevel[i] = (double) validReduceCountForEachLevel[i] / allReduceCount;
        }

        validReduceRate = (double) vaildReduceCount / allReduceCount;
    }

    public void printReduceCountRate() {
        log.info("---------------------------Reduce Rate Statistics Start---------------------------");
        statistics.append("---------------------------Reduce Rate Statistics Start---------------------------\n");
        // 总共的有效简化率
        for (int i = 1; i <= 4; i++) {
            // 每层的有效简化贡献率 ；每层的有效简化率
            log.info("{}th Level: Valid Reduce Rate Contrib. {}, Valid Reduce Rate {}", String.valueOf(i),
                    String.valueOf(reduceRateContribOfEachLevel[i]), String.valueOf(reduceRateForEachLevel[i]));
            statistics.append(String.valueOf(i) + "th Level: Valid Reduce Rate Contrib.: "
                    + String.valueOf(reduceRateContribOfEachLevel[i])
                    + ", Valid Reduce Rate: " + String.valueOf(reduceRateForEachLevel[i]) + "\n");
        }
        for (int i = 1; i <= 4; i++) {
            statistics.append(String.valueOf(i) + "th Level: Valid Reduce Count: "
                    + String.valueOf(validReduceCountForEachLevel[i])
                    + ", Reduce Count: " + String.valueOf(reduceCountForEachLevel[i]) + "\n");
        }
        statistics.append("Total Valid Reduce Rate : " + String.valueOf(validReduceRate) + "\n");
        log.info("Total Valid Reduce Rate {}", String.valueOf(validReduceRate));
        statistics.append("---------------------------Reudce Rate Statistics End  ---------------------------\n");
        log.info("---------------------------Reudce Rate Statistics End  ---------------------------");
    }

    public void printSimplificationRate() {
        log.info("---------------------------Simplification Rate Statistics Start---------------------------");
        LocalDateTime dateTime = LocalDateTime.now();
        String formattedDateTime = dateTime.format(formatter);
        statistics.append(formattedDateTime);
        statistics
                .append("---------------------------Simplification Rate Statistics Start---------------------------\n");
        for (int i = 1; i <= 4; i++) {
            log.info("{}th Level: Simplification Rate {}, Added Rate: {}", String.valueOf(i),
                    String.valueOf(simplificationRate[i]), String.valueOf(addedSimplificationRate[i]));
            statistics.append(
                    String.valueOf(i) + "th Level: Simplification Rate :" + String.valueOf(simplificationRate[i]) +
                            ", Added Rate :" + String.valueOf(addedSimplificationRate[i]) + "\n");
        }
        statistics
                .append("---------------------------Simplification Rate Statistics End  ---------------------------\n");
        log.info("---------------------------Simplification Rate Statistics End  ---------------------------");
    }

    public void calculateSimplificationRate() {
        for (int i = 1; i <= 4; i++) {
            simplificationRate[i] = (double) (lengthOfCase[0] - lengthOfCase[i]) / lengthOfCase[0];
            addedSimplificationRate[i] = (double) (lengthOfCase[i - 1] - lengthOfCase[i]) / lengthOfCase[0];
        }
    }

    private TestCase simplifyTable(TestCase testCase, OracleChecker oracleChecker, SimplifyType type) {
        TestCase clonedTestCase = testCaseClone(testCase);
        String createSQL = clonedTestCase.createStmt.getStatement();
        // 找到")"位置然后将后面的表定义替换掉
        int rightBracketIdx = createSQL.lastIndexOf(")");
        String replacedStmt = createSQL.substring(0, rightBracketIdx) + ")";
        if (replacedStmt.equals(createSQL)) {
            log.info("createSQL[{}] can't be simpilified", clonedTestCase.createStmt.getStatement());
            stmtSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[2]++;
            return testCase;
        }
        clonedTestCase.createStmt.setStmt(replacedStmt);
        // System.out.println(clonedTestCase.createStmt.getStatement());

        if (oracleChecker.hasBug(clonedTestCase.toString())) {
            log.info("createSQL[{}] simpilify success", clonedTestCase.createStmt.getStatement());
            // 删除后仍能复现bug则更新测试用例
            testCase = testCaseClone(clonedTestCase);
            stmtSimplifySelector.updateWeight(type, true);
            validReduceCountForEachLevel[2]++;
            reduceCountForEachLevel[2]++;
        } else {
            log.info("createSQL[{}] simpilify failed", clonedTestCase.createStmt);
            stmtSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[2]++;
        }
        return testCase;
    }

    private TestCase delSelectCol(TestCase testCase, OracleChecker oracleChecker, SimplifyType type) {
        TestCase clonedTestCase = testCaseClone(testCase);
        ArrayList<StatementCell> stmtListForSelect = new ArrayList<>();
        for (StatementCell stmt : clonedTestCase.tx1.getStatements()) {
            if (stmt.getType().toString().equals("SELECT") ||
                    stmt.getType().toString().equals("SELECT_SHARE") ||
                    stmt.getType().toString().equals("SELECT_UPDATE"))
                stmtListForSelect.add(stmt);
        }
        for (StatementCell stmt : clonedTestCase.tx2.getStatements()) {
            if (stmt.getType().toString().equals("SELECT") ||
                    stmt.getType().toString().equals("SELECT_SHARE") ||
                    stmt.getType().toString().equals("SELECT_UPDATE"))
                stmtListForSelect.add(stmt);
        }
        if (stmtListForSelect.size() == 0) {
            log.info("There is no selectSQL");
            stmtSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[2]++;
            return testCase;
        }
        int simpilifyIdx = (int) (Math.random() * stmtListForSelect.size());

        // int simpilifyIdx = 0;
        StatementCell selectCell = stmtListForSelect.get(simpilifyIdx);
        StatementCell selectCellCopy = selectCell.copy();
        if (selectCellCopy.getWhereClause() == "")
            selectCellCopy.setStmt(selectCellCopy.getStatement() + " WHERE");
        String selectStmt = selectCellCopy.getStatement();
        // 将SELECT 和 FROM 变为"@"标识符 方便后续获取select项
        String replacedSelectStmt = selectStmt.replace("SELECT", "@");
        String finalReplacedSelectStmt = replacedSelectStmt.replace("FROM", "@");
        // 获取"@"标识符的位置
        int firstIdx = finalReplacedSelectStmt.indexOf("@");
        int lastIdx = finalReplacedSelectStmt.lastIndexOf("@");
        String selectStr = finalReplacedSelectStmt.substring(firstIdx + 1, lastIdx - 1);
        String[] selectWords = selectStr.split(",");
        if (selectWords.length == 1) {
            log.info("selectSQL [{}] can't be simplified",
                    selectCell.toString() + ":" + selectCell.getStatement().toString());
            stmtSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[2]++;
            return testCase;
        }
        int colLength = selectWords.length;
        int idx = (int) (Math.random() * colLength);
        // 如果要反复删除就恢复for注释
        // for (int idx = 0; idx < colLength - 1; idx++) {
        StringBuffer newSelectStr = new StringBuffer();
        for (int i = 0, cnt = 0; i < selectWords.length; i++) {
            if (i == idx)
                continue;
            else {
                if (cnt == 0) {
                    newSelectStr.append(selectWords[i]);
                    cnt++;
                } else {
                    newSelectStr.append("," + selectWords[i]);
                }
            }
        }
        newSelectStr.append(" ");
        String tmpSelectStr = finalReplacedSelectStmt.replace("WHERE", "#");
        StringBuffer replacedSelectStr = new StringBuffer();
        StringBuffer replacedSelectStrPrefx = new StringBuffer();

        replacedSelectStr.append(tmpSelectStr);
        replacedSelectStr.replace(firstIdx + 1, lastIdx, newSelectStr.toString());

        replacedSelectStr.insert(replacedSelectStr.toString().indexOf("@") + 1, "SELECT");
        replacedSelectStr.insert(replacedSelectStr.toString().lastIndexOf("@") + 1, "FROM");
        replacedSelectStr.deleteCharAt(replacedSelectStr.toString().indexOf("@"));
        replacedSelectStr.deleteCharAt(replacedSelectStr.toString().lastIndexOf("@"));

        if (!selectCell.getWhereClause().equals(""))
            replacedSelectStr.insert(replacedSelectStr.toString().indexOf("#") + 1, "WHERE");

        replacedSelectStrPrefx.append(replacedSelectStr.toString());
        replacedSelectStr.deleteCharAt(replacedSelectStr.toString().indexOf("#"));

        selectCell.setStmt(replacedSelectStr.toString());
        selectCell.setWherePrefix(
                replacedSelectStrPrefx.substring(0, replacedSelectStrPrefx.toString().indexOf("#") - 1));
        if (oracleChecker.hasBug(clonedTestCase.toString())) {
            log.info("selectSQL [{}] simpilify success",
                    selectCell.toString() + ":" + selectCell.getStatement().toString());
            stmtSimplifySelector.updateWeight(type, true);
            validReduceCountForEachLevel[2]++;
            reduceCountForEachLevel[2]++;

            // 删除后仍能复现bug则更新测试用例

            testCase = testCaseClone(clonedTestCase);
            return testCase;

            /*
             * 要反复删除则恢复下列注释
             * idx--;
             * selectCell = stmtListForSelect.get(simpilifyIdx);
             */
        } else {
            log.info("selectSQL [{}] simpilify failed",
                    selectCell.toString() + ":" + selectCell.getStatement().toString());
            stmtSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[2]++;
            return testCase;
            /*
             * 要反复删除则恢复下列注释
             * selectCell = selectCellCopy;
             */
        }
        /*
         * 要反复删除则恢复下列注释
         * selectCellCopy = selectCell.copy();
         * if (selectCellCopy.getWhereClause() == "")
         * selectCellCopy.setStmt(selectCellCopy.getStatement() + " WHERE");
         * selectStmt = selectCellCopy.getStatement();
         * // 将SELECT 和 FROM 变为"@"标识符 方便后续获取select项
         * replacedSelectStmt = selectStmt.replace("SELECT", "@");
         * finalReplacedSelectStmt = replacedSelectStmt.replace("FROM", "@");
         * // 获取"@"标识符的位置
         * firstIdx = finalReplacedSelectStmt.indexOf("@");
         * lastIdx = finalReplacedSelectStmt.lastIndexOf("@");
         * selectStr = finalReplacedSelectStmt.substring(firstIdx + 1, lastIdx - 1);
         * selectWords = selectStr.split(",");
         * colLength = selectWords.length;
         */
        // }
        // return testCase;
    }

    private TestCase simplifyConstant(TestCase testCase, OracleChecker oracleChecker) {
        TestCase clonedTestCase = testCaseClone(testCase);
        // 选取需要简化的类型
        ConstantStatementType type = constantSimplifySelector.selectNext();
        // ConstantStatementType type = ConstantStatementType.SELECT;
        ArrayList<StatementCell> stmtListForType = new ArrayList<>();
        for (StatementCell stmt : clonedTestCase.prepareTableStmts) {
            if (stmt.getType().toString().equals(type.toString())) {
                stmtListForType.add(stmt);
            }
        }
        for (StatementCell stmt : clonedTestCase.tx1.getStatements()) {
            // 由于语句类型里只有UPDATE 因此常量简化UPDATE_SET类型或者UPDATE_WHERE类型时都可以将UPDATE语句加入
            if (stmt.getType().toString().equals("UPDATE")) {
                if (type.toString().equals("UPDATE_SET") || type.toString().equals("UPDATE_WHERE"))
                    stmtListForType.add(stmt);
            }

            else if (stmt.getType().toString().equals(type.toString())) {
                stmtListForType.add(stmt);
            }
        }
        for (StatementCell stmt : clonedTestCase.tx2.getStatements()) {
            // 由于语句类型里只有UPDATE 因此常量简化UPDATE_SET类型或者UPDATE_WHERE类型时都可以将UPDATE语句加入
            if (stmt.getType().toString().equals("UPDATE")) {
                if (type.toString().equals("UPDATE_SET") || type.toString().equals("UPDATE_WHERE"))
                    stmtListForType.add(stmt);
            } else if (stmt.getType().toString().equals(type.toString())) {
                stmtListForType.add(stmt);
            }
        }
        if (stmtListForType.size() == 0) {
            log.info("There is no {} SQL", type.toString());
            constantSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[4]++;
            return testCase;
        }
        int simpilifyIdx = (int) (Math.random() * stmtListForType.size());
        // int simpilifyIdx = 2;

        StatementCell constantCell = stmtListForType.get(simpilifyIdx);
        switch (type.toString()) {
            case "INSERT":
                testCase = simplifyConstantOfWherePrefix(constantCell, clonedTestCase, testCase, oracleChecker, type);
                break;
            case "SELECT":
                testCase = simplifyConstantOfWhereClause(constantCell, clonedTestCase, testCase, oracleChecker, type);
                break;
            case "UPDATE_SET":
                testCase = simplifyConstantOfWherePrefixOfUpdate(constantCell, clonedTestCase, testCase,
                        oracleChecker, type);
                break;
            case "UPDATE_WHERE":
                testCase = simplifyConstantOfWhereClause(constantCell, clonedTestCase, clonedTestCase, oracleChecker,
                        type);
                break;
            case "SELECT_SHARE":
                testCase = simplifyConstantOfWhereClause(constantCell, clonedTestCase, testCase, oracleChecker, type);
                break;

            case "SELECT_UPDATE":
                testCase = simplifyConstantOfWhereClause(constantCell, clonedTestCase, testCase, oracleChecker, type);
                break;

            case "DELETE":
                testCase = simplifyConstantOfWhereClause(constantCell, clonedTestCase, testCase, oracleChecker, type);
                break;
        }
        return testCase;
    }

    private TestCase simplifyConstantOfWherePrefix(StatementCell constantCell, TestCase clonedTestCase,
            TestCase testCase, OracleChecker oracleChecker, ConstantStatementType type) {
        StatementCell constantCellCopy = constantCell.copy();
        // 获得一条insert语句
        String insertStmt = constantCellCopy.getStatement();
        // 取得值括号对的位置
        int valueLeftBracket = insertStmt.lastIndexOf("(");
        int valueRightBracket = insertStmt.lastIndexOf(")");

        // 得到插入值
        String valueStr = insertStmt.substring(valueLeftBracket + 1, valueRightBracket);
        String[] valueWords = valueStr.split(",");

        int colLength = valueWords.length;
        int idx = (int) (Math.random() * colLength);
        // 如果要反复删除就恢复for注释
        // for (int idx = 0; idx < colLength; idx++) {
        Integer num = Randomly.getNextInt(-10, 10);
        String numStr = num.toString();
        int tmp = -1314;
        if (idx == 0) {
            tmp = Integer.parseInt(valueWords[idx].substring(0));
        } else {
            tmp = Integer.parseInt(valueWords[idx].substring(1));
        }
        if (tmp >= -10 && tmp <= 10) {
            log.info("Constant of wherePrefixSQL [{}] simpilify failed",
                    constantCell.toString() + ":" + constantCell.getStatement().toString());
            constantSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[4]++;
            return testCase;
            /// continue;
        }
        if (idx == 0)
            valueWords[idx] = numStr;
        else
            valueWords[idx] = " " + numStr;
        StringBuffer newValueStr = new StringBuffer();
        for (int i = 0; i < colLength; i++) {
            if (i == 0)
                newValueStr.append(valueWords[i]);
            else
                newValueStr.append("," + valueWords[i]);

        }
        StringBuffer newInsertStmt = new StringBuffer();
        newInsertStmt.append(insertStmt.substring(0, valueLeftBracket + 1));
        newInsertStmt.append(newValueStr.toString());
        newInsertStmt.append(insertStmt.substring(valueRightBracket));
        constantCell.setStmt(newInsertStmt.toString());

        if (oracleChecker.hasBug(clonedTestCase.toString())) {
            log.info("Constant of wherePrefixSQL [{}] simpilify success",
                    constantCell.toString() + ":" + constantCell.getStatement().toString());
            constantSimplifySelector.updateWeight(type, true);
            validReduceCountForEachLevel[4]++;
            reduceCountForEachLevel[4]++;
            // 删除后仍能复现bug则更新测试用例
            testCase = testCaseClone(clonedTestCase);
            return testCase;
        } else {
            log.info("Constant of wherePrefixSQL [{}] simpilify failed",
                    constantCell.toString() + ":" + constantCell.getStatement().toString());
            constantSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[4]++;
            return testCase;
            /*
             * 如果要反复删除就恢复下列注释
             * constantCell = constantCellCopy;
             */
        }
        /*
         * 如果要反复删除就恢复下列注释
         * constantCellCopy = constantCell.copy();
         * // 获得一条insert语句
         * insertStmt = constantCellCopy.getStatement();
         * // 取得值括号对的位置
         * valueLeftBracket = insertStmt.lastIndexOf("(");
         * valueRightBracket = insertStmt.lastIndexOf(")");
         * // 得到插入值
         * valueStr = insertStmt.substring(valueLeftBracket + 1, valueRightBracket);
         * valueWords = valueStr.split(",");
         * colLength = valueWords.length;
         */
        // }
        // return testCase;
    }

    private TestCase simplifyConstantOfWhereClause(StatementCell constantCell, TestCase clonedTestCase,
            TestCase testCase, OracleChecker oracleChecker, ConstantStatementType type) {
        StatementCell constantCellCopy = constantCell.copy();
        MySQLExpression rootPredicate = constantCell.getPredicate();
        if (rootPredicate == null) {
            log.info("Constant of whereClauseSQL [{}] can't be simpilified ",
                    constantCell.toString() + ":" + constantCell.getStatement().toString());
            constantSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[4]++;
            return testCase;
        }
        // 用来判断整个where子句有没有常量
        boolean hasSimplifyConstantNode = false;
        LinkedList<MySQLExpression> nodeQueue = new LinkedList<>();
        nodeQueue.add(rootPredicate);
        while (!nodeQueue.isEmpty()) {
            MySQLExpression node = nodeQueue.getFirst();
            nodeQueue.removeFirst();
            // 如果是常量节点的话
            if (judgeConstantNode(node.getClass().getSimpleName())) {
                hasSimplifyConstantNode = true;
                long num = Randomly.getNextInt(-10, 10);
                MySQLIntConstant nodeInt = (MySQLIntConstant) node;
                if (nodeInt.getCanSimplify() == false) {
                    hasSimplifyConstantNode = false;
                    continue;
                }
                long tmp = nodeInt.getInt();
                if (tmp >= -10 && tmp <= 10) {
                    // log.info("Constant of whereClauseSQL [{}] simpilify failed ",
                    // constantCell.toString() + ":" + constantCell.getStatement().toString());
                    // constantSimplifySelector.updateWeight(type, false);
                    // reduceCountForEachLevel[4]++;
                    nodeInt.setCanSimplify(false);
                    hasSimplifyConstantNode = false;
                    continue;
                }
                nodeInt.setIntConstant(num, String.valueOf(num));
                constantCell.setWhereClause(MySQLVisitor.asString(rootPredicate));
                constantCell.setStmt(constantCell.getWherePrefix() + " WHERE " + constantCell.getWhereClause() + " "
                        + constantCell.getForPostFix());
                if (oracleChecker.hasBug(clonedTestCase.toString())) {
                    log.info("Constant of whereClauseSQL [{}] simpilify success ",
                            constantCell.toString() + ":" + constantCell.getStatement().toString());
                    constantSimplifySelector.updateWeight(type, true);
                    validReduceCountForEachLevel[4]++;
                    reduceCountForEachLevel[4]++;
                    // 删除后仍能复现bug则更新测试用例
                    testCase = testCaseClone(clonedTestCase);
                    return testCase;
                } else {
                    log.info("Constant of whereClauseSQL [{}] simpilify failed ",
                            constantCell.toString() + ":" + constantCell.getStatement().toString());
                    constantSimplifySelector.updateWeight(type, false);
                    reduceCountForEachLevel[4]++;
                    nodeInt.setCanSimplify(false);
                    return testCase;
                    /*
                     * 如果要反复删除则恢复下列注释
                     * if (nodeQueue.isEmpty()) {
                     * break;
                     * }
                     */
                }
            } else {
                switch (node.getClass().getSimpleName()) {
                    case "MySQLBetweenOperation":
                        MySQLBetweenOperation tmpNodeForBetweenOperation = (MySQLBetweenOperation) node;
                        nodeQueue.add(tmpNodeForBetweenOperation.getExpr());
                        nodeQueue.add(tmpNodeForBetweenOperation.getLeft());
                        nodeQueue.add(tmpNodeForBetweenOperation.getRight());
                        break;

                    case "MySQLBinaryComparsionOperation":
                        MySQLBinaryComparisonOperation tmpNodeForBinaryComp = (MySQLBinaryComparisonOperation) node;
                        nodeQueue.add(tmpNodeForBinaryComp.getLeft());
                        nodeQueue.add(tmpNodeForBinaryComp.getRight());
                        break;

                    case "MySQLBinaryLogicalOperation":
                        MySQLBinaryLogicalOperation tmpNodeForBinaryLog = (MySQLBinaryLogicalOperation) node;
                        nodeQueue.add(tmpNodeForBinaryLog.getLeft());
                        nodeQueue.add(tmpNodeForBinaryLog.getRight());
                        break;

                    case "MySQLBinaryOperation":
                        MySQLBinaryOperation tmpNodeForBinary = (MySQLBinaryOperation) node;
                        nodeQueue.add(tmpNodeForBinary.getLeft());
                        nodeQueue.add(tmpNodeForBinary.getRight());
                        break;

                    case "MySQLCastOperation":
                        MySQLCastOperation tmpNodeForCast = (MySQLCastOperation) node;
                        nodeQueue.add(tmpNodeForCast.getExpr());
                        break;

                    case "MySQLInOperation":
                        MySQLInOperation tmpNodeForIn = (MySQLInOperation) node;
                        nodeQueue.add(tmpNodeForIn.getExpr());

                        for (MySQLExpression tmpNodeIn : tmpNodeForIn.getListElements()) {
                            nodeQueue.add(tmpNodeIn);
                        }

                        break;

                    case "MySQLUnaryPostfixOperation":
                        MySQLUnaryPostfixOperation tmpNodeForUnaryPostFix = (MySQLUnaryPostfixOperation) node;
                        nodeQueue.add(tmpNodeForUnaryPostFix.getExpression());
                        break;

                    case "MySQLUnaryPrefixOperation":
                        MySQLUnaryPrefixOperation tmpNodeForUnaryPreFix = (MySQLUnaryPrefixOperation) node;
                        nodeQueue.add(tmpNodeForUnaryPreFix.getExpression());
                        break;
                }
            }

        }
        // 如果遍历了整个where子句都没发现常量节点表明这个更新是失败的
        if (hasSimplifyConstantNode == false) {
            log.info("Constant of whereClauseSQL [{}] simpilify failed ",
                    constantCell.toString() + ":" + constantCell.getStatement().toString());
            constantSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[4]++;
        }
        return testCase;
    }

    private int deleteSpaceOfStrValue(String str) {
        int spaceIdx = str.indexOf(" ");
        StringBuffer newStr = new StringBuffer("");
        if (spaceIdx > 0)
            newStr.append(str.substring(0, spaceIdx));
        else
            newStr.append(str);
        return Integer.parseInt(newStr.toString());
    }

    private TestCase simplifyConstantOfWherePrefixOfUpdate(StatementCell constantCell, TestCase clonedTestCase,
            TestCase testCase, OracleChecker oracleChecker, ConstantStatementType type) {
        StatementCell constantCellCopy = constantCell.copy();
        if (constantCellCopy.getWhereClause() == "")
            constantCellCopy.setStmt(constantCellCopy.getStatement() + " WHERE");
        // 获得一条update语句
        String updatetStmt = constantCellCopy.getStatement();
        String replacedUpdatestmt = updatetStmt.replace("SET", "@");
        String finalReplacedUpdatestmt = replacedUpdatestmt.replace("WHERE", "@");
        // 取得更新列对的位置
        int updateColLeftIdx = finalReplacedUpdatestmt.indexOf("@");
        int updateColRightIdx = finalReplacedUpdatestmt.lastIndexOf("@");

        // 得到更新列对
        String updateColStr = finalReplacedUpdatestmt.substring(updateColLeftIdx + 1, updateColRightIdx - 1);
        String[] updateColWords = updateColStr.split(",");
        int colLength = updateColWords.length;
        int idx = (int) (Math.random() * colLength);
        // for (int idx = 0; idx < colLength; idx++) {
        Integer num = Randomly.getNextInt(-10, 10);
        String numStr = num.toString();

        int equalIdx = updateColWords[idx].indexOf("=");
        int tmp = deleteSpaceOfStrValue(updateColWords[idx].substring(equalIdx + 1));
        if (tmp >= -10 && tmp <= 10) {
            log.info("Constant of wherePrefixAndWhereClauseSQL [{}] simpilify failed",
                    constantCell.toString() + ":" + constantCell.getStatement().toString());
            constantSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[4]++;
            return testCase;
            /*
             * 如果要反复删除就恢复注释
             * continue;
             */
        }
        updateColWords[idx] = updateColWords[idx].substring(0, equalIdx + 1) + numStr;
        StringBuffer newUpdateColStr = new StringBuffer();
        for (int i = 0; i < colLength; i++) {
            if (i == 0)
                newUpdateColStr.append(updateColWords[i]);
            else
                newUpdateColStr.append("," + updateColWords[i]);
        }
        newUpdateColStr.append(" ");

        StringBuffer newUpdateCol = new StringBuffer();
        StringBuffer newUpdateColWherePrefix = new StringBuffer();

        newUpdateCol.append(finalReplacedUpdatestmt);
        newUpdateCol.replace(updateColLeftIdx + 1, updateColRightIdx, newUpdateColStr.toString());

        newUpdateCol.insert(newUpdateCol.toString().indexOf("@") + 1, "SET");
        newUpdateColWherePrefix.append(newUpdateCol.toString());
        if (!constantCell.getWhereClause().equals(""))
            newUpdateCol.insert(newUpdateCol.toString().lastIndexOf("@") + 1, "WHERE");

        newUpdateCol.deleteCharAt(newUpdateCol.toString().indexOf("@"));
        newUpdateColWherePrefix.deleteCharAt(newUpdateColWherePrefix.toString().indexOf("@"));
        newUpdateCol.deleteCharAt(newUpdateCol.toString().lastIndexOf("@"));

        constantCell.setStmt(newUpdateCol.toString());
        constantCell.setWherePrefix(
                newUpdateColWherePrefix.substring(0, newUpdateColWherePrefix.toString().lastIndexOf("@") - 1));
        if (oracleChecker.hasBug(clonedTestCase.toString())) {
            log.info("Constant of wherePrefixAndWhereClauseSQL [{}] simpilify success",
                    constantCell.toString() + ":" + constantCell.getStatement().toString());
            constantSimplifySelector.updateWeight(type, true);
            validReduceCountForEachLevel[4]++;
            reduceCountForEachLevel[4]++;
            // 删除后仍能复现bug则更新测试用例
            testCase = testCaseClone(clonedTestCase);
            return testCase;
        } else {
            log.info("Constant of wherePrefixAndWhereClauseSQL [{}] simpilify failed",
                    constantCell.toString() + ":" + constantCell.getStatement().toString());
            constantSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[4]++;
            return testCase;
            /*
             * 如果要反复删除就恢复注释
             * constantCell = constantCellCopy;
             */
        }
        /*
         * 如果要反复删除就恢复注释
         * constantCellCopy = constantCell.copy();
         * if (constantCellCopy.getWhereClause() == "")
         * constantCellCopy.setStmt(constantCellCopy.getStatement() + " WHERE");
         * // 获得一条update语句
         * updatetStmt = constantCellCopy.getStatement();
         * replacedUpdatestmt = updatetStmt.replace("SET", "@");
         * finalReplacedUpdatestmt = replacedUpdatestmt.replace("WHERE", "@");
         * // 取得更新列对的位置
         * updateColLeftIdx = finalReplacedUpdatestmt.indexOf("@");
         * updateColRightIdx = finalReplacedUpdatestmt.lastIndexOf("@");
         * 
         * // 得到更新列对
         * updateColStr = finalReplacedUpdatestmt.substring(updateColLeftIdx + 1,
         * updateColRightIdx - 1);
         * updateColWords = updateColStr.split(",");
         * colLength = updateColWords.length;
         */
        // }
        // return testCase;
    }

    private TestCase simplifyWhereExpr(TestCase testCase, OracleChecker oracleChecker) {
        TestCase clonedTestCase = testCaseClone(testCase);
        // 获取简化类型
        WhereExprType exprType = exprSimplifySelector.selectNext();
        // WhereExprType exprType = WhereExprType.MySQLBinaryLogicalOperation;
        ArrayList<StatementCell> whereStmtList = new ArrayList<>();
        for (StatementCell whereStmt : clonedTestCase.tx1.getStatements()) {
            if (whereStmt.getWhereClause() != "")
                whereStmtList.add(whereStmt);
        }
        for (StatementCell whereStmt : clonedTestCase.tx2.getStatements()) {
            if (whereStmt.getWhereClause() != "")
                whereStmtList.add(whereStmt);
        }
        if (whereStmtList.size() == 0) {
            log.info("There is no whereSQL");
            exprSimplifySelector.updateWeight(exprType, false);
            reduceCountForEachLevel[3]++;
            return testCase;
        }
        int simpilifyIdx = (int) (Math.random() * whereStmtList.size());
        // int simpilifyIdx = 7;
        StatementCell whereCell = whereStmtList.get(simpilifyIdx);
        // 给predicate建立一个虚的父节点
        MySQLExpression rootPredicate = whereCell.getPredicate();
        MySQLDummyExpression dummyNode = new MySQLDummyExpression(rootPredicate);
        Pair<MySQLExpression, MySQLExpression> rootNode = new Pair<>(dummyNode, rootPredicate);
        // Queue <MySQLExpression> nodeQueue = new LinkedList<>();
        LinkedList<Pair<MySQLExpression, MySQLExpression>> nodeQueue = new LinkedList<>();
        nodeQueue.add(rootNode);
        while (!nodeQueue.isEmpty()) {
            // MySQLExpression nodePredicate = nodeQueue.getFirst();
            Pair<MySQLExpression, MySQLExpression> node = nodeQueue.getFirst();
            nodeQueue.removeFirst();
            // 判断当前节点是否符合type类型
            if (node.right.getClass().getSimpleName().equals(exprType.toString())) {
                // int start = -10;
                // int end = 10;
                // long num = new Random().nextInt(end - start +1) + start;
                long num = Randomly.getNextInt(0, 1);
                MySQLExpression nodeInt = new MySQLIntConstant(num, true);
                switch (node.left.getClass().getSimpleName()) {
                    case "MySQLDummyExpression":
                        MySQLDummyExpression tmpNodeForDummy = (MySQLDummyExpression) node.left;
                        tmpNodeForDummy.setNode(nodeInt);
                        break;
                    case "MySQLBetweenOperation":
                        MySQLBetweenOperation tmpNodeForBetween = (MySQLBetweenOperation) node.left;
                        if (tmpNodeForBetween.getExpr() == node.right) {
                            tmpNodeForBetween.setExpr(nodeInt);
                        } else if (tmpNodeForBetween.getLeft() == node.right) {
                            tmpNodeForBetween.setLeft(nodeInt);
                        } else if (tmpNodeForBetween.getRight() == node.right) {
                            tmpNodeForBetween.setRight(nodeInt);
                        }
                        break;

                    case "MySQLBinaryComparsionOperation":
                        MySQLBinaryComparisonOperation tmpNodeForBinaryComp = (MySQLBinaryComparisonOperation) node.left;
                        if (tmpNodeForBinaryComp.getLeft() == node.right) {
                            tmpNodeForBinaryComp.setLeft(nodeInt);
                        } else if (tmpNodeForBinaryComp.getRight() == node.right) {
                            tmpNodeForBinaryComp.setRight(nodeInt);
                        }
                        break;

                    case "MySQLBinaryLogicalOperation":
                        MySQLBinaryLogicalOperation tmpNodeForBinaryLog = (MySQLBinaryLogicalOperation) node.left;
                        if (tmpNodeForBinaryLog.getLeft() == node.right) {
                            tmpNodeForBinaryLog.setLeft(nodeInt);
                        } else if (tmpNodeForBinaryLog.getRight() == node.right) {
                            tmpNodeForBinaryLog.setRight(nodeInt);
                        }
                        break;

                    case "MySQLBinaryOperation":
                        MySQLBinaryOperation tmpNodeForBinary = (MySQLBinaryOperation) node.left;
                        if (tmpNodeForBinary.getLeft() == node.right) {
                            tmpNodeForBinary.setLeft(nodeInt);
                        } else if (tmpNodeForBinary.getRight() == node.right) {
                            tmpNodeForBinary.setRight(nodeInt);
                        }
                        break;

                    case "MySQLCastOperation":
                        MySQLCastOperation tmpNodeForCast = (MySQLCastOperation) node.left;
                        tmpNodeForCast.setExpr(nodeInt);
                        break;

                    case "MySQLInOperation":
                        MySQLInOperation tmpNodeForIn = (MySQLInOperation) node.left;
                        if (tmpNodeForIn.getExpr() == node.right) {
                            tmpNodeForIn.setExpr(nodeInt);
                        } else {
                            for (int i = 0; i < tmpNodeForIn.getListElements().size(); i++) {
                                if (tmpNodeForIn.getListElements().get(i) == node.right) {
                                    tmpNodeForIn.getListElements().remove(i);
                                    tmpNodeForIn.getListElements().add(i, nodeInt);
                                    break;
                                }
                            }

                        }
                        break;

                    case "MySQLUnaryPostfixOperation":
                        MySQLUnaryPostfixOperation tmpNodeForUnaryPostFix = (MySQLUnaryPostfixOperation) node.left;
                        tmpNodeForUnaryPostFix.setExpression(nodeInt);
                        break;

                    case "MySQLUnaryPrefixOperation":
                        MySQLUnaryPrefixOperation tmpNodeForUnaryPrefix = (MySQLUnaryPrefixOperation) node.left;
                        tmpNodeForUnaryPrefix.setExpr(nodeInt);
                        break;

                }
                whereCell.setPredicate(dummyNode.getNode());
                whereCell.setWhereClause(MySQLVisitor.asString(whereCell.getPredicate()));
                whereCell.setStmt(whereCell.getWherePrefix() + " WHERE " + whereCell.getWhereClause() + " "
                        + whereCell.getForPostFix());
                if (oracleChecker.hasBug(clonedTestCase.toString())) {
                    log.info("[{}] whereSQL Expr [{}] simpilify success", exprType,
                            whereCell.toString() + ":" + whereCell.getStatement().toString());
                    exprSimplifySelector.updateWeight(exprType, true);
                    // 删除后仍能复现bug则更新测试用例
                    testCase = testCaseClone(clonedTestCase);
                    validReduceCountForEachLevel[3]++;
                    reduceCountForEachLevel[3]++;
                    break;
                } else {
                    log.info("[{}] whereSQL Expr[{}] simpilify failed", exprType,
                            whereCell.toString() + ":" + whereCell.getStatement().toString());
                    exprSimplifySelector.updateWeight(exprType, false);
                    reduceCountForEachLevel[3]++;
                    break;
                    // if(nodeQueue.isEmpty()){
                    // exprSimplifySelector.updateWeight(exprType, false);
                    // break;
                    // }
                }
            } else {
                boolean isAllLeafNodes = true;
                switch (node.right.getClass().getSimpleName()) {
                    case "MySQLDummyExpression":
                        MySQLDummyExpression tmpNodeForDummy = (MySQLDummyExpression) node.right;
                        if (!judgeLeafNode(tmpNodeForDummy.getNode().getClass().getSimpleName())) {
                            addNodeToQueue(isAllLeafNodes, nodeQueue, tmpNodeForDummy, tmpNodeForDummy.getNode());
                        }
                        break;
                    case "MySQLBetweenOperation":
                        MySQLBetweenOperation tmpNodeForBetweenOperation = (MySQLBetweenOperation) node.right;
                        if (!judgeLeafNode(tmpNodeForBetweenOperation.getExpr().getClass().getSimpleName())) {
                            addNodeToQueue(isAllLeafNodes, nodeQueue, tmpNodeForBetweenOperation,
                                    tmpNodeForBetweenOperation.getExpr());
                        }
                        if (!judgeLeafNode(tmpNodeForBetweenOperation.getLeft().getClass().getSimpleName())) {
                            addNodeToQueue(isAllLeafNodes, nodeQueue, tmpNodeForBetweenOperation,
                                    tmpNodeForBetweenOperation.getLeft());
                        }
                        if (!judgeLeafNode(tmpNodeForBetweenOperation.getRight().getClass().getSimpleName())) {
                            addNodeToQueue(isAllLeafNodes, nodeQueue, tmpNodeForBetweenOperation,
                                    tmpNodeForBetweenOperation.getRight());
                        }
                        break;

                    case "MySQLBinaryComparsionOperation":
                        MySQLBinaryComparisonOperation tmpNodeForBinaryComp = (MySQLBinaryComparisonOperation) node.right;
                        if (!judgeLeafNode(tmpNodeForBinaryComp.getLeft().getClass().getSimpleName())) {
                            addNodeToQueue(isAllLeafNodes, nodeQueue, tmpNodeForBinaryComp,
                                    tmpNodeForBinaryComp.getLeft());
                        }
                        if (!judgeLeafNode(tmpNodeForBinaryComp.getRight().getClass().getSimpleName())) {
                            addNodeToQueue(isAllLeafNodes, nodeQueue, tmpNodeForBinaryComp,
                                    tmpNodeForBinaryComp.getRight());
                        }
                        break;

                    case "MySQLBinaryLogicalOperation":
                        MySQLBinaryLogicalOperation tmpNodeForBinaryLog = (MySQLBinaryLogicalOperation) node.right;
                        if (!judgeLeafNode(tmpNodeForBinaryLog.getLeft().getClass().getSimpleName())) {
                            addNodeToQueue(isAllLeafNodes, nodeQueue, tmpNodeForBinaryLog,
                                    tmpNodeForBinaryLog.getLeft());
                        }
                        if (!judgeLeafNode(tmpNodeForBinaryLog.getRight().getClass().getSimpleName())) {
                            addNodeToQueue(isAllLeafNodes, nodeQueue, tmpNodeForBinaryLog,
                                    tmpNodeForBinaryLog.getRight());
                        }
                        break;

                    case "MySQLBinaryOperation":
                        MySQLBinaryOperation tmpNodeForBinary = (MySQLBinaryOperation) node.right;
                        if (!judgeLeafNode(tmpNodeForBinary.getLeft().getClass().getSimpleName())) {
                            addNodeToQueue(isAllLeafNodes, nodeQueue, tmpNodeForBinary, tmpNodeForBinary.getLeft());
                        }
                        if (!judgeLeafNode(tmpNodeForBinary.getRight().getClass().getSimpleName())) {
                            addNodeToQueue(isAllLeafNodes, nodeQueue, tmpNodeForBinary, tmpNodeForBinary.getRight());
                        }
                        break;

                    case "MySQLCastOperation":
                        MySQLCastOperation tmpNodeForCast = (MySQLCastOperation) node.right;
                        if (!judgeLeafNode(tmpNodeForCast.getExpr().getClass().getSimpleName())) {
                            addNodeToQueue(isAllLeafNodes, nodeQueue, tmpNodeForCast, tmpNodeForCast.getExpr());
                        }
                        break;

                    case "MySQLInOperation":
                        MySQLInOperation tmpNodeForIn = (MySQLInOperation) node.right;
                        if (!judgeLeafNode(tmpNodeForIn.getExpr().getClass().getSimpleName())) {
                            addNodeToQueue(isAllLeafNodes, nodeQueue, tmpNodeForIn, tmpNodeForIn.getExpr());
                        }

                        for (MySQLExpression tmpNodeIn : tmpNodeForIn.getListElements()) {
                            if (!judgeLeafNode(tmpNodeIn.getClass().getSimpleName())) {
                                addNodeToQueue(isAllLeafNodes, nodeQueue, tmpNodeForIn, tmpNodeIn);
                            }
                        }

                        break;

                    case "MySQLUnaryPostfixOperation":
                        MySQLUnaryPostfixOperation tmpNodeForUnaryPostFix = (MySQLUnaryPostfixOperation) node.right;
                        if (!judgeLeafNode(tmpNodeForUnaryPostFix.getExpression().getClass().getSimpleName())) {
                            addNodeToQueue(isAllLeafNodes, nodeQueue, tmpNodeForUnaryPostFix,
                                    tmpNodeForUnaryPostFix.getExpression());
                        }
                        break;

                    case "MySQLUnaryPrefixOperation":
                        MySQLUnaryPrefixOperation tmpNodeForUnaryPreFix = (MySQLUnaryPrefixOperation) node.right;
                        if (!judgeLeafNode(tmpNodeForUnaryPreFix.getExpression().getClass().getSimpleName())) {
                            addNodeToQueue(isAllLeafNodes, nodeQueue, tmpNodeForUnaryPreFix,
                                    tmpNodeForUnaryPreFix.getExpression());
                        }
                        break;

                }

                if (isAllLeafNodes == true) {
                    if (nodeQueue.isEmpty()) {
                        log.info("[{}] whereSQL Expr[{}] can't be simpilified", exprType,
                                whereCell.toString() + ":" + whereCell.getStatement().toString());
                        exprSimplifySelector.updateWeight(exprType, false);
                        reduceCountForEachLevel[3]++;
                        break;
                    }
                }
            }

        }
        return testCase;
    }

    private void addNodeToQueue(boolean isAllLeafNodes, LinkedList<Pair<MySQLExpression, MySQLExpression>> nodeQueue,
            MySQLExpression nodeParent, MySQLExpression nodeSon) {
        Pair<MySQLExpression, MySQLExpression> newNode = new Pair<>(nodeParent, nodeSon);
        nodeQueue.add(newNode);
        isAllLeafNodes = false;
    }

    private boolean judgeLeafNode(String nodeTypeName) {
        if (nodeTypeName.equals(WhereExprType.MySQLIntConstant.toString()) ||
                nodeTypeName.equals(WhereExprType.MySQLColumnReference.toString())) {
            return true;
        }
        return false;
    }

    private boolean judgeConstantNode(String nodeTypeName) {
        if (nodeTypeName.equals(WhereExprType.MySQLIntConstant.toString())) {
            return true;
        }
        return false;
    }

    private TestCase delWhereClause(TestCase testCase, OracleChecker oracleChecker, SimplifyType type) {
        TestCase clonedTestCase = testCaseClone(testCase);
        ArrayList<StatementCell> whereStmtList = new ArrayList<>();
        for (StatementCell whereStmt : clonedTestCase.tx1.getStatements()) {
            if (whereStmt.getWhereClause() != "")
                whereStmtList.add(whereStmt);
        }
        for (StatementCell whereStmt : clonedTestCase.tx2.getStatements()) {
            if (whereStmt.getWhereClause() != "")
                whereStmtList.add(whereStmt);
        }
        if (whereStmtList.size() == 0) {
            log.info("There is no whereSQL");
            stmtSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[2]++;
            return testCase;
        }
        int simpilifyIdx = (int) (Math.random() * whereStmtList.size());
        // int simpilifyIdx = 2;
        StatementCell whereCell = whereStmtList.get(simpilifyIdx);
        // StatementCell whereCellCopy = whereCell.copy();
        StringBuffer delWhereClause = new StringBuffer();
        delWhereClause.append(whereCell.getWherePrefix());
        delWhereClause.append(whereCell.getForPostFix());
        // 删除事务Tx1语句中where部分以及Predicate谓词
        whereCell.setStmt(delWhereClause.toString());
        whereCell.setPredicate(null);
        whereCell.setWhereClause("");

        if (oracleChecker.hasBug(clonedTestCase.toString())) {
            log.info("whereSQL [{}] simpilify success",
                    whereCell.toString() + ":" + whereCell.getStatement().toString());
            // 删除后仍能复现bug则更新测试用例
            testCase = testCaseClone(clonedTestCase);
            stmtSimplifySelector.updateWeight(type, true);
            validReduceCountForEachLevel[2]++;
            reduceCountForEachLevel[2]++;
        } else {
            log.info("whereSQL [{}] simpilify failed",
                    whereCell.toString() + ":" + whereCell.getStatement().toString());
            stmtSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[2]++;
        }
        return testCase;
    }

    private TestCase delInsertCol(TestCase testCase, OracleChecker oracleChecker, SimplifyType type) {
        TestCase clonedTestCase = testCaseClone(testCase);
        ArrayList<StatementCell> insertStmtList = new ArrayList<>();
        for (StatementCell insertStmt : clonedTestCase.prepareTableStmts) {
            if (insertStmt.getType() == StatementType.INSERT)
                insertStmtList.add(insertStmt);
        }
        for (StatementCell insertStmt : clonedTestCase.tx1.getStatements()) {
            if (insertStmt.getType() == StatementType.INSERT)
                insertStmtList.add(insertStmt);
        }
        for (StatementCell insertStmt : clonedTestCase.tx2.getStatements()) {
            if (insertStmt.getType() == StatementType.INSERT)
                insertStmtList.add(insertStmt);
        }
        if (insertStmtList.size() == 0) {
            log.info("There is no insertSQL");
            stmtSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[2]++;
            return testCase;
        }
        int simplifyIdx = (int) (Math.random() * insertStmtList.size());
        // int simplifyIdx = 6;
        StatementCell insertCell = insertStmtList.get(simplifyIdx);
        StatementCell insertCellCopy = insertCell.copy();
        // 随机找个insert语句
        String insertStmt = insertCellCopy.getStatement();
        // 取得列名括号对和值括号对的位置
        int columnLeftBracket = insertStmt.indexOf("(");
        int columnRightBracket = insertStmt.indexOf(")");
        int valueLeftBracket = insertStmt.lastIndexOf("(");
        int valueRightBracket = insertStmt.lastIndexOf(")");
        // 得到插入列
        String columnStr = insertStmt.substring(columnLeftBracket + 1, columnRightBracket);
        String[] columnWords = columnStr.split(",");
        // 得到插入值
        String valueStr = insertStmt.substring(valueLeftBracket + 1, valueRightBracket);
        String[] valueWords = valueStr.split(",");
        // 如果只有一列就不删了
        if (columnWords.length == 1) {
            log.info("insertSQL [{}] can't be simplified",
                    insertCell.toString() + ":" + insertCell.getStatement().toString());
            stmtSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[2]++;
            return testCase;
        }
        // 剩下的都是至少两列插入，一列删除
        int colLength = columnWords.length;
        int idx = (int) (Math.random() * colLength);
        // 如果要反复删除的话就恢复for循环
        // for (int idx = 0; idx < colLength - 1; idx++) {
        StringBuffer newColumnStr = new StringBuffer();
        StringBuffer newValueStr = new StringBuffer();
        for (int i = 0, cntVal = 0, cntCol = 0, cnt = 0; i < colLength; i++) {
            // 不补null的
            if (i == idx && idx == 0)
                continue;
            else if (i != idx && idx != 0) {
                if (i == 0) {
                    newValueStr.append(valueWords[i]);
                    newColumnStr.append(columnWords[i]);
                } else {
                    newValueStr.append("," + valueWords[i]);
                    newColumnStr.append("," + columnWords[i]);
                }
            } else if (i != idx && idx == 0) {
                if (cnt == 0) {
                    newValueStr.append(valueWords[i].substring(1));
                    newColumnStr.append(columnWords[i].substring(1));
                    cnt++;
                } else {
                    newValueStr.append("," + valueWords[i]);
                    newColumnStr.append("," + columnWords[i]);
                    cnt++;
                }
            }
            /*
             * 补null的
             * if(i==idx && idx==0){
             * newValueStr.append("null");
             * cntVal++;
             * }
             * else if(i==idx && idx!=0){
             * newValueStr.append(", null");
             * cntVal++;
             * }
             * else if(i!=idx && idx !=0){
             * if(cntCol==0 && cntVal == 0){
             * newValueStr.append(valueWords[i]);
             * newColumnStr.append(columnWords[i]);
             * cntVal++;
             * cntCol++;
             * }
             * else if(cntCol!=0){
             * newValueStr.append(","+valueWords[i]);
             * newColumnStr.append(","+columnWords[i]);
             * cntVal++;
             * cntCol++;
             * }
             * }
             * else if(i!=idx && idx == 0){
             * if(cntCol==0){
             * newValueStr.append(","+valueWords[i]);
             * newColumnStr.append(columnWords[i].substring(1));
             * cntVal++;
             * cntCol++;
             * }
             * else if(cntCol!=0){
             * newValueStr.append(","+valueWords[i]);
             * newColumnStr.append(","+columnWords[i]);
             * cntVal++;
             * cntCol++;
             * }
             * }
             */
        }
        StringBuffer newInsertStmt = new StringBuffer();
        // 将去掉某一列后的column列和value列与原先句子进行拼接 Pre里INSERT句子最后加个";"，事务里不需要
        newInsertStmt.append(insertStmt.substring(0, columnLeftBracket + 1));
        newInsertStmt.append(newColumnStr.toString());
        newInsertStmt.append(insertStmt.substring(columnRightBracket, valueLeftBracket + 1));
        newInsertStmt.append(newValueStr.toString());
        newInsertStmt.append(insertStmt.substring(valueRightBracket));
        insertCell.setStmt(newInsertStmt.toString());

        if (oracleChecker.hasBug(clonedTestCase.toString())) {
            log.info("insertSQL [{}] simpilify success",
                    insertCell.toString() + ":" + insertCell.getStatement().toString());
            stmtSimplifySelector.updateWeight(type, true);
            validReduceCountForEachLevel[2]++;
            reduceCountForEachLevel[2]++;
            // 删除后仍能复现bug则更新测试用例
            testCase = testCaseClone(clonedTestCase);
            return testCase;
            /*
             * 如果要反复删除的话就恢复这段注释
             * idx--;
             * insertCell = insertStmtList.get(simplifyIdx);
             */
        } else {
            log.info("insertSQL [{}] simpilify failed",
                    insertCell.toString() + ":" + insertCell.getStatement().toString());
            stmtSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[2]++;
            return testCase;
            /*
             * 如果要反复删除的话就恢复这段注释
             * insertCell = insertCellCopy;
             */
        }

        // 继续下一轮 把insertCell更新为最新的
        /*
         * 如果要反复删除的话就恢复这段注释
         * insertCellCopy = insertCell.copy();
         * // 随机找个insert语句
         * insertStmt = insertCellCopy.getStatement();
         * // 取得列名括号对和值括号对的位置
         * columnLeftBracket = insertStmt.indexOf("(");
         * columnRightBracket = insertStmt.indexOf(")");
         * valueLeftBracket = insertStmt.lastIndexOf("(");
         * valueRightBracket = insertStmt.lastIndexOf(")");
         * // 得到插入列
         * columnStr = insertStmt.substring(columnLeftBracket + 1, columnRightBracket);
         * columnWords = columnStr.split(",");
         * // 得到插入值
         * valueStr = insertStmt.substring(valueLeftBracket + 1, valueRightBracket);
         * valueWords = valueStr.split(",");
         * colLength = valueWords.length;
         */
        // }
        // return testCase;
    }

    private TestCase delUpdateCol(TestCase testCase, OracleChecker oracleChecker, SimplifyType type) {
        TestCase clonedTestCase = testCaseClone(testCase);
        ArrayList<StatementCell> updateStmtList = new ArrayList<>();
        for (StatementCell updateStmt : clonedTestCase.tx1.getStatements()) {
            if (updateStmt.getType() == StatementType.UPDATE)
                updateStmtList.add(updateStmt);
        }
        for (StatementCell updateStmt : clonedTestCase.tx2.getStatements()) {
            if (updateStmt.getType() == StatementType.UPDATE)
                updateStmtList.add(updateStmt);
        }
        if (updateStmtList.size() == 0) {
            log.info("There is no updateSQL");
            stmtSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[2]++;
            return testCase;
        }
        int simplifyIdx = (int) (Math.random() * updateStmtList.size());
        // int simplifyIdx = 0;
        StatementCell upCell = updateStmtList.get(simplifyIdx);
        StatementCell UpCellCopy = upCell.copy();
        if (UpCellCopy.getWhereClause() == "")
            UpCellCopy.setStmt(UpCellCopy.getStatement() + " WHERE");
        String upStmt = UpCellCopy.getStatement();
        // 将SET和WHERE转变成“@”，这样的话以第一个和最后一个“@”就会包含更新列项 将其subString提取出来
        String replacedUpStmt = upStmt.replace("SET", "WHERE");
        String finalReplacedUpStmt = replacedUpStmt.replace("WHERE", "@");
        // System.out.println(finalReplacedUpStmt);
        int firstCommaIdx = finalReplacedUpStmt.indexOf("@");
        int lastCommaIdx = finalReplacedUpStmt.lastIndexOf("@");
        // System.out.println("--------------------------------------------------------");
        // System.out.println(finalReplacedUpStmt);
        // System.out.println("--------------------------------------------------------");
        String updateStr = finalReplacedUpStmt.substring(firstCommaIdx + 1, lastCommaIdx - 1);
        // 获取更新列项
        String[] updateWords = updateStr.split(",");
        // 如果只有一个则不进行简化
        if (updateWords.length == 1) {
            log.info("updateSQL [{}] can't be simplified", upCell.toString() + ":" + upCell.getStatement().toString());
            stmtSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[2]++;
            return testCase;
        }
        int colLength = updateWords.length;
        int idx = (int) (Math.random() * colLength);
        // 要反复删除则恢复for循环注释
        // for (int idx = 0; idx < colLength - 1; idx++) {
        // 删除并重新拼接
        StringBuffer updateStmt = new StringBuffer();
        for (int i = 0, cnt = 0; i < updateWords.length; i++) {
            if (i == idx)
                continue;
            else {
                if (cnt == 0) {
                    updateStmt.append(updateWords[i]);
                    cnt++;
                } else {
                    updateStmt.append("," + updateWords[i]);
                }
            }
        }
        updateStmt.append(" ");
        StringBuffer replacedUpdateStr = new StringBuffer();
        StringBuffer replacedUpdateStrPrefix = new StringBuffer();

        replacedUpdateStr.append(finalReplacedUpStmt);
        replacedUpdateStr.replace(firstCommaIdx + 1, lastCommaIdx, updateStmt.toString());

        replacedUpdateStr.insert(replacedUpdateStr.toString().indexOf("@") + 1, "SET");
        replacedUpdateStrPrefix.append(replacedUpdateStr.toString());
        if (!upCell.getWhereClause().equals(""))
            replacedUpdateStr.insert(replacedUpdateStr.toString().lastIndexOf("@") + 1, "WHERE");

        replacedUpdateStr.deleteCharAt(replacedUpdateStr.toString().indexOf("@"));
        replacedUpdateStrPrefix.deleteCharAt(replacedUpdateStrPrefix.toString().indexOf("@"));
        replacedUpdateStr.deleteCharAt(replacedUpdateStr.toString().lastIndexOf("@"));

        upCell.setStmt(replacedUpdateStr.toString());
        upCell.setWherePrefix(
                replacedUpdateStrPrefix.substring(0, replacedUpdateStrPrefix.toString().lastIndexOf("@") - 1));
        if (oracleChecker.hasBug(clonedTestCase.toString())) {
            log.info("updateSQL [{}] simpilify success",
                    upCell.toString() + ":" + upCell.getStatement().toString());
            stmtSimplifySelector.updateWeight(type, true);
            validReduceCountForEachLevel[2]++;
            reduceCountForEachLevel[2]++;

            // 删除后仍能复现bug则更新测试用例
            testCase = testCaseClone(clonedTestCase);
            return testCase;
            /*
             * 如果要反复删除的话就恢复这段注释
             * idx--;
             * upCell = updateStmtList.get(simplifyIdx);
             */
        } else {
            log.info("updateSQL [{}] simpilify failed", upCell.toString() + ":" + upCell.getStatement().toString());
            stmtSimplifySelector.updateWeight(type, false);
            reduceCountForEachLevel[2]++;
            return testCase;
            /*
             * 如果要反复删除的话就恢复这段注释
             * upCell = UpCellCopy;
             */
        }
        /*
         * 如果要反复删除的话就恢复这段注释
         * UpCellCopy = upCell.copy();
         * if (UpCellCopy.getWhereClause() == "")
         * UpCellCopy.setStmt(UpCellCopy.getStatement() + " WHERE");
         * upStmt = UpCellCopy.getStatement();
         * // 将SET和WHERE转变成“@”，这样的话以第一个和最后一个“@”就会包含更新列项 将其subString提取出来
         * replacedUpStmt = upStmt.replace("SET", "WHERE");
         * finalReplacedUpStmt = replacedUpStmt.replace("WHERE", "@");
         * 
         * firstCommaIdx = finalReplacedUpStmt.indexOf("@");
         * lastCommaIdx = finalReplacedUpStmt.lastIndexOf("@");
         * updateStr = finalReplacedUpStmt.substring(firstCommaIdx + 1, lastCommaIdx -
         * 1);
         * // 获取更新列项
         * updateWords = updateStr.split(",");
         * colLength = updateWords.length;
         */
        // }

        // return testCase;
    }

    private StatementCell deleteStatement(TestCase testCase) {
        List<StatementType> excludedTypes = new ArrayList<>();
        // 遍历stmtTypeMap，将列表为空的类型加入excludedTypes
        for (Map.Entry<StatementType, ArrayList<StatementCell>> entry : stmtTypeMap.entrySet()) {
            if (entry.getValue().isEmpty()) {
                excludedTypes.add(entry.getKey());
            }
        }
        StatementType type = stmtDelOrderSelector.selectNext(excludedTypes);
        if (type == null) {
            return null;
        }
        ArrayList<StatementCell> stmts = stmtTypeMap.get(type);
        // System.out.println("待删除语句列表：" + stmts);
        // 随机抽取一个语句删除
        int idx = (int) (Math.random() * stmts.size());
        int txId = stmts.get(idx).getTx().getTxId();
        // 待删除语句
        if (txId == 1) {
            testCase.tx1.getStatements().remove(stmts.get(idx));
        } else if (txId == 2) {
            testCase.tx2.getStatements().remove(stmts.get(idx));
        } else {
            testCase.prepareTableStmts.remove(stmts.get(idx));
        }
        // 更新submittedOrder
        testCase.submittedOrder.remove(stmts.get(idx));
        // TODO: 目前不管本次删除是否合理，都将该语句从stmtTypeMap中删除，后续可以考虑删除失败的情况只对语句做标记。
        StatementCell delStmt = stmts.remove(idx);
        return delStmt;
    }

    /*
     * 从Scanner中读取事务
     */
    public static Transaction readTransaction(Scanner input, int txId) {
        Transaction tx = new Transaction(txId);
        String isolationAlias = input.nextLine();
        tx.setIsolationlevel(IsolationLevel.getFromAlias(isolationAlias));
        String sql;
        int cnt = 0;
        do {
            if (!input.hasNext())
                break;
            sql = input.nextLine();
            if (sql.equals("") || sql.equals("END"))
                break;
            tx.getStatements().add(new StatementCell(tx, cnt++, sql));
        } while (true);
        return tx;
    }

    /*
     * 从Scanner中读取提交顺序
     */
    public static String readSchedule(Scanner input) {
        do {
            if (!input.hasNext())
                break;
            String scheduleStr = input.nextLine();
            if (scheduleStr.equals(""))
                continue;
            if (scheduleStr.equals("END"))
                break;
            return scheduleStr;
        } while (true);
        return "";
    }

    /*
     * 解析提交顺序是否合法
     */
    public static ArrayList<StatementCell> parseSchedule(Transaction tx1, Transaction tx2, String scheduleStr) {
        String[] schedule = scheduleStr.split("-");
        int len1 = tx1.getStatements().size();
        int len2 = tx2.getStatements().size();
        if (schedule.length != len1 + len2) {
            throw new RuntimeException("Invalid Schedule");
        }
        ArrayList<StatementCell> submittedOrder = new ArrayList<>();
        int idx1 = 0, idx2 = 0;
        for (String txId : schedule) {
            if (txId.equals("1")) {
                submittedOrder.add(tx1.getStatements().get(idx1++));
            } else if (txId.equals("2")) {
                submittedOrder.add(tx2.getStatements().get(idx2++));
            } else {
                throw new RuntimeException("Invalid Schedule");
            }
        }
        return submittedOrder;
    }

    /*
     * 解析测试用例
     */
    public static TestCase parse(String tc) {
        // 从字符串读取事务
        Scanner input = new Scanner(tc);
        // 读取建表语句
        String sql;
        TestCase testCase = new TestCase();
        sql = input.nextLine();
        testCase.createStmt = new StatementCell(null, -1, sql);
        int i = 0;
        do {
            sql = input.nextLine();
            if (sql.equals(""))
                break;
            testCase.prepareTableStmts.add(new StatementCell(new Transaction(0), i, sql));
            i++;
        } while (true);
        // 读取其他语句
        testCase.tx1 = readTransaction(input, 1);
        testCase.tx2 = readTransaction(input, 2);
        // 读取提交顺序
        String scheduleStr = readSchedule(input);
        testCase.submittedOrder = parseSchedule(testCase.tx1, testCase.tx2, scheduleStr);
        input.close();
        return testCase;
    }
}
