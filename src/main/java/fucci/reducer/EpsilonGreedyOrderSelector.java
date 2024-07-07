package fucci.reducer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fucci.Randomly;

public class EpsilonGreedyOrderSelector<T> implements OrderSelector<T> {
    Map<T, Double> candidatesGainMap;
    double epsilon = 0.7;
    int iteration = 0;

    public double getEpsilon() {
        return epsilon;
    }

    @Override
    public T selectNext(List<T> excludedList) {
        Map<T, Double> candidatesGainMapCopy = new HashMap<T, Double>(candidatesGainMap);
        // 从candidatesMapCopy中移除key在excludedList中的元素
        for (T key : excludedList) {
            candidatesGainMapCopy.remove(key);
        }
        if (candidatesGainMapCopy.isEmpty()) {
            return null;
        }

        // 获取candidatesGainMap中value最大的entry
        Map.Entry<T, Double> maxEntry = null;
        for (Map.Entry<T, Double> entry : candidatesGainMapCopy.entrySet()) {
            if (maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0) {
                maxEntry = entry;
            }
        }
        // 如果maxEntry为null，返回null
        if (maxEntry == null) {
            return null;
        }
        // 以epsilon的概率随机选择一个元素,以1-epsilon的概率选择value最大的元素
        if (Math.random() < epsilon) {
            return Randomly.fromList(new ArrayList<>(candidatesGainMapCopy.keySet()));
        } else {
            return maxEntry.getKey();
        }
    }

    @Override
    public T selectNext() {
        // 获取candidatesGainMap中value最大的entry
        Map.Entry<T, Double> maxEntry = null;
        for (Map.Entry<T, Double> entry : candidatesGainMap.entrySet()) {
            if (maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0) {
                maxEntry = entry;
            }
        }
        // 如果maxEntry为null，返回null
        if (maxEntry == null) {
            return null;
        }
        // 以epsilon的概率随机选择一个元素,以1-epsilon的概率选择value最大的元素
        if (Math.random() < epsilon) {
            return Randomly.fromList(new ArrayList<>(candidatesGainMap.keySet()));
        } else {
            return maxEntry.getKey();
        }
    }

    public EpsilonGreedyOrderSelector(List<T> candidates) {
        candidatesGainMap = new HashMap<>();
        for (T candidate : candidates) {
            candidatesGainMap.put(candidate, 0.0);
        }
    }

    @Override
    public void updateWeight(T candidate, boolean success) {
        double weight = success ? 1.0 : 0.0;
        double oldWeight = candidatesGainMap.get(candidate);
        double newWeight = oldWeight + (weight - oldWeight) / (iteration + 1);
        candidatesGainMap.put(candidate, newWeight);
    }
}
