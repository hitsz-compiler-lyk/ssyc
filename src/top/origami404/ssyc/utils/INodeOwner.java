package top.origami404.ssyc.utils;

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
        final var inode = getINode();
        if (!inode.isFree()) {
            inode.getParent().remove(inode.getOwner());
        }
    }
}
