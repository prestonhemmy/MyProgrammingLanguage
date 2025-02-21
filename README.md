# MyProgrammingLanguage

This repository contains an in-progress implementation of a custom programming language. The goal is to develop a full-featured language with its own syntax, semantics, and runtime.

Currently, the lexer and parser components are partially implemented. The lexer breaks the source code into tokens based on the language grammar, while the parser generates an Abstract Syntax Tree (AST) representing the program structure. 

Key features:
- Hand-written recursive descent parser
- Detailed AST representation of source code
- Arbitrary-precision integer and decimal support using `BigInteger` and `BigDecimal`

<!-- TODO: Add a concise code sample showcasing the language syntax. -->

## Language Overview

<!-- TODO: More detailed language description, covering: -->
<!-- - Data types -->
<!-- - Variables and scoping -->
<!-- - Control flow statements -->
<!-- - Functions and parameters -->
<!-- - Classes/objects -->
<!-- - Standard library -->
<!-- - Unique features that differentiate it from other languages -->

### Grammar

The language grammar is defined as follows:
```
source ::= stmt*

stmt::= let_stmt | def_stmt | if_stmt | for_stmt | return_stmt | expression_or_assignment_stmt
let_stmt ::= 'LET' identifier ('=' expr)? ';'
def_stmt ::= 'DEF' identifier '(' (identifier (',' identifier)*)? ')' 'DO' stmt* 'END'
if_stmt ::= 'IF' expr 'DO' stmt* ('ELSE' stmt*)? 'END'
for_stmt ::= 'FOR' identifier 'IN' expr 'DO' stmt* 'END'
return_stmt ::= 'RETURN' expr? ';'
expression_or_assignment_stmt ::= expr ('=' expr)? ';'

expr ::= logical_expr
logical_expr ::= comparison_expr (('AND' | 'OR') comparison_expr)*
comparison_expr ::= additive_expr (('<' | '<=' | '>' | '>=' | '==' | '!=') additive_expr)*
additive_expr ::= multiplicative_expr (('+' | '-') multiplicative_expr)*
multiplicative_expr ::= secondary_expr (('*' | '/') secondary_expr)*

secondary_expr ::= primary_expr ('.' identifier ('(' (expr (',' expr)*)? ')')?)*
primary_expr ::= literal_expr | group_expr | object_expr | variable_or_function_expr
literal_expr ::= 'NIL' | 'TRUE' | 'FALSE' | integer | decimal | character | string
group_expr ::= '(' expr')'
object_expr ::= 'OBJECT' identifier? 'DO' let_stmt* def_stmt* 'END'
variable_or_function_expr ::= identifier ('(' (expr (',' expr)*)? ')')?

//these rules correspond to lexer tokens
token ::= identifier | number | character | string | operator
identifier ::= [A-Za-z_] [A-Za-z0-9_-]*
number ::= [+-]? [0-9]+ ('.' [0-9]+)? ('e' [+-]? [0-9]+)?
character ::= ['] ([^'\n\r\\] | escape) [']
string ::= '"' ([^"\n\r\\] | escape)* '"'
escape ::= '\' [bnrt'"\]
operator ::= [<>!=] '='? | [^A-Za-z_0-9'" \b\n\r\t]
```

<!-- TODO: Finish including the provided grammar. -->

## Implementation

### Lexer

The lexer is implemented using a rule-based approach, with each rule corresponding to a token type. Input is matched against the rules to produce a stream of tokens.

<!-- TODO: More lexer implementation details. -->

### Parser

The parser is implemented using a recursive descent approach, with each grammar rule corresponding to a parse function. The output is an Abstract Syntax Tree representing the structure of the source code.

<!-- TODO: More parser implementation details. -->
<!-- TODO: AST node type details. -->

## Usage

<!-- TODO: Build instructions. -->
<!-- TODO: Running the REPL. -->
<!-- TODO: Executing a source file. -->

## Future Work

- Complete parser implementation
- Semantic analysis
- Interpreter to execute code
- Compiled implementation (outputting Java bytecode, LLVM IR, WebAssembly, etc.)

---

<!-- TODO: Add an image demonstrating the lexing and parsing of a sample source file. A railroad diagram of the grammar would also be helpful. -->

<!-- TODO: Include an architecture diagram once more components are implemented, showing the lexer, parser, AST, semantic analyzer, interpreter and/or compiler. -->
