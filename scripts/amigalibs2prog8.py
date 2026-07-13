#!/usr/bin/env python3
"""
Convert Amiga NDK LVO, SFD, and .i files to Prog8 definitions.

Usage:
  python amigalibs2prog8.py /path/to/AmigaNDK_headers libraryname                > output.p8
  python amigalibs2prog8.py /path/to/AmigaNDK_headers libraryname --structs --consts  > out.p8

Parses the LVO file for LVO offsets, SFD file for function signatures,
and optionally .i files for struct definitions and constants.
"""

import sys
import re
import os
import argparse

# ---------------------------------------------------------------------------
# Type mapping
# ---------------------------------------------------------------------------

TYPE_MAP = {
    'VOID': None, 'void': None,
    'LONG': 'long', 'ULONG': 'long',
    'WORD': 'word', 'UWORD': 'uword',
    'BYTE': 'byte', 'UBYTE': 'ubyte',
    'BOOL': 'bool',
    'FLOAT': 'float', 'DOUBLE': 'float',
    'APTR': 'pointer', 'CONST_APTR': 'pointer',
    'CPTR': 'pointer', 'BPTR': 'pointer',
    'STRPTR': 'str', 'CONST_STRPTR': 'str',
    'TEXT': 'str', 'CONST_TEXT': 'str',
    'BSTR': 'str',
    'PLANEPTR': 'pointer',
    'DisplayInfoHandle': 'pointer',
}

# ---------------------------------------------------------------------------
# Struct name mapping: assembly tag -> (C name, expected field prefix)
# ---------------------------------------------------------------------------

STRUCT_NAME_MAP = {
    # exec
    'LN':             ('Node',             'LN_'),
    'MLN':            ('MinNode',          'MLN_'),
    'LH':             ('List',             'LH_'),
    'MLH':            ('MinList',          'MLH_'),
    'MP':             ('MsgPort',          'MP_'),
    'MN':             ('Message',          'MN_'),
    'IO':             ('IORequest',        'IO_'),
    'IS':             ('Interrupt',        'IS_'),
    'LIB':            ('Library',          'LIB_'),
    'TC_Struct':      ('Task',             'TC_'),
    # dos
    'FileHandle':     ('FileHandle',       'fh_'),
    'FileLock':       ('FileLock',         'fl_'),
    'Process':        ('Process',          'pr_'),
    'DosPacket':      ('DosPacket',        'dp_'),
    'StandardPacket': ('StandardPacket',   'sp_'),
    'DateStamp':      ('DateStamp',        'date_'),
    'FileInfoBlock':  ('FileInfoBlock',    'fib_'),
    'InfoData':       ('InfoData',         'info_'),
    'ErrorString':    ('ErrorString',      'error_'),
    'ExAllData':      ('ExAllData',        'eda_'),
    'ExAllControl':   ('ExAllControl',     'eac_'),
    # graphics
    'RastPort':       ('RastPort',         'rp_'),
    'Layer':          ('Layer',            'layer_'),
    'ClipRect':       ('ClipRect',         'cr_'),
    'BitMap':         ('BitMap',           'bm_'),
    'View':           ('View',             'view_'),
    'TextFont':       ('TextFont',         'tf_'),
    'TextAttr':       ('TextAttr',         'ta_'),
    'ColorMap':       ('ColorMap',         'cm_'),
    'TmpRas':         ('TmpRas',           'tr_'),
    'AreaInfo':       ('AreaInfo',         'ai_'),
    'TextExtent':     ('TextExtent',       'te_'),
    'Layer_Info':     ('Layer_Info',       'li_'),
    # intuition
    'Window':         ('Window',           'wd_'),
    'NewWindow':      ('NewWindow',        'nw_'),
    'Screen':         ('Screen',           'sc_'),
    'NewScreen':      ('NewScreen',        'ns_'),
    'IntuiMessage':   ('IntuiMessage',     'im_'),
    'ExtIntuiMessage':('ExtIntuiMessage',  'eim_'),
    'Gadget':         ('Gadget',           'gd_'),
    'ExtGadget':      ('ExtGadget',        'eg_'),
    'Menu':           ('Menu',             'mu_'),
    'MenuItem':       ('MenuItem',         'mi_'),
    'IntuiText':      ('IntuiText',        'it_'),
    'Border':         ('Border',           'bd_'),
    'Image':          ('Image',            'img_'),
    'Requester':      ('Requester',        'rq_'),
    'EasyStruct':     ('EasyStruct',       'es_'),
    'DrawInfo':       ('DrawInfo',         'di_'),
    'IBox':           ('IBox',             'ib_'),
    'ColorSpec':      ('ColorSpec',        'cs_'),
}

