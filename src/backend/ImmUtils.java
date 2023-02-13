package backend;

import backend.lir.operand.Operand;

public class ImmUtils {
    /**
     * 检查立即数是否可以被编码进指令里 <br>
     * ARMv7 汇编因为指令长度限制, 并非所有立即数都可以作为 mov/add/... 等指令的参数.
     * @param imm 待检查的立即数
     * @return 是否可以被编码进指令中
     */
    public static boolean checkEncodeImm(int imm) {
        int n = imm;
        for (int i = 0; i < 32; i += 2) {
            if ((n & ~0xFF) == 0) {
                return true;
            }
            n = (n << 2) | (n >>> 30);
        }
        return false;
    }

    public static boolean is2Power(int val) {
        return (val & (val - 1)) == 0;
    }

    public static boolean is2Power(long val) {
        return (val & (val - 1)) == 0;
    }

    /**
     * 统计 val 中末尾的零的数量, 经常简写为 ctz 操作
     * @param val 二进制串
     * @return 末尾的零的数量
     */
    public static int countTailingZeros(int val) {
        int ret = 0;
        while (val != 0) {
            val >>>= 1;
            ret++;
            if ((val & 1) == 1) {
                return ret;
            }
        }
        return ret;
    }

    /**
     * 检查偏移量是否可以被编码到指令里 <br>
     * ARMv7 汇编因为指令长度限制, 并非所有立即数都可以作为立即数间接寻址的偏移量, 它必须足够小. <br>
     * 此函数根据指令的结果参数判断是否为浮点寻址, 从而作出检查.
     * @param offset 待编码的偏移量
     * @param dst 结果地址
     * @return 该偏移量能否被编码进 [reg, #imm] 间接寻址中
     */
    public static boolean checkOffsetRange(int offset, Operand dst) {
        return checkOffsetRange(offset, dst.isFloat());
    }

    /**
     * 检查偏移量是否可以被编码到指令里 <br>
     * ARMv7 汇编因为指令长度限制, 并非所有立即数都可以作为立即数间接寻址的偏移量, 它必须足够小.
     * @param offset 待编码的偏移量
     * @param isFloat 是否在寻址浮点数
     * @return 该偏移量能否被编码进 [reg, #imm] 间接寻址中
     */
    public static boolean checkOffsetRange(int offset, boolean isFloat) {
        if (isFloat) {
            // 对 f32 的间接寻址的偏移量必须在 [-1020, 1020] 范围内
            return offset >= -1020 && offset <= 1020;
        } else {
            // 对 i32 的间接寻址的偏移量必须在 [-4095, 4095] 范围内
            return offset >= -4095 && offset <= 4095;
        }
    }

    // TODO: 考虑是否将这个辅助函数放到优化立即数乘法附加而不是这里
    /**
     * 判断立即数乘法是否能被优化为更廉价的指令
     * @param n 立即数
     * @return 能否被优化
     */
    public static boolean canOptimizeMul(int n) {
        long abs = Math.abs(n);
        if (is2Power(abs)) {
            return true;
        }
        for (long i = 1; i <= abs; i <<= 1) {
            if (is2Power(abs + i) && abs + i <= 2147483647L) {
                return true;
            }
            if (is2Power(abs - i)) {
                return true;
            }
        }
        return false;
    }
}
