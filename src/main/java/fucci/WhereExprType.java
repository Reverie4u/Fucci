package fucci;

public enum WhereExprType {
    MySQLBetweenOperation, /* （Between运算）  */
    MySQLBinaryComparisonOperation, /* (二元比较运算：>，>=，<，<=，!=，=，LIKE） */
    MySQLBinaryLogicalOperation, /* （二元逻辑运算：AND，OR，XOR） */
    MySQLBinaryOperation, /* （位运算：&，|，^） */
    MySQLCastOperation, /* （Cast运算） */
    MySQLInOperation, /* （IN运算）  */
    MySQLUnaryPostfixOperation, /* （一元后缀运算：IS (NOT)? NULL，TRUE，FALSE） */
    MySQLUnaryPrefixOperation, /* （一元前缀运算：！，+，-）  */
    MySQLColumnReference,
    MySQLIntConstant,
}
