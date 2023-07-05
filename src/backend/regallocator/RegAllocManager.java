package backend.regallocator;

import utils.ImmUtils;
import backend.lir.ArmFunction;
import backend.lir.ArmModule;
import backend.lir.inst.*;
import backend.lir.operand.*;
import utils.Log;

import java.util.*;
import java.util.function.BiFunction;

/**
 * 去除 LIR 中的所有虚拟寄存器
 * <br>
 * 其会进行寄存器分配以提高性能
 */
public class RegAllocManager {
    private final RegAllocator regAllocator = new SimpleGraphColoring();
    private final ArmModule armModule;

    public RegAllocManager(ArmModule armModule) {
        this.armModule = armModule;
    }

    public void regAllocate() {
        for (var func : armModule.getFunctions()) {
            fixStack(func);
            boolean isFix = true;
            while (isFix) {
                var allocatorMap = regAllocator.run(func);
                for (var kv : allocatorMap.entrySet()) {
                    Log.ensure(kv.getKey().isVirtual(), "allocatorMap key not Virtual");
                    Log.ensure(kv.getValue().isPhy(), "allocatorMap value not Phy");
                }
                Set<IPhyReg> iPhyRegs = new HashSet<>();
                Set<FPhyReg> fPhyRegs = new HashSet<>();
                for (var block : func) {
                    for (var inst : block) {
                        for (var op : inst.getOperands()) {
                            assert op instanceof Reg;
                            if (allocatorMap.containsKey(op)) {
                                op = allocatorMap.get(op);
                            }
                            if (op instanceof IPhyReg) {
                                iPhyRegs.add((IPhyReg) op);
                            }
                            if (op instanceof FPhyReg) {
                                fPhyRegs.add((FPhyReg) op);
                            }
                        }
                    }
                }
                calcIUseRegs(func, iPhyRegs);
                calcFUseRegs(func, fPhyRegs);
                isFix = recoverRegAllocate(func);
                isFix |= fixStack(func);

                if (!isFix) {
                    for (var block : func) {
                        for (var inst : block) {
                            for (var op : inst.getOperands()) {
                                assert op instanceof Reg;
                                if (op.isVirtual()) {
                                    Log.ensure(allocatorMap.containsKey(op), "virtual reg:" + op.print() + " not exist in allocator map");
                                    inst.replaceOperand(op, allocatorMap.get(op));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean fixStack(ArmFunction func) {
        boolean isFix = false;
        int regCnt = func.getFUsedRegs().size() + func.getIUsedRegs().size();
        // 栈的位置要与8对齐
        int stackSize = (func.getStackSize() + 4 * regCnt + 4) / 8 * 8 - 4 * regCnt;
        func.setFinalStackSize(stackSize);
        stackSize = stackSize - func.getStackSize();
        Map<Integer, Integer> stackMap = new HashMap<>();
        var stackObject = func.getStackObject();
        var stackObjectOffset = func.getStackObjectOffset();
        // 反向构建栈的位置信息
        for (int i = stackObjectOffset.size() - 1; i >= 0; i--) {
            stackMap.put(stackObjectOffset.get(i), stackSize);
            stackSize += stackObject.get(i);
        }
        Map<Integer, Operand> stackAddrMap = new HashMap<>();
        Map<Operand, Integer> addrStackMap = new HashMap<>();
        Log.ensure(stackSize == func.getFinalStackSize(), "stack size error");

        int finalStackSize = stackSize;
        // 修复ArmInstAddr的寻址
        BiFunction<ArmInstAddr, Integer, Boolean> fixArmInstAddr = (inst, trueOffset) -> {
            boolean isInstFix = false;
            if (!ImmUtils.checkOffsetRange(trueOffset, inst.getDst())) {
                if (inst.getAddr().equals(IPhyReg.SP)) {
                    isInstFix = true;
                    for (var entry : stackAddrMap.entrySet()) {
                        var offset = entry.getKey();
                        var op = entry.getValue();
                        if (offset <= trueOffset && ImmUtils.checkOffsetRange(trueOffset - offset, inst.getDst())) {
                            inst.replaceAddr(op);
                            inst.setTrueOffset(new IImm(trueOffset - offset));
                            func.getSpillNodes().remove(op);
                            // 相当于当前节点改变了生命周期
                            break;
                        }
                    }
                    if (inst.getAddr().equals(IPhyReg.SP)) {
                        int addrTrueOffset = trueOffset / 1024 * 1024;
                        var vr = new IVirtualReg();
                        int instOffset = finalStackSize - addrTrueOffset;
                        var stackAddr = new ArmInstStackAddr(vr, new IImm(instOffset));
                        stackAddr.setTrueOffset(new IImm(addrTrueOffset));
                        inst.insertBeforeCO(stackAddr);
                        inst.replaceAddr(vr);
                        Log.ensure(trueOffset >= addrTrueOffset, "check offset is illegal");
                        Log.ensure(ImmUtils.checkOffsetRange(trueOffset - addrTrueOffset, inst.getDst()), "check offset is illegal");
                        inst.setTrueOffset(new IImm(trueOffset - addrTrueOffset));
                        stackAddrMap.put(addrTrueOffset, vr);
                        addrStackMap.put(vr, addrTrueOffset);
                        func.getStackAddrMap().put(vr, stackAddr);
                    }
                } else {
                    var addrTrueOffset = addrStackMap.get(inst.getAddr());
                    var nowTrueOffset = trueOffset - addrTrueOffset;
                    Log.ensure(ImmUtils.checkOffsetRange(nowTrueOffset, inst.getDst()), "check offset is illegal");
                    inst.setTrueOffset(new IImm(nowTrueOffset));
                }
            } else {
                inst.setTrueOffset(new IImm(trueOffset));
            }
            return isInstFix;
        };

        for (var block : func) {
            for (var inst : block) {
                if (inst instanceof ArmInstStackAddr stackAddr) {
                    // 修复基准地址
                    if (stackAddr.isCAlloc()) {
                        Log.ensure(stackMap.containsKey(stackAddr.getOffset().getImm()), "stack offset not present");
                        stackAddr.setTrueOffset(new IImm(stackMap.get(stackAddr.getOffset().getImm())));
                    } else if (stackAddr.isFix()) {
                        continue;
                    } else {
                        int oldTrueOffset = stackSize - stackAddr.getOffset().getImm();
                        int nowTrueOffset = (oldTrueOffset + 1023) / 1024 * 1024;
                        stackAddr.replaceOffset(new IImm(stackSize - nowTrueOffset));
                        stackAddr.setTrueOffset(new IImm(nowTrueOffset));
                        stackAddrMap.put(nowTrueOffset, stackAddr.getDst());
                        addrStackMap.put(stackAddr.getDst(), nowTrueOffset);
                    }
                } else if (inst instanceof ArmInstParamLoad paramLoad) {
                    int trueOffset = paramLoad.getOffset().getImm() + stackSize + 4 * regCnt;
                    isFix |= fixArmInstAddr.apply(paramLoad, trueOffset);
                } else if (inst instanceof ArmInstStackLoad stackLoad) {
                    Log.ensure(stackMap.containsKey(stackLoad.getOffset().getImm()), "stack offset not present");
                    int trueOffset = stackMap.get(stackLoad.getOffset().getImm());
                    isFix |= fixArmInstAddr.apply(stackLoad, trueOffset);
                } else if (inst instanceof ArmInstStackStore stackStore) {
                    Log.ensure(stackMap.containsKey(stackStore.getOffset().getImm()), "stack offset not present");
                    int trueOffset = stackMap.get(stackStore.getOffset().getImm());
                    isFix |= fixArmInstAddr.apply(stackStore, trueOffset);
                }
            }
        }
        return isFix;
    }

    private boolean recoverRegAllocate(ArmFunction func) {
        boolean isFix = false;
        Map<Operand, Operand> recoverMap = new HashMap<>();
        for (var block : func) {
            Map<Addr, Operand> addrMap = new HashMap<>();
            Map<IImm, Operand> offsetMap = new HashMap<>();
            Map<IImm, Operand> paramMap = new HashMap<>();
            Map<IImm, Operand> stackLoadMap = new HashMap<>();
            Map<Imm, Operand> immMap = new HashMap<>();
            var haveRecoverAddrs = block.getHaveRecoverAddrs();
            var haveRecoverOffset = block.getHaveRecoverOffset();
            var haveRecoverLoadParam = block.getHaveRecoverLoadParam();
            var haveRecoverImm = block.getHaveRecoverImm();
            var haveRecoverStackLoad = block.getHaveRecoverStackLoad();
            for (var inst : block) {
                if (inst instanceof ArmInstStackAddr stackAddr) {
                    // 恢复一个基本块内的基准地址
                    var offset = stackAddr.getOffset();
                    if (!stackAddr.getDst().isVirtual()) {
                        continue;
                    }
                    if (offsetMap.containsKey(offset)) {
                        recoverMap.put(stackAddr.getDst(), offsetMap.get(offset));
                        stackAddr.freeFromIList();
                        isFix = true;
                    } else if (!haveRecoverOffset.contains(offset)) {
                        haveRecoverOffset.add(offset);
                        offsetMap.put(offset, stackAddr.getDst());
                        func.getSpillNodes().remove(stackAddr.getDst());
                    }
                }
                if (inst instanceof ArmInstLoad load) {
                    // 恢复一个基本块内的Load指令
                    if (!(load.getAddr() instanceof Addr addr)) {
                        continue;
                    }
                    if (!load.getDst().isVirtual()) {
                        continue;
                    }
                    if (addrMap.containsKey(addr)) {
                        recoverMap.put(load.getDst(), addrMap.get(addr));
                        load.freeFromIList();
                        isFix = true;
                    } else if (!haveRecoverAddrs.contains(addr)) {
                        haveRecoverAddrs.add(addr);
                        addrMap.put(addr, load.getDst());
                        func.getSpillNodes().remove(load.getDst());
                    }
                }
                if (inst instanceof ArmInstParamLoad load) {
                    // 恢复一个基本块内的ParamLoad指令
                    if (!load.getAddr().equals(IPhyReg.SP)) {
                        continue;
                    }
                    if (!load.getDst().isVirtual()) {
                        continue;
                    }
                    var offset = load.getOffset();
                    if (paramMap.containsKey(offset)) {
                        recoverMap.put(load.getDst(), paramMap.get(offset));
                        load.freeFromIList();
                        isFix = true;
                    } else if (!haveRecoverLoadParam.contains(offset)) {
                        haveRecoverLoadParam.add(offset);
                        paramMap.put(offset, load.getDst());
                        func.getSpillNodes().remove(load.getDst());
                    }
                }
                if (inst instanceof ArmInstMove move) {
                    // 恢复一个基本块内的立即数赋值
                    if (!move.getDst().isVirtual()) {
                        continue;
                    }
                    if (!func.getImmMap().containsKey(move.getDst())) {
                        continue;
                    }
                    var imm = (Imm) move.getSrc();
                    if (immMap.containsKey(imm)) {
                        recoverMap.put(move.getDst(), immMap.get(imm));
                        move.freeFromIList();
                        isFix = true;
                    } else if (!haveRecoverImm.contains(imm)) {
                        haveRecoverImm.add(imm);
                        immMap.put(imm, move.getDst());
                        func.getSpillNodes().remove(move.getDst());
                    }
                }
                if (inst instanceof ArmInstStackLoad load) {
                    // 恢复一个基本块内的寄存器分裂的Load
                    if (!load.getAddr().equals(IPhyReg.SP)) {
                        continue;
                    }
                    if (!load.getDst().isVirtual()) {
                        continue;
                    }
                    var offset = load.getOffset();
                    if (stackLoadMap.containsKey(offset)) {
                        recoverMap.put(load.getDst(), stackLoadMap.get(offset));
                        load.freeFromIList();
                        isFix = true;
                    } else if (!haveRecoverStackLoad.contains(offset)) {
                        haveRecoverStackLoad.add(offset);
                        stackLoadMap.put(offset, load.getDst());
                        func.getSpillNodes().remove(load.getDst());
                    }
                }
                if (inst instanceof ArmInstStackStore store) {
                    // 这样利用Store的寄存器也可以给Load继续使用
                    // 理论上可以删除只保留最后一个，但不方便恢复
                    if (!store.getAddr().equals(IPhyReg.SP)) {
                        continue;
                    }
                    if (!store.getDst().isVirtual()) {
                        continue;
                    }
                    var offset = store.getOffset();
                    if (!haveRecoverStackLoad.contains(offset)) {
                        haveRecoverStackLoad.add(offset);
                        stackLoadMap.put(offset, store.getDst());
                        func.getSpillNodes().remove(store.getDst());
                        func.getStackLoadMap().put(store.getDst(), new ArmInstStackLoad(store.getDst(), offset));
                    }
                }
            }
        }
        for (var block : func) {
            for (var inst : block) {
                var ops = new ArrayList<>(inst.getOperands());
                for (var op : ops) {
                    if (recoverMap.containsKey(op)) {
                        inst.replaceOperand(op, recoverMap.get(op));
                    }
                }
            }
        }
        return isFix;
    }

    private void calcIUseRegs(ArmFunction func, Set<IPhyReg> regs) {
        var iUseRegs = func.getIUsedRegs();
        iUseRegs.clear();
        for (int i = 4; i <= 14; i++) {
            if (i == 13) {
                continue;
            }
            if (regs.contains(IPhyReg.R(i))) {
                iUseRegs.add(IPhyReg.R(i));
            }
        }
    }

    private void calcFUseRegs(ArmFunction func, Set<FPhyReg> regs) {
        var fUseRegs = func.getFUsedRegs();
        fUseRegs.clear();
        for (int i = 16; i <= 31; i++) {
            if (regs.contains(FPhyReg.S(i))) {
                fUseRegs.add(FPhyReg.S(i));
            }
        }
    }
}
