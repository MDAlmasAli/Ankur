package ankur.semantic;

public enum Type {
    INT,
    FLOAT,
    STRING,
    BOOLEAN,
    UNKNOWN // error-recovery sentinel; suppresses cascading errors after an earlier one
}
