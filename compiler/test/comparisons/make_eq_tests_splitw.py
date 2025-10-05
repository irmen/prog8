# generates various Prog8 files with a large amount of number equality tests,
# using various forms of the if statement (because these forms have their own code gen paths)

import sys

fail_index = 0


def header(dt):
    print(f"""
%import textio
%import floats
%import test_stack
%zeropage dontuse
%option no_sysinit

main {{
    ubyte success = 0
    str datatype = "{dt}"
    uword @shared comparison

    sub start() {{
        txt.print("\\n(in)equality tests for split words datatype: ")
        txt.print(datatype)
        txt.nl()
        test_stack.test()
        txt.print("==0: ")
        test_is_zero()
        txt.print("\\n!=0: ")
        test_not_zero()
        txt.print("\\n==number: ")
        test_is_number()
        txt.print("\\n!=number: ")
        test_not_number()
        txt.print("\\n==var: ")
        test_is_var()
        txt.print("\\n!=var: ")
        test_not_var()
        txt.print("\\n==array[] split: ")
        test_is_array_splitw()
        txt.print("\\n!=array[] split: ")
        test_not_array_splitw()
        txt.print("\\n==expr: ")
        test_is_expr()
        txt.print("\\n!=expr: ")
        test_not_expr()
        test_stack.test()
    }}
    
    sub verify_success(ubyte expected) {{
        if success==expected {{
            txt.print("ok")
        }} else {{
            txt.print(" **failed** ")
            txt.print_ub(success)
            txt.print(" success, expected ")
            txt.print_ub(expected)
        }}
    }}
    
    sub fail_word(uword idx, word v1) {{
        txt.print(" **fail#")
        txt.print_uw(idx)
        txt.chrout(':')
        txt.print_w(v1)
        txt.print(" **")
    }}

    sub fail_uword(uword idx, uword v1) {{
        txt.print(" **fail#")
        txt.print_uw(idx)
        txt.chrout(':')
        txt.print_uw(v1)
        txt.print(" **")
    }}
    
""")


zero_values = {
    "byte": 0,
    "ubyte": 0,
    "word": 0,
    "uword": 0,
    "float": 0.0
}

nonzero_values = {
    "byte": -100,
    "ubyte": 100,
    "word": -9999,
    "uword": 9999,
    "float": 1234.56
}


def make_test_is_zero(datatype):
    print(f"""
    sub test_is_zero() {{
        {datatype}[] sources = [9999, 9999]
        success = 0

        sources[1]={zero_values[datatype]}
        ; direct jump
        if sources[1]==0
            goto lbl1
        goto skip1
lbl1:   success++
skip1:
        ; indirect jump
        cx16.r3 = &lbl2
        if sources[1]==0
            goto cx16.r3
        goto skip2
lbl2:   success++
skip2:
        ; no else
        if sources[1]==0
            success++

        ; with else
        if sources[1]==0
            success++
        else
            cx16.r0L++     
            
        sources[1] = {nonzero_values[datatype]}
        ; direct jump
        if sources[1]==0
            goto skip3
        success++
skip3:
        ; indirect jump
        cx16.r3 = &skip4
        if sources[1]==0
            goto cx16.r3
        success++
skip4:
        ; no else
        success++
        if sources[1]==0
            success--

        ; with else
        if sources[1]==0
            cx16.r0L++                      
        else
            success++

        verify_success(8)
    }}
""")


def make_test_not_zero(datatype):
    print(f"""
    sub test_not_zero() {{
        {datatype}[] sources = [9999, 9999]
        success = 0

        sources[1]={nonzero_values[datatype]}
        ; direct jump
        if sources[1]!=0
            goto lbl1
        goto skip1
lbl1:   success++
skip1:
        ; indirect jump
        cx16.r3 = &lbl2
        if sources[1]!=0
            goto cx16.r3
        goto skip2
lbl2:   success++
skip2:
        ; no else
        if sources[1]!=0
            success++

        ; with else
        if sources[1]!=0
            success++
        else
            cx16.r0L++     
            
        sources[1] = {zero_values[datatype]}
        ; direct jump
        if sources[1]!=0
            goto skip3
        success++
skip3:
        ; indirect jump
        cx16.r3 = &skip4
        if sources[1]!=0
            goto cx16.r3
        success++
skip4:
        ; no else
        success++
        if sources[1]!=0
            success--

        ; with else
        if sources[1]!=0
            cx16.r0L++                      
        else
            success++

        verify_success(8)
    }}
""")


def tc(value):
    if value < 0x8000:
        return value
    else:
        return -(65536 - value)


testnumbers = {
    "byte": [-100, 0, 100],
    "ubyte": [0, 1, 255],
    "word": [tc(0xaabb), 0, 0x00aa, 0x7700, 0x7fff],
    "uword": [0, 1, 0x7700, 0xffff],
    "float": [0.0, 1234.56]
}