# Prog8 keywords that conflict with field names
PROG8_KEYWORDS = {
    'str', 'end', 'type', 'class', 'for', 'while', 'repeat', 'if', 'else',
    'do', 'in', 'to', 'not', 'and', 'or', 'xor', 'as', 'is', 'sub',
    'return', 'break', 'continue', 'void', 'true', 'false', 'pointer',
    'memory', 'inline', 'private', 'step', 'alias', 'const', 'enum',
    'struct', 'then', 'until', 'downto', 'when', 'goto', 'defer', 'swap',
}

PROG8_KEYWORDS.update({
    'exec':1, 'dos':1, 'graphics':1, 'intuition':1,
    'sys':1, 'main':1, 'txt':1,
})

# ---------------------------------------------------------------------------
# .i file parsers
# ---------------------------------------------------------------------------

RAW_TYPE_TOKENS = {'APTR','WORD','UWORD','BYTE','UBYTE','LONG','ULONG',
                   'BPTR','CPTR','STRPTR','CONST_APTR','CONST_STRPTR',
                   'TEXT','CONST_TEXT','BSTR','PLANEPTR','FLOAT','DOUBLE',
                   'BOOL','DisplayInfoHandle'}


def parse_struct_i_files(i_dir: str) -> dict:
    """Parse all .i files in a directory.  Returns dict tag->raw_struct."""
    if not os.path.isdir(i_dir):
        return {}
    structs = {}
    for fn in sorted(os.listdir(i_dir)):
        if not fn.endswith('.i'):
            continue
        structs.update(_parse_one_i(os.path.join(i_dir, fn)))
    # Second pass: resolve total sizes for structs with base_offset references
    # Iterate because some bases reference structs that also have bases
    for _ in range(10):
        sizes = {}
        for tag, rs in structs.items():
            if tag in STRUCT_NAME_MAP and rs['total_size'] > 0:
                tag_up = tag.upper()
                sizes[f'{tag_up}_SIZE'] = rs['total_size']
                sizes[f'{tag_up}_SIZEOF'] = rs['total_size']
        changed = False
        for tag, rs in structs.items():
            base_off = _parse_size_expr(rs['base_offset_str'], sizes)
            expected = base_off + sum(f.get('_size', 0) for f in rs.get('fields_raw', []))
            if base_off > 0 and rs['total_size'] != expected:
                rs['total_size'] = expected
                changed = True
        if not changed:
            break
    return structs


