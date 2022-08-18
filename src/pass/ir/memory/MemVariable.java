package pass.ir.memory;

import ir.GlobalVar;
import ir.Parameter;
import ir.Value;
import ir.inst.*;
import utils.Log;

import java.util.Optional;

/**
 * <h3>表示一个特定的内存 "变量"</h3>
 * <p>
 *     内存变量是指一个指针类型的 IR Value, 它有可能是:
 *     <ul>
 *         <li>全局数组: GlobalVar</li>
 *         <li>全局变量: GlobalVar</li>
 *         <li>局部数组: CAlloc</li>
 *     </ul>
 * </p>
 */
class MemVariable {
    public static Optional<MemVariable> createWithLoad(LoadInst load) {
        return createWithPointer(load.getPtr());
    }

    public static MemVariable createWithStore(StoreInst store) {
        return createWithPointer(store.getPtr())
            .orElseThrow(() -> new RuntimeException("Pointer in Store must point to a real memory position"));
    }

    public static MemVariable createWithMemInit(MemInitInst memInit) {
        return new MemVariable(LocationKind.LocalArray, memInit.getArrayPtr());
    }

    /**
     * 根据指针 (如 LoadInst/StoreInst/MemInitInst 中的 getPtr() 方法获得的 Value) 来构造内存变量
     */
    public static Optional<MemVariable> createWithPointer(Value ptr) {
        Log.ensure(ptr.getType().isPtr());
        if (ptr instanceof GlobalVar) {
            final var gv = (GlobalVar) ptr;

            if (gv.getType().getBaseType().isPtr()) {
                // 当一条 Load 指令直接 Load 一个全局数组的时候, 说明这条 Load 指令只是负责把全局数组地址加载进来的
                // 这种情况不算对任何内存位置的访问, 所以返回 Empty
                return Optional.empty();
            } else {
                return Optional.of(new MemVariable(LocationKind.GlobalVariable, ptr));
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
            return Optional.of(new MemVariable(LocationKind.GlobalArray, load.getPtr()));

        } else if (ptr instanceof CAllocInst) {
            final var calloc = (CAllocInst) ptr;
            return Optional.of(new MemVariable(LocationKind.LocalArray, calloc));

        } else if (ptr instanceof Parameter) {
            final var param = (Parameter) ptr;
            Log.ensure(param.getType().isPtr());
            return Optional.of(new MemVariable(LocationKind.LocalArray, param));

        } else {
            throw new RuntimeException("Unknown structure: " + ptr);
        }
    }

    static MemVariable createWithGlobalVariable(GlobalVar gv) {
        final var kind = gv.isArray() ? LocationKind.GlobalArray : LocationKind.GlobalVariable;
        return new MemVariable(kind, gv);
    }

    public boolean isGlobal() {
        return kind == LocationKind.GlobalArray || kind == LocationKind.GlobalVariable;
    }

    public boolean isLocal() {
        return kind == LocationKind.LocalArray;
    }

    private MemVariable(LocationKind kind, Value location) {
        this.kind = kind;
        this.location = location;
    }

    @Override
    public int hashCode() {
        return kind.ordinal() * 37 + System.identityHashCode(location);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MemVariable) {
            final var position = (MemVariable) obj;

            final var locationEqual = this.location == position.location;
            final var kindEqual = this.kind == position.kind;
            // locationEqual ==> kindEqual (蕴含关系)
            Log.ensure(!locationEqual || kindEqual, "LocationEqual should equal to kindEqual");

            return locationEqual;

        } else {
            return false;
        }
    }

    enum LocationKind {GlobalVariable, GlobalArray, LocalArray}

    private LocationKind kind;
    private Value location;
}
