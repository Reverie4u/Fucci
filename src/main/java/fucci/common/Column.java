package fucci.common;

import java.util.ArrayList;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class Column {
    protected Table table;
    protected String columnName;
    protected DataType dataType;
    protected boolean primaryKey;
    protected boolean unique;
    protected boolean notNull;
    protected int size;
    protected ArrayList<String> appearedValues = new ArrayList<>();

    protected Column() {
    }

    protected Column(Table table, String columnName, DataType dataType, boolean primaryKey, boolean unique,
            boolean notNull, int size) {
        this.table = table;
        this.columnName = columnName;
        this.dataType = dataType;
        this.primaryKey = primaryKey;
        this.unique = unique;
        this.notNull = notNull;
        this.size = size;
    }

    public abstract String getRandomVal();
}
