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
import subprocess
import json
import tempfile

# ---------------------------------------------------------------------------
# Type mapping
# ---------------------------------------------------------------------------

TYPE_MAP = {
    'VOID': None, 'void': None,
    'LONG': 'long', 'ULONG': 'long',
    'WORD': 'word', 'UWORD': 'uword',
    'BYTE': 'byte', 'UBYTE': 'ubyte',
    'BOOL': 'uword',  # uword (not bool) for STRUCT fields: BOOL is 16-bit WORD on m68k; using 1-byte bool would read the high byte on big-endian, always seeing 0 for small values. Function returns override to 'bool' in parse_c_type (reads D0 low byte, which is correct).
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
    'IO':             ('IOStdReq',         'IO_',      ('IORequest', 32)),
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
    'exec', 'dos', 'graphics', 'intuition',
    'sys', 'main', 'txt',
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
        new_structs = _parse_one_i(os.path.join(i_dir, fn))
        for tag in new_structs:
            if tag in structs:
                print(f"Warning: struct '{tag}' redefined in {fn}, overwriting", file=sys.stderr)
        structs.update(new_structs)
    # Second pass: resolve total sizes for structs with base_offset references
    # Iterate because some bases reference structs that also have bases
    changed = True
    while changed:
        changed = False
        sizes = {}
        for tag, rs in structs.items():
            if tag in STRUCT_NAME_MAP and rs['total_size'] > 0:
                tag_up = tag.upper()
                sizes[f'{tag_up}_SIZE'] = rs['total_size']
                sizes[f'{tag_up}_SIZEOF'] = rs['total_size']
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
                        labels['_names'].append(lm.group(1))
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
    'BYTE': 1, 'UBYTE': 1, 'BOOL': 2,
    'FLOAT': 4, 'DOUBLE': 8,
    'STRUCT': 0,
}


def _read_i_lines(filepath: str) -> list:
    with open(filepath, encoding='latin-1') as f:
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
        if s not in known:
            print(f"Warning: unknown size symbol '{s}', defaulting to 4", file=sys.stderr)
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
        entry = STRUCT_NAME_MAP[tag]
        c_name = entry[0]
        prefix = entry[1]
        flat = _flatten(tag, rs, raw_structs, prefix, frozenset(), sizes)
        # Deduplicate field names: track used names and prefix colliding
        # fields with the origin struct's C name.
        used_original = {}  # base name -> first origin
        used_all = set()    # all field names (original + renamed)
        for f in flat:
            base = f['prog8_name']
            origin = f.get('origin_tag', tag)
            origin_cname = STRUCT_NAME_MAP.get(origin, (origin,))[0]
            if base not in used_original:
                used_original[base] = origin
                used_all.add(base)
            else:
                prev_origin = used_original[base]
                if origin == tag:
                    # own field — rename the conflicting previous embedding
                    for prev in flat:
                        if prev is f:
                            break
                        if prev['prog8_name'] == base:
                            newname = _unique_name(f'{origin_cname}_{base}', used_all)
                            prev['prog8_name'] = newname
                            used_all.add(newname)
                            break
                else:
                    newname = _unique_name(f'{origin_cname}_{base}', used_all)
                    f['prog8_name'] = newname
                    used_all.add(newname)
        resolved[tag] = {
            'tag': tag,
            'c_name': c_name,
            'prefix': prefix,
            'fields': flat,
            'size': rs.get('total_size', 0) or (flat[-1]['offset'] + _type_size(flat[-1]['prog8_type']) if flat else 0),
        }
        # If the mapping has a split, also emit the base struct
        if len(entry) >= 3 and isinstance(entry[2], tuple):
            base_name, base_size = entry[2]
            base_fields = [f for f in flat if f['offset'] < base_size]
            resolved[f'{tag}_BASE'] = {
                'tag': f'{tag}_BASE',
                'c_name': base_name,
                'prefix': prefix,
                'fields': base_fields,
                'size': base_size,
            }
    return resolved


def _flatten(tag: str, rs, all_raw: dict, prefix: str, visiting: frozenset, sizes: dict) -> list:
    if tag in visiting:
        return []           # guard against cycles
    new_visiting = visiting | {tag}
    flat = []
    offset = 0
    base_off = _parse_size_expr(rs['base_offset_str'], sizes)
    if base_off > 0:
        parent_tag = _find_tag_by_label(rs['base_offset_str'], all_raw)
        if parent_tag is None:
            parent_tag = _find_tag_by_size(base_off, all_raw)
        if parent_tag and parent_tag in STRUCT_NAME_MAP:
            pprefix = STRUCT_NAME_MAP[parent_tag][1]
            pflat = _flatten(parent_tag, all_raw[parent_tag], all_raw, pprefix, new_visiting, sizes)
            flat.extend(dict(f) for f in pflat)
            offset = base_off
    for field in rs['fields_raw']:
        if field['type'] == 'STRUCT':
            sz = _parse_size_expr(field.get('size_str', ''), sizes)
            if field.get('_clang_processed'):
                flat.append({
                    'prog8_type': 'ubyte',
                    'prog8_name': f'emb_{field["name"]}',
                    'offset': offset,
                    'array_size': sz,
                })
            else:
                emb_tag = _find_tag_by_size(sz, all_raw)
                if emb_tag and emb_tag in STRUCT_NAME_MAP:
                    eprefix = STRUCT_NAME_MAP[emb_tag][1]
                    eflat = _flatten(emb_tag, all_raw[emb_tag], all_raw, eprefix, new_visiting, sizes)
                    for ef in eflat:
                        ec = dict(ef)
                        ec['offset'] += offset
                        flat.append(ec)
                else:
                    flat.append({
                        'prog8_type': 'ubyte',
                        'prog8_name': f'emb_{field["name"]}',
                        'offset': offset,
                        'array_size': sz,
                    })
            offset += sz
        else:
            ptype = TYPE_MAP.get(field['type'], 'pointer')
            sz = field['_size']
            if sz > 1:
                if offset & 1:
                    flat.append({
                        'prog8_type': 'ubyte',
                        'prog8_name': f'_pad_{hex(offset)[2:]}',
                        'offset': offset,
                        'origin_tag': tag,
                    })
                    offset += 1
            if field.get('_clang_processed'):
                pname = field['name']
                if field.get('_is_array'):
                    flat.append({
                        'prog8_type': ptype,
                        'prog8_name': pname,
                        'offset': offset,
                        'array_size': field['_size'] // _type_size(ptype),
                        'origin_tag': tag,
                    })
                    offset += field['_size']
                    continue
                if ptype == 'pointer' and _is_clang_string_field(pname):
                    ptype = 'str'
            else:
                pname = _field_name(field['name'], prefix)
                if ptype == 'pointer' and _is_string_field(field['name'], prefix):
                    ptype = 'str'
            flat.append({
                'prog8_type': ptype,
                'prog8_name': pname,
                'offset': offset,
                'origin_tag': tag,
            })
            offset += sz
            for extra in field.get('extra_names', []):
                ename = _field_name(extra, prefix)
                if sz > 1 and offset & 1:
                    flat.append({
                        'prog8_type': 'ubyte',
                        'prog8_name': f'_pad_{hex(offset)[2:]}',
                        'offset': offset,
                        'origin_tag': tag,
                    })
                    offset += 1
                flat.append({
                    'prog8_type': ptype,
                    'prog8_name': ename,
                    'offset': offset,
                    'origin_tag': tag,
                })
                offset += sz
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


