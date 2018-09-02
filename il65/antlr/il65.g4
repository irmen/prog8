/*
IL65 combined lexer and parser grammar

NOTES:

- whitespace is ignored. (tabs/spaces)
- every position can be empty, be a comment, or contain ONE statement.

*/

grammar il65;


LINECOMMENT : [\r\n][ \t]* COMMENT -> channel(HIDDEN);
COMMENT :  ';' ~[\r\n]* -> channel(HIDDEN) ;
WS :  [ \t] -> skip ;
EOL :  [\r\n]+ ;
NAME :  [a-zA-Z_][a-zA-Z0-9_]* ;
DEC_INTEGER :  ('0'..'9') | (('1'..'9')('0'..'9')+);
HEX_INTEGER :  '$' (('a'..'f') | ('A'..'F') | ('0'..'9'))+ ;
BIN_INTEGER :  '%' ('0' | '1')+ ;

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


module :  (modulestatement | EOL)* EOF ;

modulestatement:  directive | block ;

block:	'~' identifier integerliteral? statement_block EOL ;

statement :
	directive
	| varinitializer
	| vardecl
	| constdecl
	| memoryvardecl
	| assignment
	| augassignment
	| unconditionaljump
	| postincrdecr
	| functioncall_stmt
	| if_stmt
	| subroutine
	| inlineasm
	| labeldef
	| returnstmt
	// @todo forloop, whileloop, repeatloop
	;

labeldef :  identifier ':'  ;

unconditionaljump :  'goto'  (integerliteral | identifier | scoped_identifier) ;

directive :
	directivename=('%output' | '%launcher' | '%zeropage' | '%address' | '%import' |
                       '%breakpoint' | '%asminclude' | '%asmbinary' | '%option')
        (directivearg? | directivearg (',' directivearg)*)
        ;

directivearg : stringliteral | identifier | integerliteral ;

vardecl:  datatype arrayspec? identifier ;

varinitializer : datatype arrayspec? identifier '=' expression ;

constdecl: 'const' varinitializer ;

memoryvardecl: 'memory' varinitializer;

datatype:  'byte' | 'word' | 'float' | 'str' | 'str_p' | 'str_s' | 'str_ps' ;

arrayspec:  '[' expression (',' expression)? ']' ;

assignment :  assign_target '=' expression ;

augassignment :
	assign_target operator=('+=' | '-=' | '/=' | '*=' | '**=' | '&=' | '|=' | '^=') expression
	;

assign_target:
	register
	| identifier
	| scoped_identifier
	;

postincrdecr :  assign_target  operator = ('++' | '--') ;

expression :
	'(' expression ')'
	| expression arrayspec
	| functioncall
	| prefix = ('+'|'-'|'~') expression
	| left = expression bop = '**' right = expression
	| left = expression bop = ('*' | '/' ) right = expression
	| left = expression bop = ('+' | '-' ) right = expression
	| left = expression bop = ('<' | '>' | '<=' | '>=') right = expression
	| left = expression bop = ('==' | '!=') right = expression
	| left = expression bop = '&' right = expression
	| left = expression bop = '^' right = expression
	| left = expression bop = '|' right = expression
	| rangefrom = expression 'to' rangeto = expression	// create separate rule once for-loops are here?
	| left = expression bop = 'and' right = expression
	| left = expression bop = 'or' right = expression
	| left = expression bop = 'xor' right = expression
	| prefix = 'not' expression
	| literalvalue
	| register
	| identifier
	| scoped_identifier
	;


functioncall :
	(identifier | scoped_identifier) '(' expression_list? ')'
	;


functioncall_stmt :
	(identifier | scoped_identifier) '(' expression_list? ')'
	;


expression_list :
	expression (',' expression)*
	;

returnstmt : 'return' expression_list? ;

identifier :  NAME ;

scoped_identifier :  NAME ('.' NAME)+ ;

register :  'A' | 'X' | 'Y' | 'AX' | 'AY' | 'XY' | 'Pc' | 'Pz' | 'Pn' | 'Pv' ;

integerliteral :  DEC_INTEGER | HEX_INTEGER | BIN_INTEGER ;

booleanliteral :  'true' | 'false' ;

arrayliteral :  '[' expression (',' expression)* ']' ;

stringliteral :  STRING ;

floatliteral :  FLOAT_NUMBER ;

literalvalue :
	integerliteral
	| booleanliteral
	| arrayliteral
	| stringliteral
	| floatliteral
	;

inlineasm :  '%asm' INLINEASMBLOCK;


subroutine :
	'sub' identifier '(' sub_params? ')' '->' '(' sub_returns? ')'  (sub_address | (statement_block EOL))
	;

statement_block :
	'{' EOL
		(statement | EOL) *
	'}'
	;

sub_address : '=' integerliteral ;

sub_params : sub_param (',' sub_param)* ;

sub_param: identifier ':' register;

sub_returns : '?' | ( sub_return (',' sub_return)* ) ;

sub_return: register '?'? ;


if_stmt :  'if' '(' expression ')' EOL? (statement | statement_block) EOL? else_part? EOL ; // statement is constrained later

else_part :  'else' EOL? (statement | statement_block) ;   // statement is constrained later
