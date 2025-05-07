package plc.project.analyzer;

import plc.project.evaluator.EvaluateException;
import plc.project.evaluator.Evaluator;
import plc.project.evaluator.RuntimeValue;
import plc.project.parser.Ast;

import java.lang.annotation.AnnotationTypeMismatchException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;

public final class Analyzer implements Ast.Visitor<Ir, AnalyzeException> {

    private Scope scope;

    public Analyzer(Scope scope) {
        this.scope = scope;
    }

    @Override
    public Ir.Source visit(Ast.Source ast) throws AnalyzeException {
        var statements = new ArrayList<Ir.Stmt>();
        for (var statement : ast.statements()) {
            statements.add(visit(statement));
        }
        return new Ir.Source(statements);
    }

    private Ir.Stmt visit(Ast.Stmt ast) throws AnalyzeException {
        return (Ir.Stmt) visit((Ast) ast); //helper to cast visit(Ast.Stmt) to Ir.Stmt
    }

    @Override
    public Ir.Stmt.Let visit(Ast.Stmt.Let ast) throws AnalyzeException {
        // let_stmt ::= 'LET' identifier (':' identifier)? ('=' expr)? ';'
        // check if name already defined
        if (scope.get(ast.name(), true).isPresent()) {
            throw new AnalyzeException("'" + ast.name() + "' is already defined in the current scope");
        }

        Optional<Type> type = Optional.empty();
        if (ast.type().isPresent()) {
            if (!Environment.TYPES.containsKey(ast.type().get())) {
                throw new AnalyzeException("Type '" + ast.type() + "' is not a valid type");
            }
            type = Optional.of(Environment.TYPES.get(ast.type().get()));
        }

        Optional<Ir.Expr> value;
        if (ast.value().isPresent()) {
            value = Optional.of(visit(ast.value().get()));
        } else {
            value = Optional.empty();
        }

        Type variable_type;
        if (type.isPresent()) {
            variable_type = Environment.TYPES.get(ast.type().get());    // if type declared then explicit type
        } else if (value.isPresent()) {
            variable_type = value.get().type();                         // O.W. if value then inferred type
        } else {
            variable_type = Type.ANY;                                   // O.W. type ANY
        }

        if (value.isPresent()) {
            requireSubtype(value.get().type(), variable_type);
        }

        scope.define(ast.name(), variable_type);

        return new Ir.Stmt.Let(ast.name(), variable_type, value);
    }

    @Override
    public Ir.Stmt.Def visit(Ast.Stmt.Def ast) throws AnalyzeException {
        // def_stmt ::= 'DEF' identifier '('
        //              (identifier (':' identifier)? (',' identifier (':' identifier)?)*)?
        //              ')' (':' identifier)? 'DO' stmt* 'END'
        // check if name already defined in current scope
        if (scope.get(ast.name(), true).isPresent()) {
            throw new AnalyzeException("'" + ast.name() + "' is already defined in the current scope");
        }

        // check for unique parameters
        var unique_parameters = new HashSet<>(ast.parameters());
        if (unique_parameters.size() != ast.parameters().size()) {
            throw new AnalyzeException("Parameters must be unique");
        }

        var parameter_types = new ArrayList<Type>();
        for (int i = 0; i < ast.parameters().size(); i++) {
            Optional<String> type_str = i < ast.parameterTypes().size() ? ast.parameterTypes().get(i) : Optional.empty();
            Type type = Type.ANY;
            if (type_str.isPresent()) {
                if (!Environment.TYPES.containsKey(type_str.get())) {
                    throw new AnalyzeException("Type '" + type_str + "' is not a valid type");
                }
                type = Environment.TYPES.get(type_str.get());
            }

            parameter_types.add(type);
        }

        Type return_type = Type.ANY;
        if (ast.returnType().isPresent()) {
            String type_str = ast.returnType().get();
            if (!Environment.TYPES.containsKey(type_str)) {
                throw new AnalyzeException("Type '" + type_str + "' is not a valid type");
            }

            return_type = Environment.TYPES.get(type_str);
        }

        // scope where function is defined
        Scope def_scope = scope;

        var function_type = new Type.Function(parameter_types, return_type);
        def_scope.define(ast.name(), function_type);

        // scope within function body
        Scope function_scope = new Scope(def_scope);

        var parameters = new ArrayList<Ir.Stmt.Def.Parameter>();
        for (int i = 0; i < ast.parameters().size(); i++) {
            String name = ast.parameters().get(i);
            Type type = parameter_types.get(i);
            scope.define(name, type);
            parameters.add(new Ir.Stmt.Def.Parameter(name, type));
        }

        function_scope.define("$RETURNS", return_type);

        scope = function_scope;

        var body_statements = new ArrayList<Ir.Stmt>();
        for (var stmt : ast.body()) {
            body_statements.add(visit(stmt));
        }

        scope = def_scope;

        return new Ir.Stmt.Def(ast.name(), parameters, return_type, body_statements);
    }

