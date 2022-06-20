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

    private Optional<INode<E, P>> prev;     // 前一个节点
    private Optional<INode<E, P>> next;     // 下一个节点
    private Optional<IList<E, P>> parent;   // 包含该节点的链表
    private E value;                        // 包含该节点的对象 (也就是将其作为自己的一个成员的那个对象)
    private boolean deleted;                // 是否被删除
}