def _unique_name(base: str, used: set) -> str:
    """Find a unique name by appending numeric suffixes if needed."""
    if base not in used:
        return base
    suffix = 1
    while f'{base}{suffix}' in used:
        suffix += 1
    return f'{base}{suffix}'


def _type_size(prog8_type: str) -> int:
    return {
        'pointer': 4, 'str': 4,
        'long': 4, 'uword': 2, 'word': 2,
        'ubyte': 1, 'byte': 1, 'bool': 1,
        'float': 4,
    }.get(prog8_type, 4)


# Field name keywords that indicate an APTR is actually a string pointer
_STRING_FIELD_KEYWORDS = frozenset({
    '_TITLE', '_NAME', '_TEXT', '_STRING', '_COMMENT', '_IDSTRING',
    '_MATCHSTRING', '_SCREENTITLE', '_WINDOWTITLE',
})

# PascalCase equivalents for Clang field detection
_CLANG_STRING_SUFFIXES = frozenset({
    'Name', 'Text', 'Title', 'String', 'Comment', 'IdString',
    'MatchString', 'ScreenTitle', 'WindowTitle',
})


def _is_clang_string_field(name: str) -> bool:
    """Check if a PascalCase field name looks like a string pointer."""
    return name in _CLANG_STRING_SUFFIXES or name.rstrip('_') in _CLANG_STRING_SUFFIXES


def _is_string_field(raw_field_name: str, prefix: str) -> bool:
    """Check if a field is likely a string pointer (STRPTR in C)."""
    up = raw_field_name.upper()
    # Strip prefix and check if the remaining name contains any string keywords
    if prefix:
        pu = prefix.upper()
        if up.startswith(pu):
            up = up[len(prefix):]
    for kw in _STRING_FIELD_KEYWORDS:
        # Check exact match or keyword as suffix/prefix of a compound name
        if up == kw.strip('_') or up.endswith('_' + kw.strip('_')):
            return True
    return False


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

def parse_consts_i_files(i_dir: str, extra_dirs: list = None, extra_files: list = None) -> list:
    """Parse .i files for constants with cross-file symbol resolution.

    Collects all raw EQU definitions first, then iteratively resolves
    them using a shared symbol table built from all files in i_dir,
    extra_dirs (scanned for .i files), and extra_files (specific .i paths).
    """
    filepaths = []
    if os.path.isdir(i_dir):
        for fn in sorted(os.listdir(i_dir)):
            if fn.endswith('.i'):
                filepaths.append(os.path.join(i_dir, fn))
    if extra_dirs:
        for d in extra_dirs:
            if os.path.isdir(d):
                for fn in sorted(os.listdir(d)):
                    if fn.endswith('.i'):
                        filepaths.append(os.path.join(d, fn))
    if extra_files:
        for fp in extra_files:
            if os.path.isfile(fp) and fp.endswith('.i'):
                filepaths.append(fp)
    if not filepaths:
        return []

    all_resolved = []
    all_raw_equ = {}

    for fp in filepaths:
        resolved, raw_equ = _collect_consts_one(fp)
        all_resolved.extend(resolved)
        for name, raw_val in raw_equ.items():
            if name not in all_raw_equ:
                all_raw_equ[name] = raw_val

    # Build symbol table from already-resolved constants
    symbol_table = dict(_KNOWN_SYMBOLS)
    for c in all_resolved:
        v = _eval_const(c['value'], symbol_table)
        if v is not None:
            symbol_table[c['name']] = v

    # Iteratively resolve raw EQU defs using the shared symbol table
    resolved_names = set()
    changed = True
    while changed:
        changed = False
        for name in list(all_raw_equ.keys()):
            if name in resolved_names:
                continue
            v = _eval_const(all_raw_equ[name], symbol_table)
            if v is not None:
                all_resolved.append({'name': name, 'value': _fmt(v), 'prog8_type': _ct(v, name)})
                symbol_table[name] = v
                resolved_names.add(name)
                changed = True

    return all_resolved


