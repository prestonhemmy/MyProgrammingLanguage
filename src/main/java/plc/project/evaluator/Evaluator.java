package plc.project.evaluator;

import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;

public final class Evaluator implements Ast.Visitor<RuntimeValue, EvaluateException> {

    private Scope scope;

    public Evaluator(Scope scope) {
        this.scope = scope;
    }

    @Override
    public RuntimeValue visit(Ast.Source ast) throws EvaluateException {
        RuntimeValue value = new RuntimeValue.Primitive(null);

        // Assume function return statement
        try {
            for (var stmt : ast.statements()) {
                value = visit(stmt);
            }

            return value;

        // O.W. return statement outside of function
        } catch (ReturnException e) {
            throw new EvaluateException("Return statement outside of function");
        }
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Let ast) throws EvaluateException {
        // check if name already defined in current scope
        if (scope.get(ast.name(), true).isPresent()) {
            throw new EvaluateException("Variable '" + ast.name() + "' is already defined in the current scope");
        }

        RuntimeValue value;
        if (ast.value().isPresent()) {
            value = visit(ast.value().get());
        } else {
            value = new RuntimeValue.Primitive(null);
        }

        scope.define(ast.name(), value);

        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Def ast) throws EvaluateException {
        // check if name already defined in current scope
        if (scope.get(ast.name(), true).isPresent()) {
            throw new EvaluateException("Function '" + ast.name() + "' is already defined in the current scope");
        }

        // check for unique parameters
        var unique_parameters = new HashSet<>(ast.parameters());
        if (unique_parameters.size() != ast.parameters().size()) {
            throw new EvaluateException("Parameters must be unique");
        }

        // scope where function is defined
        Scope def_scope = scope;

        // function behavior
        RuntimeValue.Function.Definition definition = arguments -> {
            // check if number of arguments passed in matches arity
            if (arguments.size() != ast.parameters().size()) {
                throw new EvaluateException("Function '" + ast.name() + "' expects " + ast.parameters().size() +
                        " arguments, but found " + arguments.size());
            }

            // scope where function is called
            Scope caller_scope = scope;

            try {
                // scope within function definition body
                scope = new Scope(def_scope);

                for (int i = 0; i < ast.parameters().size(); i++) {
                    scope.define(ast.parameters().get(i), arguments.get(i));
                }

                RuntimeValue result = new RuntimeValue.Primitive(null); // Default to NIL

                try {
                    for (Ast.Stmt stmt : ast.body()) {
                        result = visit(stmt);
                    }

                    // return NIL by default
                    return new RuntimeValue.Primitive(null);
                } catch (ReturnException e) {
                    // extract return value
                    return e.getValue();
                }

            } finally {
                scope = caller_scope;   // revert to caller scope
            }
        };

        RuntimeValue.Function function = new RuntimeValue.Function(ast.name(), definition);
        scope.define(ast.name(), function);

        return function;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.If ast) throws EvaluateException {
        RuntimeValue condition_value = visit(ast.condition());
        Boolean bool_value = requireType(condition_value, Boolean.class);

        Scope original_scope = scope;
        RuntimeValue result = new RuntimeValue.Primitive(null); // Default to NIL

        try {
            scope = new Scope(original_scope);

            List<Ast.Stmt> body = bool_value ? ast.thenBody() : ast.elseBody();

            for (Ast.Stmt stmt : body) {
                result = visit(stmt);
            }

            return result;
        } finally {
            scope = original_scope;
        }
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.For ast) throws EvaluateException {
        RuntimeValue expr = visit(ast.expression());

        RuntimeValue.Primitive primitive = requireType(expr, RuntimeValue.Primitive.class);

        if (primitive.value() == null) {
            throw new EvaluateException("Expect an iterable, but found NIL");
        }

        // check if expression is iterable
        if (!(primitive.value() instanceof Iterable)) {
            throw new EvaluateException("Expected an iterable, but found " + primitive.value().getClass().getName());
        }

        Iterable<?> iterable = (Iterable<?>) primitive.value();

        // scope where for loop is entered
        Scope parent_scope = scope;

        try {
            for (Object element : iterable) {
                // scope corresponding to for loop body
                scope = new Scope(parent_scope);

                // check if element is a RuntimeValue
                if (!(element instanceof RuntimeValue)) {
                    throw new EvaluateException("Expected RuntimeValue in iterable, received " +
                            (element != null ? element.getClass().getName() : "null"));
                }

                scope.define(ast.name(), (RuntimeValue) element);

                for (Ast.Stmt stmt : ast.body()) {
                    visit(stmt);
                }
            }

            return new RuntimeValue.Primitive(null);

        } finally {
            // revert to parent scope
            scope = parent_scope;
        }
    }

