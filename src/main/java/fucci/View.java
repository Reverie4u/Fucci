package fucci;

import java.util.Arrays;
import java.util.HashMap;

public class View {
    HashMap<Integer, Object[]> data; // 由rowId -> 行数据的map
    HashMap<Integer, Boolean> deleted; // may be null

    View() {
        data = new HashMap<>();
    }

    View(boolean withDel) {
        data = new HashMap<>();
        if (withDel) {
            deleted = new HashMap<>();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("View{\n");
        for (int rowId : data.keySet()) {
            sb.append("\t");
            sb.append(rowId).append(":");
            sb.append(Arrays.toString(data.get(rowId)));
            if (deleted != null) {
                sb.append(" deleted: ").append(deleted.get(rowId));
            }
            sb.append("\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
