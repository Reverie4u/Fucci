package fucci.reducer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProbabilityTableBasedOrderSelector<T> implements OrderSelector<T> {
    Map<T, Double> candidatesMap;
    double initialWeight = 100;
    // 步长
    double step = 0.1;

    @Override
    public T selectNext(List<T> excludedList) {
        Map<T, Double> candidatesMapCopy = new HashMap<T, Double>(candidatesMap);
        // 从candidatesMapCopy中移除key在excludedList中的元素
        for (T key : excludedList) {
            candidatesMapCopy.remove(key);
        }
        if (candidatesMapCopy.isEmpty()) {
            return null;
        }
        // 使用轮盘赌方法选择下一个类型
        double sum = 0;
        // 遍历candidatesMapCopy，计算权重总和
        for (Map.Entry<T, Double> entry : candidatesMapCopy.entrySet()) {
            sum += entry.getValue();
        }
        double rand = Math.random() * sum;
        for (Map.Entry<T, Double> entry : candidatesMapCopy.entrySet()) {
            rand -= entry.getValue();
            if (rand <= 0) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public T selectNext() {
        // 使用轮盘赌方法选择下一个类型
        double sum = 0;
        // 遍历candidatesMapCopy，计算权重总和
        for (Map.Entry<T, Double> entry : candidatesMap.entrySet()) {
            sum += entry.getValue();
        }
        double rand = Math.random() * sum;
        for (Map.Entry<T, Double> entry : candidatesMap.entrySet()) {
            rand -= entry.getValue();
            if (rand <= 0) {
                return entry.getKey();
            }
        }
        return null;
    }

    public ProbabilityTableBasedOrderSelector(List<T> candidates) {
        candidatesMap = new HashMap<>();
        for (T candidate : candidates) {
            candidatesMap.put(candidate, initialWeight);
        }
    }

    @Override
    public void updateWeight(T candidate, boolean success) {
        double weight = 0.0;
        if (success) {
            weight = 1.0;
        } else {
            weight = -1.0;
        }
        // weight只能是-1.0或者1.0
        double newWeight = candidatesMap.get(candidate) + weight * step * (candidatesMap.size() - 1);
        candidatesMap.put(candidate, newWeight);
        // 更新其他的权重
        for (T key : candidatesMap.keySet()) {
            if (key != candidate) {
                double oldWeight = candidatesMap.get(key);
                double newWeight2 = oldWeight - weight * step;
                if (newWeight2 < 0) {
                    newWeight2 = 0;
                }
                candidatesMap.put(key, newWeight2);
            }
        }
    }
}