    @Override
    public Ir.Stmt.If visit(Ast.Stmt.If ast) throws AnalyzeException {
        Ir.Expr condition = visit(ast.condition());

        requireSubtype(condition.type(), Type.BOOLEAN);

        // analyze then body
        Scope original_scope = scope;
        scope = new Scope(original_scope);

        var then_statements = new ArrayList<Ir.Stmt>();
        for (var stmt : ast.thenBody()) {
            then_statements.add(visit(stmt));
        }

        scope = original_scope;

        // analyze else body
        scope = new Scope(original_scope);

        var else_statements = new ArrayList<Ir.Stmt>();
        for (var stmt : ast.elseBody()) {
            else_statements.add(visit(stmt));
        }

        scope = original_scope;

        return new Ir.Stmt.If(condition, then_statements, else_statements);
    }

    @Override
    public Ir.Stmt.For visit(Ast.Stmt.For ast) throws AnalyzeException {
        // analyze condition expression
        Ir.Expr expr = visit(ast.expression());

        requireSubtype(expr.type(), Type.ITERABLE);

        Scope original_scope = scope;
        scope = new Scope(original_scope);

        String iterable = ast.name();
        scope.define(iterable, Type.INTEGER);

        // analyze body
        var body_statements = new ArrayList<Ir.Stmt>();
        for (var stmt : ast.body()) {
            body_statements.add(visit(stmt));
        }

        scope = original_scope;

        return new Ir.Stmt.For(iterable, Type.INTEGER, expr, body_statements);
    }

    @Override
    public Ir.Stmt.Return visit(Ast.Stmt.Return ast) throws AnalyzeException {
        Optional<Type> return_type = scope.get("$RETURNS", false);
        if (return_type.isEmpty()) {
            throw new AnalyzeException("Cannot call RETURN statement from outside of a function");
        }

        Type type = return_type.get();

        Optional<Ir.Expr> value;
        if (ast.value().isPresent()) {
            Ir.Expr expr = visit(ast.value().get());

            // check return value's type matches return type
            requireSubtype(expr.type(), type);

            value = Optional.of(expr);
        } else {
            // O.W. check NIL is subtype of return type (which is EQUITABLE OR ANY)
            requireSubtype(Type.NIL, type);

            value = Optional.empty();
        }

        return new Ir.Stmt.Return(value);
    }

    @Override
    public Ir.Stmt.Expression visit(Ast.Stmt.Expression ast) throws AnalyzeException {
        var expression = visit(ast.expression());
        return new Ir.Stmt.Expression(expression);
    }

    @Override
    public Ir.Stmt.Assignment visit(Ast.Stmt.Assignment ast) throws AnalyzeException {
        Ir.Expr lhs = visit(ast.expression());
        Ir.Expr rhs = visit(ast.value());

        // handle variable assignment
        if (lhs instanceof Ir.Expr.Variable) {
            Ir.Expr.Variable variable = (Ir.Expr.Variable) lhs;

            if (!rhs.type().equals(Type.ANY)) {
                requireSubtype(rhs.type(), variable.type());
            }

            return new Ir.Stmt.Assignment.Variable(variable, rhs);

        // handle property assignment
        } else if (lhs instanceof Ir.Expr.Property) {
            Ir.Expr.Property property = (Ir.Expr.Property) lhs;

            if (!rhs.type().equals(Type.ANY)) {
                requireSubtype(rhs.type(), property.type());
            }

            return new Ir.Stmt.Assignment.Property(property, rhs);

        }

        // O.W.
        throw new AnalyzeException("Expected left-hand side property or variable expression but found '" + ast.expression());
    }

    private Ir.Expr visit(Ast.Expr ast) throws AnalyzeException {
        return (Ir.Expr) visit((Ast) ast); //helper to cast visit(Ast.Expr) to Ir.Expr
    }

