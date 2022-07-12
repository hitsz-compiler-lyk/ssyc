package top.origami404.ssyc.utils;

import java.util.List;

public interface IListOwner<E extends INodeOwner<E, P>, P extends IListOwner<E, P>> {
    IList<E, P> getIList();

    default List<E> asElementView() {
        return getIList();
    }

    default List<INode<E, P>> asINodeView() {
        return getIList().asINodeView();
    }
}