def _parse_one_i(filepath: str) -> dict:
    """Parse a single .i file, return dict of tag->raw_struct."""
    lines = _read_i_lines(filepath)
    structs = {}
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        m = re.match(r'STRUCTURE\s+(\w+)\s*,\s*(\S+)', stripped)
        if m:
            tag = m.group(1)
            base_offset_str = m.group(2)
            fields = []
            labels = {'_names': []}
            i += 1
            offset = 0
            while i < len(lines):
                sl = lines[i].strip()
                if not sl or sl.startswith('*') or sl.startswith(';'):
                    i += 1
                    continue
                if re.match(r'STRUCTURE\s', sl):
                    break
                if re.match(r'LABEL\s+\S+', sl):
                    lm = re.match(r'LABEL\s+(\S+)', sl)
                    if lm:
                        # track this label as belonging to this struct
                        labels.setdefault('_names', []).append(lm.group(1))
                    i += 1
                    continue
                if re.match(r'\w+\s+MACRO', sl):
                    i += 1
                    while i < len(lines) and not lines[i].strip().startswith('ENDM'):
                        i += 1
                    i += 1
                    continue
                field = _parse_field_line(sl)
                if field:
                    if field['type'] == 'STRUCT':
                        sz = _parse_size_expr(field.get('size_str',''), {})
                        field['_offset'] = offset
                        field['_size'] = sz
                        offset += sz
                    else:
                        sz = TYPE_MAP_SIZES.get(field['type'], 4)
                        field['_offset'] = offset
                        field['_size'] = sz
                        offset += sz
                        for extra in field.get('extra_names', []):
                            fields.append({
                                'type': field['type'],
                                'name': extra,
                                'extra_names': [],
                                'comment': '',
                                '_offset': offset,
                                '_size': sz,
                            })
                            offset += sz
                    fields.append(field)
                i += 1
            structs[tag] = {
                'tag': tag,
                'base_offset_str': base_offset_str,
                'fields_raw': fields,
                'total_size': offset,
                'labels': labels['_names'],
            }
            continue
        i += 1
    return structs


TYPE_MAP_SIZES = {
    'APTR': 4, 'BPTR': 4, 'CPTR': 4, 'STRPTR': 4, 'CONST_APTR': 4, 'CONST_STRPTR': 4,
    'TEXT': 4, 'CONST_TEXT': 4, 'BSTR': 4, 'PLANEPTR': 4, 'DisplayInfoHandle': 4,
    'LONG': 4, 'ULONG': 4,
    'WORD': 2, 'UWORD': 2,
    'BYTE': 1, 'UBYTE': 1, 'BOOL': 1,
    'FLOAT': 4, 'DOUBLE': 4,
    'STRUCT': 0,
}


def _read_i_lines(filepath: str) -> list:
    with open(filepath) as f:
        return [line.rstrip('\n').rstrip('\r') for line in f]


def _parse_field_line(line: str) -> dict | None:
    stripped = line.strip()
    comment = ''
    for sep in ('*', ';'):
        pos = stripped.find(sep)
        if pos >= 0:
            comment = stripped[pos:].strip()
            stripped = stripped[:pos].strip()
    if not stripped:
        return None
    m = re.match(r'STRUCT\s+(\w+)\s*,\s*(\S+)', stripped)
    if m:
        return {
            'type': 'STRUCT',
            'name': m.group(1),
            'size_str': m.group(2),
            'comment': comment,
        }
    words = stripped.split()
    if not words:
        return None
    if words[0] in RAW_TYPE_TOKENS:
        ndk_type = words[0]
        field_name = words[1] if len(words) > 1 else ''
        extra_names = []
        if ',' in field_name:
            parts = [p.strip() for p in field_name.split(',')]
            field_name = parts[0]
            extra_names = parts[1:]
        return {
            'type': ndk_type,
            'name': field_name,
            'extra_names': extra_names,
            'comment': comment,
        }
    return None


def _parse_size_expr(s: str, known: dict) -> int:
    """Try to parse a size expression (number or symbol -> size)."""
    try:
        return int(s)
    except ValueError:
        return known.get(s, 4)


# ---------------------------------------------------------------------------
# Struct resolution & flattening
# ---------------------------------------------------------------------------

def resolve_struct_sizes(raw_structs: dict) -> dict:
    """Pass 2: resolve inheritance chains and flatten each struct."""
    # Build sizes dict from resolved total_size values
    sizes = {}
    for tag, rs in raw_structs.items():
        if tag in STRUCT_NAME_MAP and rs['total_size'] > 0:
            tag_up = tag.upper()
            sizes[f'{tag_up}_SIZE'] = rs['total_size']
            sizes[f'{tag_up}_SIZEOF'] = rs['total_size']

    resolved = {}
    for tag, rs in raw_structs.items():
        if tag not in STRUCT_NAME_MAP:
            continue
        c_name, prefix = STRUCT_NAME_MAP[tag]
        flat = _flatten(tag, rs, raw_structs, prefix, set(), sizes)
        resolved[tag] = {
            'tag': tag,
            'c_name': c_name,
            'prefix': prefix,
            'fields': flat,
            'size': rs['total_size'],
        }
    return resolved