    /**
     * Exception class which exits all nested scopes within a function and propagates the return value to the call scope.
     */
    public static final class ReturnException extends RuntimeException {
        public final RuntimeValue value;

        public ReturnException(RuntimeValue value) {
            super("RETURN statement");
            this.value = value;
        }

        public RuntimeValue getValue() {
            return value;
        }
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Return ast) throws EvaluateException {
        RuntimeValue value;

        if(ast.value().isPresent()) {
            value = visit(ast.value().get());
        } else {
            value = new RuntimeValue.Primitive(null);
        }

        throw new ReturnException(value);
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Expression ast) throws EvaluateException {
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Assignment ast) throws EvaluateException {
        // Handle variable assignment
        if (ast.expression() instanceof Ast.Expr.Variable) {
            String lhs = ((Ast.Expr.Variable) ast.expression()).name();

            // Check variable name exists
            if (scope.get(lhs, false).isEmpty()) {
                throw new EvaluateException("Variable '" + lhs + "' is not defined");
            }

            RuntimeValue rhs = visit(ast.value());

            scope.set(lhs, rhs);

            return rhs;

        // Handle property assignment
        } else if (ast.expression() instanceof Ast.Expr.Property) {
            Ast.Expr.Property property = (Ast.Expr.Property) ast.expression();

            RuntimeValue receiver = visit(property.receiver());

            // Check if receiver is an object
            if (!(receiver instanceof RuntimeValue.ObjectValue)) {
                throw new EvaluateException("Cannot access property since '" + property.receiver() + "' is not defined");
            }

            RuntimeValue.ObjectValue object_value = (RuntimeValue.ObjectValue) receiver;

            // Check if property is defined on receiver object
            var prop = object_value.scope().get(property.name(), false);
            if (prop.isEmpty()) {
                throw new EvaluateException("Property '" + property.name() + "' is not defined on '" + property.receiver() + "'");
            }

            RuntimeValue rhs = visit(ast.value());

            object_value.scope().set(property.name(), rhs);

            return rhs;

        } else {
            throw new EvaluateException("Expected left-hand side property or variable expression but found " + ast.expression());
        }
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Literal ast) throws EvaluateException {
        return new RuntimeValue.Primitive(ast.value());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Group ast) throws EvaluateException {
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Binary ast) throws EvaluateException {
        if (ast.operator().equals("AND")) {
            // check left first
            var left = visit(ast.left());
            var left_primitive = requireType(left, RuntimeValue.Primitive.class);
            if (!(left_primitive.value() instanceof Boolean)) {
                throw new EvaluateException("left operand must be a Boolean for AND operator");
            }

            // check for falsey short-circuiting
            if (!(Boolean) left_primitive.value()) {
                return new RuntimeValue.Primitive(false);
            }

            // O.W. left true
            var right = visit(ast.right());
            var right_primitive = requireType(right, RuntimeValue.Primitive.class);
            if (!(right_primitive.value() instanceof Boolean)) {
                throw new EvaluateException("right operand must be a Boolean for AND operator");
            }

            return new RuntimeValue.Primitive((Boolean) right_primitive.value());
        }

        if (ast.operator().equals("OR")) {
            // check left first
            var left = visit(ast.left());
            var left_primitive = requireType(left, RuntimeValue.Primitive.class);
            if (!(left_primitive.value() instanceof Boolean)) {
                throw new EvaluateException("left operand must be a Boolean for OR operator");
            }

            // check for truthy short-circuiting
            if ((Boolean) left_primitive.value()) {
                return new RuntimeValue.Primitive(true);
            }

            // O.W. left false
            var right = visit(ast.right());
            var right_primitive = requireType(right, RuntimeValue.Primitive.class);
            if (!(right_primitive.value() instanceof Boolean)) {
                throw new EvaluateException("right operand must be a Boolean for OR operator");
            }

            return new RuntimeValue.Primitive((Boolean) right_primitive.value());
        }

        var left = visit(ast.left());
        var right = visit(ast.right());

        switch (ast.operator()) {
            case "+":
                // check if left string
                var left_primitive = requireType(left, RuntimeValue.Primitive.class);

                if (left_primitive.value() instanceof String) {
                    return new RuntimeValue.Primitive(left_primitive.value().toString() + right.print());
                }

                // check if right is string
                try {
                    var right_primitive = requireType(right, RuntimeValue.Primitive.class);
                    if (right_primitive.value() instanceof String) {
                        return new RuntimeValue.Primitive(left.print() + right_primitive.value().toString());
                    }
                // O.W. continue
                } catch (EvaluateException _) {}

                // check if left is integer
                if (left_primitive.value() instanceof BigInteger) {
                    // assume right is (primitive) integer
                    var right_int = requireType(right, BigInteger.class);
                    return new RuntimeValue.Primitive(((BigInteger)left_primitive.value()).add(right_int));
                }

                // check if left is decimal
                if (left_primitive.value() instanceof BigDecimal) {
                    var right_decimal = requireType(right, BigDecimal.class);
                    return new RuntimeValue.Primitive(((BigDecimal)left_primitive.value()).add(right_decimal));
                }

                // O.W. invalid
                throw new EvaluateException("Invalid operands for '+' operator");

            case "-":
                // check if left is primitive
                left_primitive = requireType(left, RuntimeValue.Primitive.class);

                // check if left is integer
                if (left_primitive.value() instanceof BigInteger) {
                    // assume right is (primitive) integer
                    var right_int = requireType(right, BigInteger.class);
                    return new RuntimeValue.Primitive(((BigInteger)left_primitive.value()).subtract(right_int));
                }

                // check if left is decimal
                if (left_primitive.value() instanceof BigDecimal) {
                    // assume right is (primitive) decimal
                    var right_decimal = requireType(right, BigDecimal.class);
                    return new RuntimeValue.Primitive(((BigDecimal)left_primitive.value()).subtract(right_decimal));
                }

                // O.W. invalid
                throw new EvaluateException("Invalid operands for '-' operator");

            case "*":
                // check if left is primitive
                left_primitive = requireType(left, RuntimeValue.Primitive.class);

                // check if left is integer
                if (left_primitive.value() instanceof BigInteger) {
                    // assume right is (primitive) integer
                    var right_int = requireType(right, BigInteger.class);
                    return new RuntimeValue.Primitive(((BigInteger)left_primitive.value()).multiply(right_int));
                }

                // check if left is decimal
                if (left_primitive.value() instanceof BigDecimal) {
                    // assume right is (primitive) decimal
                    var right_decimal = requireType(right, BigDecimal.class);
                    return new RuntimeValue.Primitive(((BigDecimal)left_primitive.value()).multiply(right_decimal));
                }

                // O.W. invalid
                throw new EvaluateException("Invalid operands for '*' operator");

            case "/":
                // check if left is primitive
                left_primitive = requireType(left, RuntimeValue.Primitive.class);

                // check if left is integer
                if (left_primitive.value() instanceof BigInteger) {
                    // assume right is (primitive) integer
                    var right_int = requireType(right, BigInteger.class);

                    if (right_int.equals(BigInteger.ZERO)) {
                        throw new EvaluateException("Divide by zero error");
                    }

                    return new RuntimeValue.Primitive(((BigInteger)left_primitive.value()).divide(right_int));
                }

                // check if left is decimal
                if (left_primitive.value() instanceof BigDecimal) {
                    // assume right is (primitive) decimal
                    var right_decimal = requireType(right, BigDecimal.class);

                    if (right_decimal.compareTo(BigDecimal.ZERO) == 0) {
                        throw new EvaluateException("Divide by zero error");
                    }

                    return new RuntimeValue.Primitive(
                            ((BigDecimal)left_primitive.value()).divide(right_decimal, java.math.RoundingMode.HALF_EVEN));
                }

                // O.W. invalid
                throw new EvaluateException("Invalid operands for '/' operator");

            case "==":
                // check if object comparison
                if (left instanceof RuntimeValue.ObjectValue && right instanceof RuntimeValue.ObjectValue) {
                    return new RuntimeValue.Primitive(java.util.Objects.equals(left, right));
                }

                // check if object-primitive comparison
                if (left instanceof RuntimeValue.ObjectValue || right instanceof RuntimeValue.ObjectValue) {
                    return new RuntimeValue.Primitive(false);
                }

                left_primitive = requireType(left, RuntimeValue.Primitive.class);
                var right_primitive = requireType(right, RuntimeValue.Primitive.class);

                return new RuntimeValue.Primitive(
                        java.util.Objects.equals(left_primitive.value(), right_primitive.value()));


            case "!=":
                // check if object comparison
                if (left instanceof RuntimeValue.ObjectValue && right instanceof RuntimeValue.ObjectValue) {
                    return new RuntimeValue.Primitive(!java.util.Objects.equals(left, right));
                }

                // check if object-primitive comparison
                if (left instanceof RuntimeValue.ObjectValue || right instanceof RuntimeValue.ObjectValue) {
                    return new RuntimeValue.Primitive(true);
                }

                left_primitive = requireType(left, RuntimeValue.Primitive.class);
                right_primitive = requireType(right, RuntimeValue.Primitive.class);

                return new RuntimeValue.Primitive(
                        !java.util.Objects.equals(left_primitive.value(), right_primitive.value()));

            case "<":
            case "<=":
            case ">":
            case ">=":
                left_primitive = requireType(left, RuntimeValue.Primitive.class);

                // check left is comparable
                if (!(left_primitive.value() instanceof Comparable)) {
                    throw new EvaluateException("left operand must be comparable");
                }

                right_primitive = requireType(right, RuntimeValue.Primitive.class);

                // check right is comparable
                if (!(right_primitive.value() instanceof Comparable)) {
                    throw new EvaluateException("right operand must be comparable");
                }

                // check operand types match
                if (left_primitive.value() == null || right_primitive.value() == null ||
                        !left_primitive.value().getClass().equals(right_primitive.value().getClass())) {
                    throw new EvaluateException("Comparison operands types must match");
                }

                int comparison = ((Comparable) left_primitive.value()).compareTo(right_primitive.value());

                switch (ast.operator()) {
                    case "<":
                        return new RuntimeValue.Primitive(comparison < 0);
                    case "<=":
                        return new RuntimeValue.Primitive(comparison <= 0);
                    case ">":
                        return new RuntimeValue.Primitive(comparison > 0);
                    case ">=":
                        return new RuntimeValue.Primitive(comparison >= 0);
                    default:
                        throw new EvaluateException("Invalid comparison operator: '" + ast.operator() + "'");
                }

            default:
                throw new EvaluateException("Invalid operator: '" + ast.operator() + "'");
        }
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Variable ast) throws EvaluateException {
        var variable = scope.get(ast.name(), false);

        if (variable.isEmpty()) {
            throw new EvaluateException("Variable '" + ast.name() + "' is not defined");
        }

        return variable.get();
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Property ast) throws EvaluateException {
        RuntimeValue receiver = visit(ast.receiver());

        // Check if receiver is an object
        if (!(receiver instanceof RuntimeValue.ObjectValue)) {
            throw new EvaluateException("Cannot access property since '" + ast.receiver() + "' is not defined");
        }

        RuntimeValue.ObjectValue object_value = (RuntimeValue.ObjectValue) receiver;

        // Check if property is defined on receiver
        var property = object_value.scope().get(ast.name(), false);
        if (property.isEmpty()) {
            throw new EvaluateException("Property '" + ast.name() + "' is not defined on '" + ast.receiver() + "'");
        }

        return property.get();
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Function ast) throws EvaluateException {
        var function = scope.get(ast.name(), false);

        if (function.isEmpty()) {
            throw new EvaluateException("Function '" + ast.name() + "' is not defined");
        }

        // check if function type
        var funct = requireType(function.get(), RuntimeValue.Function.class);

        var evaluated_args = new java.util.ArrayList<RuntimeValue>();
        for (var arg : ast.arguments()) {
            evaluated_args.add(visit(arg));
        }

        return funct.definition().invoke(evaluated_args);
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Method ast) throws EvaluateException {
        RuntimeValue receiver = visit(ast.receiver());

        // Check if receiver is an object
        if (!(receiver instanceof RuntimeValue.ObjectValue)) {
            throw new EvaluateException("Cannot access property since '" + ast.receiver() + "' is not defined");
        }

        RuntimeValue.ObjectValue object_value = (RuntimeValue.ObjectValue) receiver;

        // Check if method is defined on receiver
        var method = object_value.scope().get(ast.name(), false);
        if (method.isEmpty()) {
            throw new EvaluateException("Property '" + ast.name() + "' is not defined on '" + ast.receiver() + "'");
        }

        var method_funct = requireType(method.get(), RuntimeValue.Function.class);

        var evaluated_args = new java.util.ArrayList<RuntimeValue>();
        evaluated_args.add(receiver);

        for (var arg : ast.arguments()) {
            evaluated_args.add(visit(arg));
        }

        return method_funct.definition().invoke(evaluated_args);
    }

