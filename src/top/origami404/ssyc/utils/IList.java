package top.origami404.ssyc.utils;

import java.util.AbstractSequentialList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Optional;

public class IList<E extends INodeOwner<E, P>, P extends IListOwner<E, P>> extends AbstractSequentialList<E> {
    // 链表的头节点, 其为 Optional.empty() 当且仅当链表为空
    private Optional<INode<E, P>> begin;
    private int size;   // 链表大小
    private P owner;    // 包含该链表的对象

    public IList(P owner) {
        // IList 在被构造时必然会知道它的 parent 是谁
        // 要不然为什么要构造一个 IList 呢
        // 但是 INode 就不然, 很多时候会先构造一个含 INode 的节点
        // 然后再把其插入到某个 IList 中
        this.owner = owner;
        this.begin = Optional.empty();
    }

    /**
     * @return 包含这个侵入式链表的对象
     */
    public P getOwner() {
        return this.owner;
    }

    /**
     * @return 返回一个与该链表绑定的, 元素为 INode 的视图
     */
    public List<INode<E, P>> asINodeView() {
        return new INodeListView();
    }

    /**
     * 已被弃用, 现在 IList 本身就是一个 {@code List<E>}
     * @return 返回一个与该链表绑定的, 元素为 E 的视图
     */
    @Deprecated
    public List<E> asElementView() {
        return this;
    }

    // Only for INode
    void setBegin(INode<E, P> begin) { this.begin = Optional.of(begin); }
    Optional<INode<E, P>> getBegin() { return begin; }

    /**
     * 已被弃用, 现在 IList 本身就是一个 {@code List<E>}, 推荐使用 {@code size()} 来获取大小
     * @return 元素数量
     */
    @Deprecated
    public int getSize() {
        return size;
    }

    void setSize(int newSize) {
        this.size = newSize;
    }

    void adjustSize(int offset) {
        this.size += offset;
    }

    /**
     * @param k 节点序号
     * @return 第 k 个节点 (从 0 开始编号)
     */
    private INode<E, P> getKthNode(final int k) {
        var curr = begin;
        for (int i = 0; i < k; i++) {
            curr = curr.flatMap(INode::getNext);
        }

        return curr
            .orElseThrow(() -> new IndexOutOfBoundsException(k));
    }

    @Override
    public int size() {
        return IList.this.size;
    }

    public void verify() throws IListException {
        if (owner == null) { throw new IListException("Owner of IList shouldn't be null"); }

        for (final var node : asINodeView()) {
            if (node.getParent().map(this::equals).orElse(false)) {
                throw new IListException("Node in IList, but the parent isn't itself");
            }
        }
    }

    public void verifyAll() throws IListException {
        verify();
        for (final var node : asINodeView()) {
            node.verify();
        }
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return new IListElementIterator(index);
    }

    public class IListElementIterator implements ListIterator<E> {
        private final IListIterator iter;
        public IListElementIterator(int index) {
            this.iter = new IListIterator(index);
        }

        @Override public boolean hasNext()      { return iter.hasNext();        }
        @Override public boolean hasPrevious()  { return iter.hasPrevious();    }
        @Override public int nextIndex()        { return iter.nextIndex();      }
        @Override public int previousIndex()    { return iter.previousIndex();  }
        @Override public E next()               { return iter.next().getValue();        }
        @Override public E previous()           { return iter.previous().getValue();    }
        @Override public void remove()          { iter.remove();                        }
        @Override public void set(E e)          { iter.set(e.getINode());               }
        @Override public void add(E e)          { iter.add(e.getINode());               }
    }

    public class INodeListView extends AbstractSequentialList<INode<E, P>> {
        @Override
        public int size() {
            return IList.this.size;
        }

        @Override
        public ListIterator<INode<E, P>> listIterator(int index) {
            return new IListIterator(index);
        }
    }

    private enum IteratorActionKind {
        NEXT, PREV, ADD, REMOVE, SET, OTHER
    }

    public class IListIterator implements ListIterator<INode<E, P>> {
        private Optional<INode<E, P>> prevNode;     // nextNode 的前继
        private Optional<INode<E, P>> nextNode;     // 下一次调用 next() 要返回的节点
        private INode<E, P> tempNode;               // 上一次调用 previous() 或者是 next() 返回的节点
        private IteratorActionKind lastModified;            // 最后一次调用修改性方法是什么方法
        private IteratorActionKind lastMoved;               // 最后一次调用移动性方法是什么方法

        public IListIterator(int index) {
            if (index > 0) {
                final var node = IList.this.getKthNode(index - 1);
                this.nextNode = Optional.of(node);
            } else {
                this.nextNode = Optional.empty();
            }

            this.prevNode = nextNode.flatMap(INode::getPrev);
            this.tempNode = null;
            this.lastModified = IteratorActionKind.OTHER;
        }

