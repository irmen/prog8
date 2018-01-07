"""
Programming Language for 6502/6510 microprocessors
This is the lexer of the IL65 code, that generates a stream of tokens for the parser.

Written by Irmen de Jong (irmen@razorvine.net)
License: GNU GPL 3.0, see LICENSE
"""

import sys
import ply.lex
from .symbols import SourceRef
from .parse import ParseError


# token names

tokens = (
    "INTEGER",
    "FLOATINGPOINT",
    "DOTTEDNAME",
    "NAME",
    "IS",
    "CLOBBEREDREGISTER",
    "REGISTER",
    "COMMENT",
    "DIRECTIVE",
    "AUGASSIGN",
    "EQUALS",
    "NOTEQUALS",
    "RARROW",
    "RETURN",
    "VARTYPE",
    "SUB",
    "DATATYPE",
    "CHARACTER",
    "STRING",
    "BOOLEAN",
    "GOTO",
    "INCR",
    "DECR",
    "LT",
    "GT",
    "LE",
    "GE",
    "BITAND",
    "BITOR",
    "BITXOR",
    "BITINVERT",
    "LOGICAND",
    "LOGICOR",
    "LOGICNOT",
    "POWER",
    "LABEL",
    "IF",
    "PRESERVEREGS",
    "INLINEASM",
    "ENDL"
)

literals = ['+', '-', '*', '/', '(', ')', '[', ']', '{', '}', '.', ',', '!', '?', ':']

# regex rules for simple tokens

t_BITAND = r"&"
t_BITOR = r"\|"
t_BITXOR = r"\^"
t_BITINVERT = r"~"
t_IS = r"="
t_AUGASSIGN = r"\+=|-=|/=|\*=|<<=|>>=|&=|\|=|\^="
t_DECR = r"--"
t_INCR = r"\+\+"
t_EQUALS = r"=="
t_NOTEQUALS = r"!="
t_LT = r"<"
t_GT = r">"
t_LE = r"<="
t_GE = r">="
t_IF = "if(_[a-z]+)?"
t_RARROW = r"->"
t_POWER = r"\*\*"


# ignore inline whitespace
t_ignore = " \t"
t_inlineasm_ignore = " \t\r\n"


# states for allowing %asm inclusion of raw assembly
states = (
    ('inlineasm', 'exclusive'),
)

# reserved words
reserved = {
    "sub": "SUB",
    "var": "VARTYPE",
    "memory": "VARTYPE",
    "const": "VARTYPE",
    "goto": "GOTO",
    "return": "RETURN",
    "true": "BOOLEAN",
    "false": "BOOLEAN",
    "not": "LOGICNOT",
    "and": "LOGICAND",
    "or": "LOGICOR",
    "AX": "REGISTER",
    "AY": "REGISTER",
    "XY": "REGISTER",
    "SC": "REGISTER",
    "SI": "REGISTER",
    "SZ": "REGISTER",
    "A": "REGISTER",
    "X": "REGISTER",
    "Y": "REGISTER",
    "if": "IF",
    "if_true": "IF",
    "if_not": "IF",
    "if_zero": "IF",
    "if_ne": "IF",
    "if_eq": "IF",
    "if_cc": "IF",
    "if_cs": "IF",
    "if_vc": "IF",
    "if_vs": "IF",
    "if_ge": "IF",
    "if_le": "IF",
    "if_gt": "IF",
    "if_lt": "IF",
    "if_pos": "IF",
    "if_get": "IF",
}


# rules for tokens with some actions

def t_inlineasm(t):
    r"%asm\s*\{\s*"
    t.lexer.code_start = t.lexer.lexpos     # Record start position
    t.lexer.level = 1                       # initial brace level
    t.lexer.begin("inlineasm")             # enter state 'inlineasm'
    t.lexer.lineno += 1


def t_inlineasm_lbrace(t):
    r"\{"
    t.lexer.level += 1


def t_inlineasm_rbrace(t):
    r"\}"
    t.lexer.level -= 1
    # if closing brace, return code fragment
    if t.lexer.level == 0:
        t.value = t.lexer.lexdata[t.lexer.code_start:t.lexer.lexpos-1]
        t.type = "INLINEASM"
        t.lexer.lineno += t.value.count("\n")
        t.lexer.begin("INITIAL")    # back to normal lexing rules
        return t


def t_inlineasm_comment(t):
    r";[^\n]*"
    pass


