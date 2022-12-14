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
        // εζΆι
        final Set<Value> mulForms    = instructionInBody().filter(this::isMulForm)      .collect(Collectors.toCollection(LinkedHashSet::new));
        final Set<Value> mulAddForms = instructionInBody().filter(this::isMulAddForm)   .collect(Collectors.toCollection(LinkedHashSet::new));
        final Set<Value> gepForms    = instructionInBody().filter(this::isGEPForm)      .collect(Collectors.toCollection(LinkedHashSet::new));

        // ηΆεη§»ι€ι£δΊζ―δΈδΈε±ζ¬‘ηζζι¨εη forms
        mulForms.removeIf(mul -> mulAddForms.containsAll(mul.getUserList()));
        mulAddForms.removeIf(mulAdd -> gepForms.containsAll(mulAdd.getUserList()));

        // ηΆεη§»ι€ι£δΊεͺδΌε¨εΎͺη―ηζδΊεζ―ιεΊη°ηθ―­ε₯
        // TODO: ε€ζ­εΈΈθ§εζ―? ζθ§ζ―«ζ ε€΄η»ͺε
        mulForms.removeIf(this::doNotDomLatch);
        mulAddForms.removeIf(this::doNotDomLatch);
        gepForms.removeIf(this::doNotDomLatch);

        // ηΆεδΎζ¬‘θ½¬ζ’
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

        // δΉθ?ΈεΊθ―₯η΄ζ₯ζ½δΈδΈͺθΏεδΈ€δΈͺζδ»€ηε½ζ°η
        // δΈθΏζ²‘ record η±» Java εε€θΏεεΌθΆ~~ιΊ»η¦η, θΏζ―η΄ζ₯θΏζ ·ε§
        // ζ’ηΆθ―­θ¨ζδΎδΊειδ½δΈΊζ½θ±‘ε·₯ε·ι£ε°±θ¦η¨ε
        // δΈθΏζθ§εΎεͺζε¨εΌδΌεδ½ζ―ζζδΈδΌεηζΆεζθ½η¨ειε¦
        GEPInst init = null, body = null;

        // θΆιΏεη±»θ?¨θ?Ί! ι«θε€§ι’!
        /*
         * ζ Ήζ?εεη΄’εΌηη±»εηδΈε, ζδΈεηε€ηζΉεΌ:
         *                  body                init
         * εεηη΄’εΌη±»ε   ζ―ζ¬‘θ¦η§»ε¨ηθ·η¦»     εε§θ¦η§»ε¨ηθ·η¦»
         *  phi         ==> step,           begin
         *  mul         ==> factor * step,  factor * begin
         *  mulAdd      ==> factor * step,  factor * begin + offset
         *
         * ε δΈΊε―Ή init/body ζδ»€ηε€ηζ―ηΈεη, ζδ»₯η»δΈε¨δΈι’η if ιηζε?δΊδΉειθΏειδΌ εΊε»
         */
        if (isPhiForm(variantIndex)) {
            final var indices = CollectionTools.concatTail(invariantIndex, indexInit);
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

        // init θ¦ε ε° preHeader, body θ¦ζΏδ»£ε gep, phi θ¦θ?Ύη½? incoming
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
                && (isPhiForm(tail) || isMulForm(tail) || isMulAddForm(tail));
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

