/*
Prog8 combined lexer and parser grammar

NOTES:

- whitespace is ignored. (tabs/spaces)
- every position can be empty, be a comment, or contain ONE statement.

*/

// -> java classes Prog8ANTLRParser and Prog8ANTLRLexer,
// both NOT to be used from Kotlin code, but ONLY through Kotlin class Prog8Parser
grammar Prog8ANTLR;

@header {
package prog8.parser;
}

EOL :  ('\r'? '\n' | '\r' | '\n')+ ;
LINECOMMENT : EOL [ \t]* COMMENT -> channel(HIDDEN);
COMMENT :  ';' ~[\r\n]* -> channel(HIDDEN) ;
BLOCK_COMMENT : '/*' ( BLOCK_COMMENT | . )*? '*/'  -> skip ;

WS :  [ \t] -> skip ;
// WS2 : '\\' EOL -> skip;
VOID: 'void';
ON: 'on';
GOTO: 'goto';
CALL: 'call';
INLINE: 'inline';
STEP: 'step';
ELSE: 'else';

NAME :  [\p{Letter}][\p{Letter}\p{Mark}\p{Digit}_]* ;           // match unicode properties
UNDERSCORENAME :  '_' NAME ;           // match unicode properties
DEC_INTEGER :  DEC_DIGIT (DEC_DIGIT | '_')* ;
HEX_INTEGER :  '$' HEX_DIGIT (HEX_DIGIT | '_')* ;
BIN_INTEGER :  '%' BIN_DIGIT (BIN_DIGIT | '_')* ;
ADDRESS_OF: '&' ;
ADDRESS_OF_MSB: '&>' ;
ADDRESS_OF_LSB: '&<' ;
INVALID_AND_COMPOSITE: '&&' ;

fragment HEX_DIGIT: ('a'..'f') | ('A'..'F') | ('0'..'9') ;
fragment BIN_DIGIT: ('0' | '1') ;
fragment DEC_DIGIT: ('0'..'9') ;

FLOAT_NUMBER :  FNUMBER (('E'|'e') ('+' | '-')? DEC_INTEGER)? ;    // sign comes later from unary expression
FNUMBER : FDOTNUMBER |  FNUMDOTNUMBER ;
FDOTNUMBER : '.' (DEC_DIGIT | '_')+ ;
FNUMDOTNUMBER : DEC_DIGIT (DEC_DIGIT | '_')* FDOTNUMBER? ;

