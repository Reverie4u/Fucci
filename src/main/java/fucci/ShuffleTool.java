package fucci;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ShuffleTool {
    public static ArrayList<ArrayList<StatementCell>> genAllSubmittedTrace(Transaction tx1, Transaction tx2) {
        int n1 = tx1.statements.size(), n2 = tx2.statements.size();
        ArrayList<ArrayList<StatementCell>> res = new ArrayList<>();
        shuffle(res, new ArrayList<>(), tx1.statements, n1, 0, tx2.statements, n2, 0);
        // log.info("before filter: {}, size: {}", res, res.size());
        // 这里添加过滤逻辑
        TableTool.submitOrderCountBeforeFilter += res.size();
        if (TableTool.isFilterSubmittedOrder) {
            res = filterSubmittedOrder(res, tx1, tx2);
            TableTool.submitOrderCountAfterFilter += res.size();
        }
        // log.info("after filter: {}, size:{}", res, res.size());
        return res;
    }

    private static ArrayList<ArrayList<StatementCell>> filterSubmittedOrder(ArrayList<ArrayList<StatementCell>> res,
            Transaction tx1, Transaction tx2) {
        ArrayList<ArrayList<StatementCell>> filtered = new ArrayList<>();
        ArrayList<StatementCell> invalid1 = new ArrayList<>();
        ArrayList<StatementCell> invalid2 = new ArrayList<>();
        for (int i = 0; i < tx1.statements.size(); i++) {
            invalid1.add(new StatementCell(new Transaction(1), i));
        }
        for (int i = 0; i < tx2.statements.size(); i++) {
            invalid1.add(new StatementCell(new Transaction(2), i));
        }
        for (int i = 0; i < tx2.statements.size(); i++) {
            invalid2.add(new StatementCell(new Transaction(2), i));
        }
        for (int i = 0; i < tx1.statements.size(); i++) {
            invalid2.add(new StatementCell(new Transaction(1), i));
        }
        for (ArrayList<StatementCell> trace : res) {
            String traceStr = trace.toString();
            // 去除事务没有交叉的提交顺序
            if (trace.toString().equals(invalid1.toString()) || trace.toString().equals(invalid2.toString())) {
                continue;
            }
            // 去除不符合异常历史的提交顺序
            if (!containAnomalousHistory(trace, tx1, tx2)) {
                continue;
            }
            // 将两个连续的begin统一变成begin(tx1) begin(tx2)
            if (trace.get(0).statementId == 0 && trace.get(1).statementId == 0) {
                if (trace.get(0).tx.txId == 2) {
                    // 交换一下
                    StatementCell tmp = trace.get(0);
                    trace.set(0, trace.get(1));
                    trace.set(1, tmp);
                }
            }
            filtered.add(trace);
        }
        // 对ArrayList<ArrayList<StatementCell>>去重
        Set<String> set = new HashSet<>();
        ArrayList<ArrayList<StatementCell>> result = new ArrayList<>();
        for (ArrayList<StatementCell> trace : filtered) {
            String traceStr = trace.toString();
            if (!set.contains(traceStr)) {
                set.add(traceStr);
                result.add(trace);
            }
        }
        return result;
    }

    private static boolean containAnomalousHistory(ArrayList<StatementCell> trace, Transaction tx1, Transaction tx2) {
        // 冲突语句必须要在两个提交前面
        if (trace == null || trace.isEmpty()) {
            return false;
        }
        if (tx1.conflictStmtId == -1 || tx2.conflictStmtId == -1) {
            // 没有构造冲突，不需要考虑
            // 找到两个事务的第一条非begin语句,即提交语句的位置
            int tx1FirstIdx = -1, tx2FirstIdx = -1, tx1CommitIdx = -1, tx2CommitIdx = -1;
            // 找到冲突语句及提交语句的位置
            for (int i = 0; i < trace.size(); i++) {
                StatementCell stmt = trace.get(i);
                if (stmt.tx.txId == 1) {
                    if (stmt.statementId == 1) {
                        tx1FirstIdx = i;
                    } else if (stmt.statementId == tx1.statements.size() - 1) {
                        tx1CommitIdx = i;
                    }
                } else if (stmt.tx.txId == 2) {
                    if (stmt.statementId == 1) {
                        tx2FirstIdx = i;
                    } else if (stmt.statementId == tx2.statements.size() - 1) {
                        tx2CommitIdx = i;
                    }
                }
            }
            return Math.max(tx1FirstIdx, tx2FirstIdx) < Math.min(tx1CommitIdx, tx2CommitIdx);
        }
        int tx1ConflictIdx = -1, tx2ConflictIdx = -1, tx1CommitIdx = -1, tx2CommitIdx = -1;
        // 找到冲突语句及提交语句的位置
        for (int i = 0; i < trace.size(); i++) {
            StatementCell stmt = trace.get(i);
            if (stmt.tx.txId == 1) {
                if (stmt.statementId == tx1.conflictStmtId) {
                    tx1ConflictIdx = i;
                }
                if (stmt.statementId == tx1.statements.size() - 1) {
                    tx1CommitIdx = i;
                }
            } else if (stmt.tx.txId == 2) {
                if (stmt.statementId == tx2.conflictStmtId) {
                    tx2ConflictIdx = i;
                }
                if (stmt.statementId == tx2.statements.size() - 1) {
                    tx2CommitIdx = i;
                }
            }
        }
        return Math.max(tx1ConflictIdx, tx2ConflictIdx) < Math.min(tx1CommitIdx, tx2CommitIdx);
    }

    // 枚举生成所有可能的提交顺序
    public static void shuffle(ArrayList<ArrayList<StatementCell>> res, ArrayList<StatementCell> cur,
            ArrayList<StatementCell> txn1, int txn1Len, int txn1Idx, ArrayList<StatementCell> txn2,
            int txn2Len, int txn2Idx) {
        if (txn1Idx == txn1Len && txn2Idx == txn2Len) {
            res.add(new ArrayList<>(cur));
            return;
        }
        if (txn1Idx < txn1Len) {
            cur.add(txn1.get(txn1Idx));
            shuffle(res, cur, txn1, txn1Len, txn1Idx + 1, txn2, txn2Len, txn2Idx);
            cur.remove(cur.size() - 1);
        }
        if (txn2Idx < txn2Len) {
            cur.add(txn2.get(txn2Idx));
            shuffle(res, cur, txn1, txn1Len, txn1Idx, txn2, txn2Len, txn2Idx + 1);
            cur.remove(cur.size() - 1);
        }
    }

    public static ArrayList<ArrayList<StatementCell>> sampleSubmittedTrace(Transaction tx1, Transaction tx2,
            int count) {
        // 蓄水池抽样算法，从所有可能的提交顺序中选择count个
        ArrayList<ArrayList<StatementCell>> allSubmittedTrace = genAllSubmittedTrace(tx1, tx2);
        int n = allSubmittedTrace.size();
        if (n <= count) {
            return allSubmittedTrace;
        }
        ArrayList<ArrayList<StatementCell>> res = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            res.add(allSubmittedTrace.get(i));
        }
        for (int i = count; i < n; i++) {
            int d = new Random().nextInt(i + 1);
            if (d < count) {
                res.set(d, allSubmittedTrace.get(i));
            }
        }
        return res;
    }

    public static ArrayList<ArrayList<StatementCell>> genRandomSubmittedTrace(Transaction tx1, Transaction tx2,
            int count) {
        int tx1Len = tx1.statements.size(), tx2Len = tx2.statements.size();
        if (C(tx1Len + tx2Len, tx1Len) <= count * 1.3) {
            return genAllSubmittedTrace(tx1, tx2);
        }
        ArrayList<ArrayList<StatementCell>> res = new ArrayList<>(count);
        HashSet<String> generated = new HashSet<>();
        for (int i = 0; i < count; i++) {
            ArrayList<StatementCell> temp = new ArrayList<>();
            StringBuilder order = new StringBuilder();
            int tx1Idx = 0, tx2Idx = 0;
            while (true) {
                if (tx1Idx == tx1Len && tx2Idx == tx2Len) {
                    String orderStr = order.toString();
                    if (!generated.contains(orderStr)) {
                        res.add(temp);
                        generated.add(orderStr);
                    }
                    break;
                }
                if (tx1Idx == tx1Len) {
                    order.append("2");
                    temp.add(tx2.statements.get(tx2Idx++));
                } else if (tx2Idx == tx2Len) {
                    order.append("1");
                    temp.add(tx1.statements.get(tx1Idx++));
                } else {
                    boolean pickOne = Randomly.getBoolean();
                    if (pickOne) {
                        order.append("1");
                        temp.add(tx1.statements.get(tx1Idx++));
                    } else {
                        order.append("2");
                        temp.add(tx2.statements.get(tx2Idx++));
                    }
                }
            }
        }
        return res;
    }

    private static int A(int n, int m) {
        int res = 1;
        for (int i = m; i > 0; i--) {
            res *= n;
            n--;
        }
        return res;
    }

    private static int C(int n, int m) {
        if (m > n / 2) {
            m = n - m;
        }
        return A(n, m) / A(m, m);
    }
}