def _collect_consts_one(filepath: str) -> tuple[list, dict]:
    """Parse a single .i file, return (resolved_consts, raw_equ_defs).

    resolved_consts: list of already-resolved constants (BITDEF, ENUM/EITEM, simple EQU).
    raw_equ_defs: dict of name -> raw_value_string for EQU defs that need cross-file resolution.
    """
    resolved = []
    raw_equ = {}
    enum_counter = None
    with open(filepath, encoding='latin-1') as f:
        for line in f:
            stripped = line.strip()
            if not stripped or stripped.startswith('*') or stripped.startswith(';'):
                continue
            m = re.match(r'BITDEF\s+(\w+)\s*,\s*(\w+)\s*,\s*(\d+)', stripped)
            if m:
                p = m.group(1)
                n = m.group(2)
                b = int(m.group(3))
                resolved.append({
                    'name': f'{p}B_{n}',
                    'value': str(b),
                    'prog8_type': 'ubyte',
                    'comment': f'; BITDEF {p},{n},{b}'
                })
                mask = 1 << b
                mask_type = 'long' if mask > 65535 else 'uword' if mask > 255 else 'ubyte'
                resolved.append({
                    'name': f'{p}F_{n}',
                    'value': _fmt(mask),
                    'prog8_type': mask_type,
                    'comment': f'; BITDEF mask {p},{n},{b}'
                })
                continue
            m = re.match(r'(\w+)\s+EQU\s+(\S+)', stripped)
            if m:
                cname = m.group(1)
                rawv = m.group(2)
                v = _eval_const(rawv)
                if v is not None:
                    resolved.append({'name': cname, 'value': _fmt(v), 'prog8_type': _ct(v, cname)})
                else:
                    raw_equ[cname] = rawv
                continue
            m = re.match(r'ENUM\s+(\S+)', stripped)
            if m:
                rawv = m.group(1)
                v = _eval_const(rawv)
                if v is not None:
                    enum_counter = v
                continue
            m = re.match(r'EITEM\s+(\w+)', stripped)
            if m and enum_counter is not None:
                cname = m.group(1)
                resolved.append({'name': cname, 'value': _fmt(enum_counter), 'prog8_type': _ct(enum_counter, cname)})
                enum_counter += 1
                continue
    return resolved, raw_equ


# Well-known symbols from .i files that aren't defined as EQU
_KNOWN_SYMBOLS = {
    'TAG_USER': 0x80000000,
    'TAG_USER+33': 0x80000021,
}

def _eval_const(raw: str, symbols: dict = None) -> int | None:
    raw = raw.strip()
    # Handle parentheses (must be before symbol lookups, as in:
    #   SELECTDOWN EQU (IECODE_LBUTTON)  -> strip to IECODE_LBUTTON)
    while raw.startswith('(') and raw.endswith(')'):
        raw = raw[1:-1].strip()
    # Check known symbols
    if raw in _KNOWN_SYMBOLS:
        return _KNOWN_SYMBOLS[raw]
    # Check dynamic symbol table
    if symbols is not None and raw in symbols:
        return symbols[raw]
    # Handle simple binary expressions: X + Y, X - Y
    for op in ('+', '-'):
        parts = raw.split(op, 1)
        if len(parts) == 2:
            a = _eval_const(parts[0].strip(), symbols)
            b = _eval_const(parts[1].strip(), symbols)
            if a is not None and b is not None:
                return a + b if op == '+' else a - b
    # Handle bit shift with optional type suffix
    m = re.match(r'(\d+)(?:UL|L|U)?\s*<<\s*(\d+)', raw)
    if m:
        return int(m.group(1)) << int(m.group(2))
    # Handle hex with C-style (0x) or assembler-style ($) prefix
    m = re.match(r'(?:0x|\$)([0-9a-fA-F]+)', raw)
    if m:
        return int(m.group(1), 16)
    m = re.match(r'(-?\d+)', raw)
    if m:
        return int(m.group(1))
    return None


def _fmt(v: int) -> str:
    if v < 0:
        return str(v)
    return f'${v:04x}' if v <= 65535 else f'${v:08x}' if v > 255 else f'${v:02x}'


# Constant prefixes that should always be 'long' (LONG flags fields in Amiga structs)
_LONG_CONST_PREFIXES = ('IDCMP_', 'WFLG_', 'GFLG_', 'GACT_', 'GTYP_', 'GMORE_')
# Constant prefixes that should always be 'uword' (UWORD fields like IntuiMessage.Code/Qualifier)
_UWORD_CONST_PREFIXES = ('IECODE_', 'IECODEB_', 'IECLASS_', 'IESUBCLASS_',
                          'IEQUALIFIER_', 'IEQUALIFIERB_')
# Specific names that must be 'uword' (mouse button codes matching Code field)
_UWORD_CONST_NAMES = {'SELECTDOWN', 'SELECTUP', 'MENUDOWN', 'MENUUP', 'MIDDLEDOWN', 'MIDDLEUP'}

def _ct(v: int, name: str = '') -> str:
    if any(name.startswith(p) for p in _LONG_CONST_PREFIXES):
        return 'long'
    if any(name.startswith(p) for p in _UWORD_CONST_PREFIXES):
        return 'uword'
    if name in _UWORD_CONST_NAMES:
        return 'uword'
    if v < 0:
        return 'long'
    return 'ubyte' if v <= 255 else 'uword' if v <= 65535 else 'long'


# ---------------------------------------------------------------------------
# Prog8 code generation
# ---------------------------------------------------------------------------