    @Override
    public Ir.Expr.Literal visit(Ast.Expr.Literal ast) throws AnalyzeException {
        var type = switch (ast.value()) {
            case null -> Type.NIL;
            case Boolean _ -> Type.BOOLEAN;
            case BigInteger _ -> Type.INTEGER;
            case BigDecimal _ -> Type.DECIMAL;
            case String _ -> Type.STRING;
            //If the AST value isn't one of the above types, the Parser is
            //returning an incorrect AST - this is an implementation issue,
            //hence throw AssertionError rather than AnalyzeException.
            default -> throw new AssertionError(ast.value().getClass());
        };
        return new Ir.Expr.Literal(ast.value(), type);
    }

    @Override
    public Ir.Expr.Group visit(Ast.Expr.Group ast) throws AnalyzeException {
        Ir.Expr expression = visit(ast.expression());
        return new Ir.Expr.Group(expression);
    }

    @Override
    public Ir.Expr.Binary visit(Ast.Expr.Binary ast) throws AnalyzeException {
        Ir.Expr left = visit(ast.left());
        Ir.Expr right = visit(ast.right());

        String operator = ast.operator();

        switch (operator) {
            case "+":
                // handle concatenation
                if (left.type().equals(Type.STRING) || right.type().equals(Type.STRING)) {
                    return new Ir.Expr.Binary(operator, left, right, Type.STRING);

                // O.W. INTEGER or DECIMAL addition
                } else if (left.type().equals(Type.INTEGER) || left.type().equals(Type.DECIMAL)) {
                    if (left.type().equals(right.type())) {
                        return new Ir.Expr.Binary(operator, left, right, left.type());
                    } else {
                        throw new AnalyzeException("Operand types '" + left.type() + "' and '" + right.type() + "' must match");
                    }
                } else {
                    throw new AnalyzeException("Expected operand types of INTEGER, DECIMAL, or STRING but got '"
                                                + left.type() + "' and '" + right.type() + "'");
                }

            case "-":
            case "*":
            case "/":
                // check left and right types agree
                if ((left.type().equals(Type.INTEGER) || right.type().equals(Type.DECIMAL)) &&
                        left.type().equals(right.type())) {
                    return new Ir.Expr.Binary(operator, left, right, left.type());
                } else {
                    throw new AnalyzeException("Operand types '" + left.type() + "' and '" + right.type() + "' must match");
                }
            case "<":
            case "<=":
            case ">":
            case ">=":
                requireSubtype(left.type(), Type.COMPARABLE);
                if (left.type().equals(right.type())) {
                    return new Ir.Expr.Binary(operator, left, right, Type.BOOLEAN);
                } else {
                    throw new AnalyzeException("Operand types '" + left.type() + "' and '" + right.type() + "' must match");
                }
            case "==":
            case "!=":
                requireSubtype(left.type(), Type.EQUATABLE);
                requireSubtype(right.type(), Type.EQUATABLE);
                return new Ir.Expr.Binary(operator, left, right, Type.BOOLEAN);
            case "AND":
            case "OR":
                requireSubtype(left.type(), Type.BOOLEAN);
                requireSubtype(right.type(), Type.BOOLEAN);
                return new Ir.Expr.Binary(operator, left, right, Type.BOOLEAN);
            default:
                throw new AssertionError(ast.operator());
        }
    }

    @Override
    public Ir.Expr.Variable visit(Ast.Expr.Variable ast) throws AnalyzeException {
        Optional<Type> type_optional = scope.get(ast.name(), false);

        if (type_optional.isEmpty()) {
            throw new AnalyzeException("Variable '" + ast.name() + "' is not defined");
        }

        Type type = type_optional.get();

        return new Ir.Expr.Variable(ast.name(), type);
    }

    @Override
    public Ir.Expr.Property visit(Ast.Expr.Property ast) throws AnalyzeException {
        // analyze receiver
        var receiver = visit(ast.receiver());
        if (!(receiver.type() instanceof Type.Object)) {
            throw new AnalyzeException("Expected receiver expression of type Object but got '" + receiver.type() + "'");
        }

        Type.Object object_type = (Type.Object) receiver.type();

        // analyze property
        Optional<Type> type_optional = object_type.scope().get(ast.name(), false);
        if (type_optional.isEmpty()) {
            throw new AnalyzeException("Property '" + ast.name() + "' is not defined on '" + ast.receiver() + "'");
        }

        Type type = type_optional.get();

        return new Ir.Expr.Property(receiver, ast.name(), type);
    }

