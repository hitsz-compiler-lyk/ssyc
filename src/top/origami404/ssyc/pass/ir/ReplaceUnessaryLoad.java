package top.origami404.ssyc.pass.ir;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.Function;
import top.origami404.ssyc.ir.GlobalVar;
import top.origami404.ssyc.ir.Module;
import top.origami404.ssyc.ir.Parameter;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.constant.ArrayConst;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.constant.IntConst;
import top.origami404.ssyc.ir.inst.CAllocInst;
import top.origami404.ssyc.ir.inst.CallInst;
import top.origami404.ssyc.ir.inst.GEPInst;
import top.origami404.ssyc.ir.inst.Instruction;
import top.origami404.ssyc.ir.inst.LoadInst;
import top.origami404.ssyc.ir.inst.MemInitInst;
import top.origami404.ssyc.ir.inst.StoreInst;
import top.origami404.ssyc.pass.ir.dataflow.DataFlowInfo;
import top.origami404.ssyc.pass.ir.dataflow.ForwardDataFlowPass;
import top.origami404.ssyc.utils.Log;

public class ReplaceUnessaryLoad implements IRPass {
    @Override
    public void runPass(Module module) {
        (new CollectMemoryDefination()).runPass(module);
        module.getNonExternalFunction().forEach(this::runOnFunction);
    }

    void runOnFunction(Function function) {
        for (final var block : function) {
            final var current = block.getAnalysisInfo(MemoryInfo.class).in();

            for (final var inst : block) {
                // Def
                if (inst instanceof MemInitInst) {
                    current.setByInit((MemInitInst) inst);
                } else if (inst instanceof StoreInst) {
                    current.setByStore((StoreInst) inst);
                } else if (inst instanceof CallInst) {
                    current.setByCall((CallInst) inst);
                }
                // Use
                else if (inst instanceof LoadInst) {
                    final var load = (LoadInst) inst;
                    final var newInst = current.getByLoad(load);

                    if (newInst != load) {
                        load.replaceAllUseWith(newInst);
                        load.freeFromIList();
                        load.freeFromUseDef();
                    } else {
                        current.setByLoad(load);
                    }
                }
            }
        }
    }
}

class MemoryInfo extends DataFlowInfo<MemCache> {}

class CollectMemoryDefination extends ForwardDataFlowPass<MemCache, MemoryInfo> {
    @Override
    protected MemCache transfer(BasicBlock block, MemCache in) {
        final var current = MemCache.copyFrom(in);

        for (final var inst : block) {
            if (inst instanceof MemInitInst) {
                current.setByInit((MemInitInst) inst);
            } else if (inst instanceof StoreInst) {
                current.setByStore((StoreInst) inst);
            } else if (inst instanceof CallInst) {
                current.setByCall((CallInst) inst);
            }
        }

        return current;
    }

    @Override
    protected MemCache meet(BasicBlock block, List<MemCache> predOuts) {
        Log.ensure(predOuts.isEmpty() || block == block.getParent().getEntryBBlock(),
            "Only entry block could have no pred");

        return predOuts.stream().reduce(MemCache::merge).orElse(MemCache.empty());
    }

    @Override
    protected MemCache topElement(BasicBlock block) {
        return MemCache.empty();
    }

    @Override
    protected MemCache entryIn(BasicBlock block) {
        return MemCache.empty();
    }

    @Override
    protected MemoryInfo createInfo(BasicBlock block) {
        return new MemoryInfo();
    }

    @Override
    protected Class<MemoryInfo> getInfoClass() {
        return MemoryInfo.class;
    }
}

class MemCache {
    public static MemCache empty() {
        return new MemCache();
    }

    public static MemCache copyFrom(MemCache other) {
        return new MemCache(other);
    }

    public void setByInit(MemInitInst init) {
        final var pos = MemPosition.createWithMemInit(init);
        cache.put(pos, new MemHandler(init.getInit()));
    }

    public void setByStore(StoreInst store) {
        dealWithPointer(store.getPtr(), info -> {
            if (info.isExhausted) {
                info.handler.setValue(store.getVal());
            } else {
                info.handler.setUndef();
            }
        }, handler -> handler.setValue(store.getVal()));
    }