def generate_structs(resolved: dict, lib_name: str, indent: str = '    ', header_text: str | None = None,
                     max_size: int = 256) -> str:
    lines = []
    if not resolved:
        return ''
    header = header_text if header_text else f'struct definitions for {lib_name}'
    lines.append(f'\n{indent}; ---- {header} ----')
    for tag in sorted(resolved.keys()):
        lines.append('')
        rs = resolved[tag]
        fields = rs['fields']
        total_size = rs['size']
        stripped = []
        # struct truncation override: strip everything after named field (field itself is kept)
        truncate_after = STRUCT_TRUNCATE_AFTER.get(rs["c_name"])
        if truncate_after:
            for fi, f in enumerate(fields):
                if f['prog8_name'] == truncate_after and f.get('origin_tag', tag) == tag:
                    to_strip = fields[fi+1:]
                    for fs in to_strip:
                        fsz = fs.get('array_size', 1) * _type_size(fs['prog8_type'])
                        total_size -= fsz
                        stripped.append((fs, fsz))
                    del fields[fi+1:]
                    break
        # strip trailing reserved fields if struct exceeds max_size
        if total_size > max_size:
            for fi in range(len(fields) - 1, -1, -1):
                f = fields[fi]
                fsize = f.get('array_size', 1) * _type_size(f['prog8_type'])
                if 'reserved' in f['prog8_name'].lower():
                    total_size -= fsize
                    stripped.append((f, fsize))
                    del fields[fi]
                    if total_size <= max_size:
                        break
            # if still too large, strip opaque embedded structs (ubyte[N]) from the end
            if total_size > max_size:
                for fi in range(len(fields) - 1, -1, -1):
                    f = fields[fi]
                    fsize = f.get('array_size', 1) * _type_size(f['prog8_type'])
                    if f['prog8_type'] == 'ubyte' and f.get('array_size', 1) > 4:
                        total_size -= fsize
                        stripped.append((f, fsize))
                        del fields[fi]
                        if total_size <= max_size:
                            break
        # recompute total_size from remaining fields
        if fields:
            last = max(fields, key=lambda f: f['offset'])
            last_end = last['offset'] + (last.get('array_size', 1) * _type_size(last['prog8_type']))
            total_size = last_end
            if total_size & 1:
                total_size += 1  # word-align
        else:
            total_size = 0
        lines.append(f'{indent}struct {rs["c_name"]} {{  ; total size: {total_size}')
        for f in fields:
            ptype = f['prog8_type']
            pname = f['prog8_name']
            off = f['offset']
            comment = f.get('comment', '')
            arr_size = f.get('array_size')
            if arr_size:
                line = f'{indent}    {ptype}[{arr_size}] {pname}'
            else:
                line = f'{indent}    {ptype} {pname}'
            if comment:
                line += f'  {comment}'
            line += f'  ; {off}'
            lines.append(line)
        if stripped:
            stripped.reverse()
            parts = ', '.join(
                (f'{f["prog8_type"]}[{f["array_size"]}] ' if f.get("array_size") else f'{f["prog8_type"]} ')
                + f'{f["prog8_name"]} ({fz}B)'
                for f, fz in stripped
            )
            lines.append(f'{indent}; stripped: {parts}')
        lines.append(f'{indent}}}')
    return '\n'.join(lines)


def generate_consts(consts: list, lib_name: str, indent: str = '    ', header_text: str | None = None) -> str:
    if not consts:
        return ''
    header = header_text if header_text else f'constants for {lib_name}'
    lines = [f'\n{indent}; ---- {header} ----']
    seen = set()
    for c in consts:
        if c['name'] in seen:
            continue
        seen.add(c['name'])
        line = f'{indent}const {c["prog8_type"]} {c["name"]} = {c["value"]}'
        lines.append(line)
    return '\n'.join(lines)


# ---------------------------------------------------------------------------
# Struct truncation overrides
# ---------------------------------------------------------------------------

# Struct tag -> field name: truncate struct after this field (field itself is kept).
# Fields after the named field are stripped with a comment.
STRUCT_TRUNCATE_AFTER = {
    'Screen': 'emb_RastPort',   # Keep direct fields + ViewPort + RastPort, strip everything after
}

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
    while '  ' in c_type:
        c_type = c_type.replace('  ', ' ')
    is_ptr = '*' in c_type
    c_type = c_type.replace('*', '').strip()
    c_type = re.sub(r'\b(struct|union)\s+', '', c_type).strip()
    if c_type in TYPE_MAP:
        prog8_type = TYPE_MAP[c_type]
        if c_type == 'BOOL':
            prog8_type = 'bool'
        return (prog8_type, c_type)
    return ('pointer', c_type) if is_ptr else ('long', c_type)


def parse_lvo(filepath: str) -> dict:
    lvos = {}
    with open(filepath, encoding='latin-1') as f:
        for line in f:
            m = re.match(r'^_LVO(\w+)\s+equ\s+(-?\d+)', line)
            if m:
                lvos[m.group(1)] = int(m.group(2))
    return lvos


def join_sfd_lines(filepath: str) -> list:
    joined = []
    with open(filepath, encoding='latin-1') as f:
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
        if text[i] == ')':
            close_pos = i
            break
    if close_pos < 0:
        return None
    depth = 0
    open_pos = -1
    for i in range(close_pos, -1, -1):
        if text[i] == ')':
            depth += 1
        elif text[i] == '(':
            depth -= 1
            if depth == 0:
                open_pos = i
                break
    if open_pos < 0:
        return None
    return (open_pos, close_pos, text[open_pos + 1:close_pos].strip())


def parse_sfd_param(param_str: str) -> tuple:
    param_str = param_str.strip()
    if not param_str:
        return ('long', 'arg')
    fp = re.search(r'\(\*(\w+)\)', param_str)
    if fp:
        return ('pointer', fp.group(1))
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
    in_alias = False
    libname = ''
    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith('*'):
            in_alias = False
            continue
        if stripped.startswith('==') or stripped.startswith('##'):
            d = stripped.split()[0]
            if d in ('==alias', '##alias'):
                in_alias = True
                continue
            in_alias = False
            m = re.match(r'==base\s+(\S+)', stripped)
            if m:
                libname = m.group(1)
            continue
        if in_alias:
            in_alias = False
            continue
        info = parse_sfd_func_line(line)
        if info: funcs[info['name']] = info
    funcs['__libname'] = libname
    return funcs


def split_sfd_params(param_str: str) -> list:
    """Split SFD parameters by comma, respecting nested parentheses."""
    parts = []
    depth = 0
    current = []
    for ch in param_str:
        if ch == '(':
            depth += 1
            current.append(ch)
        elif ch == ')':
            depth -= 1
            current.append(ch)
        elif ch == ',' and depth == 0:
            parts.append(''.join(current).strip())
            current = []
        else:
            current.append(ch)
    if current:
        parts.append(''.join(current).strip())
    return [p for p in parts if p]


