grammar MySQLExpression;

expression:
	literal
	| LEFT_BRACKET expression RIGHT_BRACKET
	| BITNOT expression
	| PLUS expression
	| MINUS expression	
	| NOT expression
	| expression IS (NOT)? literal
	| expression LESS expression
	| expression LESS_EQUALS expression
	| expression GREATER expression
	| expression GREATER_EQUALS expression
	| expression LIKE expression
	| expression EQUALS expression
	| expression NOT_EQUALS expression
	| expression AND_OP expression
	| expression XOR_OP expression
	| expression OR_OP expression
	| expression AND expression
	| expression BITAND expression
	| expression XOR expression
	| expression BITOR expression
	| expression OR expression
	| CAST LEFT_BRACKET expression AS TYPE RIGHT_BRACKET
	| expression BETWEEN expression AND expression
	| expression (NOT)? IN LEFT_BRACKET expressionList RIGHT_BRACKET
	;
 

expressionList
    :   expression (',' expression)*
    ;


literal: INTEGER_LITERAL | SIGNED_INTEGER_LITERAL | NULL_LITERAL | COLUMN_NAME | TRUE_LITERAL | FALSE_LITERAL | UNKNOWN_LITERAL; 

SIGNED_INTEGER_LITERAL: MINUS INTEGER_LITERAL;

PLUS: '+';
MINUS: '-';
TRUE_LITERAL: 'TRUE';
FALSE_LITERAL: 'FALSE';
TYPE: UNSIGNED | SIGNED;
UNSIGNED: 'UNSIGNED';
SIGNED: 'SIGNED';
CAST: 'CAST';
AS: 'AS';
IS: 'IS';
NOT: 'NOT';
IN: 'IN';
BETWEEN: 'BETWEEN';
AND: 'AND';
BITAND: '&&';
OR: 'OR';
BITOR: '||';
XOR: 'XOR';
INTEGER_LITERAL: [0-9]+;
NULL_LITERAL: 'NULL' ;
UNKNOWN_LITERAL: 'UNKNOWN';
COLUMN_NAME: [a-z0-9]+;
WS: [ \t\r\n]+ -> skip;
LEFT_BRACKET: '(';
RIGHT_BRACKET: ')';
EQUALS: '=';
BITNOT: '!';
NOT_EQUALS: '!=';
LESS: '<';
LESS_EQUALS: '<=';
GREATER: '>';
GREATER_EQUALS: '>=';
LIKE: 'LIKE';
AND_OP: '&';
OR_OP: '|';
XOR_OP: '^';