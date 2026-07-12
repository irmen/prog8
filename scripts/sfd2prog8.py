#!/usr/bin/env python3
"""
Convert Amiga NDK LVO and SFD files to Prog8 extsub definitions.

Usage:  python sfd2prog8.py /path/to/AmigaNDK_headers libraryname  >  output.p8

Parses the LVO file (Include_I/lvo/<lib>_lib.i) for exact LVO offsets,
and the SFD file (SFD/<lib>_lib.sfd) for function parameter types and register assignments.

Examples:
  python sfd2prog8.py /path/to/AmigaNDK_headers exec   >  exec.p8
  python sfd2prog8.py /path/to/AmigaNDK_headers dos    >  dos.p8
"""

import sys
import re
import os
import argparse


# Map Amiga system types to Prog8 types
TYPE_MAP = {
    'VOID': None,
    'void': None,
    'LONG': 'long',
    'ULONG': 'long',
    'WORD': 'word',
    'UWORD': 'uword',
    'BYTE': 'byte',
    'UBYTE': 'ubyte',
    'BOOL': 'bool',
    'FLOAT': 'float',
    'DOUBLE': 'float',
    'APTR': 'pointer',
    'CONST_APTR': 'pointer',
    'CPTR': 'pointer',
    'BPTR': 'pointer',
    'STRPTR': 'str',
    'CONST_STRPTR': 'str',
    'TEXT': 'str',
    'CONST_TEXT': 'str',
    'BSTR': 'str',
    'PLANEPTR': 'pointer',
    'DisplayInfoHandle': 'pointer',
}

# Amiga library to Prog8 bank number mapping
# These are arbitrary assignments, starting with the most common libraries.
LIBRARY_BANK_MAP = {
    'exec': 1,
    'dos': 2,
    'graphics': 3,
    'intuition': 4,
    'gadtools': 5,
    'layers': 6,
    'asl': 7,
    'console': 8,
    'utility': 9,
    'expansion': 10,
    'icon': 11,
    'wb': 12,
    'diskfont': 13,
    'iffparse': 14,
    'input': 15,
    'keymap': 16,
    'locale': 17,
    'timer': 18,
    'lowlevel': 19,
    'mathffp': 20,
    'mathieeesingbas': 21,
    'mathieeesingtrans': 22,
    'mathieeedoubbas': 23,
    'mathieeedoubtrans': 24,
    'mathtrans': 25,
    'nonvolatile': 26,
    'realtime': 27,
    'translator': 28,
    'rexxsyslib': 29,
    'commodities': 30,
    'datatypes': 31,
    'disk': 32,
    'amigaguide': 33,
    'arexx': 34,
    'battclock': 35,
    'battmem': 36,
    'bevel': 37,
    'bitmap': 38,
    'bullet': 39,
    'button': 40,
    'cardres': 41,
    'checkbox': 42,
    'chooser': 43,
    'cia': 44,
    'clicktab': 45,
    'colorwheel': 46,
    'datebrowser': 47,
    'drawlist': 48,
    'dtclass': 49,
    'fuelgauge': 50,
    'getcolor': 51,
    'getfile': 52,
    'getfont': 53,
    'getscreenmode': 54,
    'glyph': 55,
    'integer': 56,
    'label': 57,
    'layout': 58,
    'listbrowser': 59,
    'misc': 60,
    'palette': 61,
    'penmap': 62,
    'potgo': 63,
    'radiobutton': 64,
    'ramdrive': 65,
    'requester': 66,
    'scroller': 67,
    'sketchboard': 68,
    'slider': 69,
    'space': 70,
    'speedbar': 71,
    'string': 72,
    'texteditor': 73,
    'trackfile': 74,
    'virtual': 75,
    'window': 76,
}

# Keywords reserved in Prog8 that can't be used as parameter names
PROG8_KEYWORDS = {
    'str', 'end', 'type', 'class', 'for', 'while', 'repeat', 'if', 'else',
    'do', 'in', 'to', 'not', 'and', 'or', 'xor', 'as', 'is', 'sub',
    'return', 'break', 'continue', 'void', 'true', 'false', 'pointer',
    'memory', 'inline', 'private', 'step', 'alias', 'const', 'enum',
    'struct', 'then', 'until', 'downto', 'when', 'goto', 'defer', 'swap',
}