def parse_sfd_func_line(line: str):
    line = line.strip()
    if not line:
        return None
    rg = extract_last_paren_group(line)
    if rg is None:
        return None
    regs = [r.strip() for r in rg[2].replace('/', ',').split(',') if r.strip()] if rg[2] else []
    sig = line[:rg[0]].strip()
    if not sig:
        return None
    fp = sig.find('(')
    if fp < 0:
        return None
    depth = 0
    pc = -1
    for i in range(fp, len(sig)):
        if sig[i] == '(':
            depth += 1
        elif sig[i] == ')':
            depth -= 1
            if depth == 0:
                pc = i
                break
    if pc < 0:
        return None
    ps = sig[fp+1:pc].strip()
    before = sig[:fp].strip()
    words = before.split()
    if not words:
        return None
    nraw = words[-1]
    name = nraw.lstrip('*')
    stars = len(nraw) - len(name)
    rt = ' '.join(words[:-1]) + (' *' * stars) if stars > 0 else ' '.join(words[:-1])
    is_void = rt.strip() in ('VOID', 'void')
    params = [parse_sfd_param(p) for p in split_sfd_params(ps)] if ps else []
    return {'name': name, 'ret_type': rt, 'params': params, 'regs': regs, 'is_void': is_void}


# ---------------------------------------------------------------------------
# Clang JSON AST based struct parser (replaces .i file parsing)
# ---------------------------------------------------------------------------

# Mapping from C qualType (as emitted by clang) to NDK type tokens
_C_QUAL_TO_NDK = {
    'APTR': ('APTR', 4),
    'CONST_APTR': ('CONST_APTR', 4),
    'BPTR': ('BPTR', 4),
    'CPTR': ('CPTR', 4),
    'STRPTR': ('STRPTR', 4),
    'CONST_STRPTR': ('CONST_STRPTR', 4),
    'PLANEPTR': ('PLANEPTR', 4),
    'DisplayInfoHandle': ('DisplayInfoHandle', 4),
    'BSTR': ('BSTR', 4),
    'UBYTE': ('UBYTE', 1),
    'BYTE': ('BYTE', 1),
    'UWORD': ('UWORD', 2),
    'WORD': ('WORD', 2),
    'ULONG': ('ULONG', 4),
    'LONG': ('LONG', 4),
    'BOOL': ('BOOL', 2),
    'FLOAT': ('FLOAT', 4),
    'DOUBLE': ('DOUBLE', 8),
    'TEXT': ('UBYTE', 1),
    'CONST_TEXT': ('UBYTE', 1),
    'char': ('UBYTE', 1),
    'unsigned char': ('UBYTE', 1),
    'signed char': ('BYTE', 1),
}


def _c_field_type_to_ndk(qual_type: str, typedef_map: dict) -> tuple:
    """Convert a C qualType string to (ndk_type, size_in_bytes)."""
    qt = qual_type.strip()

    # Pointer types: anything with * or function pointer syntax
    if '*' in qt or '(**' in qt or '(*)(' in qt:
        # char * is a string pointer
        if qt.startswith('char ') or qt.startswith('const char '):
            return ('STRPTR', 4)
        if 'char' in qt.replace('*', '').strip():
            return ('STRPTR', 4)
        return ('APTR', 4)

    # Function pointers like void (*)()
    if qt.startswith('void (*') or qt.startswith('void (*'):
        return ('APTR', 4)

    # Embedded structs/unions: struct Name (without *)
    if qt.startswith('struct ') or qt.startswith('union '):
        return ('STRUCT', 0)

    # Arrays: BaseType[N]
    m = re.match(r'(\S+)\[(\d+)\]', qt)
    if m:
        base_qt = m.group(1)
        count = int(m.group(2))
        base_ndk, base_size = _c_field_type_to_ndk(base_qt, typedef_map)
        return (base_ndk, base_size * count)

    # Void is unusual for a field but handle it
    if qt == 'void':
        return ('APTR', 4)

    # Try direct NDK type lookup
    if qt in _C_QUAL_TO_NDK:
        return _C_QUAL_TO_NDK[qt]

    # Resolve typedef chain
    resolved = qt
    seen = set()
    while resolved in typedef_map and resolved not in seen:
        seen.add(resolved)
        resolved = typedef_map[resolved]
        if resolved in _C_QUAL_TO_NDK:
            return _C_QUAL_TO_NDK[resolved]

    # Try cleaning up qualifiers
    cleaned = re.sub(r'\b(const|volatile|unsigned|signed)\b', '', resolved).strip()
    cleaned = re.sub(r'\s+', ' ', cleaned)
    if cleaned in _C_QUAL_TO_NDK:
        return _C_QUAL_TO_NDK[cleaned]

    # Fallback: 4 bytes as APTR
    return ('APTR', 4)


def _c_field_name_to_asm(c_name: str, prefix: str) -> str:
    """Convert a C field name to Prog8 field name.

    Strips the C-style lowercase prefix (e.g. 'ln_', 'lh_', 'tc_')
    and PascalCases the result: first char uppercase, rest lowercase.
    Handles special case where l_pad doesn't match prefix lh_ (falls back to l_).
    """
    if not c_name:
        return 'unknown'

    pu = prefix.upper()
    lower_name = c_name.lower()

    # Try full prefix match first
    c_prefix = prefix.lower().rstrip('_')
    if c_prefix and lower_name.startswith(c_prefix + '_'):
        rest = c_name[len(c_prefix) + 1:]
    elif lower_name.startswith(prefix.lower()):
        rest = c_name[len(prefix):]
    else:
        # Try stripping the first underscore-delimited component
        parts = c_name.split('_', 1)
        if len(parts) == 2:
            rest = parts[1]
        else:
            rest = c_name

    if not rest:
        return 'unknown'

    # Preserve existing mixed case (already PascalCase like RPort, TxHeight)
    # Only PascalCase if all-uppercase (like REPLYPE) or all-lowercase (like replype)
    if rest.isupper() or rest.islower():
        result = rest[0].upper() + rest[1:].lower()
    else:
        result = rest  # Keep existing mixed case

    # Ensure the name doesn't start with a digit (invalid in Prog8)
    if result and result[0].isdigit():
        # Prefix with the first component of the original C name
        prefix_part = c_name.split('_')[0]
        # Keep PascalCase for the prefix part
        prefix_part = prefix_part[0].upper() + prefix_part[1:].lower()
        result = prefix_part + '_' + result

    return result


