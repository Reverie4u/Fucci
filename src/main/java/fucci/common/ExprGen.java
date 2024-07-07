package fucci.common;

import java.util.HashMap;

public abstract class ExprGen {
    protected HashMap<String, Column> columns;
    protected int depthLimit = 3;

    public ExprGen() {
    }

    public void setColumns(HashMap<String, Column> columns) {
        this.columns = columns;
    }

    public abstract Object genPredicate();
}
