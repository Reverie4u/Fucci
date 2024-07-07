package fucci;

import java.util.HashSet;

import lombok.Data;

enum LockType {
    SHARE, EXCLUSIVE, NONE
}

@Data
class LockObject {
    HashSet<Integer> rowIds;
    HashSet<String> indexes;
}

public class Lock {
    Transaction tx;
    StatementCell stmt;
    LockType type;
    LockObject lockObject;

    boolean isConflict(Transaction otherTx) {
        if (type == LockType.NONE)
            return false;
        if (otherTx.finished || otherTx.committed || otherTx.aborted)
            return false;
        for (Lock otherLock : otherTx.locks) {
            if ((this.type == LockType.EXCLUSIVE || otherLock.type == LockType.EXCLUSIVE)) {
                if (setIntersect(this.lockObject.rowIds, otherLock.lockObject.rowIds)
                        || setIntersect(this.lockObject.indexes, otherLock.lockObject.indexes)) {
                    return true;
                }
            }
            // 间隙锁，需要仔细看，这块有问题
            if (useRangeLock(this.stmt) && isRangeConflict(otherLock.stmt, this.stmt)) {
                return true;
            }
            if (useRangeLock(otherLock.stmt) && isRangeConflict(this.stmt, otherLock.stmt)) {
                return true;
            }
        }
        return false;
    }

    private boolean useRangeLock(StatementCell stmt) {
        // CS情况是没有间隙锁的
        if (TableTool.dbms == DBMS.MYSQL || TableTool.dbms == DBMS.MARIADB)
            return (stmt.tx.isolationlevel == IsolationLevel.REPEATABLE_READ
                    || stmt.tx.isolationlevel == IsolationLevel.SERIALIZABLE)
                    && (TableTool.isSetCase || TableTool.oracle.equals("MT"));
        return false;
    }

    private boolean isRangeConflict(StatementCell stmt, StatementCell affectedStmt) {
        if ((stmt.type == StatementType.INSERT || stmt.type == StatementType.UPDATE
                || stmt.type == StatementType.DELETE)
                && (affectedStmt.type == StatementType.SELECT ||
                        affectedStmt.type == StatementType.SELECT_SHARE
                        || affectedStmt.type == StatementType.SELECT_UPDATE
                        || affectedStmt.type == StatementType.UPDATE || affectedStmt.type == StatementType.DELETE)) {
            String snapshotName = "range_conflict";
            TableTool.takeSnapshotForTable(snapshotName);
            TableTool.viewToTable(affectedStmt.view);
            LockObject affectedBefore = TableTool.getLockObject(affectedStmt);
            TableTool.executeOnTable(stmt.statement);
            LockObject affectedAfter = TableTool.getLockObject(affectedStmt);
            TableTool.recoverTableFromSnapshot(snapshotName);
            return setDiffer(affectedBefore.rowIds, affectedAfter.rowIds)
                    || setDiffer(affectedBefore.indexes, affectedAfter.indexes);
        }
        return false;
    }

    static private <T> boolean setDiffer(HashSet<T> setA, HashSet<T> setB) {
        if (setA.size() != setB.size())
            return true;
        for (T elem : setA) {
            if (!setB.contains(elem)) {
                return true;
            }
        }
        return false;
    }

    static private <T> boolean setIntersect(HashSet<T> setA, HashSet<T> setB) {
        for (T elem : setA) {
            if (setB.contains(elem)) {
                return true;
            }
        }
        return false;
    }
}
