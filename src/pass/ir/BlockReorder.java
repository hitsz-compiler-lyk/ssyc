package pass.ir;

import ir.BasicBlock;
import ir.Function;
import ir.Module;
import pass.ir.ConstructDominatorInfo.DominatorInfo;
import utils.CollectionTools;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 重新排列函数中基本块的顺序, 使之:
 * <ol>
 *     <li>符合支配顺序: 设 A, B 为基本块, 若 A 支配 B, 则 A 在 function 中的顺序在 B 之前 (反之则不成立) </li>
 *     <li>直接跳转优先: 设 A, B, C 为基本块且 A 支配 B, C. 若 A 直接跳转到 B 且不直接跳转到 C, 则 B 在 function 中的顺序在 C 之前</li>
 *     <li>小块优先: 设 A, B, C 为基本块且 A 支配并直接跳转到 B, C. 若 B 的指令数小于等于 4 且 C 不符合此条件, 则 B 在 function 中的顺序在 C 之前</li>
 *     <li>循环优先: 设 A, B, C 为基本块且 A 支配并直接跳转到 B, C. 若 B 在 CFG 中的后继闭包中存在节点 D, D 有到 A 的 CFG 中的返祖边, 并且 C 不符合此条件, 则 B 在 function 中的顺序在 C 之前</li>
 * </ol>
 * 上述四条规则优先级自上而下. 规则中没有提到的情况则顺序随机.
 */
public class BlockReorder implements IRPass {
    @Override
    public void runPass(final Module module) {
        new ConstructDominatorInfo().runPass(module);
        module.getNonExternalFunction().forEach(this::runOnFunction);
    }

    public void runOnFunction(Function function) {
        final var newOrder = collect(function.getEntryBBlock());
        function.clear();
        function.addAll(newOrder);
    }

    List<BasicBlock> collect(BasicBlock curr) {
        final var children = DominatorInfo.domTreeChildren(curr);
        final var successors = new HashSet<>(curr.getSuccessors());

        final var childrenPoint = new HashMap<BasicBlock, Integer>();
        children.forEach(block -> childrenPoint.put(block, 0));

        for (final var child : children) {
            if (successors.contains(child)) {
                childrenPoint.compute(child, (block, point) -> point + 3);
            }

            if (child.size() <= 4) {
                childrenPoint.compute(child, (block, point) -> point + 2);
            }

            if (child.getSuccessors().contains(curr)) {
                childrenPoint.compute(child, ((block, point) -> point + 1));
            }
        }

        final var order = new ArrayList<>(children);
        order.sort(Comparator.comparingInt(childrenPoint::get).reversed());

        final var orderFromChildren = order.stream()
            .map(this::collect)
            .flatMap(List::stream)
            .collect(Collectors.toList());

        return CollectionTools.concatHead(curr, orderFromChildren);
    }
}