# 68000 word-alignment rule: align to min(type_size, 2)
_ALIGNMENT = lambda s: min(s, 2)


def _align_offset(offset: int, size: int) -> int:
    align = _ALIGNMENT(size)
    if align > 0 and offset % align != 0:
        offset += align - (offset % align)
    return offset


def _build_typedef_map(ast: dict) -> dict:
    """Build a mapping from typedef name to underlying qualType from the JSON AST."""
    typedef_map = {}

    def walk(node):
        if isinstance(node, dict):
            if node.get('kind') == 'TypedefDecl':
                name = node.get('name')
                qtype = node.get('type', {}).get('qualType')
                if name and qtype and '?' not in qtype and qtype != name:
                    typedef_map[name] = qtype
            for val in node.values():
                if isinstance(val, (dict, list)):
                    walk(val)
        elif isinstance(node, list):
            for item in node:
                walk(item)

    walk(ast)
    return typedef_map


def _find_record_decl(ast: dict, name: str) -> dict | None:
    """Find a RecordDecl (struct definition) by name, preferring the definition over forward declarations."""

    found = []

    def search(node):
        if isinstance(node, dict):
            if node.get('kind') == 'RecordDecl' and node.get('name') == name:
                found.append(node)
            for val in node.values():
                if isinstance(val, (dict, list)):
                    search(val)
        elif isinstance(node, list):
            for item in node:
                search(item)

    search(ast)

    # Prefer definition with fields
    for d in found:
        if d.get('isDefinition') and any(c.get('kind') == 'FieldDecl' for c in d.get('inner', [])):
            return d
    # Then any definition
    for d in found:
        if d.get('isDefinition'):
            return d
    # Then any with fields
    for d in found:
        if any(c.get('kind') == 'FieldDecl' for c in d.get('inner', [])):
            return d
    # Otherwise return first
    return found[0] if found else None


def _get_struct_fields(record_decl: dict) -> list:
    """Extract FieldDecl entries from a RecordDecl."""
    fields = []
    for child in record_decl.get('inner', []):
        if child.get('kind') == 'FieldDecl':
            name = child.get('name', '')
            qtype = child.get('type', {}).get('qualType', '')
            if name and qtype:
                fields.append((name, qtype))
    return fields


def _find_tag_by_c_name(c_name: str) -> str | None:
    """Find the assembly tag that corresponds to a C struct name."""
    for tag, entry in STRUCT_NAME_MAP.items():
        if len(entry) >= 4:
            # Some entries have alternative names
            if entry[0] == c_name or entry[3] == c_name:
                return tag
        elif entry[0] == c_name:
            return tag
    return None


_STRUCT_CACHE: dict = {}


def _compute_ast_struct_size(record_decl: dict, typedef_map: dict, ast: dict) -> int:
    """Compute total size of a struct from its Clang AST RecordDecl."""
    total = 0
    for child in record_decl.get('inner', []):
        if child.get('kind') == 'FieldDecl':
            qtype = child.get('type', {}).get('qualType', '')
            if qtype.startswith('struct ') and '*' not in qtype and '(' not in qtype:
                struct_name = qtype[len('struct '):].strip()
                emb_record = _find_record_decl(ast, struct_name)
                if emb_record and any(c.get('kind') == 'FieldDecl' for c in emb_record.get('inner', [])):
                    sz = _compute_ast_struct_size(emb_record, typedef_map, ast)
                else:
                    sz = 0
            else:
                _, sz = _c_field_type_to_ndk(qtype, typedef_map)
            total = _align_offset(total, sz) + sz
    # Tail padding to 2
    if total % 2 != 0:
        total += 1
    return total


def _add_simple_field(fields_raw: list, field_name: str, qual_type: str,
                      typedef_map: dict, ast: dict, used_names: set,
                      tag: str, prefix: str, offset: int) -> int:
    """Add a regular (non-struct) field to fields_raw, update offset."""
    # Handle array types
    array_match = re.match(r'(\w+(?:\s*\*)?)\s*\[(\d+)\]', qual_type)
    count = 1
    if array_match:
        base_type_str = array_match.group(1)
        count = int(array_match.group(2))
        ndk_type, field_size = _c_field_type_to_ndk(base_type_str, typedef_map)
        field_size *= count
    else:
        ndk_type, field_size = _c_field_type_to_ndk(qual_type, typedef_map)

    # Compute aligned offset
    offset = _align_offset(offset, field_size)

    # Determine field name
    raw_name = _c_field_name_to_asm(field_name, prefix)
    base_name = raw_name

    # Resolve name conflicts by prefixing with C struct name
    if raw_name in used_names:
        cname = STRUCT_NAME_MAP.get(tag, ('',))[0]
        raw_name = cname + '_' + raw_name

    used_names.add(base_name)
    used_names.add(raw_name)

    field_dict = {
        'type': ndk_type,
        'name': raw_name,
        'extra_names': [],
        'comment': '',
        '_offset': offset,
        '_size': field_size,
        '_clang_processed': True,
        '_is_array': bool(array_match and count > 1),
    }
    if ndk_type == 'STRUCT':
        field_dict['size_str'] = str(field_size)
    fields_raw.append(field_dict)
    return offset + field_size


