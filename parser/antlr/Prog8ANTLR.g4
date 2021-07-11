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

LINECOMMENT : ('\r'? '\n' | '\r') [ \t]* COMMENT -> channel(HIDDEN);
COMMENT :  ';' ~[\r\n]* -> channel(HIDDEN) ;
EOL :  ('\r'? '\n' | '\r')+ ;

WS :  [ \t] -> skip ;
// WS2 : '\\' EOL -> skip;
VOID: 'void';
NAME :  [a-zA-Z][a-zA-Z0-9_]* ;
DEC_INTEGER :  ('0'..'9') | (('1'..'9')('0'..'9')+);
HEX_INTEGER :  '$' (('a'..'f') | ('A'..'F') | ('0'..'9'))+ ;
BIN_INTEGER :  '%' ('0' | '1')+ ;
ADDRESS_OF: '&';
ALT_STRING_ENCODING: '@';

FLOAT_NUMBER :  FNUMBER (('E'|'e') ('+' | '-')? FNUMBER)? ;	// sign comes later from unary expression
fragment FNUMBER :  ('0' .. '9') + ('.' ('0' .. '9') +)? ;

fragment STRING_ESCAPE_SEQ :  '\\' . | '\\' EOL;
STRING :
	'"' ( STRING_ESCAPE_SEQ | ~[\\\r\n\f"] )* '"'
	{
		// get rid of the enclosing quotes
		String s = getText();
		setText(s.substring(1, s.length() - 1));
	}
	;
INLINEASMBLOCK :
	'{{' .+? '}}'
	{
		// get rid of the enclosing double braces
		String s = getText();
		setText(s.substring(2, s.length() - 2));
	}
	;

SINGLECHAR :
	'\'' ( STRING_ESCAPE_SEQ | ~[\\\r\n\f"] ) '\''
	{
		// get rid of the enclosing quotes
		String s = getText();
		setText(s.substring(1, s.length() - 1));
	}
	;

ZEROPAGE :
    '@zp'
    ;

SHARED :
    '@shared'
    ;

ARRAYSIG :
    '[]'
    ;

cpuregister: 'A' | 'X' | 'Y';
register: 'A' | 'X' | 'Y' | 'AX' | 'AY' | 'XY' | 'Pc' | 'Pz' | 'Pn' | 'Pv' | 'R0' | 'R1' | 'R2' | 'R3' | 'R4' | 'R5' | 'R6' | 'R7' | 'R8' | 'R9' | 'R10' | 'R11' | 'R12' | 'R13' | 'R14' | 'R15';

// A module (file) consists of zero or more directives or blocks, in any order.
// If there are more than one, then they must be separated by EOL (one or more newlines).
// However, trailing EOL is NOT required.
// Note: the parser may see *several* consecutive EOLs - this happens when EOL and comments are interleaved (see #47)
module: EOL* ((directive | block) (EOL+ (directive | block))*)? EOL* EOF;

block: identifier integerliteral? '{' EOL (block_statement | EOL)* '}';


block_statement:
	directive
	| variabledeclaration
	| subroutinedeclaration
	| inlineasm
	| labeldef
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
	| returnstmt
	| forloop
	| whileloop
	| untilloop
	| repeatloop
	| whenstmt
	| breakstmt
	| labeldef
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
	| romsubroutine
    ;


labeldef :  identifier ':'  ;

unconditionaljump :  'goto'  (integerliteral | scoped_identifier) ;

directive :
	directivename=('%output' | '%launcher' | '%zeropage' | '%zpreserved' | '%address' | '%import' |
                       '%breakpoint' | '%asminclude' | '%asmbinary' | '%option' | '%target' )
        (directivearg? | directivearg (',' directivearg)*)
        ;

directivearg : stringliteral | identifier | integerliteral ;

vardecl: datatype SHARED? ZEROPAGE? (arrayindex | ARRAYSIG) ? varname=identifier ;

varinitializer : vardecl '=' expression ;

constdecl: 'const' varinitializer ;

memoryvardecl: ADDRESS_OF varinitializer;

datatype:  'ubyte' | 'byte' | 'uword' | 'word' | 'float' | 'str' ;

arrayindex:  '[' expression ']' ;

assignment :  assign_target '=' expression ;

augassignment :
	assign_target operator=('+=' | '-=' | '/=' | '*=' | '**=' | '&=' | '|=' | '^=' | '%=' | '<<=' | '>>=' ) expression
	;

assign_target:
	scoped_identifier
	| arrayindexed
	| directmemory
	;

postincrdecr :  assign_target  operator = ('++' | '--') ;

expression :
	functioncall
	| <assoc=right> prefix = ('+'|'-'|'~') expression
	| left = expression EOL? bop = '**' EOL? right = expression
	| left = expression EOL? bop = ('*' | '/' | '%' ) EOL? right = expression
	| left = expression EOL? bop = ('+' | '-' ) EOL? right = expression
	| left = expression EOL? bop = ('<<' | '>>' ) EOL? right = expression
	| left = expression EOL? bop = ('<' | '>' | '<=' | '>=') EOL? right = expression
	| left = expression EOL? bop = '&' EOL? right = expression
	| left = expression EOL? bop = '^' EOL? right = expression
	| left = expression EOL? bop = '|' EOL? right = expression
	| left = expression EOL? bop = ('==' | '!=') EOL? right = expression
	| rangefrom = expression rto = ('to'|'downto') rangeto = expression ('step' rangestep = expression)?	// can't create separate rule due to mutual left-recursion
	| left = expression EOL? bop = 'and' EOL? right = expression
	| left = expression EOL? bop = 'or' EOL? right = expression
	| left = expression EOL? bop = 'xor' EOL? right = expression
	| prefix = 'not' expression
	| literalvalue
	| scoped_identifier
	| arrayindexed
	| directmemory
	| addressof
	| expression typecast
	| '(' expression ')'
	;


typecast : 'as' datatype;


arrayindexed :  scoped_identifier arrayindex  ;

directmemory : '@' '(' expression ')';

addressof : <assoc=right> ADDRESS_OF scoped_identifier ;


functioncall : scoped_identifier '(' expression_list? ')'  ;

functioncall_stmt : VOID? scoped_identifier '(' expression_list? ')'  ;

expression_list :
	expression (',' EOL? expression)*           // you can split the expression list over several lines
	;

returnstmt : 'return' expression? ;

breakstmt : 'break';

identifier :  NAME ;

scoped_identifier :  NAME ('.' NAME)* ;

integerliteral :  intpart=(DEC_INTEGER | HEX_INTEGER | BIN_INTEGER) ;

booleanliteral :  'true' | 'false' ;

arrayliteral :  '[' EOL? expression (',' EOL? expression)* EOL? ']' ;       // you can split the values over several lines

stringliteral : ALT_STRING_ENCODING? STRING ;

charliteral : ALT_STRING_ENCODING? SINGLECHAR ;

floatliteral :  FLOAT_NUMBER ;


literalvalue :
	integerliteral
	| booleanliteral
	| arrayliteral
	| stringliteral
	| charliteral
	| floatliteral
	;

inlineasm :  '%asm' INLINEASMBLOCK;

inline: 'inline';

subroutine :
	inline? 'sub' identifier '(' sub_params? ')' sub_return_part?  (statement_block EOL)
	;

sub_return_part : '->' sub_returns  ;

statement_block :
	'{' EOL
		(statement | EOL) *
	'}'
	;


sub_params :  vardecl (',' EOL? vardecl)* ;

sub_returns :  datatype (',' EOL? datatype)*  ;

asmsubroutine :
    inline? 'asmsub' asmsub_decl  statement_block
    ;

romsubroutine :
    'romsub' integerliteral '=' asmsub_decl
    ;

asmsub_decl : identifier '(' asmsub_params? ')' asmsub_clobbers? asmsub_returns? ;

asmsub_params :  asmsub_param (',' EOL? asmsub_param)* ;

asmsub_param :  vardecl '@' register ;      // A,X,Y,AX,AY,XY,Pc,Pz,Pn,Pv allowed.

asmsub_clobbers : 'clobbers' '(' clobber? ')' ;

clobber :  cpuregister (',' cpuregister)* ;       // A,X,Y allowed

asmsub_returns :  '->' asmsub_return (',' EOL? asmsub_return)* ;

asmsub_return :  datatype '@' register ;     // A,X,Y,AX,AY,XY,Pc,Pz,Pn,Pv allowed


if_stmt :  'if' expression EOL? (statement | statement_block) EOL? else_part?  ; // statement is constrained later

else_part :  'else' EOL? (statement | statement_block) ;   // statement is constrained later


branch_stmt : branchcondition EOL? (statement | statement_block) EOL? else_part? EOL ;

branchcondition: 'if_cs' | 'if_cc' | 'if_eq' | 'if_z' | 'if_ne' | 'if_nz' | 'if_pl' | 'if_pos' | 'if_mi' | 'if_neg' | 'if_vs' | 'if_vc' ;


forloop :  'for' identifier 'in' expression EOL? (statement | statement_block) ;

whileloop:  'while' expression EOL? (statement | statement_block) ;

untilloop:  'do' (statement | statement_block) EOL? 'until' expression ;

repeatloop:  'repeat' expression? EOL? (statement | statement_block) ;

whenstmt: 'when' expression '{' EOL (when_choice | EOL) * '}' EOL? ;

when_choice:  (expression_list | 'else' ) '->' (statement | statement_block ) ;