    @Override
    public Ir.Expr.Function visit(Ast.Expr.Function ast) throws AnalyzeException {
        Optional<Type> type_optional = scope.get(ast.name(), false);
        if (type_optional.isEmpty()) {
            throw new AnalyzeException("Function '" + ast.name() + "' is not defined");
        }

        Type type = type_optional.get();
        if (!(type instanceof Type.Function)) {
            throw new AnalyzeException("Expected type Function but got '" + type + "'");
        }

        Type.Function function = (Type.Function) type;

        // check arity
        if (ast.arguments().size() != function.parameters().size()) {
            throw new AnalyzeException("Function '" + ast.name() + "' expects " + function.parameters().size() +
                                       (function.parameters().size() == 1 ? " argument " : " arguments ") +
                                       "but found " + ast.arguments().size());
        }

        var analyzed_arguments = new ArrayList<Ir.Expr>();
        for (int i = 0; i < ast.arguments().size(); i++) {
            var arg = visit(ast.arguments().get(i));
            Type parameter_type = function.parameters().get(i);

            // check argument types
            try {
                requireSubtype(arg.type(), parameter_type);
            } catch (AnalyzeException e) {
                throw new AnalyzeException("Argument " + (i + 1) + " of function '" + ast.name() + "' : " +
                                            e.getMessage());
            }

            analyzed_arguments.add(arg);
        }

        return new Ir.Expr.Function(ast.name(), analyzed_arguments, function.returns());
    }

    @Override
    public Ir.Expr.Method visit(Ast.Expr.Method ast) throws AnalyzeException {
        // receiver analysis
        var receiver = visit(ast.receiver());
        if (!(receiver.type() instanceof Type.Object)) {
            throw new AnalyzeException("Expected receiver expression of type Object but got '" + receiver.type() + "'");
        }

        Type.Object object_type = (Type.Object) receiver.type();

        // method analysis
        Optional<Type> type_optional = object_type.scope().get(ast.name(), false);
        if (type_optional.isEmpty()) {
            throw new AnalyzeException("Method '" + ast.name() + "' is not defined on '" + ast.receiver() + "'");
        }

        Type type = type_optional.get();
        if (!(type instanceof Type.Function)) {
            throw new AnalyzeException("Expected type Method but got '" + type + "'");
        }

        Type.Function function = (Type.Function) type;

        // check arity
        if (ast.arguments().size() != function.parameters().size()) {
            throw new AnalyzeException("Method '" + ast.name() + "' expects " + function.parameters().size() +
                    (function.parameters().size() == 1 ? " argument " : " arguments ") +
                    "but found " + ast.arguments().size());
        }

        var analyzed_arguments = new ArrayList<Ir.Expr>();
        for (int i = 0; i < ast.arguments().size(); i++) {
            var arg = visit(ast.arguments().get(i));
            Type parameter_type = function.parameters().get(i);

            // check argument types
            try {
                requireSubtype(arg.type(), parameter_type);
            } catch (AnalyzeException e) {
                throw new AnalyzeException("Argument " + (i + 1) + " of function '" + ast.name() + "' : " +
                        e.getMessage());
            }

            analyzed_arguments.add(arg);
        }

        return new Ir.Expr.Method(receiver, ast.name(), analyzed_arguments, function.returns());
    }