def _inline_struct_fields(tag: str, ast: dict, typedef_map: dict,
                          used_names: set, offset: int) -> tuple[list, int]:
    """Recursively inline fields of a struct, handling embedded structs.

    Returns (fields_raw, new_offset).
    """
    entry = STRUCT_NAME_MAP.get(tag)
    if not entry:
        return [], offset

    c_name = entry[0]
    prefix = entry[1]
    record = _find_record_decl(ast, c_name)
    if record is None:
        return [], offset

    fields_raw = []
    current_offset = offset
    pending_anon_record = None  # anonymous RecordDecl awaiting its FieldDecl

    def _has_prefix(fname: str) -> bool:
        """Check if a field name starts with the struct's prefix (lowercase).
        E.g. mp_Node starts with mp_ prefix, ViewPort does NOT start with sc_ prefix."""
        cp = prefix.lower().rstrip('_')
        return cp and fname.lower().startswith(cp)

    # Iterate over all children; handle FieldDecl and anonymous RecordDecl (inline structs)
    for child in record.get('inner', []):
        kind = child.get('kind')

        if kind == 'RecordDecl' and not child.get('name'):
            # Anonymous inline struct: store for the following FieldDecl
            pending_anon_record = child
            continue

        if kind == 'FieldDecl':
            field_name = child.get('name', '')
            qual_type = child.get('type', {}).get('qualType', '')
            if not field_name or not qual_type:
                continue

            # Handle embedded structs/unions (not pointers) — including anonymous ones
            is_anon_struct = qual_type.startswith('struct (unnamed') or qual_type.startswith('union (unnamed')
            is_named_struct = (qual_type.startswith('struct ') or qual_type.startswith('union ')) and '*' not in qual_type
            if is_named_struct or is_anon_struct:
                if is_anon_struct and pending_anon_record:
                    # Inline the anonymous struct's fields (X, Y) directly into parent
                    for anon_child in pending_anon_record.get('inner', []):
                        if anon_child.get('kind') == 'FieldDecl':
                            fname = anon_child.get('name', '')
                            qtype = anon_child.get('type', {}).get('qualType', '')
                            if not fname or not qtype:
                                continue
                            current_offset = _add_simple_field(
                                fields_raw, fname, qtype, typedef_map, ast,
                                used_names, tag, prefix, current_offset)
                    pending_anon_record = None
                    continue
                if is_named_struct and '(' not in qual_type:
                    struct_name = qual_type[len('struct '):].strip()
                    emb_tag = _find_tag_by_c_name(struct_name)
                    if emb_tag and _has_prefix(field_name):
                        emb_fields, current_offset = _inline_struct_fields(
                            emb_tag, ast, typedef_map, used_names, current_offset)
                        fields_raw.extend(emb_fields)
                        continue

                # Anonymous struct or non-prefix embedded: compute size and create opaque block
                if is_anon_struct:
                    # Find preceding anonymous RecordDecl
                    anon_record = None
                    for sib in record.get('inner', []):
                        if sib.get('kind') == 'RecordDecl' and not sib.get('name'):
                            anon_record = sib
                            break
                    if anon_record and any(c.get('kind') == 'FieldDecl' for c in anon_record.get('inner', [])):
                        struct_size = _compute_ast_struct_size(anon_record, typedef_map, ast)
                    else:
                        struct_size = 0
                else:
                    emb_record = _find_record_decl(ast, struct_name)
                    if emb_record and any(c.get('kind') == 'FieldDecl' for c in emb_record.get('inner', [])):
                        struct_size = _compute_ast_struct_size(emb_record, typedef_map, ast)
                    else:
                        struct_size = 0

                # Create opaque ubyte[N] field for the embedded struct
                current_offset = _align_offset(current_offset, struct_size)
                raw_name = _c_field_name_to_asm(field_name, prefix)
                base_name = raw_name
                if raw_name in used_names:
                    cname = STRUCT_NAME_MAP.get(tag, ('',))[0]
                    raw_name = cname + '_' + raw_name
                used_names.add(base_name)
                used_names.add(raw_name)
                fields_raw.append({
                    'type': 'STRUCT',
                    'name': raw_name,
                    'extra_names': [],
                    'comment': '',
                    '_offset': current_offset,
                    '_size': struct_size,
                    '_clang_processed': True,
                    'size_str': str(struct_size),
                })
                current_offset += struct_size
                continue

            # Regular field (not struct or union)
            current_offset = _add_simple_field(fields_raw, field_name, qual_type, typedef_map, ast, used_names, tag, prefix, current_offset)

    return fields_raw, current_offset


