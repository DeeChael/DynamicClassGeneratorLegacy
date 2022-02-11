package net.deechael.library.dcg.dynamic.items;

public class CustomVar extends Var {

    private final String varValue;

    public CustomVar(String varValue) {
        super(null, null);
        this.varValue = varValue;
    }

    @Override
    public String varString() {
        return varValue;
    }

}
