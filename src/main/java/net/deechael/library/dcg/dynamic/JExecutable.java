package net.deechael.library.dcg.dynamic;

import net.deechael.library.dcg.dynamic.body.*;
import net.deechael.library.dcg.dynamic.items.*;
import net.deechael.library.dcg.function.ArgumentOnly;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Executable body which is without parameters
 *
 * @author DeeChael
 * @since 1.0.0
 */
public abstract class JExecutable implements JObject {

    Map<Class<?>, Map<String, JStringVar>> annotations = new HashMap<>();

    protected final Map<String, Var> vars = new HashMap<>();
    protected final List<Operation> operations = new ArrayList<>();
    private final List<Class<?>> extraClasses = new ArrayList<>();
    
    final JClass parent;
    
    protected JExecutable(JClass parent) {
        this.parent = parent;
    }

    /*

    public void removeParameter(String parameterName) {
        if (parameters.isEmpty()) return;
        if (parameterName == null) return;
        if (!Pattern.matches("^[A-Za-z_$]+[A-Za-z_$\\d]+$", parameterName)) return;
        parameterName = "jparam_" + parameterName;
        if (!parameters.containsKey(parameterName)) return;
        parameters.remove(parameterName);
        vars.remove(parameterName);
    }

    */

    /**
     * Get a created var
     * Not include parameters and fields
     * You can find the var once you created by createNewInstanceVar or createNewStringVar
     *
     * Won't generate code
     *
     * @param varName the name of the var which you want to find (Will add a prefix "jvar_" automatically to search)
     * @return The var whose name is same to varName
     */
    public Var getVar(String varName) {
        if (vars.isEmpty()) throw new RuntimeException("The var not exists");
        if (varName == null) throw new RuntimeException("The var not exists");
        if (!Pattern.matches("^[A-Za-z_$]+[A-Za-z_$\\d]+$", varName)) throw new RuntimeException("The var not exists");
        varName = "jvar_" + varName;
        if (!vars.containsKey(varName)) throw new RuntimeException("The var not exists");
        return vars.get(varName);
    }