def _flatten(tag: str, rs, all_raw: dict, prefix: str, visiting: set, sizes: dict) -> list:
    if tag in visiting:
        return []           # guard against cycles
    visiting.add(tag)
    flat = []
    offset = 0
    base_off = _parse_size_expr(rs['base_offset_str'], sizes)
    if base_off > 0:
        # First try to find parent by label name (exact match)
        parent_tag = _find_tag_by_label(rs['base_offset_str'], all_raw)
        if parent_tag is None:
            # Fall back to size-based lookup
            parent_tag = _find_tag_by_size(base_off, all_raw)
        if parent_tag and parent_tag in STRUCT_NAME_MAP:
            pprefix = STRUCT_NAME_MAP[parent_tag][1]
            pflat = _flatten(parent_tag, all_raw[parent_tag], all_raw, pprefix, visiting, sizes)
            flat.extend(dict(f) for f in pflat)
            offset = base_off
    for field in rs['fields_raw']:
        if field['type'] == 'STRUCT':
            sz = field['_size']
            emb_tag = _find_tag_by_size(sz, all_raw)
            if emb_tag and emb_tag in STRUCT_NAME_MAP:
                eprefix = STRUCT_NAME_MAP[emb_tag][1]
                eflat = _flatten(emb_tag, all_raw[emb_tag], all_raw, eprefix, visiting, sizes)
                for ef in eflat:
                    ec = dict(ef)
                    ec['offset'] += offset
                    flat.append(ec)
            else:
                flat.append({
                    'prog8_type': 'pointer',
                    'prog8_name': f'emb_{field["name"]}',
                    'offset': offset,
                    'comment': f'; TODO embedded {field["size_str"]}',
                })
            offset += sz
        else:
            ptype = TYPE_MAP.get(field['type'], 'pointer')
            pname = _field_name(field['name'], prefix)
            sz = field['_size']
            flat.append({
                'prog8_type': ptype,
                'prog8_name': pname,
                'offset': offset,
            })
            offset += sz
            for extra in field.get('extra_names', []):
                ename = _field_name(extra, prefix)
                flat.append({
                    'prog8_type': ptype,
                    'prog8_name': ename,
                    'offset': offset,
                })
                offset += sz
    visiting.discard(tag)
    return flat


def _find_tag_by_size(sz: int, all_raw: dict) -> str | None:
    for t, rs in all_raw.items():
        if t in STRUCT_NAME_MAP and rs['total_size'] == sz:
            return t
    return None


def _find_tag_by_label(label: str, all_raw: dict) -> str | None:
    """Find the struct tag that defines a given label name (e.g. LN_SIZE -> LN)."""
    for t, rs in all_raw.items():
        for ln in rs.get('labels', []):
            if ln.upper() == label.upper():
                return t
    return None


def _field_name(raw: str, prefix: str) -> str:
    """Strip the struct-specific prefix and PascalCase the result.

    Prog8 struct fields are scoped, so the per-struct prefix
    (e.g. LN_, IO_, nw_, wd_) is redundant.

    LN_SUCC      -> Succ
    IO_DEVICE    -> Device
    IO_COMMAND   -> Command
    nw_LeftEdge  -> LeftEdge      (already PascalCase, keep as-is)
    wd_RPort     -> RPort         (already PascalCase, keep as-is)
    MN_REPLYPORT -> Replyport     (all-caps, no word-boundary info)
    """
    if not raw:
        return 'unknown'
    name = raw
    if prefix:
        pu = prefix.upper()
        nu = name.upper()
        if nu.startswith(pu):
            name = name[len(prefix):]
        else:
            pp = prefix.rstrip('_')
            if pp and nu.startswith(pp.upper()):
                name = name[len(pp):]
    if not name:
        return 'unknown'
    # PascalCase: uppercase first letter, lowercase rest for all-caps names.
    # Names that already have mixed case (LeftEdge, RPort) keep their case.
    if name.isupper() or name.isdigit():
        name = name[0] + name[1:].lower()
    else:
        name = name[0].upper() + name[1:]
    if name in PROG8_KEYWORDS:
        name = f'k_{name}'
    return name