        //====================== Query ====================//
        @Override
        public boolean hasNext() {
            return nextNode.isPresent();
        }

        @Override
        public boolean hasPrevious() {
            return prevNode.isPresent();
        }

        @Override
        public int nextIndex() {
            // 在除了该迭代器之外的地方也有可能修改链表的情况下
            // 不可能做到准确地追踪迭代器的索引
            throw new UnsupportedOperationException();
        }

        @Override
        public int previousIndex() {
            throw new UnsupportedOperationException();
        }


        //====================== Movement ====================//
        @Override
        public INode<E, P> next() {
            tempNode = nextNode
                .orElseThrow(NoSuchElementException::new);
            lastMoved = IteratorActionKind.NEXT;
            lastModified = IteratorActionKind.OTHER;

            prevNode = nextNode;
            nextNode = nextNode.get().getNext();

            return tempNode;
        }

        @Override
        public INode<E, P> previous() {
            tempNode = prevNode
                .orElseThrow(NoSuchElementException::new);
            lastMoved = IteratorActionKind.PREV;
            lastModified = IteratorActionKind.OTHER;

            nextNode = prevNode;
            prevNode = prevNode.get().getNext();

            return tempNode;
        }


        //====================== Modification ====================//
        @Override
        public void remove() {
            if (lastModified == IteratorActionKind.ADD) {
                throw new IllegalStateException("Cannot call `remove` just after `add`");
            }
            lastModified = IteratorActionKind.REMOVE;

            // remove 要求删除前一个被 previous() 或是 next() 返回的节点, 即 tempNode
            final var prevTemp = tempNode.getPrev();
            final var nextTemp = tempNode.getNext();

            // 判断之前是往哪移动, 借之判断我们删去了哪个节点, 随后替换对应的下一个节点
            switch (lastMoved) {
                case NEXT -> { prevNode = prevTemp; }
                case PREV -> { nextNode = nextTemp; }
                default -> {
                    throw new IllegalStateException("Cannot call `remove` before any movement");
                }
            }

            // 更新迭代器本身的状态
            lastModified = IteratorActionKind.REMOVE;

            // 更新链的状态
            tempNode.markedAsDeleted();
            prevTemp.ifPresent(n -> n.setNextOpt(nextTemp));
            nextTemp.ifPresent(n -> n.setPrevOpt(prevTemp));
        }

        @Override
        public void set(INode<E, P> newNode) {
            if (lastModified == IteratorActionKind.ADD || lastModified == IteratorActionKind.REMOVE) {
                throw new IllegalStateException("Cannot call `set` just after `add` or `remove`");
            }
            lastModified = IteratorActionKind.SET;

            if (lastMoved == IteratorActionKind.OTHER) {
                throw new IllegalStateException("Cannot call `set` before any movement");
            }

            final var prevTemp = tempNode.getPrev();
            final var nextTemp = tempNode.getNext();

            // 修改链表

            // 将原来的节点标记为删除
            // TODO: what if newNode == tempNode ???
            tempNode.markedAsDeleted();

            // 依次链接 prevTemp <-> newNode <-> nextTemp
            prevTemp.ifPresent(n -> n.setNext(newNode));
            newNode.setPrevOpt(prevTemp);

            newNode.setNextOpt(nextTemp);
            nextTemp.ifPresent(n -> n.setPrev(newNode));

            // 如果 prevTemp 是空, 说明此时在链表头部
            // 要更新的话还得更新掉链表头的节点
            final boolean isHead = prevNode.isEmpty();
            if (isHead) {
                IList.this.begin = Optional.of(newNode);
                IList.this.size = 1;
            }

            // 维护新的节点的父母关系
            newNode.setParent(IList.this);
        }

        @Override
        public void add(INode<E, P> newNode) {
            lastModified = IteratorActionKind.ADD;

            final boolean isHead = prevNode.isEmpty();
            final boolean isTail = nextNode.isEmpty();
            if (isHead && isTail) {
                IList.this.begin = Optional.of(newNode);
                IList.this.size = 1;
            } else if (isHead) {
                nextNode.get().insertBeforeCO(newNode);
            } else if (isTail) {
                prevNode.get().insertAfterCO(newNode);
            } else {
                // 与 nextNode.get().insertBeforeCO(newNode) 等价
                prevNode.get().insertAfterCO(newNode);
            }

            // 插入后需要更新 prevNode
            prevNode = Optional.of(newNode);

            // 维护新的节点的父母关系
            newNode.setParent(IList.this);
        }
    }
}
