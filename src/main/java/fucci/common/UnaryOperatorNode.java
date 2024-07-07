package fucci.common;

import fucci.common.BinaryOperatorNode.Operator;

public abstract class UnaryOperatorNode<T, O extends Operator> extends UnaryNode<T> {

    protected O op;

    protected UnaryOperatorNode(T expr, O op) {
        super(expr);
        this.op = op;
    }

    public void setOp(O newOp) {
        this.op = newOp;
    }

    @Override
    public String getOperatorRepresentation() {
        return op.getTextRepresentation();
    }

}
