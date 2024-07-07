package fucci.common;

import fucci.common.visitor.UnaryOperation;

public abstract class UnaryNode<T> implements UnaryOperation<T> {

    protected T expr;

    protected UnaryNode(T expr) {
        this.expr = expr;
    }

    public void setExpr(T newExpr) {
        this.expr = newExpr;
    }

    @Override
    public T getExpression() {
        return expr;
    }

}
