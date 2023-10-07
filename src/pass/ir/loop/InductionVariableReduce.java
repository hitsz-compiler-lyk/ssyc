package pass.ir.loop;

import frontend.SourceCodeSymbol;
import ir.BasicBlock;
import ir.Module;
import ir.Value;
import ir.constant.IntConst;
import ir.inst.*;
import pass.ir.ConstructDominatorInfo;
import pass.ir.ConstructDominatorInfo.DominatorInfo;
import utils.CollectionTools;
import utils.Log;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InductionVariableReduce implements LoopPass {
    @Override
    public void runPass(final Module module) {
        new ConstructDominatorInfo().runPass(module);
        LoopPass.super.runPass(module);
    }

    @Override
    public void runOnLoop(final CanonicalLoop loop) {
        final var forLoop = ForLoop.tryConvertFrom(loop);
        if (forLoop == null) {
            return;
        }

        final var transformer = new InductionVariableTransformer(forLoop);
        transformer.run();
    }
}

class InductionVariableTransformer implements Runnable {
    private final ForLoop loop;
    private final Value indexInit;
    private final Value indexStep;
    private final BasicBlock preHeader;
    private final BasicBlock header;
    private final BasicBlock latch;

    public InductionVariableTransformer(ForLoop loop) {
        this.loop = loop;
        this.indexInit = loop.getBegin();
        this.indexStep = loop.getStep();
        this.preHeader = loop.canonical().getPreHeader();
        this.header = loop.canonical().getHeader();
        this.latch = loop.canonical().getLatch();
    }

    @Override
    public void run() {
        // 先收集
        final Set<Value> mulForms    = instructionInBody().filter(this::isMulForm)      .collect(Collectors.toCollection(LinkedHashSet::new));
        final Set<Value> mulAddForms = instructionInBody().filter(this::isMulAddForm)   .collect(Collectors.toCollection(LinkedHashSet::new));
        final Set<Value> gepForms    = instructionInBody().filter(this::isGEPForm)      .collect(Collectors.toCollection(LinkedHashSet::new));

        // 然后移除那些是上一层次的构成部分的 forms
        mulForms.removeIf(mul -> mulAddForms.containsAll(mul.getUserList()));
        mulAddForms.removeIf(mulAdd -> gepForms.containsAll(mulAdd.getUserList()));

        // 然后移除那些只会在循环的某些分支里出现的语句
        // TODO: 判断常见分支? 感觉毫无头绪啊
        mulForms.removeIf(this::doNotDomLatch);
        mulAddForms.removeIf(this::doNotDomLatch);
        gepForms.removeIf(this::doNotDomLatch);

        // 然后依次转换
        gepForms.forEach(this::transformGEP);
        mulAddForms.forEach(this::transformMulAdd);
        mulForms.forEach(this::transformMul);

        header.adjustPhiEnd();
    }

    boolean doNotDomLatch(Value value) {
        Log.ensure(value instanceof Instruction);
        final var block = ((Instruction) value).getParent();

        return !DominatorInfo.dom(latch).contains(block);
    }

