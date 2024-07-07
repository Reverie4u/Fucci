package fucci.mysql.ast;

import java.util.Map;

public class MySQLStringExpression implements MySQLExpression {

    private final String str;
    private final MySQLConstant expectedValue;

    public MySQLStringExpression(String str, MySQLConstant expectedValue) {
        this.str = str;
        this.expectedValue = expectedValue;
    }

    public String getStr() {
        return str;
    }

    @Override
    public MySQLConstant getExpectedValue(Map<String, Object> row) {
        return expectedValue;
    }

}
