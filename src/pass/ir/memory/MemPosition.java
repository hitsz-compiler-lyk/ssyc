package pass.ir.memory;

import ir.GlobalVar;
import ir.Parameter;
import ir.Value;
import ir.inst.*;
import utils.Log;

import java.util.Optional;

class MemPosition {
    public static Optional<MemPosition> createWithLoad(LoadInst load) {
        return createWithPointer(load.getPtr());
    }

    public static MemPosition createWithStore(StoreInst store) {
        return createWithPointer(store.getPtr())
            .orElseThrow(() -> new RuntimeException("Pointer in Store must point to a real memory position"));
    }

    public static MemPosition createWithMemInit(MemInitInst memInit) {
        return new MemPosition(LocationKind.LocalArray, memInit.getArrayPtr());
    }

    /**
     * 根据指针 (如 LoadInst/StoreInst/MemInitInst 中的 getPtr() 方法获得的 Value) 来构造内存位置
     */
    public static Optional<MemPosition> createWithPointer(Value ptr) {
        Log.ensure(ptr.getType().isPtr());
        if (ptr instanceof GlobalVar) {
            final var gv = (GlobalVar) ptr;

            if (gv.getType().getBaseType().isPtr()) {
                // direct load of global array, skip
                return Optional.empty();
            } else {
                return Optional.of(new MemPosition(LocationKind.GlobalVariable, ptr));
            }

        } else if (ptr instanceof GEPInst) {
            final var gep = (GEPInst) ptr;
            final var gepPtr = gep.getPtr();

            try {
                return createWithPointer(gepPtr);
            } catch (RuntimeException e) {
                throw new RuntimeException("Unknown structure: (" + gep + " with " + gepPtr + ")");
            }

        } else if (ptr instanceof LoadInst) {
            final var load = (LoadInst) ptr;
            Log.ensure(load.getPtr() instanceof GlobalVar);
            return Optional.of(new MemPosition(LocationKind.GlobalArray, load.getPtr()));

        } else if (ptr instanceof CAllocInst) {
            final var calloc = (CAllocInst) ptr;
            return Optional.of(new MemPosition(LocationKind.LocalArray, calloc));

        } else if (ptr instanceof Parameter) {
            final var param = (Parameter) ptr;
            Log.ensure(param.getType().isPtr());
            return Optional.of(new MemPosition(LocationKind.LocalArray, param));

        } else {
            throw new RuntimeException("Unknown structure: " + ptr);
        }
    }

    public boolean isGlobal() {
        return kind == LocationKind.GlobalArray || kind == LocationKind.GlobalVariable;
    }

    public boolean isLocal() {
        return kind == LocationKind.LocalArray;
    }

    private MemPosition(LocationKind kind, Value location) {
        this.kind = kind;
        this.location = location;
    }

    @Override
    public int hashCode() {
        return kind.ordinal() * 37 + System.identityHashCode(location);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MemPosition) {
            final var position = (MemPosition) obj;

            final var locationEqual = this.location == position.location;
            final var kindEqual = this.kind == position.kind;
            Log.ensure(locationEqual == kindEqual, "LocationEqual should equal to kindEqual");

            return locationEqual;

        } else {
            return false;
        }
    }

    enum LocationKind {GlobalVariable, GlobalArray, LocalArray}

    private LocationKind kind;
    private Value location;
}
