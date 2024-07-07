package fucci;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Scanner;

import com.beust.jcommander.JCommander;

import lombok.extern.slf4j.Slf4j;
import fucci.common.Table;
import fucci.reducer.TestCase;

@Slf4j
public class Main {
    public static void main(String[] args) {
        // // create a CharStream that reads from standard input
        // String input = " b NOT IN (1, CAST(b AS UNSIGNED))";
        // // create a lexer that feeds off of input CharStream
        // MySQLExpressionLexer lexer = new
        // MySQLExpressionLexer(CharStreams.fromString(input));
        // // create a buffer of tokens pulled from the lexer
        // CommonTokenStream tokens = new CommonTokenStream(lexer);
        // // create a parser that feeds off the tokens buffer
        // MySQLExpressionParser parser = new MySQLExpressionParser(tokens);
        // ParseTree tree = parser.expression(); // begin parsing at expression rule
        // MySQLExpressionVisitorImpl visitor = new MySQLExpressionVisitorImpl();
        // MySQLExpression expression = visitor.visit(tree);
        // System.out.println(MySQLVisitor.asString(expression));
        // // System.out.println(tree.toStringTree(parser)); // print LISP-style tree

        // 手动构造一个谓词，验证求解结果
        // MySQLExpression constant1 = new MySQLIntConstant(0);
        // MySQLExpression constant2 = new MySQLStringConstant("0.5");
        // MySQLExpression expression = new MySQLBinaryOperation(constant1, constant2,
        // MySQLBinaryOperator.XOR);
        // create a CharStream that reads from standard input
        /*
         * String input =
         * "(((c2) AND ((- (-1962991450)))) NOT IN ((+ ((+ (770305223)))))) ^ ((((1095947040) NOT IN (-1427686796)) BETWEEN (408773806) AND ((NULL) | (NULL))) IS NULL)"
         * ;
         * // create a lexer that feeds off of input CharStream
         * MySQLExpressionLexer lexer = new
         * MySQLExpressionLexer(CharStreams.fromString(input));
         * // create a buffer of tokens pulled from the lexer
         * CommonTokenStream tokens = new CommonTokenStream(lexer);
         * // create a parser that feeds off the tokens buffer
         * MySQLExpressionParser parser = new MySQLExpressionParser(tokens);
         * // 这里得到的ParseTree是通用AST
         * ParseTree tree = parser.expression(); // begin parsing at expression rule
         * MySQLExpressionVisitorImpl visitor = new MySQLExpressionVisitorImpl();
         * 
         * // 通过调用visit函数将通用AST转换成自定义AST，自定义AST中的节点类型都是预定义好的
         * // 这个地方可以调试看MySQLExpression的结构
         * MySQLExpression expression = visitor.visit(tree);
         * System.out.println(MySQLVisitor.asString(expression));
         * // 求解expression
         * // MySQLExpression result = expression.getExpectedValue(null);
         */
        Options options = new Options();
        JCommander jCmd = new JCommander();
        jCmd.addObject(options);
        jCmd.parse(args);
        verifyOptions(options);
        log.info(String.format("Run tests for %s in [DB %s]-[Table %s] on [%s:%d]",
                options.getDBMS(), options.getDbName(), options.getTableName(),
                options.getHost(), options.getPort()));

        txnTesting(options);
        TableTool.cleanFucciTables();

    }

