package plc.project.evaluator;

import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

public final class Evaluator implements Ast.Visitor<RuntimeValue, EvaluateException> {

    private Scope scope;

    public Evaluator(Scope scope) {
        this.scope = scope;
    }

    @Override
    public RuntimeValue visit(Ast.Source ast) throws EvaluateException {
        RuntimeValue value = new RuntimeValue.Primitive(null);
        for (var stmt : ast.statements()) {
            value = visit(stmt);
        }
        //TODO: Handle the possibility of RETURN being called outside of a function.
        return value;
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
            value = new RuntimeValue.Primitive(null); // NIL
        }

        scope.define(ast.name(), value);

        return value;    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Def ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO
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
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Return ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Expression ast) throws EvaluateException {
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Assignment ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO - Priority: 3 (Variable connection)
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

                    // TODO: Check if division results in decimal and update type
                    //  If decimal then also requires rounding

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
                left_primitive = requireType(left, RuntimeValue.Primitive.class);
                var right_primitive = requireType(right, RuntimeValue.Primitive.class);

                return new RuntimeValue.Primitive(
                        java.util.Objects.equals(left_primitive.value(), right_primitive.value()));


            case "!=":
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
                        throw new EvaluateException("Invalid comparison operator: " + ast.operator());
                }

            default:
                throw new EvaluateException("Invalid operator: " + ast.operator());
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
        throw new UnsupportedOperationException("TODO"); //TODO
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
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public RuntimeValue visit(Ast.Expr.ObjectExpr ast) throws EvaluateException {
        throw new UnsupportedOperationException("TODO"); //TODO
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
