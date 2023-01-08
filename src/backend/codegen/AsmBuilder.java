package backend.codegen;

import backend.lir.ArmBlock;
import backend.lir.ArmShift;
import backend.lir.inst.ArmInst;
import backend.lir.inst.ArmInst.ArmCondType;
import backend.lir.operand.IImm;
import backend.lir.operand.Operand;

import java.util.Arrays;
import java.util.Set;

/**
 * 协助创建汇编文本指令的构建者, 采用流式风格的 API, 使文本指令的构建过程能被表示为链式调用, 以模拟其它语言中类型安全的可变参数的特性. <br>
 * 此类应专注于文本构建, 只保证生成的汇编文本的语法正确, 而不检查生成结果能否被汇编器接受
 */
class AsmBuilder {
    private enum State {
        FirstArg, RestArg, Normal
    }

    private static final String WORD_DELIMITER = "\t";
    private static final String INDENT = "\t";

    private final StringBuilder builder = new StringBuilder();

    /**
     * 构建者内部状态的维护, 主要用于区分指令生成模式和普通模式
     */
    private State state = State.Normal;

    /**
     * 当前汇编块内的汇编指令条数
     */
    private int blockInstCnt = 0;
    // 由于 ARMv7 中一些汇编指令的立即数部分要求在给定的指令条数内要存在一些特殊构造, 故维护此信息
    // 目前没有对此信息的使用, 预计在后续将基于此信息构建 ToAsmManage.placeLiteralPool 的新实现

    /**
     * 确保构建者的状态合理
     *
     * @param allowStates 当前允许的状态
     */
    private void ensureState(State... allowStates) {
        for (final var s : allowStates) {
            if (s == state) {
                return;
            }
        }

        throw new RuntimeException("Disallowed state, except " + Set.of(allowStates) + ", in " + state);
    }

    //================================== 指令生成部分 ==================================//

    /**
     * 指令生成的起始语句, 用于标志指令生成的开始 <br>
     * 请查看 ToAsmManager.InstToAsm 以获得指令生成部分的使用例子
     *
     * @param op 指令的文本表示
     * @return this
     */
    public AsmBuilder instruction(String op) {
        ensureState(State.Normal);

        builder.append(INDENT);
        builder.append(op);

        state = State.FirstArg;
        return this;
    }

    /**
     * 在指令后面附加指令本身的条件执行属性
     *
     * @param inst 指令
     * @return this
     */
    public AsmBuilder cond(ArmInst inst) {
        return cond(inst.getCond());
    }

    /**
     * 在指令的后面附加条件执行属性
     *
     * @param cond 条件
     * @return this
     */
    public AsmBuilder cond(ArmCondType cond) {
        ensureState(State.FirstArg);
        if (cond.isAny()) {
            return this;
        } else {
            return add(cond.toString());
        }
    }

    /**
     * 指令参数的共有实现, 包含了状态检查, 状态改变, 参数间分隔符的插入
     *
     * @param callback 各个指令要执行的动作
     * @return this
     */
    private AsmBuilder argument(Runnable callback) {
        ensureState(State.FirstArg, State.RestArg);

        if (state != State.FirstArg) {
            builder.append(',');
        }
        builder.append(WORD_DELIMITER);
        callback.run();

        state = State.RestArg;
        return this;
    }

    /**
     * 附加一个 Operand 作为指令参数 <br>
     * {@code Operand.toString()} 将会被插入到汇编文本中, 作为指令的参数
     *
     * @param op 待附加的指令参数
     * @return this
     */
    public AsmBuilder operand(Operand op) {
        return operand(op, null);
    }

    /**
     * 附加一个 Operand 作为指令参数, 并后附对应的移位/旋转类型 <br>
     * {@code Operand.toString()} 将会被插入到汇编文本中, 作为指令的参数. <br>
     * 若 shift 非空, 则在参数后面附加对应的移位/旋转类型, 生成诸如 {@code add r0, r5, r5, lsl #2} 中的 {@code lsl #2} 部分
     *
     * @param op    待附加的指令参数
     * @param shift 移位/旋转类型, 可空
     * @return this
     */
    public AsmBuilder operand(Operand op, ArmShift shift) {
        return argument(() -> {
            builder.append(op);
            if (shift != null) {
                builder.append(' ');
                builder.append(shift);
            }
        });
    }

    /**
     * 原样附加 literal 作为参数
     *
     * @param literal 待附加的参数文本
     * @return this
     */
    public AsmBuilder literal(String literal) {
        return argument(() -> builder.append(literal));
    }

