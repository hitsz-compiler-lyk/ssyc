grammar SysY;

@header {
package top.origami404.ssyc.frontend;
}

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
    : BType lValDecl
    ;

Const: 'const' ;
decl
    : Const? BType (def (',' def)*) ';'
    ;

def
    : lValDecl ('=' initVal)?
    ;

initVal
    : exp
    | '{' (initVal (',' initVal)*)? '}'
    ;


// 在变量声明(包括函数形参中) 出现的 LVal
// 单独拎出来方便代码生成
// exp 必须都是整数常量表达式
emptyDim: '[' ']';
lValDecl
    : Ident emptyDim? ('[' exp ']')*
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
    | Break       ';'
    | Continue    ';'
    | Return exp? ';'
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
    : logOr '||' logAnd
    | logAnd
    ;

logAnd
    : logAnd '&&' logRel
    | logRel
    ;

logRel: relEq ;

relEqOp: '==' | '!=' ;
relEq
    : relEq relEqOp relComp
    | relComp
    ;

relCompOp: '<' | '>' | '<=' | '>=' ;
relComp
    : relComp relCompOp relExp
    | relExp
    ;

relExp: exp;

expAddOp: '+' | '-' ;
expAdd
    : expAdd expAddOp expMul
    | expMul
    ;

expMulOp: '*' | '/' | '%' ;
expMul
    : expMul expMulOp expUnary
    | expUnary
    ;

expUnaryOp: '+' | '-' | '!' ;
expUnary
    : atom
    | expUnaryOp expUnary
    | Ident '(' funcArgList ')'
    ;

atom
    : '(' exp ')'
    | atomLVal
    | IntConst
    | FloatConst
    ;

// 方便 lVal 区分用作左值和右值的情况
atomLVal: lVal;

//----------------- misc ----------------------------
funcArgList
    : (exp (',' exp)*)?
    ;

lVal
    : Ident ('[' exp ']')*
    ;

//------------------------- Lexical ---------------------------------------

// keywords
// 关键字的规则必须出现在标识符的规则之前, 否则 ANTLR 会把关键字识别成标识符
Break: 'break' ;
Continue: 'continue' ;
Return: 'return' ;

BType: ('int' | 'float' | 'void');

Ident: ('a'..'z' | 'A'..'Z' | '_') ('a'..'z' | 'A'..'Z' | '0'..'9' | '_')* ;

// 数字不会跟标识符冲突(标识符不以数字打头), 所以可以放 Ident 后面
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


WS: [ \t\r\n]+                      -> skip ;
LineComments: '//' .*? '\n'         -> skip ;
BlockComments: '/*' .*? '*/'        -> skip ;