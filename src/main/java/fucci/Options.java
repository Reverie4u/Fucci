package fucci;

import com.beust.jcommander.Parameter;

import lombok.Data;

@Data
public class Options {

    @Parameter(names = { "--dbms" }, description = "Specifies the target DBMS")
    private String DBMS = "mysql";

    @Parameter(names = { "--set-case" }, description = "Whether use a specified case")
    private boolean setCase = false;

    @Parameter(names = { "--case-file" }, description = "Specifies the input file of the specified case")
    private String caseFile = "";

    @Parameter(names = { "--db" }, description = "Specifies the test database")
    private String dbName = "test";

    @Parameter(names = { "--table" }, description = "Specifies the test table")
    private String tableName = "Fucci";

    @Parameter(names = "--username", description = "The user name used to log into the DBMS")
    private String userName = "root";

    @Parameter(names = "--password", description = "The password used to log into the DBMS")
    private String password = "";

    @Parameter(names = "--host", description = "The host used to log into the DBMS")
    private String host = "127.0.0.1";

    @Parameter(names = "--port", description = "The port used to log into the DBMS")
    private int port = 3306;

    @Parameter(names = "--timeout", description = "The timeout of fuzzer, in seconds")
    private long timeout = 86400;

    @Parameter(names = "--insert-conflict", description = "Whether consider insert conflict")
    private boolean insertConflict = false;

    // could be fully-shared-filter, partially-shared-filters,
    // conflict-tuple-containment, random, none
    @Parameter(names = "--filter-conflict", description = "Constructing methods of filter conflict")
    private String filterConflict = "random";

    @Parameter(names = "--filter-submitted-order", description = "Whether to enable submission order filtering")
    private boolean filterSubmittedOrder = false;

    @Parameter(names = "--submitted-order-sample-count", description = "Sampling count of submission order")
    private int submittedOrderSampleCount = 10;

    // could be DT, MT, CS
    @Parameter(names = "--oracle", description = "Specifies the oracle type")
    private String oracle = "CS";

    @Parameter(names = "--filter-duplicate-bug", description = "Whether to enable duplicate bug filtering")
    private boolean filterDuplicateBug = false;

    @Parameter(names = "--reducer", description = "Whether to enable reducer")
    private boolean reducerSwitchOn = false;

    @Parameter(names = "--reducer-type", description = "Reducer type, can be random, epsilon-greedy, probability-table, all")
    private String reducerType = "random";

    @Parameter(names = "--max-reduce-count", description = "The maximum number of reduction")
    private int maxReduceCount = 5;

    @Parameter(names = "--output-dir", description = "")
    private String outputDir = System.getProperty("user.dir");
}