    public void setByCall(CallInst call) {
        final var globals = cache.keySet().stream()
            .filter(MemPosition::isGlobal);

        final var localsInArg = call.getArgList().stream()
            .filter(arg -> arg.getType().isPtr())
            .map(MemPosition::createWithPointer)
            .filter(Optional::isPresent).map(Optional::get)
            .filter(MemPosition::isLocal);

        Stream.concat(globals, localsInArg)
            .map(this::getInitHandler).filter(Objects::nonNull)
            .forEach(MemHandler::setUndef);
    }

    public void setByLoad(LoadInst load) {
        dealWithPointer(load.getPtr(), info -> {
            if (info.isExhausted && info.handler.isUndef()) {
                info.handler.setValue(load);
            }
        }, handler -> {
            if (handler.isUndef()) {
                handler.setValue(load);
            }
        });
    }

    private void dealWithPointer(Value ptr, Consumer<IndicesInfo> whenGEP, Consumer<MemHandler> whenGlobalVar) {
        MemPosition.createWithPointer(ptr).ifPresent(pos -> {
            final var handler = getInitHandler(pos);

            if (ptr instanceof GEPInst) {
                final var gep = (GEPInst) ptr;
                final var info = getInfoFromGEP(gep, handler);
                whenGEP.accept(info);
            } else if (ptr instanceof GlobalVar) {
                final var gv = (GlobalVar) ptr;
                Log.ensure(gv.isVariable());
                whenGlobalVar.accept(handler);
            } else {
                Log.ensure(false, "Unknown structure in pointer: " + ptr);
            }
        });
    }

    public Value getByLoad(LoadInst load) {
        final var resultHandler = new MemHandler(load);

        dealWithPointer(load.getPtr(), info -> {
            if (info.isExhausted && !info.handler.isUndef()) {
                resultHandler.setValue(info.handler.getValue());
            }
        }, handler -> {
            if (!handler.isUndef()) {
                resultHandler.setValue(handler.getValue());
            }
        });

        return resultHandler.getValue();
    }

    public static MemCache merge(MemCache lhs, MemCache rhs) {
        final var result = MemCache.empty();

        final var commonKeys = lhs.cache.keySet();
        commonKeys.retainAll(rhs.cache.keySet());

        for (final var key : commonKeys) {
            final var lhsHandler = lhs.cache.get(key);
            final var rhsHandler = rhs.cache.get(key);
            result.cache.put(key, MemHandler.merge(lhsHandler, rhsHandler));
        }

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MemCache) {
            final var memCache = (MemCache) obj;
            return cache.equals(memCache.cache);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return cache.hashCode();
    }

    static class IndicesInfo {
        public IndicesInfo(MemHandler handler, boolean isExhausted) {
            this.handler = handler;
            this.isExhausted = isExhausted;
        }

        public final MemHandler handler;
        public final boolean isExhausted;
    }
    private IndicesInfo getInfoFromGEP(GEPInst inst, MemHandler init) {
        final var indices = inst.getIndices();

        var handler = init;
        var isExhausted = true;

        for (final var index : indices) {
            if (index instanceof IntConst) {
                final var ic = (IntConst) index;
                handler = handler.get(ic.getValue());
            } else {
                isExhausted = false;
                break;
            }
        }

        return new IndicesInfo(handler, isExhausted);
    }
    private IndicesInfo getInfoFromGEP(GEPInst inst, MemPosition pos) {
        return getInfoFromGEP(inst, getInitHandler(pos));
    }
    private Optional<IndicesInfo> getInfoFromGEP(GEPInst inst) {
        return MemPosition.createWithPointer(inst).map(pos -> getInfoFromGEP(inst, pos));
    }

    private MemHandler getInitHandler(MemPosition position) {
        cache.computeIfAbsent(position, pos -> new MemHandler());
        return cache.get(position);
    }

    private MemCache(MemCache other) {
        this.cache = new HashMap<>(other.cache);
    }

    private MemCache() {
        this.cache = new HashMap<>();
    }

