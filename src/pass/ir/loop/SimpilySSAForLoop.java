package pass.ir.loop;

import frontend.SourceCodeSymbol;
import ir.inst.BinaryOpInst;
import ir.inst.Instruction;
import ir.inst.PhiInst;
import utils.Log;

import java.util.*;

/**
 * 会把 SSA 变成 LCSSA, 同时做出一些方便其它 pass 处理 loop 的不改变原意的修改
 */
public class SimpilySSAForLoop implements LoopPass {
    @Override
    public void runOnLoop(final CanonicalLoop loop) {
        final var that = new SimplifierForALLoop(loop);
        that.findOutsideUsage();
        that.usePhiInExitToReplaceOutsideUse();
        that.moveInvariantToBinopRight();
    }
}

class SimplifierForALLoop {
    void findOutsideUsage() {
        for (final var block : loop.getAll()) {
            for (final var inst : block) {
                for (final var user_ : inst.getUserList()) {
                    Log.ensure(user_ instanceof Instruction);
                    assert user_ instanceof Instruction;
                    final var user = ((Instruction) user_);
                    addUserFor(inst, user);
                }
            }
        }
    }

    void usePhiInExitToReplaceOutsideUse() {
        final var exit = loop.getUniqueExit();
        for (final var entry : outOfLoopUsers.entrySet()) {
            final var beUsedOutside = entry.getKey();
            final var users = entry.getValue();

            final var symbol = beUsedOutside.getSymbolOpt()
                .map(sym -> sym.newSymbolWithSuffix("_lcssa"))
                .orElse(new SourceCodeSymbol("lcssa", 0, 0));

            final var phi = new PhiInst(beUsedOutside.getType(), symbol);
            phi.setIncomingCO(List.of(beUsedOutside));

            exit.add(phi);

            for (final var user : users) {
                user.replaceOperandCO(beUsedOutside, phi);
            }
        }
    }

    void moveInvariantToBinopRight() {
        for (final var block : loop.getAll()) {
            for (final var inst : block) {
                if (inst instanceof BinaryOpInst) {
                    final var bop = ((BinaryOpInst) inst);
                    final var lhs = bop.getLHS();
                    final var rhs = bop.getRHS();

                    if (info.isInvariant(lhs) && info.isVariants(rhs)) {
                        bop.replaceLHS(rhs);
                        bop.replaceRHS(lhs);
                    }
                }
            }
        }
    }

    private void addUserFor(Instruction instInLoop, Instruction userOutside) {
        outOfLoopUsers.computeIfAbsent(instInLoop, i -> new LinkedHashSet<>());
        outOfLoopUsers.get(instInLoop).add(userOutside);
    }

    SimplifierForALLoop(CanonicalLoop loop) {
        this.loop = loop;
        this.info = LoopInvariantInfo.collect(loop);
    }

    private final CanonicalLoop loop;
    private final LoopInvariantInfo info;
    private final Map<Instruction, Set<Instruction>> outOfLoopUsers = new HashMap<>();
}
