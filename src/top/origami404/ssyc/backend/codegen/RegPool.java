package top.origami404.ssyc.backend.codegen;

import top.origami404.ssyc.backend.operand.Reg;
import top.origami404.ssyc.utils.Log;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class RegPool<E extends Reg> {
    List<E> freeRegs = new LinkedList<>();
    List<E> takenRegs = new LinkedList<>();


    public RegPool(List<E> allRegs) {
        freeRegs.addAll(allRegs);
    }


    public void put(E e) {
        final var optional= takenRegs.stream().filter(one -> one.getId() == e.getId()).findFirst();
        optional.ifPresent(takenRegs::remove);
        optional.ifPresent(freeRegs::add);
        if (optional.isEmpty()) {
            throw new RuntimeException("Put reg doesn't contain in takenReg. ");
        }
    }

    public E freeAny() {
        Log.ensure(freeRegs.isEmpty());
        final var reg = takenRegs.remove(0);
        freeRegs.add(reg);
        return reg;
    }


    public Optional<E> take() {
        final var optional =  freeRegs.stream().findFirst();
        optional.ifPresent(takenRegs::add);
        optional.ifPresent(freeRegs::remove);
        return optional;
    }

    public boolean isAllTaken() {
        return freeRegs.isEmpty();
    }

    public Optional<E> find(E e) {
        return freeRegs.stream().filter(one -> one.getId() == e.getId()).findFirst();
    }

    public Optional<E> findAndTake(E e) {
        final var optional = find(e);
        optional.ifPresent(freeRegs::remove);
        optional.ifPresent(takenRegs::add);
        return optional;
    }

    public void clear() {
        freeRegs.addAll(takenRegs);
        takenRegs.clear();
    }
}
