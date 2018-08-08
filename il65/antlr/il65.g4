/*
IL65 lexer and parser grammar
*/

grammar il65;


NAME :  [a-zA-Z_][a-zA-Z0-9_]* ;
DEC_INTEGER : ('0'..'9') | (('1'..'9')('0'..'9')+);
HEX_INTEGER : '$' (('a'..'f') | ('A'..'F') | ('0'..'9'))+ ;
BIN_INTEGER : '%' ('0' | '1')+ ;


module :
	line*
	EOF
	;

line :
	directive
	| vardecl
	| assignment
	| augassignment
	;


directive :
	'%' singlename (literalvalue)?
	;

vardecl:
	datatype arrayspec? singlename ('=' expression)?
	;

datatype:
	'byte' | 'word' | 'float' | 'str' | 'str_p' | 'str_s' | 'str_ps'
	;

arrayspec:
	'[' expression (',' expression)? ']'
	;

assignment :
	assign_target '=' expression
	;

augassignment :
	assign_target ('+=' | '-=' | '/=' | '//=' | '*=' | '**=' |
	               '<<=' | '>>=' | '<<@=' | '>>@=' | '&=' | '|=' | '^=') expression
	;


expression :
	unary_expression
	| '(' expression ')'
	| expression '**'  expression
	| expression ('*' | '/' | '//' | '**') expression
	| expression ('+' | '-' | '%') expression
	| expression ('<<' | '>>' | '<<@' | '>>@' | '&' | '|' | '^') expression
	| expression ('and' | 'or' | 'xor') expression
	| expression ('==' | '!=' | '<' | '>' | '<=' | '>=') expression
	| literalvalue
	| register
	| dottedname
	| singlename
	;

unary_expression:
	'~' expression
	| ('+' | '-') expression
	| 'not' expression
	;

singlename:
	NAME
	;

dottedname:
	NAME ('.' NAME)+
	;

register:
	'A' | 'X' | 'Y' | 'AX' | 'AY' | 'XY' | 'SC' | 'SI' | 'SZ'
	;

literalvalue:
	BIN_INTEGER | HEX_INTEGER | DEC_INTEGER
	| 'true' | 'false'
	| array
	;

array:
	'[' expression (',' expression)* ']'
	;


assign_target:
	register
	| singlename
	| dottedname
	;

COMMENT :
	';' ~[\r\n]* -> channel(1)
	;

WS :
	[ \t] -> skip
	;

EOL :
	[\r\n]+ -> skip
	;
