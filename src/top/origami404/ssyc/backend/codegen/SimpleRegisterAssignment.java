package top.origami404.ssyc.backend.codegen;

import top.origami404.ssyc.backend.arm.*;
import top.origami404.ssyc.backend.operand.*;
import top.origami404.ssyc.utils.Log;

import java.util.*;

public class SimpleRegisterAssignment {

    /**
     * 虚拟寄存器到被分配的物理寄存器的映射
     */
    Map<Reg, Reg> virtualPhyReg;
    /**
     * 维护溢出导致的寄存器到内存的映射
     */
    Map<Reg, MemPool.Mem> regMemMap;
    /**
     * 维护目前被占用的物理寄存器到占用其的虚拟(物理)寄存器的映射
     */
    Map<Reg, Reg> currentUsedRegMap;
    /**
     * I 物理寄存器池
     */
    RegPool<IPhyReg> iPhyRegPool;
    /**
     * F 物理寄存器池
     */

    RegPool<FPhyReg> fPhyRegPool;
    MemPool memPool;
    ArmInst currentInst;
    int offset;
    List<IPhyReg> allIPhyRegs = new ArrayList<>(){{
        for (int i = 0; i <= 12; i += 1) {
            add(new IPhyReg(i));
        }
        add(new IPhyReg(14));
    }};
    List<FPhyReg> allFPhyRegs = new ArrayList<>(){{
        for (int i = 0; i <= 7; i++) {
            add(new FPhyReg(i));
        }
    }};

    public void Init() {
//        spiltReg = new HashMap<>();
        regMemMap = new HashMap<>();
        iPhyRegPool = new RegPool<>(allIPhyRegs);
        fPhyRegPool = new RegPool<>(allFPhyRegs);
        virtualPhyReg = new HashMap<>();
        memPool = new MemPool();
    }

    public Map<Reg, Reg> getAssignMap(ArmFunction armFunction) {
        Init();

        offset = armFunction.getStackSize();
        for (var block : armFunction.getIList()) {
            assignBlock(block);
        }
        armFunction.addStackSize(memPool.getSize());
        return virtualPhyReg;
    }

    /**
     * 对每个基本块进行寄存器分配<br>
     * 朴素算法: 进入基本块后按需 load 之前溢出的寄存器, 出基本块后将所有寄存器溢出
     *
     * @param armBlock 进行分配操作的基本块
     */
    private void assignBlock(ArmBlock armBlock) {
        //块内需维护虚拟寄存器到 Imm 的映射
        for (var armInst : armBlock.getIList()) {
            assignInst(armInst);
        }
        pushAllReg();
    }