def make_test_is_number(datatype, equals):
    numbers = testnumbers[datatype]
    print("    sub test_is_number() {" if equals else "    sub test_not_number() {")
    print(f"""    {datatype}[] sources = [9999, 9999]
        success = 0""")
    expected = 0
    test_index = 0
    global fail_index
    for x in numbers:
        print(f"    sources[1]={x}")
        for value in numbers:
            if value == 0:
                continue  # 0 already tested separately
            result = (x == value) if equals else (x != value)
            comp = "==" if equals else "!="
            test_index += 1
            if result:
                expected += 4  # there are 4 test types for every successful combo
                true_action1 = "success++"
                true_action2 = "success++"
                true_action3 = "success++"
                true_action4 = "success++"
            else:
                fail_index += 1
                true_action1 = f"fail_{datatype}({fail_index},{x})"
                fail_index += 1
                true_action2 = f"fail_{datatype}({fail_index},{x})"
                fail_index += 1
                true_action3 = f"fail_{datatype}({fail_index},{x})"
                fail_index += 1
                true_action4 = f"fail_{datatype}({fail_index},{x})"
            print(f"""    ; direct jump
        if sources[1]{comp}{value}
            goto lbl{test_index}a
        goto skip{test_index}a
lbl{test_index}a:   {true_action1}
skip{test_index}a:
        ; indirect jump
        cx16.r3 = &lbl{test_index}b
        if sources[1]{comp}{value}
            goto cx16.r3
        goto skip{test_index}b
lbl{test_index}b:   {true_action2}
skip{test_index}b:
        ; no else
        if sources[1]{comp}{value}
            {true_action3}

        ; with else
        if sources[1]{comp}{value}
            {true_action4}
        else
            cx16.r0L++
""")
    print(f"    verify_success({expected})\n}}")


def make_test_is_var(datatype, equals):
    numbers = testnumbers[datatype]
    print("    sub test_is_var() {" if equals else "    sub test_not_var() {")
    print(f"""    {datatype}[] sources = [9999, 9999]
        {datatype}[] values = [8888,8888]
        success = 0""")
    expected = 0
    test_index = 0
    global fail_index
    for x in numbers:
        print(f"    sources[1]={x}")
        for value in numbers:
            if value == 0:
                continue  # 0 already tested separately
            print(f"    values[1]={value}")
            result = (x == value) if equals else (x != value)
            comp = "==" if equals else "!="
            test_index += 1
            if result:
                expected += 4  # there are 4 test types for every successful combo
                true_action1 = "success++"
                true_action2 = "success++"
                true_action3 = "success++"
                true_action4 = "success++"
            else:
                fail_index += 1
                true_action1 = f"fail_{datatype}({fail_index},{x})"
                fail_index += 1
                true_action2 = f"fail_{datatype}({fail_index},{x})"
                fail_index += 1
                true_action3 = f"fail_{datatype}({fail_index},{x})"
                fail_index += 1
                true_action4 = f"fail_{datatype}({fail_index},{x})"
            print(f"""    ; direct jump
        if sources[1]{comp}values[1]
            goto lbl{test_index}a
        goto skip{test_index}a
lbl{test_index}a:   {true_action1}
skip{test_index}a:
        ; indirect jump
        cx16.r3 = &lbl{test_index}b
        if sources[1]{comp}values[1]
            goto cx16.r3
        goto skip{test_index}b
lbl{test_index}b:   {true_action2}
skip{test_index}b:
        ; no else
        if sources[1]{comp}values[1]
            {true_action3}

        ; with else
        if sources[1]{comp}values[1]
            {true_action4}
        else
            cx16.r0L++
""")
    print(f"    verify_success({expected})\n}}")