# ---------------------------------------------------------------------------
# EQU / BITDEF parser
# ---------------------------------------------------------------------------

def parse_consts_i_files(i_dir: str) -> list:
    if not os.path.isdir(i_dir):
        return []
    consts = []
    for fn in sorted(os.listdir(i_dir)):
        if not fn.endswith('.i'):
            continue
        consts.extend(_parse_consts_one(os.path.join(i_dir, fn)))
    return consts


def _parse_consts_one(filepath: str) -> list:
    consts = []
    with open(filepath) as f:
        for line in f:
            stripped = line.strip()
            if not stripped or stripped.startswith('*') or stripped.startswith(';'):
                continue
            m = re.match(r'BITDEF\s+(\w+)\s*,\s*(\w+)\s*,\s*(\d+)', stripped)
            if m:
                p = m.group(1); n = m.group(2); b = int(m.group(3))
                consts.append({'name': f'{p}B_{n}', 'value': str(b), 'prog8_type': 'ubyte', 'comment': f'; BITDEF {p},{n},{b}'})
                consts.append({'name': f'{p}F_{n}', 'value': _fmt(1 << b), 'prog8_type': 'long' if (1<<b) > 65535 else 'uword' if (1<<b) > 255 else 'ubyte', 'comment': f'; BITDEF mask {p},{n},{b}'})
                continue
            m = re.match(r'(\w+)\s+EQU\s+(\S+)', stripped)
            if m:
                cname = m.group(1)
                rawv = m.group(2)
                v = _eval_const(rawv)
                if v is not None:
                    consts.append({'name': cname, 'value': _fmt(v), 'prog8_type': _ct(v)})
                # skip unresolved expressions (they use ! operator, macros, etc.)
    return consts


def _eval_const(raw: str) -> int | None:
    raw = raw.strip()
    m = re.match(r'\$([0-9a-fA-F]+)', raw)
    if m: return int(m.group(1), 16)
    m = re.match(r'(-?\d+)', raw)
    if m: return int(m.group(1))
    m = re.match(r'1\s*<<\s*(\d+)', raw)
    if m: return 1 << int(m.group(1))
    return None


def _fmt(v: int) -> str:
    if v < 0: return str(v)
    return f'${v:04x}' if v <= 65535 else f'${v:08x}' if v > 255 else f'${v:02x}'


def _ct(v: int) -> str:
    if v < 0: return 'long'
    return 'ubyte' if v <= 255 else 'uword' if v <= 65535 else 'long'


# ---------------------------------------------------------------------------
# Prog8 code generation
# ---------------------------------------------------------------------------

def generate_structs(resolved: dict, lib_name: str) -> str:
    lines = []
    if not resolved:
        return ''
    lines.append(f'\n; ---- struct definitions for {lib_name} ----\n')
    for tag in sorted(resolved.keys()):
        rs = resolved[tag]
        lines.append(f'struct {rs["c_name"]} {{  ; total size: {rs["size"]}')
        for f in rs['fields']:
            ptype = f['prog8_type']
            pname = f['prog8_name']
            off = f['offset']
            comment = f.get('comment', '')
            line = f'    {ptype} {pname}'
            if comment:
                line += f'  {comment}'
            line += f'  ; {off}'
            lines.append(line)
        lines.append('}\n')
    return '\n'.join(lines)


def generate_consts(consts: list, lib_name: str) -> str:
    if not consts:
        return ''
    lines = [f'\n; ---- constants for {lib_name} ----\n']
    seen = set()
    for c in consts:
        if c['name'] in seen:
            continue
        seen.add(c['name'])
        line = f'const {c["prog8_type"]} {c["name"]} = {c["value"]}'
        lines.append(line)
    lines.append('')
    return '\n'.join(lines)