def parse_c_type(c_type: str) -> tuple:
    """Parse a C parameter type and return (prog8_type, base_type_name)."""
    c_type = c_type.strip()

    # Strip common C qualifiers
    c_type = re.sub(r'\b(const|volatile|register|unsigned|signed)\b', '', c_type).strip()
    while '  ' in c_type:
        c_type = c_type.replace('  ', ' ')

    # Handle pointer types
    is_ptr = '*' in c_type
    c_type = c_type.replace('*', '').strip()

    # Handle struct/union tags
    c_type = re.sub(r'\b(struct|union)\s+', '', c_type).strip()

    # Look up in type map, default based on pointer type
    if c_type in TYPE_MAP:
        return (TYPE_MAP[c_type], c_type)

    # Unknown types are likely struct pointers or other pointer types
    if is_ptr:
        return ('pointer', c_type)

    # Unknown types default to long (32-bit is standard on Amiga)
    return ('long', c_type)


def parse_lvo(filepath: str) -> dict:
    """Parse an LVO .i file to get exact LVO offsets for public functions.
    Returns dict: name -> lvo_offset"""
    lvos = {}
    with open(filepath) as f:
        for line in f:
            m = re.match(r'^_LVO(\w+)\s+equ\s+(-?\d+)', line)
            if m:
                name = m.group(1)
                offset = int(m.group(2))
                lvos[name] = offset
    return lvos


def join_sfd_lines(filepath: str) -> list:
    """Join continuation lines in an SFD file.
    Lines starting with whitespace are continuations of the previous line."""
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
    """Find the last outermost balanced parenthesized group in text.
    Returns (open_pos, close_pos, content) or None."""
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
    """Parse a single SFD parameter string into (prog8_type, name)."""
    param_str = param_str.strip()
    if not param_str:
        return ('long', 'arg')

    # Handle function pointer params: TYPE (*name)(...)
    fp_match = re.search(r'\(\*(\w+)\)', param_str)
    if fp_match:
        name = fp_match.group(1)
        return ('pointer', name)

    # Normal param: split on last whitespace
    parts = param_str.rsplit(None, 1)
    if len(parts) == 2:
        ptype_raw, pname = parts
    else:
        ptype_raw = parts[0]
        pname = 'arg'

    # Handle leading * in name (pointer param)
    while pname.startswith('*'):
        ptype_raw += ' *'
        pname = pname[1:]

    if not pname:
        return ('long', 'arg')

    prog8_type = parse_c_type(ptype_raw)[0]
    if prog8_type is None:
        prog8_type = 'long'
    if pname in PROG8_KEYWORDS:
        pname = f'k_{pname}'

    return (prog8_type, pname)


def parse_sfd(filepath: str) -> dict:
    """Parse an SFD file for function type information.
    Returns dict: name -> {ret_type, params: [(type, name)], regs: [str]}"""
    lines = join_sfd_lines(filepath)
    funcs = {}
    in_alias = False
    libname = ""

    for line in lines:
        stripped = line.strip()

        if not stripped or stripped.startswith('*'):
            in_alias = False
            continue

        if stripped.startswith('==') or stripped.startswith('##'):
            directive = stripped.split()[0] if stripped.split() else ''
            if directive in ('==alias', '##alias'):
                in_alias = True
                continue
            in_alias = False

            m = re.match(r'==base\s+(\S+)', stripped)
            if m:
                libname = m.group(1)
            continue

        if in_alias:
            in_alias = False  # Only skip the single alias function after ==alias
            continue

        # Try to parse as function definition
        info = parse_sfd_func_line(line)
        if info:
            funcs[info['name']] = info

    # Store libname so caller can access it
    funcs['__libname'] = libname
    return funcs


def parse_sfd_func_line(line: str):
    """Parse a single (joined) SFD function definition line.
    Returns dict with name, ret_type, params, regs, or None."""
    line = line.strip()
    if not line:
        return None

    # Find the register group (last outermost parenthesized group)
    reg_group = extract_last_paren_group(line)
    if reg_group is None:
        return None

    reg_str = reg_group[2]
    if not reg_str:
        regs = []
    else:
        regs = [r.strip() for r in reg_str.replace('/', ',').split(',') if r.strip()]

    # Signature is everything before the register group
    sig = line[:reg_group[0]].strip()
    if not sig:
        return None

    # Now parse the signature: return_type name(params)
    first_paren = sig.find('(')
    if first_paren < 0:
        return None

    # Find matching closing paren for params
    depth = 0
    params_close = -1
    for i in range(first_paren, len(sig)):
        if sig[i] == '(':
            depth += 1
        elif sig[i] == ')':
            depth -= 1
            if depth == 0:
                params_close = i
                break

    if params_close < 0:
        return None

    params_str = sig[first_paren + 1:params_close].strip()
    before = sig[:first_paren].strip()

    # Extract function name and return type
    words = before.split()
    if not words:
        return None

    name_raw = words[-1]
    name = name_raw.lstrip('*')
    stars = len(name_raw) - len(name)

    ret_type_parts = words[:-1]
    if stars > 0:
        ret_type_parts.append('*' * stars)
    ret_type = ' '.join(ret_type_parts).strip() if ret_type_parts else None
    if not ret_type:
        ret_type = None
    # Determine if void
    is_void = ret_type and ret_type.strip() in ('VOID', 'void')

    # Parse parameters
    params = []
    if params_str:
        for p in params_str.split(','):
            p = p.strip()
            if not p:
                continue
            params.append(parse_sfd_param(p))

    return {
        'name': name,
        'ret_type': ret_type,
        'params': params,
        'regs': regs,
        'is_void': is_void,
    }


