package plc.project.generator;

import plc.project.analyzer.Ir;
import plc.project.analyzer.Type;

import java.math.BigDecimal;
import java.math.BigInteger;

public class Generator implements Ir.Visitor<StringBuilder, RuntimeException> {

    private final StringBuilder builder = new StringBuilder();
    private int indent = 0;

    private void newline(int indent) {
        builder.append("\n");
        builder.append("    ".repeat(indent));
    }

    @Override
    public StringBuilder visit(Ir.Source ir) {
        builder.append(Environment.imports()).append("\n\n");
        builder.append("public final class Main {").append("\n\n");
        builder.append(Environment.definitions()).append("\n");
        //Java doesn't allow for nested functions, but we will pretend it does.
        //To support simple programs involving functions, we will "hoist" any
        //variable/function declaration at the start of the program to allow
        //these functions to be used as valid Java.
        indent = 1;
        boolean main = false;
        for (var statement : ir.statements()) {
            newline(indent);
            if (!main) {
                if (statement instanceof Ir.Stmt.Let || statement instanceof Ir.Stmt.Def) {
                    builder.append("static ");
                } else {
                    builder.append("public static void main(String[] args) {");
                    main = true;
                    indent = 2;
                    newline(indent);
                }
            }
            visit(statement);
        }
        if (main) {
            builder.append("\n").append("    }");
        }
        indent = 0;
        builder.append("\n\n").append("}");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Let ir) {
        // <Type> <name>;
        // <Type> <name> = <value>;
        // var <name> = <value>;

        // check if 'var' should be used
        if (ir.type() instanceof Type.Object) {
            builder.append("var ");
        } else {
            builder.append(ir.type().jvmName()).append(" ");
        }

        builder.append(ir.name());

        if (ir.value().isPresent()) {
            builder.append(" = ");
            visit(ir.value().get());
        }

        builder.append(";");

        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Def ir) {
        // <ReturnType> <name>(<First> <first>, <Second> <second>, <Third> <third>, ...) {
        //    <statements...>
        // }

        builder.append(ir.returns().jvmName()).append(" ");
        builder.append(ir.name());

        // parameters
        builder.append("(");
        for (int i = 0; i < ir.parameters().size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }

            Ir.Stmt.Def.Parameter param = ir.parameters().get(i);
            builder.append(param.type().jvmName()).append(" ").append(param.name());
        }

        builder.append(") {");

        // body
        indent++;
        for (Ir.Stmt stmt : ir.body()) {
            newline(indent);
            visit(stmt);
        }

