package fucci.reducer;

import java.util.List;

public interface OrderSelector<T> {
    // 选择下一个类型
    public T selectNext(List<T> excludedList);

    // 更新当前类型的权重
    public void updateWeight(T candidate, boolean success);

    public T selectNext();
}