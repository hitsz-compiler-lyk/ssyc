package pass.ir.memory;

import ir.GlobalVar;
import ir.Value;
import ir.constant.IntConst;
import ir.inst.*;
import utils.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

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

    public void setByGlobalVar(GlobalVar array) {
        final var pos = MemPosition.createWithGlobalVariable(array);
        final var handler = new MemHandler(array.getInit());

        // A global array can only be at when it is still undefined
        Log.ensure(!cache.containsKey(pos));
        cache.put(pos, handler);
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
