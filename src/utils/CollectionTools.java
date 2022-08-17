package utils;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public final class CollectionTools {
    @SafeVarargs
    public static <T> Set<T> intersect(Set<T> first, Set<T>... rest) {
        return intersect(concatHead(first, Arrays.asList(rest)), null);
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

    public static <T> List<T> concatHead(T car, List<T> cdr) {
        return concat(List.of(car), cdr);
    }

    public static <T> List<T> concatTail(List<T> head, T tail) {
        return concat(head, List.of(tail));
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

    public static <T> List<T> selectFrom(List<T> list, List<Integer> indices) {
        final var result = new ArrayList<T>();
        indices.forEach(i -> result.add(list.get(i)));
        return result;
    }

    public static <T> List<Integer> findForIndices(List<T> list, Predicate<T> predicate) {
        final var result = new ArrayList<Integer>();

        final var iter = list.iterator();
        for (int i = 0; iter.hasNext(); i++) {
            final var elm = iter.next();
            if (predicate.test(elm)) {
                result.add(i);
            }
        }

        return result;
    }

    public static <T> void iterWithIndex(List<T> list, RunnerWithIndex<T> runner) {
        var idx = 0;
        for (final var iter = list.iterator(); iter.hasNext(); idx++) {
            final var elm = iter.next();
            runner.run(idx, elm);
        }
    }

    public static <A, B> void zip(List<A> first, List<B> second, BiConsumer<A, B> consumer) {
        Log.ensure(first.size() == second.size());

        final var firstIter = first.iterator();
        final var secondIter = second.iterator();

        while (firstIter.hasNext()) {
            Log.ensure(secondIter.hasNext());

            final var firstElm = firstIter.next();
            final var secondElm = secondIter.next();

            consumer.accept(firstElm, secondElm);
        }
    }

    public interface RunnerWithIndex<T> { void run(int idx, T elm); }

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

    public static <K, V> Map<Integer, V> createIdentifierMap(Map<K, V> map) {
        final var identMap = new HashMap<Integer, V>();

        for (final var entry : map.entrySet()) {
            final var ident = System.identityHashCode(entry.getKey());
            identMap.put(ident, entry.getValue());
        }

        return identMap;
    }

    public static <T> List<T> findRedundant(List<T> origin) {
        final var result = new ArrayList<T>();
        final var elms = new HashSet<>(origin);

        for (final var elm : origin) {
            if (elms.contains(elm)) {
                elms.remove(elm);
                continue;
            }

            result.add(elm);
        }

        return result;
    }

    private CollectionTools() {}
}