# ---------------------------------------------------------------------------
# Library bank mapping (unchanged)
# ---------------------------------------------------------------------------

LIBRARY_BANK_MAP = {
    'exec': 1, 'dos': 2, 'graphics': 3, 'intuition': 4,
    'gadtools': 5, 'layers': 6, 'asl': 7, 'console': 8,
    'utility': 9, 'expansion': 10, 'icon': 11, 'wb': 12,
    'diskfont': 13, 'iffparse': 14, 'input': 15, 'keymap': 16,
    'locale': 17, 'timer': 18, 'lowlevel': 19, 'mathffp': 20,
    'mathieeesingbas': 21, 'mathieeesingtrans': 22,
    'mathieeedoubbas': 23, 'mathieeedoubtrans': 24,
    'mathtrans': 25, 'nonvolatile': 26, 'realtime': 27,
    'translator': 28, 'rexxsyslib': 29, 'commodities': 30,
    'datatypes': 31, 'disk': 32, 'amigaguide': 33, 'arexx': 34,
    'battclock': 35, 'battmem': 36, 'bevel': 37, 'bitmap': 38,
    'bullet': 39, 'button': 40, 'cardres': 41, 'checkbox': 42,
    'chooser': 43, 'cia': 44, 'clicktab': 45, 'colorwheel': 46,
    'datebrowser': 47, 'drawlist': 48, 'dtclass': 49,
    'fuelgauge': 50, 'getcolor': 51, 'getfile': 52, 'getfont': 53,
    'getscreenmode': 54, 'glyph': 55, 'integer': 56, 'label': 57,
    'layout': 58, 'listbrowser': 59, 'misc': 60, 'palette': 61,
    'penmap': 62, 'potgo': 63, 'radiobutton': 64, 'ramdrive': 65,
    'requester': 66, 'scroller': 67, 'sketchboard': 68,
    'slider': 69, 'space': 70, 'speedbar': 71, 'string': 72,
    'texteditor': 73, 'trackfile': 74, 'virtual': 75, 'window': 76,
}

PROG8_KEYWORDS.update(LIBRARY_BANK_MAP.keys())

# ---------------------------------------------------------------------------
# Existing SFD / LVO parsers
# ---------------------------------------------------------------------------

def parse_c_type(c_type: str) -> tuple:
    c_type = c_type.strip()
    c_type = re.sub(r'\b(const|volatile|register|unsigned|signed)\b', '', c_type).strip()
    while '  ' in c_type: c_type = c_type.replace('  ', ' ')
    is_ptr = '*' in c_type
    c_type = c_type.replace('*', '').strip()
    c_type = re.sub(r'\b(struct|union)\s+', '', c_type).strip()
    if c_type in TYPE_MAP:
        return (TYPE_MAP[c_type], c_type)
    return ('pointer', c_type) if is_ptr else ('long', c_type)


def parse_lvo(filepath: str) -> dict:
    lvos = {}
    with open(filepath) as f:
        for line in f:
            m = re.match(r'^_LVO(\w+)\s+equ\s+(-?\d+)', line)
            if m:
                lvos[m.group(1)] = int(m.group(2))
    return lvos


def join_sfd_lines(filepath: str) -> list:
    joined = []
    with open(filepath) as f:
        prev = None
        for line in f:
            line = line.rstrip('\n')
            if prev is not None and (line.startswith('\t') or line.startswith(' ')):
                prev += ' ' + line.lstrip()
            else:
                if prev is not None:
                    joined.append(prev)
                prev = line
        if prev is not None:
            joined.append(prev)
    return joined


def extract_last_paren_group(text: str):
    close_pos = -1
    for i in range(len(text) - 1, -1, -1):
        if text[i] == ')': close_pos = i; break
    if close_pos < 0: return None
    depth = 0; open_pos = -1
    for i in range(close_pos, -1, -1):
        if text[i] == ')': depth += 1
        elif text[i] == '(':
            depth -= 1
            if depth == 0: open_pos = i; break
    if open_pos < 0: return None
    return (open_pos, close_pos, text[open_pos + 1:close_pos].strip())


