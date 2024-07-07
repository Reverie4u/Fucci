package fucci.mysql.ast;

import java.util.Map;

public interface MySQLExpression {

    // 获取一行数据带入表达式的值
    default MySQLConstant getExpectedValue(Map<String, Object> row) {
        throw new AssertionError("Not supported for this operator");
    }

}
