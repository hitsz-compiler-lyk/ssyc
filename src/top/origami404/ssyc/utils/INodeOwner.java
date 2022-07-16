package top.origami404.ssyc.utils;

import java.util.Optional;

public interface INodeOwner<E extends INodeOwner<E, P>, P extends IListOwner<E, P>> {
    INode<E, P> getINode();

    default Optional<P> getParent() {
        return getINode().getParent().map(IList::getOwner);
    }

    default void setParent(IListOwner<E, P> parent) {
        getINode().setParent(parent.getIList());
    }

    default void insertAfterCO(E nodeOwner) {
        getINode().insertAfterCO(nodeOwner.getINode());
    }

    default void insertBeforeCO(E nodeOwner) {
        getINode().insertBeforeCO(nodeOwner.getINode());
    }
}
