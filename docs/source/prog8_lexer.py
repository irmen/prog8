from pygments.lexer import RegexLexer, include
from pygments.token import *


class Prog8Lexer(RegexLexer):
    name = "Prog8"
    aliases = ["prog8"]
    filenames = ["*.p8"]

    tokens = {
        "root": [
            (r";.*$", Comment.Single),
            (r"/\*", Comment.Multiline, "blockcomment"),
            (r'"', String, "string"),
            ("'", String, "char"),
            (r"\d+\.\d+(?:[eE][+-]?\d+)?", Number.Float),
            (r"\d+", Number.Integer),
            (r"\$[0-9a-fA-F_]+", Number.Hex),
            (r"\$", Operator),
            (r"%[01_]+", Number.Bin),
            (r"\btrue\b|\bfalse\b", Keyword.Constant),
            (r"\b(?:if|else|then|when)\b", Keyword),
            (r"\b(?:if_cs|if_cc|if_vs|if_vc|if_eq|if_z|if_ne|if_nz|if_pl|if_pos|if_mi|if_neg)\b", Keyword),
            (r"\b(?:for|while|do|until|repeat|unroll|in|to|downto|step)\b", Keyword),
            (r"\b(?:break|continue|goto|return|defer|swap)\b", Keyword),
            (r"\b(?:sub|asmsub|extsub|inline|private|clobbers)\b", Keyword.Declaration),
            (r"\b(?:const|enum|struct|alias|memory)\b", Keyword.Declaration),
            (r"\b(?:ubyte|byte|uword|word|long|float|str|bool|pointer)\b", Keyword.Type),
            (r"\bvoid\b", Keyword.Type),
            (r"\b(?:and|or|xor|not|as)\b", Operator.Word),
            (r"%\w+", Name.Decorator),
            (r"@\w+", Name.Decorator),
            (r"@", Operator),
            (r"#", Operator),
            (r"\\", Operator),
            (r"\^\^", Keyword.Type),
            (r"\+\+|--", Operator),
            (r"(?:<<=|>>=)", Operator),
            (r"<<|>>", Operator),
            (r"&&|\|\|", Operator),
            (r"=", Operator),
            (r"[-+*/%&|^~<>!]=?", Operator),
            (r"[{}()\[\]]", Punctuation),
            (r":", Punctuation),
            (r",", Punctuation),
            (r"\.", Punctuation),
            (r"\s+", Text),
            (r"[a-zA-Z_][a-zA-Z0-9_]*", Name),
        ],
        "string": [
            (r'\\[\\"\'nrtxXuU0]', String.Escape),
            (r'"', String, "#pop"),
            (r"[^\\\"]+", String),
            (r"\\", String),
        ],
        "char": [
            (r"\\(?:[\\\"'nrtxXuU0])", String.Escape),
            (r"'", String, "#pop"),
            (r"[^\\']+", String),
            (r"\\", String),
        ],
        "blockcomment": [
            (r"[^*/]+", Comment.Multiline),
            (r"/\*", Comment.Multiline, "#push"),
            (r"\*/", Comment.Multiline, "#pop"),
            (r"[*/]", Comment.Multiline),
        ],
    }
