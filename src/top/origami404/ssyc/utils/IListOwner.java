package top.origami404.ssyc.utils;

import java.util.*;

public interface IListOwner<E extends INodeOwner<E, P>, P extends IListOwner<E, P>> extends List<E> {
    IList<E, P> getIList();

    default List<E> asElementView() { return getIList(); }
    default List<INode<E, P>> asINodeView() { return getIList().asINodeView(); }

    default int                                 size()                                          { return getIList().size();                         }
    default boolean                             isEmpty()                                       { return getIList().isEmpty();                      }
    default boolean                             contains(Object o)                              { return getIList().contains(o);                    }
    default IList<E, P>.IListElementIterator    iterator()                                      { return listIterator();                            }
    default Object[]                            toArray()                                       { return getIList().toArray();                      }
    default <T> T[]                             toArray(T[] a)                                  { return getIList().toArray(a);                     }
    default boolean                             add(E e)                                        { return getIList().add(e);                         }
    default void                                add(int index, E e)                             { getIList().add(index, e);                         }
    default boolean                             remove(Object e)                                { return getIList().remove(e);                      }
    default E                                   remove(int index)                               { return getIList().remove(index);                  }
    default boolean                             containsAll(Collection<?> c)                    { return getIList().containsAll(c);                 }
    default boolean                             addAll(Collection<? extends E> c)               { return getIList().addAll(c);                      }
    default boolean                             addAll(int index, Collection<? extends E> c)    { return getIList().addAll(index, c);               }
    default boolean                             removeAll(Collection<?> c)                      { return getIList().removeAll(c);                   }
    default boolean                             retainAll(Collection<?> c)                      { return getIList().retainAll(c);                   }
    default E                                   get(int index)                                  { return getIList().get(index);                     }
    default E                                   set(int index, E element)                       { return getIList().set(index, element);            }
    default int                                 indexOf(Object o)                               { return getIList().indexOf(o);                     }
    default int                                 lastIndexOf(Object o)                           { return getIList().lastIndexOf(o);                 }
    default IList<E, P>.IListElementIterator    listIterator()                                  { return listIterator(0);                   }
    default IList<E, P>.IListElementIterator    listIterator(int index)                         { return getIList().listIterator(index);            }
    default List<E>                             subList(int fromIndex, int toIndex)             { return getIList().subList(fromIndex, toIndex);    }
    default void                                clear()                                         { getIList().clear();                               }
}