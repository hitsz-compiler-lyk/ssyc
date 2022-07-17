package top.origami404.ssyc.utils;

import java.util.AbstractSequentialList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Optional;

public class IList<E extends INodeOwner<E, P>, P extends IListOwner<E, P>> extends AbstractSequentialList<E> {
    // 链表的头节点, 其为 Optional.empty() 当且仅当链表为空
    private final INode<E, P> begin;
    private int size;   // 链表大小
    private final P owner;    // 包含该链表的对象

    public IList(P owner) {
        // IList 在被构造时必然会知道它的 parent 是谁
        // 要不然为什么要构造一个 IList 呢
        // 但是 INode 就不然, 很多时候会先构造一个含 INode 的节点
        // 然后再把其插入到某个 IList 中
        this.owner = owner;
        this.begin = new INode<>(null);
        this.size = 0;
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

    void adjustSize(int offset) {
        this.size += offset;
    }

    /**
     * @param k 节点序号
     * @return 第 k 个节点的前一个节点 (从 0 开始编号, 0 会返回头节点)
     */
    private INode<E, P> getKthNodeBefore(final int k) {
        var curr = begin;
        for (int i = 0; i < k; i++) {
            curr = curr.getNext().orElseThrow(NoSuchElementException::new);
        }
        return curr;
    }

    public boolean replaceFirst(E oldElm, E newElm) {
        final var iter = listIterator();
        while (iter.hasNext()) {
            final var elm = iter.next();
            if (elm.equals(oldElm)) {
                iter.set(newElm);
                return true;
            }
        }

        return false;
    }

    @Override
    public int size() {
        return IList.this.size;
    }

    public void verify() throws IListException {
        if (owner == null) { throw new IListException("Owner of IList shouldn't be null"); }

        for (final var node : asINodeView()) {
            if (!node.getParent().map(this::equals).orElse(false)) {
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
    public IListElementIterator listIterator(int index) {
        return new IListElementIterator(index);
    }

    @Override
    public IListElementIterator listIterator() {
        return listIterator(0);
    }

    public class IListElementIterator implements ListIterator<E>, Cloneable {
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

        private IListElementIterator(IListIterator iter) {
            this.iter = iter;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof IList.IListElementIterator) {
                final var other = (IList<?, ?>.IListElementIterator) obj;
                return other.iter.equals(this.iter);
            } else {
                return false;
            }
        }

        @Override
        public IListElementIterator clone() {
            try {
                super.clone();
                return new IListElementIterator(iter.clone());
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException("Clone not supported", e);
            }
        }
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

    public class IListIterator implements ListIterator<INode<E, P>>, Cloneable {
        private INode<E, P> prevNode;               // nextNode 的前继
        private INode<E, P> tempNode;               // 上一次调用 previous() 或者是 next() 返回的节点
        private IteratorActionKind lastModified;            // 最后一次调用修改性方法是什么方法
        private IteratorActionKind lastMoved;               // 最后一次调用移动性方法是什么方法

        public IListIterator(int index) {
            this.prevNode = IList.this.getKthNodeBefore(index);
            // this.nextNode = prevNode.getNext();
            this.tempNode = null;
            this.lastModified = IteratorActionKind.OTHER;
        }

        // For clone only
        private IListIterator(INode<E, P> prevNode, INode<E, P> tempNode,
                              IteratorActionKind lastModified, IteratorActionKind lastMoved) {
            this.prevNode = prevNode;
            // this.nextNode = nextNode;
            this.tempNode = tempNode;
            this.lastModified = lastModified;
            this.lastMoved = lastMoved;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof IList.IListIterator) {
                final var other = (IList<?, ?>.IListIterator) obj;
                return other.prevNode == this.prevNode;
            } else {
                return false;
            }
        }

        @Override
        public IListIterator clone() {
            try {
                super.clone();
                return new IListIterator(prevNode, tempNode, lastModified, lastMoved);
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException("Clone Not Support", e);
            }
        }

        //====================== Query ====================//
        @Override
        public boolean hasNext() {
            return prevNode.getNext().isPresent();
        }

        @Override
        public boolean hasPrevious() {
            // 确定用 == 判断同一
            return prevNode != IList.this.begin;
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
            // tempNode = nextNode.orElseThrow(NoSuchElementException::new);
            tempNode = prevNode.getNext().orElseThrow(NoSuchElementException::new);
            lastMoved = IteratorActionKind.NEXT;
            lastModified = IteratorActionKind.OTHER;

            prevNode = tempNode;

            return tempNode;
        }

        @Override
        public INode<E, P> previous() {
            throw new UnsupportedOperationException();
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
                case NEXT -> prevNode = prevTemp.orElseThrow();
                // case PREV -> nextNode = nextTemp;
                default -> throw new IllegalStateException("Cannot call `remove` before any movement");
            }

            // 更新迭代器本身的状态
            lastModified = IteratorActionKind.REMOVE;

            // 更新链的状态
            tempNode.markedAsDeleted();
            prevTemp.ifPresent(n -> n.setNextOpt(nextTemp));
            nextTemp.ifPresent(n -> n.setPrevOpt(prevTemp));

            IList.this.adjustSize(-1);
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

            // 维护新的节点的父母关系
            newNode.setParent(IList.this);
        }

        @Override
        public void add(INode<E, P> newNode) {
            Log.debug("Add %s to %s".formatted(newNode.getValue(), IList.this.getOwner()));
            lastModified = IteratorActionKind.ADD;

            newNode.setPrev(prevNode);
            newNode.setNextOpt(prevNode.getNext());

            prevNode.getNext().ifPresent(n -> n.setPrev(newNode));
            prevNode.setNext(newNode);

            // 插入后需要更新 prevNode
            prevNode = newNode;

            // 维护新的节点的父母关系
            newNode.setParent(IList.this);
            IList.this.adjustSize(+1);
        }
    }
}