def make_test_is_array(datatype, equals):
    numbers = testnumbers[datatype]
    print("    sub test_is_array_splitw() {" if equals else "    sub test_not_array_splitw() {")
    print(f"""    
        {datatype}[] values = [9999, 8888]
        {datatype}[] sources = [9999, 8888]
        success = 0""")
    expected = 0
    test_index = 0
    global fail_index
    for x in numbers:
        print(f"    values[1]={x}")
        print(f"    sources[1]={x}")
        for value in numbers:
            if value == 0:
                continue  # 0 already tested separately
            print(f"    values[1]={value}")
            result = (x == value) if equals else (x != value)
            comp = "==" if equals else "!="
            test_index += 1
            if result:
                expected += 8  # there are 8 test types for every successful combo
                true_action1 = "success++"
                true_action2 = "success++"
                true_action3 = "success++"
                true_action4 = "success++"
                true_action5 = "success++"
                true_action6 = "success++"
                true_action7 = "success++"
                true_action8 = "success++"
            else:
                fail_index += 1
                true_action1 = f"fail_{datatype}({fail_index},{x})"
                fail_index += 1
                true_action2 = f"fail_{datatype}({fail_index},{x})"
                fail_index += 1
                true_action3 = f"fail_{datatype}({fail_index},{x})"
                fail_index += 1
                true_action4 = f"fail_{datatype}({fail_index},{x})"
                fail_index += 1
                true_action5 = f"fail_{datatype}({fail_index},{x})"
                fail_index += 1
                true_action6 = f"fail_{datatype}({fail_index},{x})"
                fail_index += 1
                true_action7 = f"fail_{datatype}({fail_index},{x})"
                fail_index += 1
                true_action8 = f"fail_{datatype}({fail_index},{x})"
            print(f"""    ; direct jump
        if sources[1]{comp}values[1]
            goto lbl{test_index}a
        goto skip{test_index}a
lbl{test_index}a:   {true_action1}
skip{test_index}a:
        ; indirect jump
        cx16.r3 = &lbl{test_index}b
        if sources[1]{comp}values[1]
            goto cx16.r3
        goto skip{test_index}b
lbl{test_index}b:   {true_action2}
skip{test_index}b:
        ; no else
        if sources[1]{comp}values[1]
            {true_action3}

        ; with else
        if sources[1]{comp}values[1]
            {true_action4}
        else
            cx16.r0L++
""")
            print(f"""    ; direct jump
        if sources[1]{comp}values[1]
            goto lbl{test_index}c
        goto skip{test_index}c
lbl{test_index}c:   {true_action5}
skip{test_index}c:
        ; indirect jump
        cx16.r3 = &lbl{test_index}d
        if sources[1]{comp}values[1]
            goto cx16.r3
        goto skip{test_index}d
lbl{test_index}d:   {true_action6}
skip{test_index}d:
        ; no else
        if sources[1]{comp}values[1]
            {true_action7}

        ; with else
        if sources[1]{comp}values[1]
            {true_action8}
        else
            cx16.r0L++
""")
    print(f"    verify_success({expected})\n}}")


def make_test_is_expr(datatype, equals):
    numbers = testnumbers[datatype]
    print("    sub test_is_expr() {" if equals else "    sub test_not_expr() {")
    print(f"""    {datatype}[] sources = [9999, 9999]
        cx16.r4 = 1
        cx16.r5 = 1
        success = 0""")
    expected = 0
    test_index = 0
    global fail_index
    for x in numbers:
        print(f"    sources[1]={x}")
        for value in numbers:
            if value == 0:
                continue  # 0 already tested separately
            if datatype=="byte":
                expr = f"cx16.r4sL+{value}-cx16.r5sL"
            elif datatype=="ubyte":
                expr = f"cx16.r4L+{value}-cx16.r5L"
            elif datatype=="word":
                expr = f"cx16.r4s+{value}-cx16.r5s"
            elif datatype=="uword":
                expr = f"cx16.r4+{value}-cx16.r5"
            elif datatype=="float":
                expr = f"f4+{value}-f5"
            result = (x == value) if equals else (x != value)
            comp = "==" if equals else "!="
            test_index += 1
            if result:
                expected += 4  # there are 4 test types for every successful combo
                true_action1 = "success++"
                true_action2 = "success++"
                true_action3 = "success++"
                true_action4 = "success++"
            else:
                fail_index += 1
                true_action1 = f"fail_{datatype}({fail_index},{x})"
                fail_index += 1
                true_action2 = f"fail_{datatype}({fail_index},{x})"
                fail_index += 1
                true_action3 = f"fail_{datatype}({fail_index},{x})"
                fail_index += 1
                true_action4 = f"fail_{datatype}({fail_index},{x})"
            print(f"""    ; direct jump
        if sources[1]{comp}{expr}
            goto lbl{test_index}a
        goto skip{test_index}a
lbl{test_index}a:   {true_action1}
skip{test_index}a:
        ; indirect jump
        cx16.r3 = &lbl{test_index}b
        if sources[1]{comp}{expr}
            goto cx16.r3
        goto skip{test_index}b
lbl{test_index}b:   {true_action2}
skip{test_index}b:
        ; no else
        if sources[1]{comp}{expr}
            {true_action3}

        ; with else
        if sources[1]{comp}{expr}
            {true_action4}
        else
            cx16.r0L++
""")
    print(f"    verify_success({expected})\n}}")


def generate(datatype):
    global fail_index
    fail_index = 0
    header(datatype)
    make_test_is_zero(datatype)
    make_test_not_zero(datatype)
    make_test_is_number(datatype, True)
    make_test_is_number(datatype, False)
    make_test_is_var(datatype, True)
    make_test_is_var(datatype, False)
    make_test_is_expr(datatype, True)
    make_test_is_expr(datatype, False)
    make_test_is_array(datatype, True)
    make_test_is_array(datatype, False)
    print("\n}\n")


if __name__ == '__main__':
    for dt in ["uword", "word"]:
        sys.stdout = open(f"test_{dt}_splitw_equalities.p8", "wt")
        generate(dt)