    /**
     * 附加一个整数立即数作为参数, 不检查参数是否可编码为对应指令的参数
     *
     * @param imm 立即数
     * @return this
     */
    public AsmBuilder literal(int imm) {
        return argument(() -> {
            builder.append('#');
            builder.append(imm);
        });
    }

    /**
     * 附加 block.label 作为参数, 不对其做任何额外修饰
     *
     * @param block 基本块
     * @return this
     */
    public AsmBuilder label(ArmBlock block) {
        return argument(() -> builder.append(block.getLabel()));
    }

    /**
     * 生成形如 {@code [addr, offset, shift]} 的间接访问作为参数 <br>
     * 若 offset 为 IImm(0), 则不会生成对应的文本 <br>
     * 若 shift 为空, 则不会生成对应的文本 <br>
     * 若 offset 为 0, 且 shift 非空, 则行为未定义
     *
     * @param addr   基址
     * @param offset 偏移
     * @param shift  偏移的位移/旋转模式
     * @return this
     */
    public AsmBuilder indirect(Operand addr, Operand offset, ArmShift shift) {
        return argument(() -> {
            builder.append('[');
            builder.append(addr);

            if (!offset.equals(new IImm(0))) {
                builder.append(',');
                builder.append(offset);
            }

            if (shift != null) {
                builder.append(' ');
                builder.append(shift);
            }

            builder.append(']');
        });
    }

    /**
     * 生成形如 {@code [addr, offset]} 的间接访问作为参数
     *
     * @param addr   基址
     * @param offset 偏移
     * @return this
     */
    public AsmBuilder indirect(Operand addr, Operand offset) {
        return indirect(addr, offset, null);
    }

    /**
     * 生成形如 {@code [addr]} 的间接访问作为参数
     *
     * @param addr 基址
     * @return this
     */
    public AsmBuilder indirect(Operand addr) {
        return indirect(addr, new IImm(0), null);
    }

    /**
     * 为 push/pop 等伪指令生成形如 {@code { operand, ... }} 的参数, 不检查参数个数是否突破该伪指令的最大值
     *
     * @param operands 组中的参数
     * @return this
     */
    public AsmBuilder group(Operand... operands) {
        final var texts = Arrays.stream(operands).map(Operand::toString).toArray(String[]::new);
        return argument(() -> {
            builder.append('{');
            join(", ", texts);
            builder.append('}');
        });
    }

    /**
     * 标志指令生成的结束. 任何指令生成完成后都必须调用此函数.
     */
    public void end() {
        builder.append('\n');

        state = State.Normal;
        blockInstCnt += 1;
    }

    //================================== 其它汇编生成部分 ==================================//

    /**
     * 生成形如 {@code label: } 的块标号
     * @param block 基本块
     * @return this
     */
    public AsmBuilder block(ArmBlock block) {
        return block(block.getLabel());
    }

    /**
     * 生成形如 {@code label: } 的块标号
     * @param label 标号
     * @return this
     */
    public AsmBuilder block(String label) {
        ensureState(State.Normal);

        builder.append(label);
        builder.append(':');
        builder.append('\n');

        blockInstCnt = 0;
        return this;
    }

    /**
     * 生成形如 {@code .directive arg, arg, ...} 的 directive, 如 {@code .word 2}, {@code .global main}
     * @param directive directive
     * @param args 参数
     */
    public void directive(String directive, String... args) {
        ensureState(State.Normal);

        builder.append('.');
        builder.append(directive);
        builder.append(' ');
        join(", ", args);
        builder.append('\n');
    }

    public void newline() {
        builder.append('\n');
    }

    public AsmBuilder indent() {
        builder.append(INDENT);
        return this;
    }

    /**
     * 附加任意文本到当前已生成的汇编文本末尾 <br>
     * 此方法没有任何限制与检查, 应尽量减少使用
     * @param text 待附加的文本
     * @return this
     */
    public AsmBuilder add(String text) {
        builder.append(text);
        return this;
    }

    //================================== 辅助方法与其它接口 ==================================//

    /**
     * 将 "以 delimiter 分隔的 items 序列" 文本逐个地放入 StringBuilder 中
     * @param delimiter 分隔符
     * @param items 文本序列
     */
    private void join(String delimiter, String... items) {
        switch (items.length) {
            case 0 -> {
            }
            case 1 -> builder.append(items[0]);
            default -> {
                builder.append(items[0]);
                for (int i = 1; i < items.length; i++) {
                    builder.append(delimiter);
                    builder.append(items[i]);
                }
            }
        }
    }

    /**
     * @return 当前汇编基本块中的汇编指令条数
     */
    public int getBlockInstCnt() {
        return blockInstCnt;
    }

    /**
     * @return 获得内部的 StringBuilder
     */
    public StringBuilder getBuilder() {
        return builder;
    }
}