        indent--;
        newline(indent);
        builder.append("}");

        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.If ir) {
        // if (<condition>) {
        //    <statements...> (separated by newlines)
        // }

        // if (<condition>) {
        //    <then statements...> (separated by newlines)
        // } else {
        //    <else statements...> (only when not empty)
        // }

        builder.append("if (");
        visit(ir.condition());
        builder.append(") {");

        indent++;

        // then body
        for (Ir.Stmt stmt : ir.thenBody()) {
            newline(indent);
            visit(stmt);
        }

        newline(--indent);
        builder.append("}");

        if (!ir.elseBody().isEmpty()) {
            builder.append(" else {");

            indent++;

            // else body
            for (Ir.Stmt stmt : ir.elseBody()) {
                newline(indent);
                visit(stmt);
            }

            newline(--indent);
            builder.append("}");
        }

        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.For ir) {
        // for (<Type> <name> : <expression>) {
        //    <statements...> (separated by newlines)
        // }

        builder.append("for (");
        builder.append(ir.type().jvmName()).append(" ");
        builder.append(ir.name());

        // iterable
        builder.append(" : ");
        visit(ir.expression());
        builder.append(") {");

        // body
        indent++;
        for (Ir.Stmt stmt : ir.body()) {
            newline(indent);
            visit(stmt);
        }

        newline(--indent);
        builder.append("}");

        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Return ir) {
        builder.append("return ");

        if (ir.value().isPresent()) {
            visit(ir.value().get());
        } else {
            builder.append("null");
        }

        builder.append(";");

        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Expression ir) {
        visit(ir.expression());
        builder.append(";");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Assignment.Variable ir) {
        visit(ir.variable());
        builder.append(" = ");
        visit(ir.value());
        builder.append(";");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Assignment.Property ir) {
        visit(ir.property());
        builder.append(" = ");
        visit(ir.value());
        builder.append(";");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Literal ir) {
        var literal = switch (ir.value()) {
            case null -> "null";
            case Boolean b -> b.toString();
            case BigInteger i -> "new BigInteger(\"" + i + "\")";
            case BigDecimal d -> "new BigDecimal(\"" + d + "\")";
            case String s -> "\"" + s + "\""; //TODO: Escape characters?
            //If the IR value isn't one of the above types, the Parser/Analyzer
            //is returning an incorrect IR - this is an implementation issue,
            //hence throw AssertionError rather than a "standard" exception.
            default -> throw new AssertionError(ir.value().getClass());
        };
        builder.append(literal);
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Group ir) {
        builder.append("(");
        visit(ir.expression());
        builder.append(")");
        return builder;
    }

    /**
     * Helper method to generate binary operations with parenthesized left operand
     * @param op binary operator
     * @param left lhs
     * @param right rhs
     * @param additional_parameters special case for decimal division requiring 'RoundingMode.HALF_EVEN'
     */
    private void generateArithmeticOp(String op, Ir.Expr left, Ir.Expr right, String... additional_parameters) {
        builder.append("(");
        visit(left);
        builder.append(").");
        builder.append(op);
        builder.append("(");
        visit(right);
        for (String param : additional_parameters) {
            builder.append(", ");
            builder.append(param);
        }
        builder.append(")");
    }

    /**
     * Helper method to generate comparison operations with parenthesized left operand
     * @param op comparison operator
     * @param left lhs
     * @param right rhs
     */
    private void generateComparisonOp(String op, Ir.Expr left, Ir.Expr right) {
        builder.append("(");
        visit(left);
        builder.append(").compareTo(");
        visit(right);
        builder.append(") " + op + " 0");
    }

    @Override
    public StringBuilder visit(Ir.Expr.Binary ir) {
        switch (ir.operator()) {
            case "+":
                if (ir.type().equals(Type.STRING)) {
                    visit(ir.left());
                    builder.append(" + ");
                    visit(ir.right());

                } else {
                    generateArithmeticOp("add", ir.left(), ir.right());
                }
                return builder;

            case "-":
                generateArithmeticOp("subtract", ir.left(), ir.right());
                return builder;

            case "*":
                generateArithmeticOp("multiply", ir.left(), ir.right());
                return builder;

            case "/":
                if (ir.type().equals(Type.DECIMAL)) {
                    generateArithmeticOp("divide", ir.left(), ir.right(), "RoundingMode.HALF_EVEN");
                } else {
                    generateArithmeticOp("divide", ir.left(), ir.right());
                }
                return builder;

            case "<":
                generateComparisonOp("<", ir.left(), ir.right());
                return builder;

            case "<=":
                generateComparisonOp("<=", ir.left(), ir.right());
                return builder;

            case ">":
                generateComparisonOp(">", ir.left(), ir.right());
                return builder;

            case ">=":
                generateComparisonOp(">=", ir.left(), ir.right());
                return builder;

            case "==":
                builder.append("Objects.equals(");
                visit(ir.left());
                builder.append(", ");
                visit(ir.right());
                builder.append(")");
                return builder;

            case "!=":
                builder.append("!Objects.equals(");
                visit(ir.left());
                builder.append(", ");
                visit(ir.right());
                builder.append(")");
                return builder;

            case "AND":
                // check if left binary OR
                if (ir.left() instanceof Ir.Expr.Binary && ((Ir.Expr.Binary) ir.left()).operator().equals("OR")) {
                    builder.append("(");
                    visit(ir.left());
                    builder.append(")");
                } else {
                    visit(ir.left());
                }

                builder.append(" && ");
                visit(ir.right());
                return builder;

            case "OR":
                visit(ir.left());
                builder.append(" || ");
                visit(ir.right());
                return builder;

            default:
                throw new UnsupportedOperationException("Operator: " + ir.operator() + " not supported");
        }
    }

    @Override
    public StringBuilder visit(Ir.Expr.Variable ir) {
        builder.append(ir.name());
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Property ir) {
        visit(ir.receiver());
        builder.append(".");
        builder.append(ir.name());
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Function ir) {
        // <name>()
        // <name>(<argument>)
        // <name>(<first>, <second>, <third>, ...)
        builder.append(ir.name());
        builder.append("(");

        for (int i = 0; i < ir.arguments().size(); i++) {
            if (i > 0 ) {
                builder.append(", ");
            }

            visit(ir.arguments().get(i));
        }

        builder.append(")");

        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Method ir) {
        // <receiver>.<name>()
        // <receiver>.<name>(<argument>)
        // <receiver>.<name>(<first>, <second>, <third>, ...)
        visit(ir.receiver());
        builder.append(".");
        builder.append(ir.name());
        builder.append("(");

        for (int i = 0; i < ir.arguments().size(); i++) {
            if (i > 0 ) {
                builder.append(", ");
            }

            visit(ir.arguments().get(i));
        }

        builder.append(")");

        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.ObjectExpr ir) {
        // new Object() {
        //  <fields...>
        //  <methods...>
        // }

        builder.append("new Object() {");

        indent++;

        // fields
        for (Ir.Stmt.Let field : ir.fields()) {
            newline(indent);
            visit(field);
        }

        // methods
        for (Ir.Stmt.Def method : ir.methods()) {
            newline(indent);
            visit(method);
        }

        newline(--indent);
        builder.append("}");

        return builder;
    }

}
