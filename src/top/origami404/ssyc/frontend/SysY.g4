grammar SysY;

@header {
package top.origami404.ssyc.frontend;
}

//------------------------- Lexical ---------------------------------------

BType: ('int' | 'float' | 'void');

Ident: ('a'..'z' | 'A'..'Z' | '_') ('a'..'z' | 'A'..'Z' | '0'..'9' | '_')* ;

// See Ref: http://www.open-std.org/jtc1/sc22/wg14/www/docs/n1124.pdf page 51
// the expoent part of hex style cannot be ignored
// the expoent part of hex style is still a `digit-sequence`, which `digit` is `DEC`
FloatConst: FloatDec | FloatHex ;
fragment FloatDec:               (DEC* '.' DEC*) (('e' | 'E') ('+' | '-')? DEC+)? ;
fragment FloatHex: ('0x' | '0X') (HEX* '.' HEX*)  ('p' | 'P') ('+' | '-')? DEC+   ;

// See Ref: http://www.open-std.org/jtc1/sc22/wg14/www/docs/n1124.pdf page 54
// `0` is considered as an octal number
IntConst: IntDec | IntOct | IntHex ;
fragment IntDec: ('1'..'9') DEC* ;
fragment IntOct: '0' ('0'..'7')* ;
fragment IntHex: ('0x' | '0X') HEX+ ;

fragment DEC: '0'..'9';
fragment HEX: 'a'..'f' | 'A'..'F' | '0'..'9';

StrConst: '"' ~('"'|'\n')* '"' ;

NEWLINE: '\r'? '\n' ;
WS: [ \t]+                          -> skip ;
LineComments: '//' .*? '\n'         -> skip ;
BlockComments: '/*' .*? '*/'        -> skip ;

//------------------------- Grammar ---------------------------------------

//================== Parser ============================
compUnit: (decl | funcDef)* ;


//---------------- Declaration & Definition ------------
funcDef
    : BType Ident '(' funcParamList ')' block
    ;

funcParamList
    : (funcParam (',' funcParam)*)?
    ;
funcParam
    : BType lVal
    ;

decl
    : 'const'? BType (def (',' def)*) ';'
    ;

def
    : lVal ('=' initVal)?
    ;

initVal
    : exp
    | '{' (initVal (',' initVal)*)? '}'
    ;


//---------------------------- stmt ---------------------
stmt
    : block
    | stmtIf
    | stmtWhile
    | stmtPutf
    | exp ';'
    | lVal '=' exp ';'
    | ';'
    | 'break'       ';'
    | 'continue'    ';'
    | 'return' exp? ';'
    ;

block
    : '{' (decl | stmt)* '}'
    ;

stmtIf
    : 'if' '(' cond ')' s1=stmt ('else' s2=stmt)?
    ;

stmtWhile
    : 'while' '(' cond ')' stmt
    ;

stmtPutf
    : 'putf' '(' StrConst ',' funcArgList ')' ';'
    ;


//----------------- expression ----------------------------
cond
    : logOr
    ;

exp
    : expAdd
    ;

logOr
    : logAnd '||' logOr
    | logAnd
    ;

logAnd
    : relEq '&&' logAnd
    | relEq
    ;

relEq
    : relComp ('==' | '!=') relEq
    | relComp
    ;

relComp
    : expAdd ('<' | '>' | '<=' | '>=') relComp
    | expAdd
    ;

expAdd
    : expMul ('+' | '-') expAdd
    | expMul
    ;

expMul
    : expUnary ('*' | '/' | '%') expMul
    | expUnary
    ;

expUnary
    : atom
    | ('+' | '-' | '!') expUnary
    | Ident '(' funcArgList ')'
    ;

atom
    : '(' exp ')'
    | lVal
    | IntConst
    | FloatConst
    ;


//----------------- misc ----------------------------
funcArgList
    : (exp (',' exp)*)?
    ;

lVal
    : Ident ('[' exp ']')*
    ;
