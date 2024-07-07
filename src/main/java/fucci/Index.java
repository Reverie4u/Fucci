package fucci;

import java.util.ArrayList;

public class Index {
    String name;
    ArrayList<String> indexedCols;
    boolean isPrimary;
    boolean isUnique;

    public Index(String name) {
        this.name = name;
    }

    public Index(String name, boolean isPrimary, boolean isUnique) {
        this(name);
        this.isPrimary = isPrimary;
        this.isUnique = isUnique;
        this.indexedCols = new ArrayList<>();
    }

    public Index(String name, ArrayList<String> indexedCols,
            boolean isPrimary, boolean isUnique) {
        this(name, isPrimary, isUnique);
        this.indexedCols = indexedCols;
    }
}