STRING_ESCAPE_SEQ :  '\\' . | '\\x' . . | '\\u' . . . .;
STRING :
    '"' ( STRING_ESCAPE_SEQ | ~[\\\r\n\f"] )* '"'
    ;
INLINEASMBLOCK :
    '{{' .+? '}}'
    ;

SINGLECHAR :
    '\'' ( STRING_ESCAPE_SEQ | ~[\\\r\n\f'] ) '\''
    ;

TAG: '@' ('a'..'z' | '0'..'9')+ ;

ARRAYSIG : '[' [ \t]* ']' ;

NOT_IN: 'not' [ \t]+ 'in' [ \t] ;


// A module (file) consists of zero or more directives or blocks, in any order.
// If there are more than one, then they must be separated by EOL (one or more newlines).
// However, trailing EOL is NOT required.
// Note: the parser may see *several* consecutive EOLs - this happens when EOL and comments are interleaved (see #47)
module: EOL* (module_element (EOL+ module_element)*)? EOL* EOF;

module_element:
    directive | block ;


block: identifier integerliteral? EOL? '{' EOL? (block_statement | EOL)* '}';

block_statement:
    directive
    | variabledeclaration
    | subroutinedeclaration
    | inlineasm
    | inlineir
    | labeldef
    | alias
    ;


statement :
    directive
    | variabledeclaration
    | assignment
    | augassignment
    | unconditionaljump
    | postincrdecr
    | functioncall_stmt
    | if_stmt
    | branch_stmt
    | subroutinedeclaration
    | inlineasm
    | inlineir
    | returnstmt
    | forloop
    | whileloop
    | untilloop
    | repeatloop
    | unrollloop
    | whenstmt
    | breakstmt
    | continuestmt
    | labeldef
    | ongoto
    | defer
    | alias
    ;


variabledeclaration :
    varinitializer
    | vardecl
    | constdecl
    | memoryvardecl
    ;


subroutinedeclaration :
    subroutine
    | asmsubroutine
    | extsubroutine
    ;

alias: 'alias' identifier '=' scoped_identifier ;

defer: 'defer' (statement | statement_block) ;

labeldef :  identifier ':'  ;

unconditionaljump :  GOTO  expression ;

directive :
    directivename=('%output' | '%launcher' | '%zeropage' | '%zpreserved' | '%zpallowed' | '%address' | '%memtop' | '%import' |
                       '%breakpoint' | '%asminclude' | '%asmbinary' | '%option' | '%encoding' | '%align' | '%jmptable' )
        (directivenamelist | (directivearg? | directivearg (',' directivearg)*))
        ;

directivenamelist: '(' EOL? scoped_identifier (',' EOL? scoped_identifier)* ','? EOL?')' ;

directivearg : stringliteral | identifier | integerliteral ;

vardecl: datatype (arrayindex | ARRAYSIG)? TAG* identifier (',' identifier)* ;

varinitializer : vardecl '=' expression ;

constdecl: 'const' varinitializer ;

memoryvardecl: ADDRESS_OF varinitializer;

datatype:  'ubyte' | 'byte' | 'uword' | 'word' | 'long' | 'float' | 'str' | 'bool' ;

arrayindex:  '[' expression ']' ;

assignment :  (assign_target '=' expression) | (assign_target '=' assignment) | (multi_assign_target '=' expression);

augassignment :
    assign_target operator=('+=' | '-=' | '/=' | '*=' | '&=' | '|=' | '^=' | '%=' | '<<=' | '>>=' ) expression
    ;

assign_target:
    scoped_identifier               #IdentifierTarget
    | arrayindexed                  #ArrayindexedTarget
    | directmemory                  #MemoryTarget
    | VOID                          #VoidTarget
    ;


multi_assign_target:
    assign_target (',' assign_target)+ ;

postincrdecr :  assign_target  operator = ('++' | '--') ;

expression :
    '(' expression ')'
    | sizeof_expression = 'sizeof' '(' sizeof_argument ')'
    | functioncall
    | <assoc=right> prefix = ('+'|'-'|'~') expression
    | left = expression EOL? bop = ('*' | '/' | '%' ) EOL? right = expression
    | left = expression EOL? bop = ('+' | '-' ) EOL? right = expression
    | left = expression EOL? bop = ('<<' | '>>' ) EOL? right = expression
    | left = expression EOL? bop = '&' EOL? right = expression
    | left = expression EOL? bop = '^' EOL? right = expression
    | left = expression EOL? bop = '|' EOL? right = expression
    | left = expression EOL? bop = ('<' | '>' | '<=' | '>=') EOL? right = expression
    | left = expression EOL? bop = ('==' | '!=') EOL? right = expression
    | rangefrom = expression rto = ('to'|'downto') rangeto = expression (STEP rangestep = expression)?    // can't create separate rule due to mutual left-recursion
    | left = expression EOL? bop = 'in' EOL? right = expression
    | left = expression EOL? bop = NOT_IN EOL? right = expression
    | prefix = 'not' expression
    | left = expression EOL? bop = 'and' EOL? right = expression
    | left = expression EOL? bop = 'or' EOL? right = expression
    | left = expression EOL? bop = 'xor' EOL? right = expression
    | literalvalue
    | scoped_identifier
    | arrayindexed
    | directmemory
    | addressof
    | expression typecast
    | if_expression
    ;


sizeof_argument: datatype | expression ;


arrayindexed:
    scoped_identifier arrayindex
    ;


typecast : 'as' datatype;

directmemory : '@' '(' expression ')';

addressof : <assoc=right> (ADDRESS_OF | ADDRESS_OF_LSB | ADDRESS_OF_MSB) (scoped_identifier | arrayindexed) ;

functioncall : scoped_identifier '(' expression_list? ')'  ;

functioncall_stmt : VOID? scoped_identifier '(' expression_list? ')'  ;

expression_list :
    expression (',' EOL? expression)*           // you can split the expression list over several lines
    ;

returnstmt : 'return' returnvalues? ;

returnvalues: expression (',' expression)*  ;

breakstmt : 'break';

continuestmt: 'continue';

identifier :  NAME | UNDERSCORENAME | ON | CALL | INLINE | STEP ;              // due to the way antlr creates tokens, need to list the tokens here explicitly that we want to allow as identifiers too

scoped_identifier :  identifier ('.' identifier)* ;

integerliteral :  intpart=(DEC_INTEGER | HEX_INTEGER | BIN_INTEGER) ;

booleanliteral :  'true' | 'false' ;

arrayliteral :  '[' EOL? expression (',' EOL? expression)* ','? EOL? ']' ;       // you can split the values over several lines, trailing comma allowed

stringliteral : (encoding=NAME ':')? STRING ;

charliteral : (encoding=NAME ':')? SINGLECHAR ;

floatliteral :  FLOAT_NUMBER ;


literalvalue :
    integerliteral
    | booleanliteral
    | arrayliteral
    | stringliteral
    | charliteral
    | floatliteral
    ;

inlineasm :  '%asm' EOL? INLINEASMBLOCK;

inlineir: '%ir' EOL? INLINEASMBLOCK;

subroutine :
    'sub' identifier '(' sub_params? ')' sub_return_part? EOL? (statement_block EOL?)
    ;

sub_return_part : '->' datatype (',' datatype)*  ;

statement_block :
    '{' EOL?
        (statement | EOL) *
    '}'
    ;


sub_params :  sub_param (',' EOL? sub_param)* ;

sub_param: vardecl ('@' register=NAME)? ;

asmsubroutine :
    INLINE? 'asmsub' asmsub_decl EOL? (statement_block EOL?)
    ;

extsubroutine :
    'extsub' (TAG (constbank=integerliteral | varbank=scoped_identifier))? address=expression '=' asmsub_decl
    ;

asmsub_decl : identifier '(' asmsub_params? ')' asmsub_clobbers? asmsub_returns? ;

asmsub_params :  asmsub_param (',' EOL? asmsub_param)* ;

asmsub_param :  vardecl '@' register=NAME ;      // A,X,Y,AX,AY,XY,Pc,Pz,Pn,Pv allowed

asmsub_clobbers : 'clobbers' '(' clobber? ')' ;

clobber :  NAME (',' NAME)* ;       // A,X,Y allowed

asmsub_returns :  '->' asmsub_return (',' EOL? asmsub_return)* ;

asmsub_return :  datatype '@' register=NAME ;     // A,X,Y,AX,AY,XY,Pc,Pz,Pn,Pv allowed


if_stmt :  'if' expression EOL? (statement | statement_block) EOL? else_part?  ; // statement is constrained later

else_part :  ELSE EOL? (statement | statement_block) ;   // statement is constrained later

if_expression :  'if' expression EOL? expression EOL? ELSE EOL? expression ;

branch_stmt : branchcondition EOL? (statement | statement_block) EOL? else_part? ;

branchcondition: 'if_cs' | 'if_cc' | 'if_eq' | 'if_z' | 'if_ne' | 'if_nz' | 'if_pl' | 'if_pos' | 'if_mi' | 'if_neg' | 'if_vs' | 'if_vc' ;


forloop :  'for' scoped_identifier 'in' expression EOL? (statement | statement_block) ;

whileloop:  'while' expression EOL? (statement | statement_block) ;

untilloop:  'do' (statement | statement_block) EOL? 'until' expression ;

repeatloop:  'repeat' expression? EOL? (statement | statement_block) ;

unrollloop:  'unroll' expression EOL? (statement | statement_block) ;      // note: expression must evaluate to a constant

whenstmt: 'when' expression EOL? '{' EOL? (when_choice | EOL) * '}' EOL? ;

when_choice:  (expression_list | ELSE ) '->' (statement | statement_block ) ;

ongoto: ON expression kind=(GOTO | CALL) directivenamelist EOL? else_part? ;