def main():
    parser = argparse.ArgumentParser(
        description="Convert Amiga NDK LVO and SFD files to Prog8 extsub definitions.")
    parser.add_argument('ndk_path', nargs='?', help="Path to AmigaNDK_headers directory")
    parser.add_argument('library_name', nargs='?', help="Library name (e.g. exec, dos, graphics)")
    parser.add_argument('-m', '--mapping', action='store_true',
                        help="Print library bank mapping table to stderr")
    args = parser.parse_args()

    if args.mapping and not args.library_name:
        print_mapping(None)
        return

    if not args.ndk_path or not args.library_name:
        parser.print_help()
        sys.exit(1)

    show_mapping = args.mapping
    ndk_path = args.ndk_path.rstrip('/')
    lib_name = args.library_name

    lvo_path = os.path.join(ndk_path, 'Include_I', 'lvo', f'{lib_name}_lib.i')
    sfd_path = os.path.join(ndk_path, 'SFD', f'{lib_name}_lib.sfd')

    if not os.path.exists(lvo_path):
        print(f"Error: LVO file not found: {lvo_path}")
        sys.exit(1)
    if not os.path.exists(sfd_path):
        print(f"Error: SFD file not found: {sfd_path}")
        sys.exit(1)

    lvos = parse_lvo(lvo_path)
    sfd_funcs = parse_sfd(sfd_path)

    libname = sfd_funcs.pop('__libname', '')
    basename = lib_name

    if lib_name in LIBRARY_BANK_MAP:
        bank = LIBRARY_BANK_MAP[lib_name]
    else:
        print(f"Warning: unknown library '{lib_name}', no bank assigned. Add it to LIBRARY_BANK_MAP.", file=sys.stderr)
        bank = None

    bank_tag = f"@bank {bank} " if bank is not None else ""

    print(f";; Auto-generated from {lib_name}_lib.sfd and {lib_name}_lib.i")
    if libname:
        prog8libname = f"sys.{libname[1:]}"
        print(f";; Library base: {libname}  in prog8: {prog8libname}")
    if bank is not None:
        print(f";; Bank: {bank}")
    print(f";; Functions: {len(lvos)}")
    print()

    print(f"{basename} {{")

    for name, lvo in sorted(lvos.items(), key=lambda x: x[1], reverse=True):
        # LVO offsets are negative, reverse sort gives most negative first
        info = sfd_funcs.get(name)

        if info:
            params = info['params']
            regs = info['regs']
            ret_type = info['ret_type']
            is_void = info['is_void']
        else:
            params = []
            regs = []
            ret_type = None
            is_void = True

        # Build parameter list with register annotations
        prog8_params = []
        param_idx = 0
        for reg in regs:
            if param_idx < len(params):
                ptype, pname = params[param_idx]
                param_idx += 1
            else:
                ptype = 'long'
                pname = f'reg_{reg.lower()}'
            prog8_params.append(f"{ptype} {pname} @{reg.upper()}")

        param_str = ", ".join(prog8_params)

        # Map return type to Prog8 type
        if not is_void:
            prog8_ret, _ = parse_c_type(ret_type)
            if prog8_ret is None:
                prog8_ret = 'long'
            ret_suffix = f" -> {prog8_ret} @D0"
        else:
            ret_suffix = ""
        print(f"    extsub {bank_tag}  {lvo} = {name}({param_str}){ret_suffix}")

    print(f"}}")
    print()
    print(f";; End of auto-generated {lib_name}_lib.sfd")

    if show_mapping:
        print_mapping(lib_name)


def print_mapping(current_lib):
    """Print the library bank mapping table to stderr."""
    print(file=sys.stderr)
    print("Amiga library bank mapping:", file=sys.stderr)
    print("==========================", file=sys.stderr)
    for lib, bn in sorted(LIBRARY_BANK_MAP.items(), key=lambda x: x[1]):
        marker = "  <--" if lib == current_lib else ""
        print(f"  bank {bn:3d} = {lib}{marker}", file=sys.stderr)


if __name__ == '__main__':
    main()
