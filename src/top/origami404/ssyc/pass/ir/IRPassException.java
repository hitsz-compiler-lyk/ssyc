package top.origami404.ssyc.pass.ir;

public class IRPassException extends RuntimeException {
    public IRPassException(IRPass pass, String message) {
        super("[%s] %s".formatted(pass.getPassName(), message));
    }
}
