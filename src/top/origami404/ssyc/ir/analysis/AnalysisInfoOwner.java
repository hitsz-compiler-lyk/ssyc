package top.origami404.ssyc.ir.analysis;

import java.util.Map;

public interface AnalysisInfoOwner {
    default AnalysisInfo getAnalysisInfo(String name) {
        final var info = getInfoMap().get(name);
        if (info == null) {
            throw new RuntimeException("Analysis info " + name + " does not exist");
        }

        return info;
    }

    default void addAnalysisInfo(AnalysisInfo info) {
        final var map = getInfoMap();
        final var name = info.getInfoName();

        if (map.containsKey(name)) {
            throw new RuntimeException("Analysis info " + name + " already exist");
        }

        map.put(name, info);
    }

    public Map<String, AnalysisInfo> getInfoMap();
}