    void transformGEP(Value gep) {
        final var extractor = new GEPInfoExtractor(gep);
        final var ptr = extractor.getPtr();
        final var invariantIndex = extractor.getInvariantIndices();
        final var variantIndex = extractor.getVariantIndex();

        final var phi = extractor.getEmptyPhi();
        gep.replaceAllUseWith(phi);
        header.addPhi(phi);

        // 也许应该直接抽一个返回两个指令的函数的
        // 不过没 record 类 Java 写多返回值超~~麻烦的, 还是直接这样吧
        // 既然语言提供了变量作为抽象工具那就要用嘛
        // 不过我觉得只有在值会变但是意思不会变的时候才能用变量哦
        GEPInst init = null, body = null;

        // 超长分类讨论! 高考大题!
        /*
         * 根据变化索引的类型的不同, 有不同的处理方式:
         *                  body                init
         * 变化的索引类型   每次要移动的距离     初始要移动的距离
         *  phi         ==> step,           begin
         *  mul         ==> factor * step,  factor * begin
         *  mulAdd      ==> factor * step,  factor * begin + offset
         *
         * 因为对 init/body 指令的处理是相同的, 所以统一在下面的 if 里生成完了之后通过变量传出去
         */
        if (isPhiForm(variantIndex)) {
            final var indices = CollectionTools.concatTail(invariantIndex, indexInit);
            init = new GEPInst(ptr, indices);
            body = new GEPInst(phi, List.of(indexStep));
        } else if (isAddForm(variantIndex)) {
            final var addExtractor = new AddInfoExtractor(variantIndex);

            final var initAdd = createAdd(indexInit, addExtractor.getOffset());
            preHeader.addInstBeforeTerminator(initAdd);

            final var indices = CollectionTools.concatTail(invariantIndex, initAdd);
            init = new GEPInst(ptr, indices);

            body = new GEPInst(phi, List.of(indexStep));
        } else if (isMulForm(variantIndex)) {
            final var mulExtractor = new MulInfoExtractor(variantIndex);

            final var initMul = createMul(mulExtractor.getFactor(), indexInit);
            preHeader.addInstBeforeTerminator(initMul);

            final var indices = CollectionTools.concatTail(invariantIndex, initMul);
            init = new GEPInst(ptr, indices);

            final var stepMul = createMul(mulExtractor.getFactor(), indexStep);
            preHeader.addInstBeforeTerminator(stepMul);
            body = new GEPInst(phi, List.of(stepMul));

        } else if (isMulAddForm(variantIndex)) {
            final var mulAddExtractor = new MulAddInfoExtractor(variantIndex);

            final var initMul = createMul(mulAddExtractor.getFactor(), indexInit);
            final var initAdd = createAdd(mulAddExtractor.getOffset(), initMul);
            preHeader.addInstBeforeTerminator(initMul);
            preHeader.addInstBeforeTerminator(initAdd);

            final var indices = CollectionTools.concatTail(invariantIndex, initAdd);
            init = new GEPInst(ptr, indices);

            final var stepMul = createMul(mulAddExtractor.getFactor(), indexStep);
            preHeader.addInstBeforeTerminator(stepMul);

            body = new GEPInst(phi, List.of(stepMul));

        } else {
            Log.ensure(false);
            assert false;
        }

        // init 要加到 preHeader, body 要替代原 gep, phi 要设置 incoming
        preHeader.addInstBeforeTerminator(init);
        insertBodyAndRemoveOld(gep, body);

        phi.setIncomingCO(List.of(init, body));
    }

    void transformMulAdd(Value mulAdd) {
        final var extractor = new MulAddInfoExtractor(mulAdd);
        final var factor = extractor.getFactor();
        final var offset = extractor.getOffset();

        final var phi = extractor.getEmptyPhi();
        mulAdd.replaceAllUseWith(phi);
        header.addPhi(phi);

        final var initMul = createMul(indexInit, factor);
        final var initAdd = createAdd(initMul, offset);
        preHeader.addInstBeforeTerminator(initMul);
        preHeader.addInstBeforeTerminator(initAdd);

        final var newStep = createMul(indexStep, factor);
        preHeader.addInstBeforeTerminator(newStep);

        final var body = createAdd(phi, newStep);
        insertBodyAndRemoveOld(mulAdd, body);

        phi.setIncomingCO(List.of(initAdd, body));
    }

    void transformMul(Value mul) {
        final var extractor = new MulInfoExtractor(mul);
        final var factor = extractor.getFactor();

        final var phi = extractor.getEmptyPhi();
        mul.replaceAllUseWith(phi);
        header.addPhi(phi);

        final var init = createMul(indexInit, factor);
        preHeader.addInstBeforeTerminator(init);

        final var newStep = createMul(indexStep, factor);
        preHeader.addInstBeforeTerminator(newStep);

        final var body = createAdd(phi, newStep);
        insertBodyAndRemoveOld(mul, body);

        phi.setIncomingCO(List.of(init, body));
    }

    private BinaryOpInst createMul(Value lhs, Value rhs) {
        return new BinaryOpInst(InstKind.IMul, lhs, rhs);
    }

    private BinaryOpInst createAdd(Value lhs, Value rhs) {
        return new BinaryOpInst(InstKind.IAdd, lhs, rhs);
    }

    private void insertBodyAndRemoveOld(Value oldValue, Instruction body) {
        final var inst = (Instruction) oldValue;
        final var block = inst.getParent();

        block.addInstBeforeTerminator(body);
        inst.freeAll();
    }

    boolean isPhiForm(Value value) {
        return value == loop.getIndexPhi();
    }

    boolean isAddForm(Value value) {
        if (value instanceof BinaryOpInst) {
            final var bop = (BinaryOpInst) value;
            return bop.getKind() == InstKind.IAdd
                    && isPhiForm(bop.getLHS())
                    && isInvariant(bop.getRHS());
        }

        return false;
    }

