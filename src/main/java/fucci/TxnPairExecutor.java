package fucci;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TxnPairExecutor {

    private final ArrayList<StatementCell> submittedOrder;
    private final Transaction tx1;
    private final Transaction tx2;

    private TxnPairResult result;
    private ArrayList<StatementCell> actualSchedule;
    private ArrayList<Object> finalState;

    private boolean isDeadLock = false;
    private boolean isSematicError = false;
    private boolean isSyntaxError = false;
    private boolean isRestart = false;
    private boolean timeout = false;
    private String exceptionMessage = "";
    private final Map<Integer, Boolean> txnAbort = new HashMap<>();
    private boolean ref = false;

    public TxnPairExecutor(ArrayList<StatementCell> schedule, Transaction tx1, Transaction tx2, boolean ref) {
        this.submittedOrder = schedule;
        this.tx1 = tx1;
        this.tx2 = tx2;
        this.ref = ref;
    }

    TxnPairResult getResult() {
        if (result == null) {
            execute();
            result = new TxnPairResult(actualSchedule, finalState, isDeadLock, isSematicError, isSyntaxError);
        }
        return result;
    }

    private void execute() {
        TableTool.setIsolationLevel(tx1);
        TableTool.setIsolationLevel(tx2);
        if (ref) {
            // 如果参照数据库是TiDB，也需要设置对应隔离级别
            String anotherDBMS = TableTool.refMap.get(TableTool.getDbms().name());
            if ("TIDB".equals(anotherDBMS)) {
                TableTool.executeWithConn(tx1.refConn, "SET SESSION tidb_txn_mode = 'pessimistic'");
                TableTool.executeWithConn(tx2.refConn, "SET SESSION tidb_txn_mode = 'pessimistic'");
                String sql1 = "SET SESSION TRANSACTION ISOLATION LEVEL " + tx1.isolationlevel.getName();
                TableTool.executeWithConn(tx1.refConn, sql1);
                String sql2 = "SET SESSION TRANSACTION ISOLATION LEVEL " + tx2.isolationlevel.getName();
                TableTool.executeWithConn(tx2.refConn, sql2);
            }
        }
        actualSchedule = new ArrayList<>();
        txnAbort.put(1, false);
        txnAbort.put(2, false);
        // SynchronousQueue不存储元素，每一次put都会阻塞，直到另一个线程take
        BlockingQueue<StatementCell> queue1 = new SynchronousQueue<>();
        BlockingQueue<StatementCell> queue2 = new SynchronousQueue<>();
        BlockingQueue<StatementCell> communicationID = new SynchronousQueue<>();
        Thread producer = new Thread(new Producer(queue1, queue2, submittedOrder, communicationID));
        Thread consumer1 = new Thread(new Consumer(1, queue1, communicationID, submittedOrder.size()));
        Thread consumer2 = new Thread(new Consumer(2, queue2, communicationID, submittedOrder.size()));
        producer.start();
        consumer1.start();
        consumer2.start();
        try {
            // 等待producer子线程返回
            producer.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        finalState = TableTool.getQueryResultAsList("SELECT * FROM " + TableTool.TableName);
    }

    /**
     * Producer按照schedule中的顺序给两个队列分发任务，如果队列阻塞则将任务放入一个阻塞队列中
     * 1.对于 schedule 中的每个任务，首先检查对应的线程是否被阻塞。如果被阻塞，那么将任务添加到阻塞任务列表中，然后继续处理下一个任务。
     * 2.如果线程没有被阻塞，那么将任务添加到对应的队列中，然后等待线程的反馈。
     * 3.如果线程在一定时间内没有反馈，那么认为线程被阻塞，将任务添加到阻塞任务列表中。
     * 4.如果线程有反馈，那么根据反馈的内容处理任务。如果任务是提交或回滚操作，那么还需要处理另一个线程的阻塞情况。
     * 5.如果出现死锁，那么停止所有的事务，并结束线程的运行。
     * 6.在所有的任务都处理完之后，发送停止信号给所有的线程。
     */
    class Producer implements Runnable {
        private final BlockingQueue<StatementCell> queue1;
        private final BlockingQueue<StatementCell> queue2;
        private final ArrayList<StatementCell> schedule;
        private final BlockingQueue<StatementCell> communicationID; // represent the execution feedback

        public Producer(BlockingQueue<StatementCell> queue1, BlockingQueue<StatementCell> queue2,
                ArrayList<StatementCell> schedule, BlockingQueue<StatementCell> communicationID) {
            this.queue1 = queue1;
            this.queue2 = queue2;
            this.schedule = schedule;
            this.communicationID = communicationID;
        }

        private boolean needBreak(String exceptionMessage, int txn, StatementCell statementCell) {
            if (exceptionMessage.contains("an error in your SQL syntax")) {
                // 说明本次执行的语句语法错误，直接结束本次测试
                isSyntaxError = true;
                return true;
            }
            if (exceptionMessage.contains("Data truncation") || exceptionMessage.contains("Duplicate entry")) {
                // 说明本次执行的语句语义错误，直接结束本次测试
                isSematicError = true;
                return true;
            }

            if (exceptionMessage.contains("Deadlock") || exceptionMessage.contains("lock=true")) { // deadlock
                log.info("exceptionMessage: {}", exceptionMessage);
                log.info(" -- DeadLock happened(2)");
                isDeadLock = true;
                return true;
            }
            if (exceptionMessage.contains("restart") || exceptionMessage.contains("aborted")
                    || exceptionMessage.contains("TransactionRetry")) {
                txnAbort.put(txn, true);
                statementCell.aborted = true;
                isRestart = true;
                return true;
            }
            return false;
        }

        public void run() {
            Map<Integer, Boolean> txnBlock = new HashMap<>(); // whether a child thread is blocked
            txnBlock.put(1, false);
            txnBlock.put(2, false);
            Map<Integer, ArrayList<StatementCell>> blockedStmts = new HashMap<>(); // record blocked statements
            blockedStmts.put(1, null);
            blockedStmts.put(2, null);
            Map<Integer, BlockingQueue<StatementCell>> queues = new HashMap<>(); // record queues
            queues.put(1, queue1);
            queues.put(2, queue2);
            int queryID; // statement ID
            long startTs; // record time threshold
            timeout = false;
            isDeadLock = false;
            out: for (queryID = 0; queryID < schedule.size(); queryID++) {
                int txn = schedule.get(queryID).tx.txId;
                StatementCell statementCell = schedule.get(queryID).copy();
                int otherTxn = (txn == 1) ? 2 : 1;
                // 如果当前事务被阻塞，则将当前语句放入阻塞队列中
                if (txnBlock.get(txn)) { // if a child thread is blocked
                    // log.info("txn {} is blocked, add query {} to blockedStmts", txn,
                    // statementCell);
                    ArrayList<StatementCell> stmts = blockedStmts.get(txn);
                    stmts.add(statementCell);
                    blockedStmts.put(txn, stmts); // save its statements 这里似乎不太必要
                    continue;
                }
                try {
                    // 当前事务未被阻塞，将当前语句放入队列中
                    queues.get(txn).put(statementCell);
                    // log.info("add query {} to queue {}", statementCell, txn);
                } catch (InterruptedException e) {
                    log.info(" -- MainThread run exception");
                    log.info("Query: " + statementCell.statement);
                    log.info("Interrupted Exception: " + e.getMessage());
                }
                // 等待语句执行结果
                StatementCell queryReturn = null;
                try {
                    queryReturn = communicationID.poll(2000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    log.info(" -- MainThread run exception");
                    log.info("Query: " + statementCell.statement);
                    log.info("Interrupted Exception: " + e.getMessage());
                }
                // log.info("current query: {}, queryReturn: {}", statementCell, queryReturn);
                if (queryReturn == null) {
                    log.info(txn + "-" + statementCell.statementId + ": time out");
                    txnBlock.put(txn, true); // record blocked transaction
                    StatementCell blockPoint = statementCell.copy();
                    blockPoint.blocked = true;
                    actualSchedule.add(blockPoint);
                    ArrayList<StatementCell> stmts = new ArrayList<>();
                    stmts.add(statementCell);
                    blockedStmts.put(txn, stmts);
                } else {
                    // 需要查看queryReturn的exceptionMessage
                    String curExceptiommMessage = queryReturn.exceptionMessage;
                    if (curExceptiommMessage.length() > 0) {
                        if (needBreak(curExceptiommMessage, txn, queryReturn)) {
                            break out;
                        }
                    }
                    if ((statementCell.type == StatementType.COMMIT || statementCell.type == StatementType.ROLLBACK)
                            && txnBlock.get(otherTxn)) {
                        // 如果当前语句是提交或回滚语句，且另一个事务被阻塞，那么当前语句执行完成后，另一个事务会取消阻塞
                        StatementCell nextReturn = null;
                        try {
                            nextReturn = communicationID.poll(2000, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            log.info(" -- MainThread run exception");
                            log.info("Query: " + statementCell.statement);
                            log.info("Interrupted Exception: " + e.getMessage());
                        }
                        ;
                        // log.info("current query: {}, nextReturn: {}", statementCell, nextReturn);
                        if (nextReturn == null) {
                            log.info(" -- " + txn + "." + statementCell.statementId + ": time out");
                            timeout = true;
                            log.info(" -- next return failed: " + statementCell.statement);
                            break;
                        } else {
                            String nextExceptiommMessage = nextReturn.exceptionMessage;
                            if (nextExceptiommMessage.length() > 0) {
                                if (needBreak(nextExceptiommMessage, txn, queryReturn)) {
                                    break out;
                                }
                            }
                            if (queryReturn.toString().equals(statementCell.toString())) {
                                statementCell.result = queryReturn.result;
                                blockedStmts.get(otherTxn).get(0).result = nextReturn.result;
                            } else {
                                statementCell.result = nextReturn.result;
                                blockedStmts.get(otherTxn).get(0).result = queryReturn.result;
                            }
                            actualSchedule.add(statementCell);
                            actualSchedule.add(blockedStmts.get(otherTxn).get(0));
                        }
                    } else if (queryReturn.toString().equals(statementCell.toString())) {
                        // 收到的反馈就是当前语句
                        statementCell.result = queryReturn.result;
                        actualSchedule.add(statementCell);
                    } else {
                        // 如果收到的结果不是当前语句的结果，且本事务也没提交，那就说明出现了阻塞。
                        isDeadLock = true;
                        log.info(" -- DeadLock happened(1)");
                        statementCell.blocked = true;
                        actualSchedule.add(statementCell);
                        // 直接break跳出out标记的for循环
                        break out;
                    }
                }
                if ((statementCell.type == StatementType.COMMIT
                        || statementCell.type == StatementType.ROLLBACK) && !exceptionMessage.contains("Deadlock")
                        && !exceptionMessage.contains("lock=true")
                        && !(txnBlock.get(1) && txnBlock.get(2))) {
                    // 将另一个事务的阻塞状态取消
                    txnBlock.put(otherTxn, false);
                    if (blockedStmts.get(otherTxn) != null) {
                        for (int j = 1; j < blockedStmts.get(otherTxn).size(); j++) {
                            StatementCell blockedStmtCell = blockedStmts.get(otherTxn).get(j);
                            try {
                                // 将另一个事务的阻塞语句放入队列中
                                queues.get(otherTxn).put(blockedStmtCell);
                            } catch (InterruptedException e) {
                                log.info(" -- MainThread blocked exception");
                                log.info("Query: " + statementCell.statement);
                                log.info("Interrupted Exception: " + e.getMessage());
                            }
                            StatementCell blockedReturn = null;
                            try {
                                blockedReturn = communicationID.poll(2000, TimeUnit.MILLISECONDS);
                            } catch (InterruptedException e) {
                                log.info(" -- MainThread blocked exception");
                                log.info("Query: " + statementCell.statement);
                                log.info("Interrupted Exception: " + e.getMessage());
                            }
                            // log.info("current query: {}, blockedReturn: {}", statementCell,
                            // blockedReturn);
                            if (blockedReturn == null) {
                                log.info(" -- " + txn + "." + statementCell.statementId + ": still time out");
                                timeout = true;
                            } else {
                                String blockedExceptiommMessage = blockedReturn.exceptionMessage;
                                if (blockedExceptiommMessage.length() > 0) {
                                    if (needBreak(blockedExceptiommMessage, txn, queryReturn)) {
                                        break out;
                                    }
                                }
                                blockedStmtCell.result = blockedReturn.result;
                                actualSchedule.add(blockedStmtCell);
                            }
                        }
                    }
                }
                // 出现死锁/超时
                if ((txnBlock.get(1) && txnBlock.get(2)) || timeout) {
                    if (txnBlock.get(1) && txnBlock.get(2)) { // deadlock
                        log.info("exceptionMessage: {}", exceptionMessage);
                        log.info(" -- DeadLock happened(2)");
                        isDeadLock = true;
                        break out;
                    }
                }
            }
            // 发生死锁时，跳出for循环
            if (isDeadLock || isSematicError || isSyntaxError || isRestart || timeout) {
                try {
                    tx1.conn.createStatement().executeUpdate("ROLLBACK"); // stop transaction
                    tx2.conn.createStatement().executeUpdate("ROLLBACK");
                    if (tx1.refConn != null) {
                        tx1.refConn.createStatement().executeUpdate("ROLLBACK");
                    }
                    if (tx2.refConn != null) {
                        tx2.refConn.createStatement().executeUpdate("ROLLBACK");
                    }
                } catch (SQLException e) {
                    log.info(" -- Deadlock Commit Failed");
                }
                log.info(" -- schedule execute failed");
            }
            StatementCell stopThread1 = new StatementCell(tx1, schedule.size());
            StatementCell stopThread2 = new StatementCell(tx2, schedule.size());
            try {
                // 这里需要确保子线程都已经将结果放入communicationID
                for (StatementCell s = communicationID.poll(); s != null; s = communicationID.poll()) {
                    log.info("poll from communicationID: {}", s);
                }
                // communicationID.take();
            } catch (Exception ignored) {
            }
            // 把线程1和线程2终止
            // queue1.put(stopThread1); // 通过阻塞队列通知其他线程终止
            // queue2.put(stopThread2);
            while (!queue1.offer(stopThread1)) {
                // 如果添加元素失败, 说明消费者线程阻塞了, 需要疏通一下communicationID
                communicationID.poll();
            }
            while (!queue2.offer(stopThread2)) {
                // 如果添加元素失败, 说明消费者线程阻塞了, 需要疏通一下communicationID
                communicationID.poll();
            }
        }
    }

    /**
     * Consumer 类的 run 方法是线程的主体函数，它从队列中获取任务并执行，然后将执行结果反馈给主线程。
     * 1.在一个无限循环中，首先从队列中获取一个任务。
     * 2.如果任务的ID大于或等于 scheduleCount，那么跳出循环，结束线程的运行。
     * 3.执行任务，如果任务是查询操作，那么获取查询结果；如果任务是更新操作，那么执行更新。
     * 4.如果执行过程中出现异常，那么记录异常信息。
     * 5.将执行结果放入 communicationID 队列中，通知主线程。
     */
    class Consumer implements Runnable {
        private final BlockingQueue<StatementCell> queue;
        private final BlockingQueue<StatementCell> communicationID; // represent the execution feedback
        private final int scheduleCount;
        private final int consumerId;

        public Consumer(int consumerId, BlockingQueue<StatementCell> queue,
                BlockingQueue<StatementCell> communicationID, int scheduleCount) {
            this.consumerId = consumerId;
            this.queue = queue;
            this.communicationID = communicationID;
            this.scheduleCount = scheduleCount;
        }

        public void run() {
            try {
                while (true) {
                    StatementCell stmt = queue.take(); // communicate with main thread
                    if (stmt.statementId >= scheduleCount) {
                        break; // stop condition: schedule.size()
                    }
                    // execute a query
                    String query = stmt.statement;
                    try {
                        SQLConnection conn = null;
                        if (ref) {
                            conn = stmt.tx.refConn;
                        } else {
                            conn = stmt.tx.conn;
                        }
                        if (stmt.type == StatementType.SELECT || stmt.type == StatementType.SELECT_SHARE
                                || stmt.type == StatementType.SELECT_UPDATE) {

                            stmt.result = TableTool.getQueryResultAsListWithException(conn, query);
                        } else {
                            conn.createStatement().executeUpdate(query);
                        }
                        stmt.exceptionMessage = "";
                    } catch (SQLException e) {
                        log.info(" -- TXNThread threadExec exception");
                        log.info("Query {}: {}", stmt, query);
                        stmt.exceptionMessage = e.getMessage();
                        log.info("SQL Exception: " + stmt.exceptionMessage);
                        stmt.exceptionMessage = stmt.exceptionMessage + "; [Query] " + query;
                    } finally {
                        try {
                            // communicationID是一个阻塞队列，用于两个消费者与生产者进行通信
                            // 线程2在这个地方阻塞，导致一直无法结束
                            // log.info("add result of {} to communicationID", stmt);
                            communicationID.put(stmt); // communicate to main thread
                        } catch (InterruptedException e) { // communicationID.put()
                            log.info(" -- TXNThread threadExec exception");
                            log.info("Query {}: {}", stmt, query);
                            log.info("Interrupted Exception: " + e.getMessage());
                        }
                    }
                }
            } catch (InterruptedException e) {
                // thread stop
                log.info(" -- TXNThread run Interrupted exception: " + e.getMessage());
            }
        }
    }
}
