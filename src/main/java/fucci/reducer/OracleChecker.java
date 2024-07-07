package fucci.reducer;

public interface OracleChecker {
    // 判断测试用例是否会触发BUG
    boolean hasBug(TestCase tc);

    /*
     * 判断单语句是否有BUG
     */
    boolean hasBug(String tc);
}