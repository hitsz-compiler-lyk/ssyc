package top.origami404.ssyc.utils;

import java.util.Optional;

public class INode<E extends INodeOwner<E, P>, P extends IListOwner<E, P>> {
    public INode(E value) {
        this(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            value
        );
    }

    public INode(E value, IList<E, P> parent) {
        this(Optional.of(parent), Optional.empty(), Optional.empty(), value);
    }

    public INode(
        Optional<IList<E, P>> parent,
        Optional<INode<E, P>> prev,
        Optional<INode<E, P>> next,
        E value
    ) {
        this.parent = parent;
        this.prev = prev;
        this.next = next;
        this.value = value;
        this.deleted = false;
    }

    public Optional<INode<E, P>> getPrev() {
        return prev;
    }

    public Optional<INode<E, P>> getNext() {
        return next;
    }

    public Optional<IList<E, P>> getParent() {
        return parent;
    }

    public E getValue() {
        return value;
    }

    public E getOwner() {
        return value;
    }

    public void setNext(INode<E, P> next) {
        this.next = Optional.ofNullable(next);
    }

    public void setNextOpt(Optional<INode<E, P>> next) {
        this.next = next;
    }

    public void setPrev(INode<E, P> prev) {
        this.prev = Optional.ofNullable(prev);
    }

    public void setPrevOpt(Optional<INode<E, P>> prev) {
        this.prev = prev;
    }

    public void setParent(IList<E, P> parent) {
        this.parent = Optional.ofNullable(parent);
    }

    /**
     * 在 this 的后面插入新节点
     * prev <-> this <-> (newNext) <-> next
     * @param newNext
     */
    public void insertAfterCO(INode<E, P> newNext) {
        final var oldNext = next;

        this.setNext(newNext);
        newNext.setPrev(this);

        newNext.setNextOpt(oldNext);
        oldNext.ifPresent(n -> n.setPrev(newNext));

        parent.ifPresent(l -> l.adjustSize(+1));
    }

    /**
     * 在 this 的前面插入新节点
     * prev <-> (newPrev) <-> this <-> next
     * @param newPrev
     */
    public void insertBeforeCO(INode<E, P> newPrev) {
        final var oldPrev = prev;

        // 如果当前节点是链表的头节点, 那么当往前插入时, 还要修改链表的头节点
        oldPrev.ifPresentOrElse(
            n -> n.setNext(newPrev),
            () -> parent.ifPresent(p -> p.setBegin(newPrev)));
        newPrev.setPrevOpt(oldPrev);

        newPrev.setNext(this);
        this.setPrev(newPrev);

        parent.ifPresent(l -> l.adjustSize(+1));
    }

    /**
     * 将节点标记为 "已删除", 暂时还没什么强制力
     */
    public void markedAsDeleted() {
        this.deleted = true;
    }

    /**
     * 将已标记被删除的节点标记回 "未删除"
     */
    public void restore() {
        this.deleted = false;
    }

    /**
     * @return 返回该节点是否被标记为已删除
     */
    public boolean isDeleted() {
        return deleted;
    }

    public void verify() throws IListException {
        if (value == null) {
            throw new IListException("INode shouldn't have empty value/owner");
        }

        parent.ifPresentOrElse(l -> {
            if (!l.asINodeView().contains(this)) {
                throw new IListException("INode not in parent");
            }
        }, () -> { Log.info("Free node, owner: " + value); });

        prev.ifPresentOrElse(n -> {
            // 如果一个 Node 的 prev 非空, 那 prev 的 next 必须是自己
            final var self = n.getNext()
                .orElseThrow(() -> new IListException("INode's prev don't have a next"));
            if (self != this) {
                throw new IListException("INode's prev's next isn't itself");
            }
        }, () -> getParent().ifPresent(list -> {
            // 如果一个 Node 的 prev 是空的, 那它必须是列表的开头
            if (list.getBegin().map(this::equals).orElse(false)) {
                throw new IListException("INode's prev is null, but isn't the begin of the list");
            }
        }));

        next.ifPresentOrElse(n -> {
            // 如果一个 Node 的 next 非空, 那 next 的 prev 必须是自己
            final var self = n.getPrev()
                .orElseThrow(() -> new IListException("INode's next don't have a prev"));
            if (self != this) {
                throw new IListException("INode's next's prev isn't itself");
            }
        }, () -> getParent().ifPresent(list -> {
            // 如果一个 Node 的 next 是空, 那它必须是列表的末尾
            final var self = list.asINodeView().get(list.getSize() - 1);
            if (self != this) {
                throw new IListException("INode's next is null, but isn't the end of the list");
            }
        }));

        // 这函数太毒瘤了, 这真的不是 JavaScript 吗 (
    }

    private Optional<INode<E, P>> prev;     // 前一个节点
    private Optional<INode<E, P>> next;     // 下一个节点
    private Optional<IList<E, P>> parent;   // 包含该节点的链表
    private E value;                        // 包含该节点的对象 (也就是将其作为自己的一个成员的那个对象)
    private boolean deleted;                // 是否被删除
}
