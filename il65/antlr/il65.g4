/*
IL65 lexer and parser grammar
*/

grammar il65;


COMMENT :  ';' ~[\r\n]* -> channel(1) ;
WS :  [ \t] -> skip ;
EOL :  [\r\n]+ -> skip	;
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


module :  statement* EOF ;

statement :
	directive
	| varinitializer
	| vardecl
	| constdecl
	| memoryvardecl
	| assignment
	| augassignment
	;


directive :  '%' identifier (directivearg? | directivearg (',' directivearg)*) ;

directivearg : identifier | integerliteral ;

vardecl:  datatype arrayspec? identifier ;

varinitializer : datatype arrayspec? identifier '=' expression ;

constdecl: 'const' varinitializer ;

memoryvardecl: 'memory' varinitializer;

datatype:  'byte' | 'word' | 'float' | 'str' | 'str_p' | 'str_s' | 'str_ps' ;

arrayspec:  '[' expression (',' expression)? ']' ;

assignment :  assign_target '=' expression ;

augassignment :
	assign_target operator=('+=' | '-=' | '/=' | '//=' | '*=' | '**=' |
	               '<<=' | '>>=' | '<<@=' | '>>@=' | '&=' | '|=' | '^=') expression
	;

assign_target:
	register
	| identifier
	| scoped_identifier
	;

expression :
	unaryexp = unary_expression
	| '(' precedence_expr=expression ')'
	| left = expression '**' right = expression
	| left = expression ('*' | '/' | '//' | '**') right = expression
	| left = expression ('+' | '-' | '%') right = expression
	| left = expression ('<<' | '>>' | '<<@' | '>>@' | '&' | '|' | '^') right = expression
	| left = expression ('and' | 'or' | 'xor') right = expression
	| left = expression ('==' | '!=' | '<' | '>' | '<=' | '>=') right = expression
	| literalvalue
	| register
	| identifier
	| scoped_identifier
	;

unary_expression :
	operator = '~' expression
	| operator = ('+' | '-') expression
	| operator = 'not' expression
	;

identifier :  NAME ;

scoped_identifier :  NAME ('.' NAME)+ ;

register :  'A' | 'X' | 'Y' | 'AX' | 'AY' | 'XY' | 'SC' | 'SI' | 'SZ' ;

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