    boolean isMulForm(Value value) {
        if (value instanceof final BinaryOpInst bop) {
            return bop.getKind() == InstKind.IMul
                && isPhiForm(bop.getLHS())
                && isInvariant(bop.getRHS());
        }

        return false;
    }

    boolean isMulAddForm(Value value) {
        if (value instanceof final BinaryOpInst bop) {
            return bop.getKind() == InstKind.IAdd
                && isMulForm(bop.getLHS())
                && isInvariant(bop.getRHS());
        }

        return false;
    }

    boolean isGEPForm(Value value) {
        if (value instanceof final GEPInst gep) {

            final var indices = gep.getIndices();
            final var head = CollectionTools.head(indices);
            final var tail = CollectionTools.tail(indices);

            return isInvariant(gep.getPtr())
                && head.stream().allMatch(this::isInvariant)
                && (isPhiForm(tail) || isAddForm(tail) || isMulForm(tail) || isMulAddForm(tail));
        }

        return false;
    }

    private Stream<Instruction> instructionInBody() {
        return loop.canonical().getBody().stream().flatMap(List::stream);
    }

    private boolean isInvariant(Value value) {
        return loop.getInfo().isInvariant(value);
    }

    private static SourceCodeSymbol getSymbolOrElse(Instruction instruction, String name) {
        if (instruction.getSymbolOpt().isPresent()) {
            return instruction.getSymbol().newSymbolWithSuffix("_mul");
        } else {
            return new SourceCodeSymbol(name, 0, 0);
        }
    }

    private String getNameFor(Value value) {
        if (value instanceof IntConst) {
            return value.toString();
        } else if (value instanceof final Instruction inst) {
            return inst.getSymbolOpt().map(SourceCodeSymbol::getName).orElse("U");
        } else {
            return "U";
        }
    }

    class AddInfoExtractor {
        public AddInfoExtractor(Value add) {
            this.add = (BinaryOpInst) add;
        }

        public Value getOffset() {
            return add.getRHS();
        }

        private BinaryOpInst add;
    }

    class MulInfoExtractor {
        public MulInfoExtractor(Value mul) {
            this.mul = (BinaryOpInst) mul;
        }

        public Value getFactor() {
            return mul.getRHS();
        }

        public PhiInst getEmptyPhi() {
            return new PhiInst(mul.getType(), getSymbol());
        }

        public SourceCodeSymbol getSymbol() {
            final var indexName = loop.getIndexPhi().getSymbol().getName();
            final var factorName = getNameFor(getFactor());
            final var name = "%s_%s_muladd".formatted(indexName, factorName);

            return getSymbolOrElse(mul, name);
        }

        private final BinaryOpInst mul;
    }

    class MulAddInfoExtractor {
        public MulAddInfoExtractor(Value mulAdd) {
            this.mulAdd = (BinaryOpInst) mulAdd;
            this.mulExtractor = new MulInfoExtractor(this.mulAdd.getLHS());
        }

        public Value getFactor() {
            return mulExtractor.getFactor();
        }

        public Value getOffset() {
            return mulAdd.getRHS();
        }

        public PhiInst getEmptyPhi() {
            return new PhiInst(mulAdd.getType(), getSymbol());
        }

        public SourceCodeSymbol getSymbol() {
            final var indexName = loop.getIndexPhi().getSymbol().getName();
            final var factorName = getNameFor(getFactor());
            final var offsetName = getNameFor(getOffset());
            final var name = "%s_%s_%s_muladd".formatted(indexName, factorName, offsetName);

            return getSymbolOrElse(mulAdd, name);
        }

        private final BinaryOpInst mulAdd;
        private final MulInfoExtractor mulExtractor;
    }

    static class GEPInfoExtractor {
        public GEPInfoExtractor(Value gep) {
            this.gep = (GEPInst) gep;
        }

        public Value getPtr() {
            return gep.getPtr();
        }

        public List<Value> getInvariantIndices() {
            return CollectionTools.head(gep.getIndices());
        }

        public Value getVariantIndex() {
            return CollectionTools.tail(gep.getIndices());
        }

        public String getName() {
            final var baseName = getPtr().getSymbol().getName();
            return "%s_gep".formatted(baseName);
        }

        public PhiInst getEmptyPhi() {
            return new PhiInst(gep.getType(), getSymbolOrElse(gep, getName()));
        }

        private final GEPInst gep;
    }
}