    private static void txnTesting(Options options) {
        TableTool.initialize(options);
        Transaction tx1, tx2;

        if (options.isSetCase()) {
            // 从文件或命令行读取事务
            Scanner scanner;
            if (options.getCaseFile().equals("")) {
                log.info("Read database and transactions from command line");
                scanner = new Scanner(System.in);
            } else {
                try {
                    File caseFile = new File(options.getCaseFile());
                    scanner = new Scanner(caseFile);
                    log.info("Read database and transactions from file: {}", options.getCaseFile());
                } catch (FileNotFoundException e) {
                    throw new RuntimeException("Read case from file failed: ", e);
                }
            }
            Table table = TableTool.dbms.buildTable(options.getTableName());
            TestCase testCase = new TestCase();
            // 执行文件中或命令行输入的建表语句
            TableTool.prepareTableFromScanner(scanner, table);
            // 对表进行预处理
            TableTool.preProcessTable();
            testCase.createStmt = new StatementCell(null, -1, table.getCreateTableSql());
            for (String sql : table.getInitializeStatements()) {
                testCase.prepareTableStmts.add(new StatementCell(new Transaction(0), -1, sql));
            }
            log.info("Initial table:\n{}", TableTool.tableToView());
            // 读取两个事务
            tx1 = TableTool.readTransactionFromScanner(scanner, 1);
            tx2 = TableTool.readTransactionFromScanner(scanner, 2);
            testCase.tx1 = tx1;
            testCase.tx2 = tx2;
            // 读取提交顺序
            String scheduleStr = TableTool.readScheduleFromScanner(scanner);
            scanner.close();
            log.info("Read transactions from file:\n{}{}", tx1, tx2);
            TableTool.txPair++;
            FucciChecker checker = new FucciChecker(tx1, tx2);
            if (!scheduleStr.equals("")) {
                log.info("Get schedule from file: {}", scheduleStr);
                // 根据读取的提交顺序进行check
                checker.checkSchedule(scheduleStr, testCase);
            } else {
                checker.checkAll();
            }
        } else {
            while (true) {
                // 循环fuzzing
                log.info("Create new table.");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
                Table table = TableTool.dbms.buildTable(options.getTableName());
                table.initialize();
                // 建表及插入语句已保证同步
                if (table.getInitRowCount() == 0) {
                    log.info("Table is empty, skip.");
                    continue;
                }
                log.info(table.getCreateTableSql());
                log.info("InitializeStatements: {}", table.getInitializeStatements());
                // 这个地方已经创建好表了，并填充数据了
                TableTool.preProcessTable();
                TableTool.bugReport.setCreateTableSQL(table.getCreateTableSql());
                TableTool.bugReport.setInitializeStatements(table.getInitializeStatements());
                TableTool.bugReport.setInitialTable(TableTool.tableToView().toString());
                log.info("Initial table:\n{}", TableTool.tableToView());
                for (int _i = 0; _i < 5; _i++) {
                    log.info("Generate new transaction pair.");
                    TableTool.txPairHasConflict = false;
                    TableTool.txPair++;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                    TestCase testCase = new TestCase();
                    testCase.createStmt = new StatementCell(null, -1, table.getCreateTableSql());
                    for (String sql : table.getInitializeStatements()) {
                        testCase.prepareTableStmts.add(new StatementCell(new Transaction(0), -1, sql));
                    }
                    // 恢复原始table
                    TableTool.recoverOriginalTable();
                    // 生成两个事务
                    tx1 = table.genTransaction(1);
                    tx2 = table.genTransaction(2);
                    testCase.tx1 = tx1;
                    testCase.tx2 = tx2;
                    TableTool.recoverOriginalTable();
                    // 手动构建冲突
                    log.info("Before make conflict------------------------");
                    log.info("Transaction 1:\n{}", tx1);
                    log.info("Transaction 2:\n{}", tx2);
                    TableTool.makeConflict(tx1, tx2, table);
                    TableTool.bugReport.setTx1(tx1);
                    TableTool.bugReport.setTx2(tx2);
                    log.info("After make conflict------------------------");
                    log.info("Transaction 1:\n{}", tx1);
                    log.info("Transaction 2:\n{}", tx2);
                    FucciChecker checker = new FucciChecker(tx1, tx2);
                    // 随机生成提交顺序
                    checker.checkRandom(testCase);
                    log.info("submitOrderCountBeforeFilter:{}, submitOrderCountAfterFilter:{}",
                            TableTool.submitOrderCountBeforeFilter, TableTool.submitOrderCountAfterFilter);
                    if (TableTool.txPairHasConflict) {
                        TableTool.conflictTxPair++;
                    }
                    try {
                        tx1.conn.close();
                        tx2.conn.close();
                    } catch (SQLException e) {
                        log.info("Close connection failed.");
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static void verifyOptions(Options options) {
        options.setDBMS(options.getDBMS().toUpperCase());
        if (Arrays.stream(DBMS.values()).map(DBMS::name).noneMatch(options.getDBMS()::equals)) {
            throw new RuntimeException("Unknown DBMS: " + options.getDBMS());
        }
    }
}
