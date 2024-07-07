package fucci;

import java.util.Arrays;

public class Version {
    Object[] data;
    Transaction tx;
    boolean deleted;

    Version(Object[] data, Transaction tx, boolean deleted) {
        this.data = data;
        this.tx = tx;
        this.deleted = deleted;
    }

    @Override
    public String toString() {
        return "Version{" +
                "data=" + Arrays.toString(data) +
                ", tx=" + tx.txId +
                ", deleted=" + deleted +
                '}';
    }
}