    @Override
    public RuntimeValue visit(Ast.Expr.ObjectExpr ast) throws EvaluateException {
        var object_scope = new Scope(null);
        var object_value = new RuntimeValue.ObjectValue(ast.name(), object_scope);

        // property handling
        for (var field : ast.fields()) {
            // check if field name already defined in object scope
            if (object_scope.get(field.name(), true).isPresent()) {
                throw new EvaluateException("Variable '" + field.name() + "' is already defined in '" + ast.name() + "'s' scope");
            }

            RuntimeValue value;
            if (field.value().isPresent()) {
                value = visit(field.value().get());
            } else {
                value = new RuntimeValue.Primitive(null);
            }

            object_scope.define(field.name(), value);
        }

        // method handling
        Scope def_scope = scope;            // scope where object is defined

        for (var method : ast.methods()) {
            // check if name already defined in current scope
            if (object_scope.get(method.name(), true).isPresent()) {
                throw new EvaluateException("Method '" + method.name() + "' is already defined in '" + ast.name() + "'s' scope");
            }

            // check for unique parameters
            var unique_parameters = new HashSet<>(method.parameters());
            if (unique_parameters.size() != method.parameters().size()) {
                throw new EvaluateException("Method parameters must be unique");
            }

            // method behavior
            RuntimeValue.Function.Definition definition = arguments -> {
                // check if number of arguments passed in matches arity
                if (arguments.size() != method.parameters().size() + 1) {
                    throw new EvaluateException("Method '" + method.name() + "' expects " + method.parameters().size() +
                            " arguments, but found " + (arguments.size() - 1));
                }

                // scope where method is called
                Scope caller_scope = scope;

                try {
                    // scope within method definition body
                    scope = new Scope(object_scope);

                    scope.define("this", arguments.getFirst());

                    for (int i = 0; i < method.parameters().size(); i++) {
                        scope.define(method.parameters().get(i), arguments.get(i + 1));
                    }

                    RuntimeValue result = new RuntimeValue.Primitive(null); // Default to NIL

                    try {
                        for (Ast.Stmt stmt : method.body()) {
                            result = visit(stmt);
                        }

                        // return NIL by default
                        return new RuntimeValue.Primitive(null);
                    } catch (ReturnException e) {
                        // extract return value
                        return e.getValue();
                    }

                } finally {
                    scope = caller_scope;   // revert to caller scope
                }
            };

            RuntimeValue.Function function = new RuntimeValue.Function(method.name(), definition);
            object_scope.define(method.name(), function);
        }

        return object_value;
    }

    /**
     * Helper function for extracting RuntimeValues of specific types. If the
     * type is subclass of {@link RuntimeValue} the check applies to the value
     * itself, otherwise the value is expected to be a {@link RuntimeValue.Primitive}
     * and the check applies to the primitive value.
     */
    private static <T> T requireType(RuntimeValue value, Class<T> type) throws EvaluateException {
        //To be discussed in lecture 3/5.
        if (RuntimeValue.class.isAssignableFrom(type)) {
            if (!type.isInstance(value)) {
                throw new EvaluateException("Expected value to be of type " + type + ", received " + value.getClass() + ".");
            }
            return (T) value;
        } else {
            var primitive = requireType(value, RuntimeValue.Primitive.class);
            if (!type.isInstance(primitive.value())) {
                var received = primitive.value() != null ? primitive.value().getClass() : null;
                throw new EvaluateException("Expected value to be of type " + type + ", received " + received + ".");
            }
            return (T) primitive.value();
        }
    }

}
