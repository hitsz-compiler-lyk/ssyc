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

/**
 * 记录数组/全局变量与其对应索引对应的内存位置的值
 */
class MemCache {
    public static MemCache empty() {
        return new MemCache();
    }

    public static MemCache copyFrom(MemCache other) {
        final var result = empty();
        for (final var entry : other.cache.entrySet()) {
            result.cache.put(entry.getKey(), entry.getValue().deepCopy());
        }

        return result;
    }

    public void setByInit(MemInitInst init) {
        final var pos = MemVariable.createWithMemInit(init);
        cache.put(pos, new MemPositionHandler(init.getInit()));
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
            .filter(MemVariable::isGlobal);

        final var localsInArg = call.getArgList().stream()
            .filter(arg -> arg.getType().isPtr())
            .map(MemVariable::createWithPointer)
            .filter(Optional::isPresent).map(Optional::get)
            .filter(MemVariable::isLocal);

        Stream.concat(globals, localsInArg)
            .map(this::getInitHandler).filter(Objects::nonNull)
            .forEach(MemPositionHandler::setUndef);
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
        final var pos = MemVariable.createWithGlobalVariable(array);
        final var handler = new MemPositionHandler(array.getInit());

        // A global array can only be at when it is still undefined
        Log.ensure(!cache.containsKey(pos));
        cache.put(pos, handler);
    }

    private void dealWithPointer(Value ptr, Consumer<IndicesInfo> whenGEP, Consumer<MemPositionHandler> whenGlobalVar) {
        MemVariable.createWithPointer(ptr).ifPresent(pos -> {
            final var handler = getInitHandler(pos);

            if (ptr instanceof final GEPInst gep) {
                final var info = getInfoFromGEP(gep, handler);
                whenGEP.accept(info);
            } else if (ptr instanceof final GlobalVar gv) {
                Log.ensure(gv.isVariable());
                whenGlobalVar.accept(handler);
            } else {
                Log.ensure(false, "Unknown structure in pointer: " + ptr);
            }
        });
    }

    public Value getByLoad(LoadInst load) {
        final var resultHandler = new MemPositionHandler(load);

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
            result.cache.put(key, MemPositionHandler.merge(lhsHandler, rhsHandler));
        }

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof final MemCache memCache) {
            return cache.equals(memCache.cache);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return cache.hashCode();
    }

    record IndicesInfo(MemPositionHandler handler, boolean isExhausted) {}

    private IndicesInfo getInfoFromGEP(GEPInst inst, MemPositionHandler init) {
        final var indices = inst.getIndices();

        var handler = init;
        var isExhausted = true;

        for (final var index : indices) {
            if (index instanceof final IntConst ic) {
                handler = handler.get(ic.getValue());
            } else {
                isExhausted = false;
                break;
            }
        }

        return new IndicesInfo(handler, isExhausted);
    }

    private IndicesInfo getInfoFromGEP(GEPInst inst, MemVariable pos) {
        return getInfoFromGEP(inst, getInitHandler(pos));
    }

    private Optional<IndicesInfo> getInfoFromGEP(GEPInst inst) {
        return MemVariable.createWithPointer(inst).map(pos -> getInfoFromGEP(inst, pos));
    }

    private MemPositionHandler getInitHandler(MemVariable position) {
        cache.computeIfAbsent(position, pos -> new MemPositionHandler());
        return cache.get(position);
    }

    private MemCache(MemCache other) {
        this.cache = new HashMap<>(other.cache);
    }

    private MemCache() {
        this.cache = new HashMap<>();
    }

    final Map<MemVariable, MemPositionHandler> cache;
}
