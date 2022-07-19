package top.origami404.ssyc.ir.type;

import top.origami404.ssyc.ir.Value;

public class IRTypeException extends RuntimeException{
    public IRTypeException(Value value, String message) {
        super(message + "(" + value.getType() + " " + value + ")");
        this.value = value;
    }

    public Value getValue() {
        return value;
    }

    private final Value value;
}