    Map<MemPosition, MemHandler> cache;
}

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

            if (gepPtr instanceof LoadInst) {
                final var load = (LoadInst) gepPtr;
                Log.ensure(load.getPtr() instanceof GlobalVar);
                return Optional.of(new MemPosition(LocationKind.GlobalArray, load.getPtr()));

            } else if (gepPtr instanceof CAllocInst) {
                final var calloc = (CAllocInst) gepPtr;
                return Optional.of(new MemPosition(LocationKind.LocalArray, calloc));

            } else if (gepPtr instanceof Parameter) {
                final var param = (Parameter) gepPtr;
                Log.ensure(param.getType().isPtr());
                return Optional.of(new MemPosition(LocationKind.LocalArray, param));

            } else {
                throw new RuntimeException("Unknown structure: (GEP " + gepPtr + ")");
            }

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

    enum LocationKind { GlobalVariable, GlobalArray, LocalArray }
    private LocationKind kind;
    private Value location;
}

class MemHandler {
    MemHandler() {
        setUndef();
    }

    MemHandler(Instruction insruction) {
        setValue(insruction);
    }

    MemHandler(Constant constant) {
        if (constant instanceof ArrayConst) {
            this.value = null;
            this.elements = new HashMap<>();

            final var elms = ((ArrayConst) constant).getRawElements();
            final var size = elms.size();
            for (int i = 0; i < size; i++) {
                final var handler = new MemHandler(elms.get(i));
                elements.put(i, handler);
            }

        } else {
            this.value = constant;
            this.elements = null;
        }
    }

    public MemHandler get(int idx) {
        Log.ensure(!isVariable(), "get can only be called on array (or undef)");
        // 如果本身是 undef 或者是 Array 但是没有对应位置的缓存就直接返回一个新的 undef
        return Optional.ofNullable(elements)
            .map(elms -> elms.get(idx))
            .orElse(new MemHandler());
    }

    public Value getValue() {
        Log.ensure(isVariable(), "getValue can only be called on variable");
        return value;
    }

    public boolean isUndef() {
        return elements == null && value == null;
    }

    public boolean isArray() {
        return elements != null && value == null;
    }

    public boolean isVariable() {
        return elements == null && value != null;
    }

    public void setValue(Value value) {
        this.elements = null;
        this.value = value;
    }

    public void setUndef() {
        this.elements = null;
        this.value = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MemHandler) {
            final var handler = (MemHandler) obj;
            if (this.isArray() && handler.isArray()) {
                return elements.equals(handler.elements);
            } else if (this.isVariable() && handler.isVariable()) {
                return value.equals(handler.value);
            } else {
                return this.isUndef() && handler.isUndef();
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        final var valueHashCode = value == null ? 0 : value.hashCode();
        final var elementsHashCode = elements == null ? 0 : elements.hashCode();
        return valueHashCode * 37 + elementsHashCode;
    }

    public boolean canMerge(MemHandler other) {
        // 有一个是 Undef 或者两个的类型相同就可以合并
        return this.isUndef() || other.isUndef()
            || this.isArray() && other.isArray()
            || this.isVariable() && other.isVariable();
    }

    public static MemHandler merge(MemHandler lhs, MemHandler rhs) {
        if (lhs.isUndef() || rhs.isUndef()) {
            return new MemHandler();
        } else if (lhs.isArray() && rhs.isArray()) {
            final var commonKeys = lhs.elements.keySet();
            commonKeys.retainAll(rhs.elements.keySet());

            final var elements = new HashMap<Integer, MemHandler>();
            for (final var key : commonKeys) {
                final var lhsValue = lhs.elements.get(key);
                final var rhsValue = rhs.elements.get(key);
                elements.put(key, merge(lhsValue, rhsValue));
            }

            return new MemHandler(null, elements);
        } else if (lhs.isVariable() && rhs.isVariable()) {
            final var lhsValue = lhs.getValue();
            final var rhsValue = rhs.getValue();
            return lhsValue == rhsValue ? new MemHandler(lhsValue, null) : new MemHandler();
        } else {
            throw new RuntimeException("Cannot merge two handler");
        }
    }

    private MemHandler(Value value, Map<Integer, MemHandler> elements) {
        this.value = value;
        this.elements = elements;
    }
    public MemHandler copy() {
        return new MemHandler(value, new HashMap<>(elements));
    }

    private Map<Integer, MemHandler> elements;
    private Value value;
}
