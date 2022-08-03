package backend.regallocator;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class MemPool {
    public static final class Mem {
        int offset;

        Mem(int offset) {
            this.offset = offset;
        }

        public int getOffset() {
            return this.offset;
        }
    }

    List<Mem> freeMem = new LinkedList<>();
    List<Mem> takenMem = new LinkedList<>();
    int size = 0;

    public Mem add() {
        final var mem = new Mem(size * 4);
        freeMem.add(mem);
        size++;
        return mem;
    }

    public void put(Mem mem) {
        if (takenMem.contains(mem)) {
            takenMem.remove(mem);
            freeMem.add(mem);
        } else {
            throw new RuntimeException("Put mem doesn't contain in takenMem");
        }
    }

    public Mem take() {
        if (freeMem.isEmpty()) {
            add();
        }
        freeMem.sort(Comparator.comparingInt(Mem::getOffset));
        final var mem = freeMem.remove(0);
        takenMem.add(mem);
        return mem;
    }

    public int getSize() {
        return size;
    }
}