def parse_sfd_param(param_str: str) -> tuple:
    param_str = param_str.strip()
    if not param_str:
        return ('long', 'arg')
    fp = re.search(r'\(\*(\w+)\)', param_str)
    if fp: return ('pointer', fp.group(1))
    parts = param_str.rsplit(None, 1)
    if len(parts) == 2:
        ptype_raw, pname = parts
    else:
        ptype_raw = parts[0]
        pname = 'arg'
    while pname.startswith('*'):
        ptype_raw += ' *'; pname = pname[1:]
    if not pname:
        return ('long', 'arg')
    prog8_type = parse_c_type(ptype_raw)[0] or 'long'
    if pname in PROG8_KEYWORDS:
        pname = f'k_{pname}'
    return (prog8_type, pname)


def parse_sfd(filepath: str) -> dict:
    lines = join_sfd_lines(filepath)
    funcs = {}
    in_alias = False; libname = ''
    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith('*'):
            in_alias = False; continue
        if stripped.startswith('==') or stripped.startswith('##'):
            d = stripped.split()[0] if stripped.split() else ''
            if d in ('==alias', '##alias'): in_alias = True; continue
            in_alias = False
            m = re.match(r'==base\s+(\S+)', stripped)
            if m: libname = m.group(1)
            continue
        if in_alias: in_alias = False; continue
        info = parse_sfd_func_line(line)
        if info: funcs[info['name']] = info
    funcs['__libname'] = libname
    return funcs