    @Override
    public Ir.Expr.ObjectExpr visit(Ast.Expr.ObjectExpr ast) throws AnalyzeException {
        // ensure object name not contained in Environment.TYPES
        if (ast.name().isPresent() && Environment.TYPES.containsKey(ast.name().get())) {
            throw new AnalyzeException("Object '" + ast.name().get() + "' is already a predefined type");
        }

        Scope object_scope = new Scope(scope);
        Type.Object object_type = new Type.Object(object_scope);

        Scope original_scope = scope;
        scope = object_scope;

        // handle fields
        var fields = new ArrayList<Ir.Stmt.Let>();
        for (var field : ast.fields()) {
            if (object_scope.get(field.name(), true).isPresent()) {
                throw new AnalyzeException("Field '" + ast.name() + "' is already defined on the object '" + ast.name() + "'");
            }

            Optional<Type> type = Optional.empty();
            if (field.type().isPresent()) {
                if (!Environment.TYPES.containsKey(field.name())) {
                    throw new AnalyzeException("Type '" + field.type() + "' is not a valid type");
                }
                type = Optional.of(Environment.TYPES.get(field.type().get()));
            }

            Optional<Ir.Expr> value = Optional.empty();
            if (field.value().isPresent()) {
                value = Optional.of(visit(field.value().get()));
            }

            Type field_type;
            if (type.isPresent()) {
                field_type = type.get();    // if type declared then explicit type
            } else if (value.isPresent()) {
                field_type = value.get().type();                         // O.W. if value then inferred type
            } else {
                field_type = Type.ANY;                                   // O.W. type ANY
            }

            if (value.isPresent()) {
                requireSubtype(value.get().type(), field_type);
            }

            object_scope.define(field.name(), field_type);

            fields.add(new Ir.Stmt.Let(field.name(), field_type, value));
        }

        // handle methods
        for (var method : ast.methods()) {
            // check if name already defined in current scope
            if (object_scope.get(method.name(), true).isPresent()) {
                throw new AnalyzeException("Method '" + ast.name() + "' is already defined on the object '" + ast.name() + "'");
            }

            // check for unique parameters
            var unique_parameters = new HashSet<>(method.parameters());
            if (unique_parameters.size() != method.parameters().size()) {
                throw new AnalyzeException("Parameters must be unique");
            }

            var parameter_types = new ArrayList<Type>();
            for (int i = 0; i < method.parameters().size(); i++) {
                Optional<String> type_str = i < method.parameterTypes().size() ? method.parameterTypes().get(i) : Optional.empty();
                Type type = Type.ANY;
                if (type_str.isPresent()) {
                    if (!Environment.TYPES.containsKey(type_str.get())) {
                        throw new AnalyzeException("Type '" + type_str.get() + "' is not a valid type");
                    }
                    type = Environment.TYPES.get(type_str.get());
                }

                parameter_types.add(type);
            }

            Type return_type = Type.ANY;
            if (method.returnType().isPresent()) {
                String type_str = method.returnType().get();
                if (!Environment.TYPES.containsKey(type_str)) {
                    throw new AnalyzeException("Type '" + type_str + "' is not a valid type");
                }

                return_type = Environment.TYPES.get(type_str);
            }

            var method_type = new Type.Function(parameter_types, return_type);
            object_scope.define(method.name(), method_type);
        }

        // handle method bodies
        var methods = new ArrayList<Ir.Stmt.Def>();
        for (var method : ast.methods()) {
            Type return_type = Type.ANY;
            if (method.returnType().isPresent()) {
                return_type = Environment.TYPES.get(method.returnType().get());
            }

            Scope method_scope = new Scope(object_scope);

            method_scope.define("this", object_type);

            var parameters = new ArrayList<Ir.Stmt.Def.Parameter>();
            for (int i = 0; i < method.parameters().size(); i++) {
                String name = method.parameters().get(i);
                Type type = i < method.parameterTypes().size() && method.parameterTypes().get(i).isPresent() ?
                        Environment.TYPES.get(method.parameterTypes().get(i).get()) :
                        Type.ANY;

                method_scope.define(name, type);

                parameters.add(new Ir.Stmt.Def.Parameter(name, type));
            }

            method_scope.define("$RETURNS", return_type);

            Scope def_scope = scope;
            scope = method_scope;

            var body_statements = new ArrayList<Ir.Stmt>();
            for (var stmt : method.body()) {
                body_statements.add(visit(stmt));
            }

            scope = def_scope;

            methods.add(new Ir.Stmt.Def(method.name(), parameters, return_type, body_statements));
        }

        scope = original_scope;

        return new Ir.Expr.ObjectExpr(ast.name(), fields, methods, object_type);
    }

    public static void requireSubtype(Type type, Type other) throws AnalyzeException {
        //         ANY
        //          |
        //      EQUITABLE                         COMPARABLE
        //    /     |     \                   /    |      |    \
        // NIL  COMPARABLE ITERABLE     BOOLEAN INTEGER DECIMAL STRING

        // if trivial subtype
        if (type.equals(other)) {
            return;
        }

        // if subtype of ANY
        if (other.equals(Type.ANY)) {
            return;
        }

        // if subtype of EQUITABLE
        if ((type.equals(Type.NIL) || type.equals(Type.COMPARABLE) || isSubtypeOfComparable(type) ||
             type.equals(Type.ITERABLE)) && other.equals(Type.EQUATABLE)) {
            return;
        }

        // if subtype of COMPARABLE
        if (isSubtypeOfComparable(type) && other.equals(Type.COMPARABLE)) {
            return;
        }

        // O.W.
        throw new AnalyzeException("Expected '" + type + "' to be a subtype of '" + other + "'");
    }

    /**
     * Helper function for checking if subtype of COMPARABLE
     */
    private static boolean isSubtypeOfComparable(Type type) {
        return type.equals(Type.BOOLEAN) || type.equals(Type.INTEGER) || type.equals(Type.DECIMAL) ||
               type.equals(Type.STRING);
    }
}
