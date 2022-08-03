package utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class CollectionTools {
    @SafeVarargs
    public static <T> Set<T> intersect(Set<T> first, Set<T>... rest) {
        return intersect(concat(first, Arrays.asList(rest)), null);
    }

    public static <T> Set<T> intersect(Collection<Set<T>> sets, Set<T> atEmpty) {
        return intersect(sets.iterator(), atEmpty);
    }

    public static <T> Set<T> intersect(Iterator<Set<T>> iterator, Set<T> atEmpty) {
        if (!iterator.hasNext()) {
            return atEmpty;
        }

        final var result = new HashSet<>(iterator.next());
        iterator.forEachRemaining(result::retainAll);
        return result;
    }

    @SafeVarargs
    public static <T> Set<T> union(Set<T>... sets) {
        return union(Arrays.asList(sets).iterator());
    }

    public static <T> Set<T> union(Collection<Set<T>> sets) {
        return union(sets.iterator());
    }

    public static <T> Set<T> union(Iterator<Set<T>> iterator) {
        final var result = new HashSet<T>();
        iterator.forEachRemaining(result::addAll);
        return result;
    }

    public static <T> List<T> concat(T head, List<T> rest) {
        return concat(List.of(head), rest);
    }

    @SafeVarargs
    public static <T> List<T> concat(List<T>... lists) {
        return concat(Arrays.asList(lists));
    }

    public static <T> List<T> concat(Collection<List<T>> lists) {
        return concat(lists.iterator());
    }

    public static <T> List<T> concat(Iterator<List<T>> iterator) {
        final var result = new ArrayList<T>();
        iterator.forEachRemaining(result::addAll);
        return result;
    }

    public static <T> T car(List<T> list) {
        Log.ensure(!list.isEmpty());
        return list.get(0);
    }

    public static <T> List<T> cdr(List<T> list) {
        Log.ensure(!list.isEmpty());
        return Collections.unmodifiableList(list.subList(1, list.size()));
    }

    public static <T> List<T> head(List<T> list) {
        Log.ensure(!list.isEmpty());
        return Collections.unmodifiableList(list.subList(0, list.size() - 1));
    }

    public static <T> T tail(List<T> list) {
        Log.ensure(!list.isEmpty());
        return list.get(list.size() - 1);
    }

    public static class TwoList<T> {
        public TwoList(List<T> first, List<T> second) {
            this.first = first;
            this.second = second;
        }

        public List<T> first() { return first; }
        public List<T> second() { return second; }

        public final List<T> first;
        public final List<T> second;
    }

    public static <T> TwoList<T> split(List<T> list, int i) {
        final var first = Collections.unmodifiableList(list.subList(0, i));
        final var second = Collections.unmodifiableList(list.subList(i, list.size()));
        return new TwoList<>(first, second);
    }

    private CollectionTools() {}
}