    private void assignInst(ArmInst armInst) {
        currentInst = armInst;
        // 处理使用寄存器
        // 先处理物理寄存器避免分配冲突
        List<Reg> usedRegs = new ArrayList<>();
        for (var regUse : armInst.getRegUse()) {
            if (regUse instanceof IPhyReg) {
                if (usedRegs.contains(regUse)) {
                    break;
                } else {
                    usedRegs.add(regUse);
                }

                final var iPhyReg = (IPhyReg) regUse;
                if (iPhyReg.getId() == 13 || iPhyReg.getId() > 14) {
                    break;
                }
                if (currentUsedRegMap.containsValue(iPhyReg)) {
                    break;
                }

                if (iPhyRegPool.find(iPhyReg).isEmpty()) {
                    push(currentUsedRegMap.remove(regUse));
                }
                final var optionalReg = iPhyRegPool.findAndTake(iPhyReg);
                Log.ensure(optionalReg.isPresent());
                pop(iPhyReg);
                assign(iPhyReg, iPhyReg);

            } else if (regUse instanceof FPhyReg) {
                if (usedRegs.contains(regUse)) {
                    break;
                } else {
                    usedRegs.add(regUse);
                }

                final var fPhyReg = (FPhyReg) regUse;
                if (currentUsedRegMap.containsValue(fPhyReg)) {
                    break;
                }
                if (fPhyRegPool.find(fPhyReg).isEmpty()) {
                    push(currentUsedRegMap.remove(regUse));
                }
                final var optionalReg = fPhyRegPool.findAndTake(fPhyReg);
                Log.ensure(optionalReg.isPresent());
                pop(fPhyReg);
                assign(fPhyReg, fPhyReg);
            }
        }

        // 处理定值寄存器
        // 物理定值寄存器
        for (var regDef : armInst.getRegDef()) {
            if (regDef instanceof IPhyReg) {
                if (usedRegs.contains(regDef)) {
                    break;
                } else {
                    usedRegs.add(regDef);
                }
                final var iPhyReg = (IPhyReg) regDef;
                if (iPhyReg.getId() == 13 || iPhyReg.getId() > 14) {
                    break;
                }
                if (currentUsedRegMap.containsValue(iPhyReg)) {
                    break;
                }

                if (iPhyRegPool.find(iPhyReg).isEmpty()) {
                    push(currentUsedRegMap.remove(regDef));
                }
                final var optionalReg = iPhyRegPool.findAndTake(iPhyReg);
                Log.ensure(optionalReg.isPresent());
                //直接使用, 无需pop
                clear(iPhyReg);
                assign(iPhyReg, iPhyReg);
            } else if (regDef instanceof FPhyReg) {
                if (usedRegs.contains(regDef)) {
                    break;
                } else {
                    usedRegs.add(regDef);
                }

                final var fPhyReg = (FPhyReg) regDef;
                if (currentUsedRegMap.containsValue(fPhyReg)) {
                    break;
                }
                if (fPhyRegPool.find(fPhyReg).isEmpty()) {
                    push(currentUsedRegMap.remove(regDef));
                }
                final var optionalReg = fPhyRegPool.findAndTake(fPhyReg);
                Log.ensure(optionalReg.isPresent());
                //直接使用, 无需pop
                clear(fPhyReg);
                assign(fPhyReg, fPhyReg);
            }
        }
        // 分配虚拟寄存器
        for (var regUse : armInst.getRegUse()) {
             if (regUse instanceof IVirtualReg) {
                 if (usedRegs.contains(regUse)) {
                     break;
                 } else {
                     usedRegs.add(regUse);
                 }

                final var iVReg = (IVirtualReg) regUse;
                 if (currentUsedRegMap.containsValue(iVReg)) {
                     break;
                 }
                 if (iPhyRegPool.isAllTaken()) {
                     pushAnyIV();
                 }
                 final var optionalReg = iPhyRegPool.take();
                 Log.ensure(optionalReg.isPresent());
                 pop(iVReg);
                 assign(iVReg, optionalReg.get());

            } else if (regUse instanceof FVirtualReg) {
                 if (usedRegs.contains(regUse)) {
                     break;
                 } else {
                     usedRegs.add(regUse);
                 }

                final var fVReg = (FVirtualReg) regUse;
                 if (currentUsedRegMap.containsValue(fVReg)) {
                     break;
                 }
                 if (fPhyRegPool.isAllTaken()) {
                     pushAnyFV();
                 }
                 final var optionalReg = fPhyRegPool.take();
                 Log.ensure(optionalReg.isPresent());
                 pop(fVReg);
                 assign(fVReg, optionalReg.get());
            }
        }



        // 虚拟定值寄存器
        for (var regDef : armInst.getRegDef()) {
            if (regDef instanceof IVirtualReg) {
                if (usedRegs.contains(regDef)) {
                    break;
                } else {
                    usedRegs.add(regDef);
                }

                final var iVReg = (IVirtualReg) regDef;
                if (currentUsedRegMap.containsValue(iVReg)) {
                    break;
                }
                if (iPhyRegPool.isAllTaken()) {
                    pushAnyIV();
                }
                final var optionalReg = iPhyRegPool.take();
                Log.ensure(optionalReg.isPresent());
                //直接使用, 无需pop
                clear(iVReg);
                assign(iVReg, optionalReg.get());

            } else if (regDef instanceof FVirtualReg) {
                if (usedRegs.contains(regDef)) {
                    break;
                } else {
                    usedRegs.add(regDef);
                }

                final var fVReg = (FVirtualReg) regDef;
                if (currentUsedRegMap.containsValue(fVReg)) {
                    break;
                }
                if (fPhyRegPool.isAllTaken()) {
                    pushAnyFV();
                }
                final var optionalReg = fPhyRegPool.take();
                Log.ensure(optionalReg.isPresent());
                //直接使用, 无需pop
                clear(fVReg);
                assign(fVReg, optionalReg.get());
            }
        }

    }

    private void pushAllReg() {
        for (var reg : currentUsedRegMap.values()) {
            final var mem = memPool.take();
            Reg currentReg = reg;
            if (reg instanceof IVirtualReg) {
                currentReg = new IVirtualReg();
                reg.replaceRegAfter(currentInst, currentReg);
            } else if (reg instanceof FVirtualReg) {
                currentReg = new FVirtualReg();
                reg.replaceRegAfter(currentInst, currentReg);
            }
            regMemMap.put(currentReg, mem);
            currentInst.insertAfterCO(new ArmInstStore(
                    reg, new IPhyReg("sp"), new IImm(offset + mem.offset)
            ));
        }
    }


    /**
     *
     * 维护映射
     * 不维护 currentusedRegMap
     * @param reg
     */
    private void push(Reg reg) {
        final var mem = memPool.take();
        Reg currentReg = reg;
        if (reg instanceof IVirtualReg) {
            currentReg = new IVirtualReg();
            reg.replaceRegAfter(currentInst, currentReg);
        } else if (reg instanceof FVirtualReg) {
            currentReg = new FVirtualReg();
            reg.replaceRegAfter(currentInst, currentReg);
        }
        regMemMap.put(currentReg, mem);
        currentInst.insertBeforeCO(new ArmInstStore(
                reg, new IPhyReg("sp"), new IImm(offset + mem.offset)
        ));
    }

    private void pushAnyFV() {
        final var fPhyReg = fPhyRegPool.freeAny();
        final var fVReg = currentUsedRegMap.get(fPhyReg);
        push(fVReg);

    }

    private void pushAnyIV() {
        final var iPhyReg = iPhyRegPool.freeAny();
        final var iVReg = currentUsedRegMap.get(iPhyReg);
        push(iVReg);
    }

    /**
     * 不维护映射
     * 有pop后必定进行一次assign, 占用映射在 assign 维护
     * @param reg
     */
    private void pop(Reg reg) {
        final var mem = regMemMap.remove(reg);
        memPool.put(mem);
        currentInst.insertBeforeCO(new ArmInstLoad(
                reg, new IPhyReg("sp"), new IImm(offset + mem.getOffset())
        ));
    }

    private void clear(Reg reg) {
        final var mem = regMemMap.remove(reg);
        memPool.put(mem);
    }


    private void assign(Reg Vreg, Reg Preg) {
        currentUsedRegMap.put(Preg, Vreg);
        virtualPhyReg.put(Vreg, Preg);
    }
}
