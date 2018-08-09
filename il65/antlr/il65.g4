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
	| constdecl
	| memoryvardecl
	| vardecl
	| varinitializer
	| assignment
	| augassignment
	;


directive :  '%' singlename (directivearg? | directivearg (',' directivearg)*) ;

directivearg : singlename | integerliteral ;

vardecl:  datatype arrayspec? singlename ;

varinitializer : datatype arrayspec? singlename '=' expression ;

constdecl: 'const' varinitializer ;

memoryvardecl: 'memory' varinitializer;

datatype:  'byte' | 'word' | 'float' | 'str' | 'str_p' | 'str_s' | 'str_ps' ;

arrayspec:  '[' expression (',' expression)? ']' ;

assignment :  assign_target '=' expression ;

augassignment :
	assign_target ('+=' | '-=' | '/=' | '//=' | '*=' | '**=' |
	               '<<=' | '>>=' | '<<@=' | '>>@=' | '&=' | '|=' | '^=') expression
	;

assign_target:
	register
	| singlename
	| dottedname
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

unary_expression :
	'~' expression
	| ('+' | '-') expression
	| 'not' expression
	;

singlename :  NAME ;

dottedname :  NAME ('.' NAME)+ ;

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
