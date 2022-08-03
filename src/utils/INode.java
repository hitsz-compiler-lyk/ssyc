package utils;

import java.util.Optional;

import ir.GlobalModifitationStatus;

/**
 * 带反向引用的侵入式链表的节点
 * @param <E> 包含列表元素的类的类型
 * @param <P> 包含列表的类的类型
 */
public class INode<E extends INodeOwner<E, P>, P extends IListOwner<E, P>> {
    /** 创建一个自由的节点 */
    public INode(E value) {
        this(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            value // 当且仅当该节点作为头部节点时它是 null 的
        );
    }

    /** 创建一个非自由节点 */
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
    }

    public Optional<INode<E, P>> getPrev() {
        return prev;
    }

    public Optional<INode<E, P>> getNext() {
        return next;
    }

    public Optional<IList<E, P>> getParentOpt() {
        return parent;
    }

    public IList<E, P> getParent() {
        return parent.orElseThrow(() -> new RuntimeException("This INode do NOT have a parent: " + value));
    }

    public E getValue() {
        return value;
    }

    public E getOwner() {
        return value;
    }

    public void replaceInIList(INode<E, P> newNode) {
        newNode.getParentOpt().ifPresent(p -> p.remove(newNode.getOwner()));
        newNode.setParent(getParentOpt().orElse(null));
        this.setParent(null);

        prev.ifPresent(n -> n.setNext(newNode));
        next.ifPresent(n -> n.setPrev(newNode));
        newNode.setPrevOpt(prev);
        newNode.setNextOpt(next);
    }

    public void freeFromIList() {
        getParentOpt().ifPresent(p -> p.asINodeView().remove(this));
        GlobalModifitationStatus.current().markAsChanged();
    }

    public void insertBeforeCO(INode<E, P> newPrev) {
        // 先将对方从原来的 IList 中挪走, 然后将其 parent 设为自己的 IList
        newPrev.freeFromIList();
        newPrev.setParent(this.getParentOpt().orElse(null));

        // 将原先的 prev 与 newPrev 连接
        newPrev.setPrevOpt(getPrev());
        getPrev().ifPresent(n -> n.setNext(newPrev));

        // 将 newPrev 与自己连接
        this.setPrev(newPrev);
        newPrev.setNext(this);

        // 调整自己的 IList 的大小
        getParentOpt().ifPresent(l -> l.adjustSize(+1));
    }

    public void insertAfterCO(INode<E, P> newNext) {
        // 先将对方从原来的 IList 中挪走, 然后将其 parent 设为自己的 IList
        newNext.freeFromIList();
        newNext.setParent(getParentOpt().orElse(null));

        // 将原先的 next 与 newNext 连接
        newNext.setNextOpt(getNext());
        getNext().ifPresent(n -> n.setPrev(newNext));

        // 将 newNext 与自己连接
        this.setNext(newNext);
        newNext.setPrev(this);

        // 调整自己的 IList 的大小
        getParentOpt().ifPresent(l -> l.adjustSize(+1));
    }

    void setNext(INode<E, P> next) {
        this.next = Optional.ofNullable(next);
    }

    void setNextOpt(Optional<INode<E, P>> next) {
        this.next = next;
    }

    void setPrev(INode<E, P> prev) {
        this.prev = Optional.ofNullable(prev);
    }

    void setPrevOpt(Optional<INode<E, P>> prev) {
        this.prev = prev;
    }

    void setParent(IList<E, P> parent) {
        this.parent = Optional.ofNullable(parent);
    }

    public boolean isHeader() {
        return value == null;
    }

    public boolean isFree() {
        return parent.isEmpty();
    }

    public void verify() throws IListException {
        if (value == null) {
            throw new IListException("INode shouldn't have empty value/owner");
        }

        parent.ifPresentOrElse(l -> {
            if (!l.asINodeView().contains(this)) {
                throw new IListException("INode not in parent");
            }
        }, () -> Log.info("Free node, owner: " + value));

        prev.ifPresentOrElse(n -> {
            // 如果一个 Node 的 prev 非空, 那 prev 的 next 必须是自己
            final var self = n.getNext()
                .orElseThrow(() -> new IListException("INode's prev don't have a next"));
            if (self != this) {
                throw new IListException("INode's prev's next isn't itself");
            }
        }, () -> {
            if (parent.isPresent()) {
                throw new IListException("INode has empty prev node but has non-empty parent");
            }
        });

        next.ifPresentOrElse(n -> {
            // 如果一个 Node 的 next 非空, 那 next 的 prev 必须是自己
            final var self = n.getPrev()
                .orElseThrow(() -> new IListException("INode's next don't have a prev"));
            if (self != this) {
                throw new IListException("INode's next's prev isn't itself");
            }
        }, () -> getParentOpt().ifPresent(list -> {
            // 如果一个 Node 的 next 是空, 那它必须是列表的末尾
            final var self = list.asINodeView().get(list.size() - 1);
            if (self != this) {
                throw new IListException("INode's next is null, but isn't the end of the list");
            }
        }));

        // 这函数太毒瘤了, 这真的不是 JavaScript 吗 (
    }

    private Optional<INode<E, P>> prev;     // 前一个节点 (只有不在链表里的时候它才为空)
    private Optional<INode<E, P>> next;     // 下一个节点
    private Optional<IList<E, P>> parent;   // 包含该节点的链表
    private final E value;                        // 包含该节点的对象 (也就是将其作为自己的一个成员的那个对象)
}
