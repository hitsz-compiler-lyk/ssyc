package ir.analysis;

import java.util.Map;
import java.util.function.Supplier;

public interface AnalysisInfoOwner {
    default <T extends AnalysisInfo> T getAnalysisInfo(Class<T> cls) {
        final var name = cls.getSimpleName();

        @SuppressWarnings("unchecked")
        final var info = (T) getInfoMap().get(name);

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

    default void addOrOverwriteInfo(AnalysisInfo info) {
        getInfoMap().put(info.getInfoName(), info);
    }

    default <T extends AnalysisInfo> boolean containsAnalysisInfo(Class<T> cls) {
        final var name = cls.getSimpleName();
        return getInfoMap().containsKey(name);
    }

    default <T extends AnalysisInfo> void addIfAbsent(Class<T> cls, Supplier<T> supplier) {
        if (!containsAnalysisInfo(cls)) {
            addAnalysisInfo(supplier.get());
        }
    }

    default <T extends AnalysisInfo> void removeAnalysisInfo(Class<T> cls) {
        final var name = cls.getSimpleName();
        if (!containsAnalysisInfo(cls)) {
            throw new RuntimeException("Try to remove a non-exist info: " + name);
        } else {
            getInfoMap().remove(name);
        }
    }

    Map<String, AnalysisInfo> getInfoMap();
}
