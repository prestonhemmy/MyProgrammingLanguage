package plc.project.analyzer;

import plc.project.evaluator.EvaluateException;
import plc.project.evaluator.RuntimeValue;
import plc.project.parser.Ast;

import java.lang.annotation.AnnotationTypeMismatchException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
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
        // TODO update parser let statement handling to:
        //  let_stmt ::= 'LET' identifier (':' identifier)? ('=' expr)? ';'
        throw new UnsupportedOperationException("TODO"); //TODO (see lecture)
    }

    @Override
    public Ir.Stmt.Def visit(Ast.Stmt.Def ast) throws AnalyzeException {
        // TODO update parser def statement handling to:
        //  def_stmt ::= 'DEF' identifier '(' (identifier (':' identifier)? (',' identifier (':' identifier)?)*)? ')'
        //               (':' identifier)? 'DO' stmt* 'END'
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public Ir.Stmt.If visit(Ast.Stmt.If ast) throws AnalyzeException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public Ir.Stmt.For visit(Ast.Stmt.For ast) throws AnalyzeException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public Ir.Stmt.Return visit(Ast.Stmt.Return ast) throws AnalyzeException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public Ir.Stmt.Expression visit(Ast.Stmt.Expression ast) throws AnalyzeException {
        var expression = visit(ast.expression());
        return new Ir.Stmt.Expression(expression);
    }

    @Override
    public Ir.Stmt.Assignment visit(Ast.Stmt.Assignment ast) throws AnalyzeException {
        throw new UnsupportedOperationException("TODO"); //TODO (see lecture)
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
        throw new UnsupportedOperationException("TODO"); //TODO
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
     * @param type
     * @return
     */
    private static boolean isSubtypeOfComparable(Type type) {
        return type.equals(Type.BOOLEAN) || type.equals(Type.INTEGER) || type.equals(Type.DECIMAL) ||
               type.equals(Type.STRING);
    }
}
