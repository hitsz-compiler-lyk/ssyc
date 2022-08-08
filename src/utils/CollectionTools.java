package utils;

import ir.BasicBlock;
import ir.inst.PhiInst;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

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

    public static void fillBlockWithPhiInherited(BasicBlock oldBB, BasicBlock newBB, List<Integer> inheritIndices) {
        // 从旧块中将对应位置的 phi 参数抢过来成为新的 phi 参数
        for (final var phi : oldBB.phis()) {
            final var newPhi = new PhiInst(phi.getType(), phi.getWaitFor());

            final var incomingValueOutsideLoop = selectFrom(phi.getIncomingValues(), inheritIndices);
            newPhi.setIncomingValueWithoutCheckingPredecessorsCO(incomingValueOutsideLoop);

            newBB.addPhi(newPhi);
        }
        newBB.adjustPhiEnd();

        // 然后更新外面前继的指向
        final var outsidePreds = selectFrom(oldBB.getPredecessors(), inheritIndices);
        for (final var outsidePred : outsidePreds) {
            outsidePred.getTerminator().replaceOperandCO(oldBB, newBB);
            newBB.addPredecessor(outsidePred);
        }
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

    private CollectionTools() {}
}
