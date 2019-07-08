/*
Prog8 combined lexer and parser grammar

NOTES:

- whitespace is ignored. (tabs/spaces)
- every position can be empty, be a comment, or contain ONE statement.

*/

grammar prog8;

@header {
package prog8.parser;
}

LINECOMMENT : [\r\n][ \t]* COMMENT -> channel(HIDDEN);
COMMENT :  ';' ~[\r\n]* -> channel(HIDDEN) ;
WS :  [ \t] -> skip ;
EOL :  [\r\n]+ ;
// WS2 : '\\' EOL -> skip;
NAME :  [a-zA-Z_][a-zA-Z0-9_]* ;
DEC_INTEGER :  ('0'..'9') | (('1'..'9')('0'..'9')+);
HEX_INTEGER :  '$' (('a'..'f') | ('A'..'F') | ('0'..'9'))+ ;
BIN_INTEGER :  '%' ('0' | '1')+ ;
ADDRESS_OF: '&';

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

ARRAYSIG :
    '[]'
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
	| branch_stmt
	| subroutine
	| asmsubroutine
	| inlineasm
	| returnstmt
	| forloop
	| whileloop
	| repeatloop
	| whenstmt
	| breakstmt
	| continuestmt
	| labeldef
	;


labeldef :  identifier ':'  ;

unconditionaljump :  'goto'  (integerliteral | scoped_identifier) ;

directive :
	directivename=('%output' | '%launcher' | '%zeropage' | '%zpreserved' | '%address' | '%import' |
                       '%breakpoint' | '%asminclude' | '%asmbinary' | '%option')
        (directivearg? | directivearg (',' directivearg)*)
        ;

directivearg : stringliteral | identifier | integerliteral ;

vardecl: datatype ZEROPAGE? (arrayindex | ARRAYSIG) ? identifier ;

varinitializer : vardecl '=' expression ;

constdecl: 'const' varinitializer ;

memoryvardecl: ADDRESS_OF varinitializer;

datatype:  'ubyte' | 'byte' | 'uword' | 'word' | 'float' | 'str' | 'str_s' ;

arrayindex:  '[' expression ']' ;

assignment :  assign_targets '=' expression ;

assign_targets : assign_target (',' assign_target)* ;

augassignment :
	assign_target operator=('+=' | '-=' | '/=' | '*=' | '**=' | '&=' | '|=' | '^=' | '%=' | '<<=' | '>>=' ) expression
	;

assign_target:
	register
	| scoped_identifier
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
	| left = expression EOL? bop = ('==' | '!=') EOL? right = expression
	| left = expression EOL? bop = '&' EOL? right = expression
	| left = expression EOL? bop = '^' EOL? right = expression
	| left = expression EOL? bop = '|' EOL? right = expression
	| rangefrom = expression 'to' rangeto = expression ('step' rangestep = expression)?	// can't create separate rule due to mutual left-recursion
	| left = expression EOL? bop = 'and' EOL? right = expression
	| left = expression EOL? bop = 'or' EOL? right = expression
	| left = expression EOL? bop = 'xor' EOL? right = expression
	| prefix = 'not' expression
	| literalvalue
	| register
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


functioncall :	scoped_identifier '(' expression_list? ')'  ;


functioncall_stmt :  scoped_identifier '(' expression_list? ')'	;

expression_list :
	expression (',' EOL? expression)*           // you can split the expression list over several lines
	;

returnstmt : 'return' expression_list? ;

breakstmt : 'break';

continuestmt: 'continue';

identifier :  NAME ;

scoped_identifier :  NAME ('.' NAME)* ;

register :  'A' | 'X' | 'Y' ;

registerorpair :  'A' | 'X' | 'Y' | 'AX' | 'AY' | 'XY' ;          // only used in subroutine params and returnvalues

statusregister :  'Pc' | 'Pz' | 'Pn' | 'Pv' ;

integerliteral :  intpart=(DEC_INTEGER | HEX_INTEGER | BIN_INTEGER) wordsuffix? ;

wordsuffix : '.w' ;

booleanliteral :  'true' | 'false' ;

arrayliteral :  '[' EOL? expression (',' EOL? expression)* EOL? ']' ;       // you can split the array list over several lines

stringliteral :  STRING ;

charliteral : SINGLECHAR ;

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


subroutine :
	'sub' identifier '(' sub_params? ')' sub_return_part?  (statement_block EOL)
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
    'asmsub' identifier '(' asmsub_params? ')'  EOL?
    asmsub_clobbers? asmsub_returns?  (asmsub_address  | statement_block )
    ;

asmsub_address :  '=' address=integerliteral  ;

asmsub_params :  asmsub_param (',' EOL? asmsub_param)* ;

asmsub_param :  vardecl '@' (registerorpair | statusregister | stack='stack') ;

asmsub_clobbers : 'clobbers' '(' clobber? ')' ;

clobber :  register (',' register)* ;

asmsub_returns :  '->' asmsub_return (',' EOL? asmsub_return)* ;

asmsub_return :  datatype '@' (registerorpair | statusregister | stack='stack') ;


if_stmt :  'if' expression EOL? (statement | statement_block) EOL? else_part?  ; // statement is constrained later

else_part :  'else' EOL? (statement | statement_block) ;   // statement is constrained later


branch_stmt : branchcondition EOL? (statement | statement_block) EOL? else_part? EOL ;

branchcondition: 'if_cs' | 'if_cc' | 'if_eq' | 'if_z' | 'if_ne' | 'if_nz' | 'if_pl' | 'if_pos' | 'if_mi' | 'if_neg' | 'if_vs' | 'if_vc' ;


forloop :  'for' datatype? ZEROPAGE? (register | identifier) 'in' expression EOL? (statement | statement_block) ;

whileloop:  'while' expression EOL? (statement | statement_block) ;

repeatloop:  'repeat' (statement | statement_block) EOL? 'until' expression ;

whenstmt: 'when' expression '{' EOL (when_choice | EOL) * '}' EOL? ;

when_choice:  (expression | 'else' ) '->' (statement | statement_block ) ;
