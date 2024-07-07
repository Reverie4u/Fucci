package fucci.mysql;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

import fucci.Randomly;
import fucci.TableTool;
import fucci.common.ExprGen;

public class MySQLExprGen extends ExprGen {

    // 这里改成生成MySQL的表达式，而不直接返回字符串
    @Override
    public String genPredicate() {
        String expr = genExpr(0);
        if (expr.equals("")) {
            return "True";
        } else {
            return expr;
        }
    }

    public String genExpr(int depth) {
        if (Randomly.getBoolean() || depth > depthLimit) {
            return genLeaf();
        }
        String opName = Randomly.fromList(Arrays.asList(
                "genColumn", "genConstant", "genUaryPrefixOp", "genUaryPostfixOp",
                "genBinaryLogicalOp", "genBinaryBitOp", "genBinaryMathOp", "genBinaryCompOp",
                "genInOp", "genBetweenOp", "genCastOp", "genFunction"));
        String expr;
        try {
            Method method = this.getClass().getMethod(opName, int.class);
            expr = (String) method.invoke(this, depth);
        } catch (Exception e) {
            throw new RuntimeException("Gen expr by reflection failed: ", e);
        }
        return expr;
    }

    public String genLeaf() {
        if (Randomly.getBoolean()) {
            return genColumn(0);
        } else {
            return genConstant(0);
        }
    }

    public String genColumn(int depth) {
        return Randomly.fromList(new ArrayList<>(columns.keySet()));
    }

    public String genConstant(int depth) {
        String constType = Randomly.fromOptions("INT", "NULL", "STRING", "DOUBLE");
        switch (constType) {
            case "INT":
                return Long.toString(TableTool.rand.getInteger());
            case "NULL":
                return Randomly.getBoolean() ? "NULL" : Long.toString(TableTool.rand.getInteger());
            case "STRING":
                return "\"" + TableTool.rand.getString() + "\"";
            case "DOUBLE":
                return Double.toString(TableTool.rand.getDouble());
        }
        return "0";
    }

    public String genUaryPrefixOp(int depth) {
        String op = Randomly.fromOptions("NOT", "!", "+", "-");
        return op + "(" + genExpr(depth + 1) + ")";
    }

    public String genUaryPostfixOp(int depth) {
        String op = Randomly.fromOptions("IS NULL", "IS FALSE", "IS TRUE");
        return "(" + genExpr(depth + 1) + ")" + op;
    }

    public String genBinaryLogicalOp(int depth) {
        String op = Randomly.fromOptions("AND", "OR", "XOR");
        return "(" + genExpr(depth + 1) + ") " + op + " (" + genExpr(depth + 1) + ")";
    }

    public String genBinaryBitOp(int depth) {
        String op = Randomly.fromOptions("&", "|", "^", ">>", "<<");
        return "(" + genExpr(depth + 1) + ") " + op + " (" + genExpr(depth + 1) + ")";
    }

    public String genBinaryMathOp(int depth) {
        String op = Randomly.fromOptions("+", "-", "*", "/", "%");
        return "(" + genExpr(depth + 1) + ") " + op + " (" + genExpr(depth + 1) + ")";
    }

    public String genBinaryCompOp(int depth) {
        String op = Randomly.fromOptions("=", "!=", "<", "<=", ">", ">=", "LIKE");
        return "(" + genExpr(depth + 1) + ") " + op + " (" + genExpr(depth + 1) + ")";
    }

    public String genInOp(int depth) {
        ArrayList<String> exprList = new ArrayList<>();
        exprList.add("0");
        for (int i = 0; i < Randomly.baseInt() + 1; i++) {
            exprList.add(genExpr(depth + 1));
        }
        return "(" + genExpr(depth + 1) + ") IN ((" + String.join("), (", exprList) + "))";
    }

    public String genBetweenOp(int depth) {
        String fromExpr = genExpr(depth + 1);
        String toExpr = genExpr(depth + 1);
        return "(" + genExpr(depth + 1) + ") BETWEEN (" + fromExpr + ") AND (" + toExpr + ")";
    }

    public String genCastOp(int depth) {
        String castedExpr = genExpr(depth + 1);
        // INT有问题，应该是SIGNED或者UNSIGNED
        String castType = Randomly.fromOptions("INT", "FLOAT", "DOUBLE", "CHAR");
        return "CAST((" + castedExpr + ") AS " + castType + ")";
    }

    public String genFunction(int depth) {
        MySQLFunction function = MySQLFunction.getRandomFunc();
        ArrayList<String> argList = new ArrayList<>();
        for (int i = 0; i < function.getArgCnt(); i++) {
            argList.add(genExpr(depth + 1));
        }
        return function.name() + "((" + String.join("), (", argList) + "))";
    }
}
