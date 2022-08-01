package top.origami404.ssyc.pass.ir;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.MemoryHandler;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import top.origami404.ssyc.ir.BasicBlock;
import top.origami404.ssyc.ir.GlobalVar;
import top.origami404.ssyc.ir.Module;
import top.origami404.ssyc.ir.Parameter;
import top.origami404.ssyc.ir.Value;
import top.origami404.ssyc.ir.constant.ArrayConst;
import top.origami404.ssyc.ir.constant.Constant;
import top.origami404.ssyc.ir.constant.FloatConst;
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

public class MemoryAnalysis implements IRPass {
    @Override
    public void runPass(Module module) {
        return;
    }

    static class MemoryDefinationInfo extends DataFlowInfo<MemCache> {}
    static class CollectMemoryDefination extends ForwardDataFlowPass<MemCache, MemoryDefinationInfo> {
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
            // TODO Auto-generated method stub
            return null;
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
        protected MemoryDefinationInfo createInfo(BasicBlock block) {
            return new MemoryDefinationInfo();
        }

        @Override
        protected Class<MemoryDefinationInfo> getInfoClass() {
            return MemoryDefinationInfo.class;
        }
    }

    static class MemCache {
        public static MemCache empty() {
            return new MemCache();
        }

        public static MemCache copyFrom(MemCache other) {
            return new MemCache(other);
        }

        public void setByInit(MemInitInst init) {
            final var pos = MemPosition.getPositionWithPointer(init.getArrayPtr()).orElseThrow();
            cache.put(pos, new MemHandler(init.getInit()));
        }

        public void setByStore(StoreInst store) {
            final var pos = MemPosition.getPositionWithPointer(store.getPtr()).orElseThrow();

            cache.computeIfAbsent(pos, key -> new MemHandler());
            final var handler = cache.get(pos);

            final var ptr = store.getPtr();
            if (ptr instanceof GEPInst) {
                final var info = getInfoFromGEP((GEPInst) ptr, handler);
                if (info.isExhausted) {
                    info.handler.setValue(store.getVal());
                } else {
                    info.handler.setUndef();
                }
            } else if (ptr instanceof GlobalVar) {
                final var gv = (GlobalVar) ptr;
                Log.ensure(!gv.getType().getBaseType().isPtr());

                handler.setValue(store.getVal());
            } else {
                Log.ensure(false, "Unknown structure: (Store " + ptr + ")");
            }
        }

        public void setByCall(CallInst call) {
            final var globals = cache.keySet().stream()
                .filter(MemPosition::isGlobal);

            final var localsInArg = call.getArgList().stream()
                .filter(arg -> arg.getType().isPtr())
                .map(MemPosition::getPositionWithPointer)
                .filter(Optional::isPresent).map(Optional::get)
                .filter(MemPosition::isLocal);

            Stream.concat(globals, localsInArg)
                .map(cache::get).filter(Objects::nonNull)
                .forEach(MemHandler::setUndef);
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

        private MemCache(MemCache other) {
            this.cache = new HashMap<>(other.cache);
        }

        private MemCache() {
            this.cache = new HashMap<>();
        }

        Map<MemPosition, MemHandler> cache;
    }

    static class MemPosition {
        public static Optional<MemPosition> getPositionWithPointer(Value ptr) {
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
                return this.kind == position.kind && this.location == position.location;
            } else {
                return false;
            }
        }

        enum LocationKind { GlobalVariable, GlobalArray, LocalArray }
        private LocationKind kind;
        private Value location;
    }

    static class MemHandler {
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
            final var result = elements.get(idx);
            return result == null ? new MemHandler() : result;
        }

        public Value getValue() {
            Log.ensure(isVariable());
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

        private Map<Integer, MemHandler> elements;
        private Value value;
    }
}