    /**
     * Create a new var by constructor
     *
     * Generated code look likes: Example example = new Example(...);
     *
     * @param type The type of the var
     * @param name the name of the var which you want (Will add a prefix "jvar_" automatically)
     * @param arguments The argument vars which the constructor need
     * @return Created var which can be found by method "getVar"
     */
    public Var createNewInstanceVar(@NotNull Class<?> type, @NotNull String name, Var... arguments) {
        if (!Pattern.matches("^[A-Za-z_$]+[A-Za-z_$\\d]+$", name)) throw new RuntimeException("This var name is not allowed!");
        name = "jvar_" + name;
        if (!vars.isEmpty()) {
            if (vars.containsKey(name)) return vars.get(name);
        }
        Var var = new Var(type, name);
        vars.put(name, var);
        StringBuilder bodyBuilder = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            bodyBuilder.append(arguments[i].varString());
            if (i != arguments.length - 1) {
                bodyBuilder.append(", ");
            }
        }
        CreateNewInstanceVar createVar = new CreateNewInstanceVar(type, name, bodyBuilder.toString());
        operations.add(createVar);
        return var;
    }

    /**
     * Create a new var by String value
     * Only support types: Class, Enum, Integer, String, Long, Short, Byte, Short, Character, Float, Double and primitive classes
     * Needn't give a type because the types that JStringVar supports are limited
     *
     * Create a JStringVar by the static methods in the JStringVar class
     *
     * Generated code looks like: String(Or other types which are by JStringVar) jvar_varName = {value};
     *
     * @param name the name of the var which you want (Will add a prefix "jvar_" automatically)
     * @param stringValue The value you want to make the var be
     * @return Created var which can be found by method "getVar"
     */
    public Var createNewStringVar(String name, JStringVar stringValue) {
        if (!Pattern.matches("^[A-Za-z_$]+[A-Za-z_$\\d]+$", name)) throw new RuntimeException("This var name is not allowed!");
        name = "jvar_" + name;
        if (!vars.isEmpty()) {
            if (vars.containsKey(name)) throw new RuntimeException("This var name has been used!");
        }
        Class<?> latest = stringValue.getType();
        while (latest.isArray()) {
            latest = latest.getComponentType();
        }
        if (latest.isEnum()) {
            this.extraClasses.add(latest);
        }
        Var var = new Var(stringValue.getType(), name);
        CreateNewStringVar createVar = new CreateNewStringVar(stringValue.getType(), name, stringValue.varString());
        operations.add(createVar);
        return var;
    }

    /**
     * Using a method in the executable body
     *
     * Tips: If you are using the method which you added by JMethod,
     *       please add "jmethod_" before your methodName because
     *       generated code will add the prefix automatically
     *
     * Generated code looks like: jvar_varName.methodName(arguments);
     *
     * @param var The var that you want to use the method,
     *            the method must be contained in the class of the var,
     *            or else will throw an exception
     * @param methodName The method that you want to use,
     *                   the method must be contained in the class of the var,
     *                   or else will throw an exception
     * @param arguments The arguments that the method needs,
     *                  is easier than reflection
     */
    public void usingMethod(@NotNull Var var, @NotNull String methodName, Var... arguments) {
        if (!isVarExists(var)) throw new RuntimeException("The var not exists in the executing body!");
        Class<?> clazz = var.getType();
        boolean hasMethod = false;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && !Modifier.isStatic(method.getModifiers())) {
                hasMethod = true;
                break;
            }
        }
        if (!hasMethod) {
            throw new RuntimeException("Unknown method of the class " + var.getType().getName() + ": " + methodName + "(...);");
        }
        StringBuilder bodyBuilder = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            bodyBuilder.append(arguments[i].varString());
            if (i != arguments.length - 1) {
                bodyBuilder.append(", ");
            }
        }
        UsingMethod usingMethod = new UsingMethod(var.varString(), methodName, bodyBuilder.toString());
        operations.add(usingMethod);
    }

    /**
     * Using a static method
     *
     * Generated code looks like: Type.methodName(arguments);
     *
     * @param clazz The type has the static method
     * @param methodName The method you want to use
     * @param arguments The arguments that the method needs
     */
    public void usingMethod(Class<?> clazz, String methodName, Var... arguments) {
        if (!extraClasses.contains(clazz)) {
            extraClasses.add(clazz);
        }
        boolean hasMethod = false;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && Modifier.isStatic(method.getModifiers())) {
                hasMethod = true;
                break;
            }
        }
        if (!hasMethod) {
            throw new RuntimeException("Unknown method of the class " + clazz.getName() + ": " + methodName + "(...);");
        }
        StringBuilder bodyBuilder = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            bodyBuilder.append(arguments[i].varString());
            if (i != arguments.length - 1) {
                bodyBuilder.append(", ");
            }
        }
        UsingStaticMethod usingMethod = new UsingStaticMethod(clazz.getName(), methodName, bodyBuilder.toString());
        operations.add(usingMethod);
    }

    /**
     * Using the method which implemented by parent class in child class
     * 
     * Generated code looks like: super.methodName(arguments);
     * 
     * @param methodName The method name implemented by parent class
     * @param arguments The arguments that the method needs
     */
    public void usingSuperMethod(String methodName, Var... arguments) {
        StringBuilder bodyBuilder = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            bodyBuilder.append(arguments[i].varString());
            if (i != arguments.length - 1) {
                bodyBuilder.append(", ");
            }
        }
        UsingMethod usingMethod = new UsingMethod("super", methodName, bodyBuilder.toString());
        operations.add(usingMethod);
    }

    /**
     * Using the method, will execute the method is implemented by this class, if this class didn't implement this method but parent class did, execute the method in parent class
     *
     * Generated code looks like: this.methodName(arguments);
     *
     * @param methodName The method name
     * @param arguments The arguments that the method needs
     */
    public void usingThisMethod(String methodName, Var... arguments) {
        StringBuilder bodyBuilder = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            bodyBuilder.append(arguments[i].varString());
            if (i != arguments.length - 1) {
                bodyBuilder.append(", ");
            }
        }
        UsingMethod usingMethod = new UsingMethod("this", methodName, bodyBuilder.toString());
        operations.add(usingMethod);
    }

    /**
     * Cast a var to another type
     * @param originalVar The var to be cast
     * @param castToClass The class type the var will be cast to
     * @param newVarName New var name
     * @return New var
     */
    public Var castObject(Var originalVar, Class<?> castToClass, String newVarName) {
        if (!Pattern.matches("^[A-Za-z_$]+[A-Za-z_$\\d]+$", newVarName)) throw new RuntimeException("This var name is not allowed!");
        newVarName = "jvar_" + newVarName;
        if (!vars.isEmpty()) {
            if (vars.containsKey(newVarName)) {
                throw new RuntimeException("Failed to cast because it exists");
            }
        }
        Var var = new Var(castToClass, newVarName);
        vars.put(newVarName, var);
        CastVar castVar = new CastVar(castToClass, newVarName, originalVar.varString());
        operations.add(castVar);
        return var;
    }

    public Var castObjectAsVar(Var originalVar, Class<?> castToClass) {
        return new CastingVar(castToClass, originalVar.varString());
    }

    public Var createNewVarFromObjectsField(String varName, Var var, String fieldName) {
        if (!Pattern.matches("^[A-Za-z_$]+[A-Za-z_$\\d]+$", varName)) throw new RuntimeException("This var name is not allowed!");
        varName = "jvar_" + varName;
        if (!vars.isEmpty()) {
            if (vars.containsKey(varName)) {
                throw new RuntimeException("Failed to cast because it exists");
            }
        }
        Field field = object_field_exists_parentClass(var.getType(), fieldName);
        if (field == null) {
            throw new RuntimeException("Cannot find the field!");
        }
        Var newVar = new Var(field.getType(), varName);
        vars.put(varName, newVar);
        CreateNewObjectsFieldVar createVar = new CreateNewObjectsFieldVar(field.getType(), varName, var.varString(), fieldName);
        operations.add(createVar);
        return newVar;
    }

    public Var objectsFieldAsVar(Var var, String fieldName) {
        Field field = object_field_exists_parentClass(var.getType(), fieldName);
        if (field == null) {
            throw new RuntimeException("Cannot find the field!");
        }
        return new ObjectsFieldVar(field.getType(), var.varString(), fieldName);
    }

    private Field object_field_exists_parentClass(Class<?> clazz, String name) {
        try {
            Field field = clazz.getField(name);
            if (Modifier.isPublic(field.getModifiers()) || Modifier.isProtected(field.getModifiers())) {
                return field;
            } else {
                return null;
            }
        } catch (NoSuchFieldException e) {
            Class<?> parent = clazz.getSuperclass();
            if (parent == null) {
                return null;
            } else {
                if (parent == Object.class) {
                    return null;
                } else {
                    return object_field_exists_parentClass(parent, name);
                }
            }
        }
    }
    
    /**
     * Using a method and create var whose value will be set to the return value of the method
     * If the return type of the method is void.class, it will throw an exception
     * 
     * Generated code looks like: ReturnType object = var.methodName(arguments); 
     * 
     * @param varName The name of the new var (Will be added a prefix "jvar_" automatically)
     * @param var the var has the method
     * @param methodName the method to use
     * @param arguments the arguments that the method needs
     * @return New created var
     */
    public Var usingMethodAndCreateVar(String varName, @NotNull Var var, @NotNull String methodName, Var... arguments) {
        if (!isVarExists(var)) throw new RuntimeException("The var not exists in the executing body!");
        if (!Pattern.matches("^[A-Za-z_$]+[A-Za-z_$\\d]+$", varName)) throw new RuntimeException("The var name not allowed!");
        varName = "jvar_" + varName;
        if (vars.containsKey(varName)) throw new RuntimeException("The var exists!");
        Class<?> clazz = var.getType();
        Class<?> returnType = null;
        boolean hasMethod = false;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && !Modifier.isStatic(method.getModifiers())) {
                if (method.getReturnType() == void.class) {
                    throw new RuntimeException("Fail to handle \"usingMethodAndCreateVar\" because the method " + methodName + "(...); of the class" + var.getType().getName() + "; return a void type!");
                }
                hasMethod = true;
                returnType = method.getReturnType();
                break;
            }
        }
        if (!hasMethod) {
            throw new RuntimeException("Unknown method of the class " + var.getType().getName() + ": " + methodName + "(...);");
        }
        StringBuilder bodyBuilder = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            bodyBuilder.append(arguments[i].varString());
            if (i != arguments.length - 1) {
                bodyBuilder.append(", ");
            }
        }
        UsingMethodAndCreateVar usingMethodAndCreateVar = new UsingMethodAndCreateVar(returnType.getName(), varName, var.varString(), methodName, bodyBuilder.toString());
        operations.add(usingMethodAndCreateVar);
        Var newVar = new Var(returnType, varName);
        vars.put(varName, newVar);
        return newVar;
    }

    /**
     * Using the method implemented by parent class and create a new var whose value is the return value of the method
     * If the JClass didn't extend any class, it will throw an exception
     * If the parent class didn't have the method, it will throw an exception
     *
     * Generated code looks like: ReturnType object = super.methodName(arguments);
     *
     * TODO: The method hasn't been finished yet!
     *
     * @param varName The name of the new var that you want to create (Will add a prefix "jvar_" automatically)
     * @param methodName The method name that implemented by the parent class
     * @param arguments The arguments that the method needs
     */
    public void usingSuperMethodAndCreateVar(String varName, String methodName, Var... arguments) {
        //TODO
        /*
        StringBuilder bodyBuilder = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            bodyBuilder.append(arguments[i].getString());
            if (i != arguments.length - 1) {
                bodyBuilder.append(", ");
            }
        }
        UsingMethod usingMethod = new UsingMethod("super", methodName, bodyBuilder.toString());
        operations.add(usingMethod);
        */
    }

    /**
     * Using a method and create var whose value will be set to the return value of the method
     * If the return type of the method is void.class, it will throw an exception
     *
     * Generated code looks like: ReturnType object = Type.methodName(arguments);
     *
     * @param varName The name of the new var that you want to create (Will add a prefix "jvar_" automatically)
     * @param clazz The class has the method
     * @param methodName The method that you want to use
     * @param arguments The arguments that the method needs
     * @return New created var
     */
    public Var usingMethodAndCreateVar(String varName, @NotNull Class<?> clazz, @NotNull String methodName, Var... arguments) {
        if (!extraClasses.contains(clazz)) {
            extraClasses.add(clazz);
        }
        if (!Pattern.matches("^[A-Za-z_$]+[A-Za-z_$\\d]+$", varName)) throw new RuntimeException("The var name not allowed!");
        varName = "jvar_" + varName;
        if (vars.containsKey(varName)) throw new RuntimeException("The var exists!");
        Class<?> returnType = null;
        boolean hasMethod = false;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && !Modifier.isStatic(method.getModifiers())) {
                if (method.getReturnType() == void.class && Modifier.isStatic(method.getModifiers())) {
                    throw new RuntimeException("Fail to handle \"usingMethodAndCreateVar\" because the method " + methodName + "(...); of the class" + clazz.getName() + "; return a void type!");
                }
                hasMethod = true;
                returnType = method.getReturnType();
                break;
            }
        }
        if (!hasMethod) {
            throw new RuntimeException("Unknown method of the class " + clazz.getName() + ": " + methodName + "(...);");
        }
        StringBuilder bodyBuilder = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            bodyBuilder.append(arguments[i].varString());
            if (i != arguments.length - 1) {
                bodyBuilder.append(", ");
            }
        }
        UsingStaticMethodAndCreateVar usingMethodAndCreateVar = new UsingStaticMethodAndCreateVar(returnType.getName(), varName, clazz.getName(), methodName, bodyBuilder.toString());
        operations.add(usingMethodAndCreateVar);
        Var newVar = new Var(returnType, varName);
        vars.put(varName, newVar);
        return newVar;
    }

    /**
     * Using if-else block in executable body
     * ArgumentOnly is a functional interface
     * You can replace with lambda:
     *     (executable) -> {
     *         //Modify the executable body
     *         //Example: executable.usingMethod(...);
     *     }
     *
     * Check two vars with "=="
     *
     * Generated code looks like:
     *     if (var == isEqual) {
     *         The code generated by the JExecutable in ifExecuting
     *     } else {
     *         The code generated by the JExecutable in elseExecuting
     *     }
     *
     * @param var The var at the left of equal checker(==)
     * @param isEqual The var at the right of equal checker(==)
     * @param ifExecuting The executable body in if block
     * @param elseExecuting The executable body in else block
     */
    public void ifElse_Equal(Var var, Var isEqual, ArgumentOnly<JExecutable> ifExecuting, ArgumentOnly<JExecutable> elseExecuting) {
        if (!isVarExists(var)) throw new RuntimeException("The var not exists in the executing body!");
        if (!isVarExists(isEqual)) throw new RuntimeException("The var not exists in the executing body!");
        JExecutableForIfElse ifBody = new JExecutableForIfElse(this, this.parent);
        JExecutableForIfElse elseBody = new JExecutableForIfElse(this, this.parent);
        ifExecuting.apply(ifBody);
        elseExecuting.apply(elseBody);
        IfAndElse ifAndElse = new IfAndElse(new EqualCheck(var, isEqual), ifBody, elseBody);
        operations.add(ifAndElse);
    }

    public void ifElse_BooleanVar(Var booleanTypeVar, ArgumentOnly<JExecutable> ifExecuting, ArgumentOnly<JExecutable> elseExecuting) {
        if (!isVarExists(booleanTypeVar)) throw new RuntimeException("The var not exists in the executing body!");
        if (!(booleanTypeVar.getType() == boolean.class || booleanTypeVar.getType() == Boolean.class)) {
            throw new RuntimeException("The var is not boolean type var");
        }
        JExecutableForIfElse ifBody = new JExecutableForIfElse(this, this.parent);
        JExecutableForIfElse elseBody = new JExecutableForIfElse(this, this.parent);
        ifExecuting.apply(ifBody);
        elseExecuting.apply(elseBody);
        IfAndElse ifAndElse = new IfAndElse(new BooleanVarCheck(booleanTypeVar.varString()), ifBody, elseBody);
        operations.add(ifAndElse);
    }

    public void ifElse_Method(ArgumentOnly<JExecutable> ifExecuting, ArgumentOnly<JExecutable> elseExecuting, @NotNull Var var, @NotNull String methodName, Var... arguments) {
        if (!isVarExists(var)) throw new RuntimeException("The var not exists in the executing body!");
        Class<?> clazz = var.getType();
        boolean hasMethod = false;
        Method result = null;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && !Modifier.isStatic(method.getModifiers())) {
                if (!(method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                    throw new RuntimeException("The return type of the method is not a boolean type!");
                }
                hasMethod = true;
                result = method;
                break;
            }
        }
        if (!hasMethod) {
            throw new RuntimeException("Unknown method of the class " + var.getType().getName() + ": " + methodName + "(...);");
        }
        StringBuilder bodyBuilder = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            bodyBuilder.append(arguments[i]);
            if (i != arguments.length - 1) {
                bodyBuilder.append(", ");
            }
        }
        JExecutableForIfElse ifBody = new JExecutableForIfElse(this, this.parent);
        JExecutableForIfElse elseBody = new JExecutableForIfElse(this, this.parent);
        ifExecuting.apply(ifBody);
        elseExecuting.apply(elseBody);
        IfAndElse ifAndElse = new IfAndElse(new UsingMethodAsVar(var.varString(), result, bodyBuilder.toString()), ifBody, elseBody);
        operations.add(ifAndElse);
    }

    public void ifElse_StaticMethod(ArgumentOnly<JExecutable> ifExecuting, ArgumentOnly<JExecutable> elseExecuting, @NotNull Class<?> clazz, @NotNull String methodName, Var... arguments) {
        if (!extraClasses.contains(clazz)) {
            extraClasses.add(clazz);
        }
        boolean hasMethod = false;
        Method result = null;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && !Modifier.isStatic(method.getModifiers())) {
                if (!(method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                    throw new RuntimeException("The return type of the method is not a boolean type!");
                }
                hasMethod = true;
                result = method;
                break;
            }
        }
        if (!hasMethod) {
            throw new RuntimeException("Unknown method of the class " + clazz.getName() + ": " + methodName + "(...);");
        }
        StringBuilder bodyBuilder = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            bodyBuilder.append(arguments[i]);
            if (i != arguments.length - 1) {
                bodyBuilder.append(", ");
            }
        }
        JExecutableForIfElse ifBody = new JExecutableForIfElse(this, this.parent);
        JExecutableForIfElse elseBody = new JExecutableForIfElse(this, this.parent);
        ifExecuting.apply(ifBody);
        elseExecuting.apply(elseBody);
        IfAndElse ifAndElse = new IfAndElse(new UsingStaticMethodAsVar(result, bodyBuilder.toString()), ifBody, elseBody);
        operations.add(ifAndElse);
    }

    public void ifOnly_Equal(Var var, Var isEqual, ArgumentOnly<JExecutable> ifExecuting) {
        if (!isVarExists(var)) throw new RuntimeException("The var not exists in the executing body!");
        if (!isVarExists(isEqual)) throw new RuntimeException("The var not exists in the executing body!");
        JExecutableForIfElse ifBody = new JExecutableForIfElse(this, this.parent);
        ifExecuting.apply(ifBody);
        IfOnly ifOnly = new IfOnly(new EqualCheck(var, isEqual), ifBody);
        operations.add(ifOnly);
    }

    public void ifOnly_BooleanVar(Var booleanTypeVar, ArgumentOnly<JExecutable> ifExecuting) {
        if (!isVarExists(booleanTypeVar)) throw new RuntimeException("The var not exists in the executing body!");
        JExecutableForIfElse ifBody = new JExecutableForIfElse(this, this.parent);
        ifExecuting.apply(ifBody);
        IfOnly ifOnly = new IfOnly(new BooleanVarCheck(booleanTypeVar.varString()), ifBody);
        operations.add(ifOnly);
    }

    public void ifOnly_Method(ArgumentOnly<JExecutable> ifExecuting, @NotNull Var var, @NotNull String methodName, Var... arguments) {
        if (!isVarExists(var)) throw new RuntimeException("The var not exists in the executing body!");
        Class<?> clazz = var.getType();
        boolean hasMethod = false;
        Method result = null;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && !Modifier.isStatic(method.getModifiers())) {
                if (!(method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                    throw new RuntimeException("The return type of the method is not a boolean type!");
                }
                hasMethod = true;
                result = method;
                break;
            }
        }
        if (!hasMethod) {
            throw new RuntimeException("Unknown method of the class " + var.getType().getName() + ": " + methodName + "(...);");
        }
        StringBuilder bodyBuilder = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            bodyBuilder.append(arguments[i]);
            if (i != arguments.length - 1) {
                bodyBuilder.append(", ");
            }
        }
        JExecutableForIfElse ifBody = new JExecutableForIfElse(this, this.parent);
        ifExecuting.apply(ifBody);
        IfOnly ifOnly = new IfOnly(new UsingMethodAsVar(var.varString(), result, bodyBuilder.toString()), ifBody);
        operations.add(ifOnly);
    }

    public void ifOnly_StaticMethod(ArgumentOnly<JExecutable> ifExecuting, Class<?> clazz, @NotNull String methodName, Var... arguments) {
        if (!extraClasses.contains(clazz)) {
            extraClasses.add(clazz);
        }
        boolean hasMethod = false;
        Method result = null;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && !Modifier.isStatic(method.getModifiers())) {
                if (!(method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                    throw new RuntimeException("The return type of the method is not a boolean type!");
                }
                hasMethod = true;
                result = method;
                break;
            }
        }
        if (!hasMethod) {
            throw new RuntimeException("Unknown method of the class " + clazz.getName() + ": " + methodName + "(...);");
        }
        StringBuilder bodyBuilder = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            bodyBuilder.append(arguments[i]);
            if (i != arguments.length - 1) {
                bodyBuilder.append(", ");
            }
        }
        JExecutableForIfElse ifBody = new JExecutableForIfElse(this, this.parent);
        ifExecuting.apply(ifBody);
        IfOnly ifOnly = new IfOnly(new UsingStaticMethodAsVar(result, bodyBuilder.toString()), ifBody);
        operations.add(ifOnly);
    }

    public Var createUsingMethodAsString(@NotNull Var var, @NotNull String methodName, Var... arguments) {
        if (!isVarExists(var)) throw new RuntimeException("The var not exists in the executing body!");
        Class<?> clazz = var.getType();
        boolean hasMethod = false;
        Method result = null;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && !Modifier.isStatic(method.getModifiers())) {
                hasMethod = true;
                result = method;
                break;
            }
        }
        if (!hasMethod) {
            throw new RuntimeException("Unknown method of the class " + var.getType().getName() + ": " + methodName + "(...);");
        }
        StringBuilder bodyBuilder = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            bodyBuilder.append(arguments[i].varString());
            if (i != arguments.length - 1) {
                bodyBuilder.append(", ");
            }
        }
        return new UsingMethodAsVar(var.varString(), result, bodyBuilder.toString());
    }

    public Var createUsingMethodAsString(@NotNull Class<?> clazz, @NotNull String methodName, Var... arguments) {
        if (!extraClasses.contains(clazz)) {
            extraClasses.add(clazz);
        }
        boolean hasMethod = false;
        Method result = null;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && !Modifier.isStatic(method.getModifiers())) {
                hasMethod = true;
                result = method;
                break;
            }
        }
        if (!hasMethod) {
            throw new RuntimeException("Unknown method of the class " + clazz.getName() + ": " + methodName + "(...);");
        }
        StringBuilder bodyBuilder = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            bodyBuilder.append(arguments[i].varString());
            if (i != arguments.length - 1) {
                bodyBuilder.append(", ");
            }
        }
        return new UsingStaticMethodAsVar(result, bodyBuilder.toString());
    }

    public void returnValue(Var var) {
        if (!isVarExists(var)) throw new RuntimeException("The var not exists in the executing body!");
        this.operations.add(new ReturnValue(var.varString()));
    }

    public void returnEmpty() {
        this.operations.add(new ReturnVoid());
    }

    protected boolean isVarExists(Var var) {
        if (var instanceof UsingMethodAsVar || var instanceof UsingStaticMethodAsVar) {
            return true;
        }
        if (var instanceof JStringVar) {
            return true;
        }
        if (var instanceof JField) {
            if (((JField) var).parent != this.parent) {
                throw new RuntimeException("The field not exists in the class!");
            }
            return parent.varExists(var);
        }
        return (vars.containsValue(var) || (vars.containsKey(var.getName()) && vars.get(var.getName()).getType().getName().equals(var.getType().getName())));
    }

    protected List<Operation> getOperations() {
        return new ArrayList<>(this.operations);
    }

    public void setFieldValue(JField field, Var var) {
        if (field.parent != this.parent)
        if (!isVarExists(var)) throw new RuntimeException("The var not exists in the executing body!");
        this.operations.add(new SetFieldValue(field, var.varString()));
    }

    /**
     * Be used to if and else executable body
     */
    private static class JExecutableForIfElse extends JExecutable {

        private final JExecutable executable;

        public JExecutableForIfElse(JExecutable parent, JClass clazz) {
            super(clazz);
            this.executable = parent;
        }

        public Var getVar(String varName) {
            if (executable.vars.isEmpty()) throw new RuntimeException("The var not exists");
            if (varName == null) throw new RuntimeException("The var not exists");
            if (!Pattern.matches("^[A-Za-z_$]+[A-Za-z_$\\d]+$", varName)) throw new RuntimeException("The var not exists");
            varName = "jvar_" + varName;
            if (!executable.vars.containsKey(varName)) throw new RuntimeException("The var not exists");
            return executable.vars.get(varName);
        }

        public Var createNewInstanceVar(@NotNull Class<?> type, @NotNull String name, Var... arguments) {
            if (!Pattern.matches("^[A-Za-z_$]+[A-Za-z_$\\d]+$", name)) throw new RuntimeException("This var name is not allowed!");
            name = "jvar_" + name;
            if (!vars.isEmpty()) {
                if (vars.containsKey(name)) return vars.get(name);
            }
            Var var = new Var(type, name);
            executable.vars.put(name, var);
            StringBuilder bodyBuilder = new StringBuilder();
            for (int i = 0; i < arguments.length; i++) {
                bodyBuilder.append(arguments[i].varString());
                if (i != arguments.length - 1) {
                    bodyBuilder.append(", ");
                }
            }
            CreateNewInstanceVar createVar = new CreateNewInstanceVar(type, name, bodyBuilder.toString());
            operations.add(createVar);
            return var;
        }

        public Var createNewStringVar(String name, JStringVar stringValue) {
            if (!Pattern.matches("^[A-Za-z_$]+[A-Za-z_$\\d]+$", name)) throw new RuntimeException("This var name is not allowed!");
            name = "jvar_" + name;
            if (!executable.vars.isEmpty()) {
                if (executable.vars.containsKey(name)) throw new RuntimeException("This var name has been used!");
            }
            Class<?> latest = stringValue.getType();
            while (latest.isArray()) {
                latest = latest.getComponentType();
            }
            if (latest.isEnum()) {
                this.executable.extraClasses.add(latest);
            }
            Var var = new Var(stringValue.getType(), name);
            CreateNewStringVar createVar = new CreateNewStringVar(stringValue.getType(), name, stringValue.varString());
            operations.add(createVar);
            executable.vars.put(name, var);
            return var;
        }

        public void usingMethod(Class<?> clazz, String methodName, Var... arguments) {
            if (!executable.extraClasses.contains(clazz)) {
                executable.extraClasses.add(clazz);
            }
            boolean hasMethod = false;
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && Modifier.isStatic(method.getModifiers())) {
                    hasMethod = true;
                    break;
                }
            }
            if (!hasMethod) {
                throw new RuntimeException("Unknown method of the class " + clazz.getName() + ": " + methodName + "(...);");
            }
            StringBuilder bodyBuilder = new StringBuilder();
            for (int i = 0; i < arguments.length; i++) {
                bodyBuilder.append(arguments[i].varString());
                if (i != arguments.length - 1) {
                    bodyBuilder.append(", ");
                }
            }
            UsingStaticMethod usingMethod = new UsingStaticMethod(clazz.getName(), methodName, bodyBuilder.toString());
            operations.add(usingMethod);
        }

        public Var usingMethodAndCreateVar(String varName, @NotNull Var var, @NotNull String methodName, Var... arguments) {
            if (!isVarExists(var)) throw new RuntimeException("The var not exists in the executing body!");
            if (!Pattern.matches("^[A-Za-z_$]+[A-Za-z_$\\d]+$", varName)) throw new RuntimeException("The var name not allowed!");
            varName = "jvar_" + varName;
            if (executable.vars.containsKey(varName)) throw new RuntimeException("The var exists!");
            Class<?> clazz = var.getType();
            Class<?> returnType = null;
            boolean hasMethod = false;
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && !Modifier.isStatic(method.getModifiers())) {
                    if (method.getReturnType() == void.class) {
                        throw new RuntimeException("Fail to handle \"usingMethodAndCreateVar\" because the method " + methodName + "(...); of the class" + var.getType().getName() + "; return a void type!");
                    }
                    hasMethod = true;
                    returnType = method.getReturnType();
                    break;
                }
            }
            if (!hasMethod) {
                throw new RuntimeException("Unknown method of the class " + var.getType().getName() + ": " + methodName + "(...);");
            }
            StringBuilder bodyBuilder = new StringBuilder();
            for (int i = 0; i < arguments.length; i++) {
                bodyBuilder.append(arguments[i].varString());
                if (i != arguments.length - 1) {
                    bodyBuilder.append(", ");
                }
            }
            UsingMethodAndCreateVar usingMethodAndCreateVar = new UsingMethodAndCreateVar(returnType.getName(), varName, var.varString(), methodName, bodyBuilder.toString());
            operations.add(usingMethodAndCreateVar);
            Var newVar = new Var(returnType, varName);
            executable.vars.put(varName, newVar);
            return newVar;
        }

        public void usingSuperMethodAndCreateVar(String varName, String methodName, Var... arguments) {
            //TODO
        /*
        StringBuilder bodyBuilder = new StringBuilder();
        for (int i = 0; i < arguments.length; i++) {
            bodyBuilder.append(arguments[i].getString());
            if (i != arguments.length - 1) {
                bodyBuilder.append(", ");
            }
        }
        UsingMethod usingMethod = new UsingMethod("super", methodName, bodyBuilder.toString());
        operations.add(usingMethod);
        */
        }

        public Var usingMethodAndCreateVar(String varName, @NotNull Class<?> clazz, @NotNull String methodName, Var... arguments) {
            if (!executable.extraClasses.contains(clazz)) {
                executable.extraClasses.add(clazz);
            }
            if (!Pattern.matches("^[A-Za-z_$]+[A-Za-z_$\\d]+$", varName)) throw new RuntimeException("The var name not allowed!");
            varName = "jvar_" + varName;
            if (executable.vars.containsKey(varName)) throw new RuntimeException("The var exists!");
            Class<?> returnType = null;
            boolean hasMethod = false;
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && !Modifier.isStatic(method.getModifiers())) {
                    if (method.getReturnType() == void.class && Modifier.isStatic(method.getModifiers())) {
                        throw new RuntimeException("Fail to handle \"usingMethodAndCreateVar\" because the method " + methodName + "(...); of the class" + clazz.getName() + "; return a void type!");
                    }
                    hasMethod = true;
                    returnType = method.getReturnType();
                    break;
                }
            }
            if (!hasMethod) {
                throw new RuntimeException("Unknown method of the class " + clazz.getName() + ": " + methodName + "(...);");
            }
            StringBuilder bodyBuilder = new StringBuilder();
            for (int i = 0; i < arguments.length; i++) {
                bodyBuilder.append(arguments[i].varString());
                if (i != arguments.length - 1) {
                    bodyBuilder.append(", ");
                }
            }
            UsingStaticMethodAndCreateVar usingMethodAndCreateVar = new UsingStaticMethodAndCreateVar(returnType.getName(), varName, clazz.getName(), methodName, bodyBuilder.toString());
            operations.add(usingMethodAndCreateVar);
            Var newVar = new Var(returnType, varName);
            executable.vars.put(varName, newVar);
            return newVar;
        }

        public void ifElse_Equal(Var var, Var isEqual, ArgumentOnly<JExecutable> ifExecuting, ArgumentOnly<JExecutable> elseExecuting) {
            if (!isVarExists(var)) throw new RuntimeException("The var not exists in the executing body!");
            if (!isVarExists(isEqual)) throw new RuntimeException("The var not exists in the executing body!");
            JExecutableForIfElse ifBody = new JExecutableForIfElse(this, this.parent);
            JExecutableForIfElse elseBody = new JExecutableForIfElse(this, this.parent);
            ifExecuting.apply(ifBody);
            elseExecuting.apply(elseBody);
            IfAndElse ifAndElse = new IfAndElse(new EqualCheck(var, isEqual), ifBody, elseBody);
            operations.add(ifAndElse);
        }

        public void ifElse_BooleanVar(Var booleanTypeVar, ArgumentOnly<JExecutable> ifExecuting, ArgumentOnly<JExecutable> elseExecuting) {
            if (!isVarExists(booleanTypeVar)) throw new RuntimeException("The var not exists in the executing body!");
            if (!(booleanTypeVar.getType() == boolean.class || booleanTypeVar.getType() == Boolean.class)) {
                throw new RuntimeException("The var is not boolean type var");
            }
            JExecutableForIfElse ifBody = new JExecutableForIfElse(this, this.parent);
            JExecutableForIfElse elseBody = new JExecutableForIfElse(this, this.parent);
            ifExecuting.apply(ifBody);
            elseExecuting.apply(elseBody);
            IfAndElse ifAndElse = new IfAndElse(new BooleanVarCheck(booleanTypeVar.varString()), ifBody, elseBody);
            operations.add(ifAndElse);
        }

        public void ifElse_Method(ArgumentOnly<JExecutable> ifExecuting, ArgumentOnly<JExecutable> elseExecuting, @NotNull Var var, @NotNull String methodName, Var... arguments) {
            if (!isVarExists(var)) throw new RuntimeException("The var not exists in the executing body!");
            Class<?> clazz = var.getType();
            boolean hasMethod = false;
            Method result = null;
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && !Modifier.isStatic(method.getModifiers())) {
                    if (!(method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                        throw new RuntimeException("The return type of the method is not a boolean type!");
                    }
                    hasMethod = true;
                    result = method;
                    break;
                }
            }
            if (!hasMethod) {
                throw new RuntimeException("Unknown method of the class " + var.getType().getName() + ": " + methodName + "(...);");
            }
            StringBuilder bodyBuilder = new StringBuilder();
            for (int i = 0; i < arguments.length; i++) {
                bodyBuilder.append(arguments[i]);
                if (i != arguments.length - 1) {
                    bodyBuilder.append(", ");
                }
            }
            JExecutableForIfElse ifBody = new JExecutableForIfElse(this, this.parent);
            JExecutableForIfElse elseBody = new JExecutableForIfElse(this, this.parent);
            ifExecuting.apply(ifBody);
            elseExecuting.apply(elseBody);
            IfAndElse ifAndElse = new IfAndElse(new UsingMethodAsVar(var.varString(), result, bodyBuilder.toString()), ifBody, elseBody);
            operations.add(ifAndElse);
        }

        public void ifElse_StaticMethod(ArgumentOnly<JExecutable> ifExecuting, ArgumentOnly<JExecutable> elseExecuting, @NotNull Class<?> clazz, @NotNull String methodName, Var... arguments) {
            if (!executable.extraClasses.contains(clazz)) {
                executable.extraClasses.add(clazz);
            }
            boolean hasMethod = false;
            Method result = null;
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && !Modifier.isStatic(method.getModifiers())) {
                    if (!(method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                        throw new RuntimeException("The return type of the method is not a boolean type!");
                    }
                    hasMethod = true;
                    result = method;
                    break;
                }
            }
            if (!hasMethod) {
                throw new RuntimeException("Unknown method of the class " + clazz.getName() + ": " + methodName + "(...);");
            }
            StringBuilder bodyBuilder = new StringBuilder();
            for (int i = 0; i < arguments.length; i++) {
                bodyBuilder.append(arguments[i]);
                if (i != arguments.length - 1) {
                    bodyBuilder.append(", ");
                }
            }
            JExecutableForIfElse ifBody = new JExecutableForIfElse(this, this.parent);
            JExecutableForIfElse elseBody = new JExecutableForIfElse(this, this.parent);
            ifExecuting.apply(ifBody);
            elseExecuting.apply(elseBody);
            IfAndElse ifAndElse = new IfAndElse(new UsingStaticMethodAsVar(result, bodyBuilder.toString()), ifBody, elseBody);
            operations.add(ifAndElse);
        }

        public void ifOnly_Equal(Var var, Var isEqual, ArgumentOnly<JExecutable> ifExecuting) {
            if (!isVarExists(var)) throw new RuntimeException("The var not exists in the executing body!");
            if (!isVarExists(isEqual)) throw new RuntimeException("The var not exists in the executing body!");
            JExecutableForIfElse ifBody = new JExecutableForIfElse(this, this.parent);
            ifExecuting.apply(ifBody);
            IfOnly ifOnly = new IfOnly(new EqualCheck(var, isEqual), ifBody);
            operations.add(ifOnly);
        }

        public void ifOnly_BooleanVar(Var booleanTypeVar, ArgumentOnly<JExecutable> ifExecuting) {
            if (!isVarExists(booleanTypeVar)) throw new RuntimeException("The var not exists in the executing body!");
            JExecutableForIfElse ifBody = new JExecutableForIfElse(this, this.parent);
            ifExecuting.apply(ifBody);
            IfOnly ifOnly = new IfOnly(new BooleanVarCheck(booleanTypeVar.varString()), ifBody);
            operations.add(ifOnly);
        }

        public void ifOnly_Method(ArgumentOnly<JExecutable> ifExecuting, @NotNull Var var, @NotNull String methodName, Var... arguments) {
            if (!isVarExists(var)) throw new RuntimeException("The var not exists in the executing body!");
            Class<?> clazz = var.getType();
            boolean hasMethod = false;
            Method result = null;
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && !Modifier.isStatic(method.getModifiers())) {
                    if (!(method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                        throw new RuntimeException("The return type of the method is not a boolean type!");
                    }
                    hasMethod = true;
                    result = method;
                    break;
                }
            }
            if (!hasMethod) {
                throw new RuntimeException("Unknown method of the class " + var.getType().getName() + ": " + methodName + "(...);");
            }
            StringBuilder bodyBuilder = new StringBuilder();
            for (int i = 0; i < arguments.length; i++) {
                bodyBuilder.append(arguments[i]);
                if (i != arguments.length - 1) {
                    bodyBuilder.append(", ");
                }
            }
            JExecutableForIfElse ifBody = new JExecutableForIfElse(this, this.parent);
            ifExecuting.apply(ifBody);
            IfOnly ifOnly = new IfOnly(new UsingMethodAsVar(var.varString(), result, bodyBuilder.toString()), ifBody);
            operations.add(ifOnly);
        }

        public void ifOnly_StaticMethod(ArgumentOnly<JExecutable> ifExecuting, Class<?> clazz, @NotNull String methodName, Var... arguments) {
            if (!executable.extraClasses.contains(clazz)) {
                executable.extraClasses.add(clazz);
            }
            boolean hasMethod = false;
            Method result = null;
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && !Modifier.isStatic(method.getModifiers())) {
                    if (!(method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                        throw new RuntimeException("The return type of the method is not a boolean type!");
                    }
                    hasMethod = true;
                    result = method;
                    break;
                }
            }
            if (!hasMethod) {
                throw new RuntimeException("Unknown method of the class " + clazz.getName() + ": " + methodName + "(...);");
            }
            StringBuilder bodyBuilder = new StringBuilder();
            for (int i = 0; i < arguments.length; i++) {
                bodyBuilder.append(arguments[i]);
                if (i != arguments.length - 1) {
                    bodyBuilder.append(", ");
                }
            }
            JExecutableForIfElse ifBody = new JExecutableForIfElse(this, this.parent);
            ifExecuting.apply(ifBody);
            IfOnly ifOnly = new IfOnly(new UsingStaticMethodAsVar(result, bodyBuilder.toString()), ifBody);
            operations.add(ifOnly);
        }

        public Var createUsingMethodAsString(@NotNull Var var, @NotNull String methodName, Var... arguments) {
            if (!isVarExists(var)) throw new RuntimeException("The var not exists in the executing body!");
            Class<?> clazz = var.getType();
            boolean hasMethod = false;
            Method result = null;
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && !Modifier.isStatic(method.getModifiers())) {
                    hasMethod = true;
                    result = method;
                    break;
                }
            }
            if (!hasMethod) {
                throw new RuntimeException("Unknown method of the class " + var.getType().getName() + ": " + methodName + "(...);");
            }
            StringBuilder bodyBuilder = new StringBuilder();
            for (int i = 0; i < arguments.length; i++) {
                bodyBuilder.append(arguments[i].varString());
                if (i != arguments.length - 1) {
                    bodyBuilder.append(", ");
                }
            }
            return new UsingMethodAsVar(var.varString(), result, bodyBuilder.toString());
        }

        public Var createUsingMethodAsString(@NotNull Class<?> clazz, @NotNull String methodName, Var... arguments) {
            if (!executable.extraClasses.contains(clazz)) {
                executable.extraClasses.add(clazz);
            }
            boolean hasMethod = false;
            Method result = null;
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && Modifier.isStatic(method.getModifiers())) {
                    hasMethod = true;
                    result = method;
                    break;
                }
            }
            if (!hasMethod) {
                throw new RuntimeException("Unknown method of the class " + clazz.getName() + ": " + methodName + "(...);");
            }
            StringBuilder bodyBuilder = new StringBuilder();
            for (int i = 0; i < arguments.length; i++) {
                bodyBuilder.append(arguments[i].varString());
                if (i != arguments.length - 1) {
                    bodyBuilder.append(", ");
                }
            }
            return new UsingStaticMethodAsVar(result, bodyBuilder.toString());
        }

        @Override
        protected boolean isVarExists(Var var) {
            return executable.isVarExists(var);
        }

        @Override
        public String getString() {
            StringBuilder base = new StringBuilder();
            for (Operation operation : this.getOperations()) {
                base.append(operation.getString()).append("\n");
            }
            return base.toString();
        }
    }

    protected List<Class<?>> getRequirementTypes() {
        List<Class<?>> list = new ArrayList<>();
        for (Var var : vars.values()) {
            list.add(var.getType());
        }
        list.addAll(extraClasses);
        return list;
    }

    @Override
    public void addAnnotation(Class<?> annotation, Map<String, JStringVar> values) {
        getAnnotations().put(annotation, values);
    }

    @Override
    public Map<Class<?>, Map<String, JStringVar>> getAnnotations() {
        return annotations;
    }

}
