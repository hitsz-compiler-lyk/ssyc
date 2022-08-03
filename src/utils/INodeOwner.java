package utils;

import java.util.Optional;

public interface INodeOwner<E extends INodeOwner<E, P>, P extends IListOwner<E, P>> {
    INode<E, P> getINode();

    default Optional<P> getParentOpt() {
        return getINode().getParentOpt().map(IList::getOwner);
    }

    default P getParent() {
        return getINode().getParent().getOwner();
    }

    default void setParent(IListOwner<E, P> parent) {
        getINode().setParent(parent.getIList());
    }

    default void freeFromIList() {
        getINode().freeFromIList();
    }

    default void replaceInIList(E newElm) {
        getINode().replaceInIList(newElm.getINode());
    }

    default void insertBeforeCO(E newPrevElm) {
        getINode().insertBeforeCO(newPrevElm.getINode());
    }

    default void insertAfterCO(E newNextElm) {
        getINode().insertAfterCO(newNextElm.getINode());
    }
}
