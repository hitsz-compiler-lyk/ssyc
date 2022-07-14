package top.origami404.ssyc.utils;

import top.origami404.ssyc.ir.inst.Instruction;

import java.util.*;

public class IteratorTools {
    public static <E> Iterator<E> iterBetweenFromBegin(List<E> sources, ListIterator<E> end) {
        return iterBetween(sources, sources.listIterator(), end);
    }

    public static <E> Iterator<E> iterBetweenToEnd(List<E> sources, ListIterator<E> begin) {
        return iterBetween(sources, begin, sources.listIterator(sources.size()));
    }

    public static <E> Iterator<E> iterBetween(List<E> sources, ListIterator<E> begin, ListIterator<E> end) {
        return new Iterator<>() {
            final ListIterator<E> iter = begin;

            @Override
            public boolean hasNext() {
                return !iter.equals(end);
            }

            @Override
            public E next() {
                return iter.next();
            }
        };
    }

    public static <A, B> Iterator<B> iterConvert(Iterator<A> iterator, java.util.function.Function<A, B> converter) {
        return new Iterator<>() {
            final Iterator<A> iter = iterator;

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public B next() {
                return converter.apply(iter.next());
            }
        };
    }

    public static <E> ListIterator<E> emptyIter() {
        return new ListIterator<>() {
            @Override public boolean hasNext()        { return false; }
            @Override public E next()                 { throw new NoSuchElementException(); }
            @Override public boolean hasPrevious()    { return false; }
            @Override public E previous()             { throw new NoSuchElementException(); }
            @Override public int nextIndex()          { return 0; }
            @Override public int previousIndex()      { return -1; }
            @Override public void remove()            { throw new UnsupportedOperationException(); }
            @Override public void set(E instruction)  { throw new UnsupportedOperationException(); }
            @Override public void add(E instruction)  { throw new UnsupportedOperationException(); }
        };
    }

    public static <E> List<E> unique(List<E> list) {
        return new ArrayList<>(new LinkedHashSet<>(list));
    }

    public static <E> boolean isUnique(List<E> list) {
        return unique(list).equals(list);
    }
}