def parse_sfd_func_line(line: str):
    line = line.strip()
    if not line: return None
    rg = extract_last_paren_group(line)
    if rg is None: return None
    regs = [r.strip() for r in rg[2].replace('/', ',').split(',') if r.strip()] if rg[2] else []
    sig = line[:rg[0]].strip()
    if not sig: return None
    fp = sig.find('(')
    if fp < 0: return None
    depth = 0; pc = -1
    for i in range(fp, len(sig)):
        if sig[i] == '(': depth += 1
        elif sig[i] == ')':
            depth -= 1
            if depth == 0: pc = i; break
    if pc < 0: return None
    ps = sig[fp+1:pc].strip()
    before = sig[:fp].strip()
    words = before.split()
    if not words: return None
    nraw = words[-1]; name = nraw.lstrip('*'); stars = len(nraw) - len(name)
    rt = ' '.join(words[:-1]) + (' *' * stars) if stars > 0 else ' '.join(words[:-1])
    is_void = rt.strip() in ('VOID', 'void')
    params = [parse_sfd_param(p) for p in ps.split(',') if p.strip()] if ps else []
    return {'name': name, 'ret_type': rt, 'params': params, 'regs': regs, 'is_void': is_void}


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description="Convert Amiga NDK LVO, SFD, .i files to Prog8.")
    ap.add_argument('ndk_path', nargs='?', help="Path to AmigaNDK_headers directory")
    ap.add_argument('lib_name', nargs='?', help="Library name (e.g. exec, dos, graphics)")
    ap.add_argument('-m', '--mapping', action='store_true', help="Print library bank mapping")
    ap.add_argument('-s', '--structs', action='store_true', help="Generate struct definitions")
    ap.add_argument('-c', '--consts', action='store_true', help="Generate constant definitions")
    args = ap.parse_args()

    if args.mapping and not args.lib_name:
        print_mapping(None); return
    if not args.ndk_path or not args.lib_name:
        ap.print_help(); sys.exit(1)

    ndk = args.ndk_path.rstrip('/')
    lib = args.lib_name

    # Detect if we need to generate structs section inside the block
    if args.structs or args.consts:
        inc = os.path.join(ndk, 'Include_I', lib)
        exec_inc = os.path.join(ndk, 'Include_I', 'exec')
        all_raw = {}
        if os.path.isdir(exec_inc):
            all_raw.update(parse_struct_i_files(exec_inc))
        if os.path.isdir(inc):
            all_raw.update(parse_struct_i_files(inc))
    else:
        all_raw = {}

    # Parse LVO and SFD for function signatures
    lvo_p = os.path.join(ndk, 'Include_I', 'lvo', f'{lib}_lib.i')
    sfd_p = os.path.join(ndk, 'SFD', f'{lib}_lib.sfd')
    if not os.path.exists(lvo_p) or not os.path.exists(sfd_p):
        print(f"Error: LVO or SFD file not found for '{lib}'", file=sys.stderr)
        sys.exit(1)
    lvos = parse_lvo(lvo_p)
    sfd_funcs = parse_sfd(sfd_p)
    libname = sfd_funcs.pop('__libname', '')
    bank = LIBRARY_BANK_MAP.get(lib)
    btag = f"@bank {bank} " if bank else ""

    print(f";; Auto-generated from {lib}_lib.sfd and {lib}_lib.i")
    if libname:
        print(f";; Library base: {libname}  in prog8: sys.{libname[1:]}")
    if bank: print(f";; Bank: {bank}")
    print(f";; Functions: {len(lvos)}\n")

    print(f"{lib} {{")

    # extsub definitions
    for name, lvo in sorted(lvos.items(), key=lambda x: x[1], reverse=True):
        info = sfd_funcs.get(name)
        pi = info['params'] if info else []
        regs = info['regs'] if info else []
        iv = info['is_void'] if info else True
        rt = info['ret_type'] if info and info['ret_type'] else None
        pp = []
        idx = 0
        for r in regs:
            if idx < len(pi):
                pt, pn = pi[idx]; idx += 1
            else:
                pt, pn = 'long', f'reg_{r.lower()}'
            pp.append(f"{pt} {pn} @{r.upper()}")
        ps = ", ".join(pp)
        rets = ''
        if not iv:
            pr = parse_c_type(rt)[0] or 'long'
            rets = f" -> {pr} @D0"
        print(f"    extsub {btag}  {lvo} = {name}({ps}){rets}")

    # structs inside the block
    if args.structs:
        resolved = resolve_struct_sizes(all_raw)
        target_tags = _tags_in_dir(inc) if os.path.isdir(inc) else set()
        print(f"\n    ; ---- struct definitions ----")
        for tag in sorted(resolved.keys()):
            rs = resolved[tag]
            print(f'    struct {rs["c_name"]} {{  ; total size: {rs["size"]}')
            for f in rs['fields']:
                ptype = f['prog8_type']
                pname = f['prog8_name']
                off = f['offset']
                comment = f.get('comment', '')
                line = f'        {ptype} {pname}'
                if comment:
                    line += f'  {comment}'
                line += f'  ; {off}'
                print(line)
            print('    }')

    # constants inside the block
    if args.consts:
        consts = []
        if os.path.isdir(inc):
            consts.extend(parse_consts_i_files(inc))
        print(f"\n    ; ---- constants ----")
        seen = set()
        for c in consts:
            if c['name'] in seen:
                continue
            seen.add(c['name'])
            print(f'    const {c["prog8_type"]} {c["name"]} = {c["value"]}')

    print(f"}}")
    print(f";; End of auto-generated {lib}_lib.sfd")
    if args.mapping: print_mapping(lib)


def _tags_in_dir(d: str) -> set:
    tags = set()
    for fn in os.listdir(d):
        if not fn.endswith('.i'): continue
        with open(os.path.join(d, fn)) as f:
            for line in f:
                m = re.match(r'STRUCTURE\s+(\w+)\s*,', line)
                if m: tags.add(m.group(1))
    return tags


def print_mapping(cl):
    print(file=sys.stderr)
    for lib, bn in sorted(LIBRARY_BANK_MAP.items(), key=lambda x: x[1]):
        m = "  <--" if lib == cl else ""
        print(f"  bank {bn:3d} = {lib}{m}", file=sys.stderr)


if __name__ == '__main__':
    main()