def t_inlineasm_string(t):
    r"""(?x)   # verbose mode
    (?<!\\)    # not preceded by a backslash
    "          # a literal double-quote
    .*?        # 1-or-more characters
    (?<!\\)    # not preceded by a backslash
    "          # a literal double-quote
    |
    (?<!\\)    # not preceded by a backslash
    '          # a literal single quote
    .*?        # 1-or-more characters
    (?<!\\)    # not preceded by a backslash
    '          # a literal double-quote
    """
    pass


def t_inlineasm_nonspace(t):
    r'[^\s\{\}\'\"]+'
    pass


def t_inlineasm_error(t):
    # For bad characters, we just skip over it
    t.lexer.skip(1)


def t_CLOBBEREDREGISTER(t):
    r"(AX|AY|XY|A|X|Y)\?"
    t.value = t.value[:-1]
    return t


def t_DATATYPE(t):
    r"\.byte|\.wordarray|\.float|\.array|\.word|\.text|\.stext|\.ptext|\.pstext|\.matrix"
    t.value = t.value[1:]
    return t


def t_LABEL(t):
    r"[a-zA-Z_]\w*\s*:"
    t.value = t.value[:-1].strip()
    return t


def t_DOTTEDNAME(t):
    r"[a-zA-Z_]\w*(\.[a-zA-Z_]\w*)+"
    return t


def t_NAME(t):
    r"[a-zA-Z_]\w*"
    t.type = reserved.get(t.value, "NAME")   # check for reserved words
    return t


def t_DIRECTIVE(t):
    r"%[a-z]+\b"
    t.value = t.value[1:]
    return t


def t_STRING(t):
    r"""(?x)   # verbose mode
    (?<!\\)    # not preceded by a backslash
    "          # a literal double-quote
    .*?        # 1-or-more characters
    (?<!\\)    # not preceded by a backslash
    "          # a literal double-quote
    |
    (?<!\\)    # not preceded by a backslash
    '          # a literal single quote
    .*?        # 1-or-more characters
    (?<!\\)    # not preceded by a backslash
    '          # a literal double-quote
    """
    t.value = t.value[1:-1]
    if len(t.value) == 1:
        t.type = "CHARACTER"
    if len(t.value) == 2 and t.value[0] == '\\':
        t.type = "CHARACTER"
    return t


def t_FLOATINGPOINT(t):
    r"((?: (?: \d* \. \d+ ) | (?: \d+ \.? ) )(?: [Ee] [+-]? \d+ ) ?)(?![a-z])"
    try:
        t.value = int(t.value)
        t.type = "INTEGER"
    except ValueError:
        t.value = float(t.value)
    return t


def t_INTEGER(t):
    r"\$?[a-fA-F\d]+ | [\$%]?\d+ | %?[01]+"
    sign = 1
    if t.value[0] in "+-":
        sign = -1 if t.value[0] == "-" else 1
        t.value = t.value[1:]
    if t.value[0] == '$':
        t.value = int(t.value[1:], 16) * sign
    elif t.value[0] == '%':
        t.value = int(t.value[1:], 2) * sign
    else:
        t.value = int(t.value) * sign
    return t


def t_COMMENT(t):
    r"[ \t]*;[^\n]*"    # dont eat newline
    return None   # don't process comments


def t_PRESERVEREGS(t):
    r"!\s*[AXY]{0,3}\s*(?!=)"
    t.value = t.value[1:-1].strip()
    return t


def t_ENDL(t):
    r"\n+"
    t.lexer.lineno += len(t.value)
    t.value = "\n"
    return t    # end of lines are significant to the parser


def t_error(t):
    line, col = t.lineno, find_tok_column(t)
    filename = getattr(t.lexer, "source_filename", "<unknown-file>")
    sref = SourceRef(filename, line, col)
    t.lexer.error_function("{}: Illegal character '{:s}'", sref, t.value[0], sourceref=sref)
    t.lexer.skip(1)


def find_tok_column(token):
    """ Find the column of the token in its line."""
    last_cr = lexer.lexdata.rfind('\n', 0, token.lexpos)
    return token.lexpos - last_cr


def error_function(fmtstring, *args, sourceref: SourceRef=None) -> None:
    raise ParseError(fmtstring.format(*args), None, sourceref=sourceref)


lexer = ply.lex.lex()
lexer.error_function = error_function        # can override this


if __name__ == "__main__":
    ply.lex.runmain()
    # lexer = ply.lex.Lexer()
    # ply.lex.runmain(lexer=lexer)