def parse_structs_from_clang(ndk_path: str, lib_name: str) -> dict:
    """Parse struct definitions from C headers via clang JSON AST dump.

    Returns dict in the same raw_structs format as parse_struct_i_files().
    Falls back to .i file parsing if clang is unavailable or fails.
    """
    inc_path = os.path.join(ndk_path, 'Include_H')

    # Build the C source to feed to clang
    c_lines = ['#include <exec/exec.h>']

    lib_headers = {
        'exec': [],
        'dos': ['dos/dosextens.h', 'dos/exall.h'],
        'graphics': ['graphics/gfx.h', 'graphics/rastport.h', 'graphics/clip.h',
                     'graphics/view.h', 'graphics/text.h', 'graphics/layers.h',
                     'graphics/videocontrol.h'],
        'intuition': ['intuition/intuition.h',
                      'graphics/gfx.h', 'graphics/view.h', 'graphics/rastport.h',
                      'graphics/clip.h', 'graphics/text.h', 'graphics/layers.h'],
    }

    # Which struct tags belong to which library (to avoid cross-library duplication)
    lib_struct_tags = {
        'exec': {'LN', 'MLN', 'LH', 'MLH', 'MP', 'MN', 'IO', 'IS', 'LIB', 'TC_Struct'},
        'dos': {'FileHandle', 'FileLock', 'Process', 'DosPacket', 'StandardPacket',
                'DateStamp', 'FileInfoBlock', 'InfoData', 'ErrorString', 'ExAllData', 'ExAllControl'},
        'graphics': {'RastPort', 'Layer', 'ClipRect', 'BitMap', 'View', 'TextFont',
                     'TextAttr', 'ColorMap', 'TmpRas', 'AreaInfo', 'TextExtent'},
        'intuition': {'Window', 'Screen', 'IntuiMessage', 'Gadget', 'Image', 'Border',
                      'IntuiText', 'MenuItem', 'Requester', 'NewScreen', 'NewWindow',
                      'IBox', 'EasyStruct', 'ExtGadget', 'ExtIntuiMessage', 'DrawInfo',
                      'ColorSpec', 'Menu'},
    }
    for hdr in lib_headers.get(lib_name, []):
        c_lines.append(f'#include <{hdr}>')

    c_source = '\n'.join(c_lines)

    # Write temp C file and run clang
    try:
        with tempfile.NamedTemporaryFile(suffix='.c', mode='w', delete=False) as f:
            f.write(c_source)
            c_file = f.name

        result = subprocess.run(
            ['clang', '-Xclang', '-ast-dump=json', '-fsyntax-only',
             f'-I{inc_path}', c_file],
            capture_output=True, text=True, timeout=30)

        if result.returncode != 0:
            print(f"Warning: clang error: {result.stderr}", file=sys.stderr)
            return None

        ast = json.loads(result.stdout)
    except (subprocess.TimeoutExpired, FileNotFoundError, json.JSONDecodeError,
            PermissionError, OSError) as e:
        print(f"Warning: clang AST dump failed: {e}", file=sys.stderr)
        return None
    finally:
        try:
            os.unlink(c_file)
        except OSError:
            pass

    # Build typedef map
    typedef_map = _build_typedef_map(ast)

    # Parse all structs in STRUCT_NAME_MAP that exist in the AST
    allowed_tags = lib_struct_tags.get(lib_name, set())
    structs = {}
    for tag in STRUCT_NAME_MAP:
        # Only include structs that belong to this library
        if tag not in allowed_tags:
            continue
        entry = STRUCT_NAME_MAP[tag]
        c_name = entry[0]
        prefix = entry[1]

        record = _find_record_decl(ast, c_name)
        if record is None:
            continue

        # Check struct size from AST if available
        if record.get('type', {}).get('qualType'):
            t = record.get('type', {})
            size_info = t.get('size', 0)
            # Not all AST versions provide size in RecordDecl
        used_names = set()
        fields_raw, total_size = _inline_struct_fields(tag, ast, typedef_map,
                                                       used_names, 0)

        # Tail padding: round total size to highest alignment
        max_align = 2
        if total_size % max_align != 0:
            total_size += max_align - (total_size % max_align)

        structs[tag] = {
            'tag': tag,
            'base_offset_str': '0',
            'fields_raw': fields_raw,
            'total_size': total_size,
            'labels': [],
        }

    if not structs:
        print("Warning: no structs found via clang AST dump", file=sys.stderr)
        return None

    return structs


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
        print_mapping(None)
        return
    if not args.ndk_path or not args.lib_name:
        ap.print_help()
        sys.exit(1)

    ndk = args.ndk_path.rstrip('/')
    lib = args.lib_name

    # Parse struct definitions - prefer clang JSON AST over .i files
    all_raw = {}
    inc = os.path.join(ndk, 'Include_I', lib)
    exec_inc = os.path.join(ndk, 'Include_I', 'exec')
    if args.structs or args.consts:
        clang_structs = parse_structs_from_clang(ndk, lib)
        if clang_structs is not None:
            all_raw = clang_structs
        else:
            # Fallback to .i file parsing
            if os.path.isdir(exec_inc):
                all_raw.update(parse_struct_i_files(exec_inc))
            if os.path.isdir(inc):
                lib_structs = parse_struct_i_files(inc)
                for tag in lib_structs:
                    if tag in all_raw:
                        print(f"Warning: struct '{tag}' from exec overwritten by {lib}", file=sys.stderr)
                all_raw.update(lib_structs)
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
    if bank:
        print(f";; Bank: {bank}")
    print(f";; Functions: {len(lvos)}\n")

    print(f"{lib} {{")

    # extsub definitions
    for name, lvo in sorted(lvos.items(), key=lambda x: x[1], reverse=True):
        info = sfd_funcs.get(name)
        pi = (info or {}).get('params', [])
        regs = (info or {}).get('regs', [])
        iv = (info or {}).get('is_void', True)
        rt = (info or {}).get('ret_type')
        pp = []
        idx = 0
        for r in regs:
            if idx < len(pi):
                pt, pn = pi[idx]
                idx += 1
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
        print(generate_structs(resolved, lib, indent='    ', header_text='struct definitions'))

    # constants inside the block
    if args.consts:
        consts = []
        extra_files = []
        if lib == 'intuition':
            ie_file = os.path.join(ndk, 'Include_I', 'devices', 'inputevent.i')
            if os.path.isfile(ie_file):
                extra_files.append(ie_file)
        if os.path.isdir(inc):
            consts.extend(parse_consts_i_files(inc, extra_files=extra_files))
        print(generate_consts(consts, lib, indent='    ', header_text='constants'))

    print(f"}}")
    print(f";; End of auto-generated {lib}_lib.sfd")
    if args.mapping:
        print_mapping(lib)


def _tags_in_dir(d: str) -> set:
    tags = set()
    for fn in os.listdir(d):
        if not fn.endswith('.i'):
            continue
        with open(os.path.join(d, fn), encoding='latin-1') as f:
            for line in f:
                m = re.match(r'STRUCTURE\s+(\w+)\s*,', line)
                if m:
                    tags.add(m.group(1))
    return tags


def print_mapping(cl):
    print(file=sys.stderr)
    for lib, bn in sorted(LIBRARY_BANK_MAP.items(), key=lambda x: x[1]):
        m = "  <--" if lib == cl else ""
        print(f"  bank {bn:3d} = {lib}{m}", file=sys.stderr)


if __name__ == '__main__':
    main()
