package fucci;

public enum IsolationLevel {
    READ_UNCOMMITTED("READ UNCOMMITTED", "RU"),
    READ_COMMITTED("READ COMMITTED", "RC"),
    REPEATABLE_READ("REPEATABLE READ", "RR"),
    SERIALIZABLE("SERIALIZABLE", "SER");

    String name;
    String alias;

    IsolationLevel(String name, String alias) {
        this.name = name;
        this.alias = alias;
    }

    public String getName() {
        return this.name;
    }

    public String getAlias() {
        return alias;
    }

    public static IsolationLevel getFromAlias(String alias) {
        switch (alias) {
            case "RU":
                return READ_UNCOMMITTED;
            case "RC":
                return READ_COMMITTED;
            case "RR":
                return REPEATABLE_READ;
            case "SER":
                return SERIALIZABLE;
            default:
                throw new RuntimeException("Invalid isolation level alias: " + alias);
        }
    }
}
