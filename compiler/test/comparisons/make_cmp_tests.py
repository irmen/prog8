# generates various Prog8 files with a large amount of number comparison tests,
# using various forms of the if statement (because these forms have their own code gen paths)

import sys

fail_index = 0


class C:
    def __init__(self, short, long, operator, compare):
        self.short=short
        self.long=long
        self.operator=operator
        self.compare=compare


def header(dt, comparison: C):
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
        txt.print("\\n{comparison.long} tests for: ")
        txt.print(datatype)
        txt.nl()
        test_stack.test()
        txt.print("\\n{comparison.operator}number: ")
        test_cmp_number()
        txt.print("\\n{comparison.operator}var: ")
        test_cmp_var()
        txt.print("\\n{comparison.operator}array[]: ")
        test_cmp_array()
        txt.print("\\n{comparison.operator}expr: ")
        test_cmp_expr()
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
    
    sub fail_byte(uword idx, byte v1, byte v2) {{
        txt.print(" **fail#")
        txt.print_uw(idx)
        txt.chrout(':')
        txt.print_b(v1)
        txt.chrout(',')
        txt.print_b(v2)
        txt.print(" **")
    }}

    sub fail_ubyte(uword idx, ubyte v1, ubyte v2) {{
        txt.print(" **fail#")
        txt.print_uw(idx)
        txt.chrout(':')
        txt.print_ub(v1)
        txt.chrout(',')
        txt.print_ub(v2)
        txt.print(" **")
    }}
    
    sub fail_word(uword idx, word v1, word v2) {{
        txt.print(" **fail#")
        txt.print_uw(idx)
        txt.chrout(':')
        txt.print_w(v1)
        txt.chrout(',')
        txt.print_w(v2)
        txt.print(" **")
    }}

    sub fail_uword(uword idx, uword v1, uword v2) {{
        txt.print(" **fail#")
        txt.print_uw(idx)
        txt.chrout(':')
        txt.print_uw(v1)
        txt.chrout(',')
        txt.print_uw(v2)
        txt.print(" **")
    }}
    
    sub fail_float(uword idx, float v1, float v2) {{
        txt.print(" **fail#")
        txt.print_uw(idx)
        txt.chrout(':')
        floats.print(v1)
        txt.chrout(',')
        floats.print(v2)
        txt.print(" **")
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


def make_test_number(datatype, comparison: C):
    numbers = testnumbers[datatype]
    print("    sub test_cmp_number() {")
    print(f"""    {datatype} @shared x
        success = 0""")
    expected = 0
    test_index = 0
    global fail_index
    for x in numbers:
        print(f"    x={x}")
        for value in numbers:
            result = comparison.compare(x, value)
            comp = comparison.operator
            test_index += 1
            if result:
                expected += 4  # there are 4 test types for every successful combo
                true_action1 = "success++"
                true_action2 = "success++"
                true_action3 = "success++"
                true_action4 = "success++"
            else:
                fail_index += 1
                true_action1 = f"fail_{datatype}({fail_index},x,{value})"
                fail_index += 1
                true_action2 = f"fail_{datatype}({fail_index},x,{value})"
                fail_index += 1
                true_action3 = f"fail_{datatype}({fail_index},x,{value})"
                fail_index += 1
                true_action4 = f"fail_{datatype}({fail_index},x,{value})"
            print(f"""    ; direct jump
        if x{comp}{value}
            goto lbl{test_index}a
        goto skip{test_index}a
lbl{test_index}a:   {true_action1}
skip{test_index}a:
        ; indirect jump
        cx16.r3 = &lbl{test_index}b
        if x{comp}{value}
            goto cx16.r3
        goto skip{test_index}b
lbl{test_index}b:   {true_action2}
skip{test_index}b:
        ; no else
        if x{comp}{value}
            {true_action3}

        ; with else
        if x{comp}{value}
            {true_action4}
        else
            cx16.r0L++
""")
    print(f"    verify_success({expected})\n}}")


def make_test_var(datatype, comparison: C):
    numbers = testnumbers[datatype]
    print("    sub test_cmp_var() {")
    print(f"""    {datatype} @shared x, value
        success = 0""")
    expected = 0
    test_index = 0
    global fail_index
    for x in numbers:
        print(f"    x={x}")
        for value in numbers:
            if value == 0:
                continue  # 0 already tested separately
            print(f"    value={value}")
            result = comparison.compare(x, value)
            comp = comparison.operator
            test_index += 1
            if result:
                expected += 4  # there are 4 test types for every successful combo
                true_action1 = "success++"
                true_action2 = "success++"
                true_action3 = "success++"
                true_action4 = "success++"
            else:
                fail_index += 1
                true_action1 = f"fail_{datatype}({fail_index},x,value)"
                fail_index += 1
                true_action2 = f"fail_{datatype}({fail_index},x,value)"
                fail_index += 1
                true_action3 = f"fail_{datatype}({fail_index},x,value)"
                fail_index += 1
                true_action4 = f"fail_{datatype}({fail_index},x,value)"
            print(f"""    ; direct jump
        if x{comp}value
            goto lbl{test_index}a
        goto skip{test_index}a
lbl{test_index}a:   {true_action1}
skip{test_index}a:
        ; indirect jump
        cx16.r3 = &lbl{test_index}b
        if x{comp}value
            goto cx16.r3
        goto skip{test_index}b
lbl{test_index}b:   {true_action2}
skip{test_index}b:
        ; no else
        if x{comp}value
            {true_action3}

        ; with else
        if x{comp}value
            {true_action4}
        else
            cx16.r0L++
""")
    print(f"    verify_success({expected})\n}}")


def make_test_array(datatype, comparison: C):
    numbers = testnumbers[datatype]
    print("    sub test_cmp_array() {")
    print(f"""    {datatype} @shared x
        {datatype}[] values = [0, 0]
        success = 0""")
    expected = 0
    test_index = 0
    global fail_index
    for x in numbers:
        print(f"    x={x}")
        for value in numbers:
            if value == 0:
                continue  # 0 already tested separately
            print(f"    values[1]={value}")
            result = comparison.compare(x, value)
            comp = comparison.operator
            test_index += 1
            if result:
                expected += 4  # there are 4 test types for every successful combo
                true_action1 = "success++"
                true_action2 = "success++"
                true_action3 = "success++"
                true_action4 = "success++"
            else:
                fail_index += 1
                true_action1 = f"fail_{datatype}({fail_index},x,{value})"
                fail_index += 1
                true_action2 = f"fail_{datatype}({fail_index},x,{value})"
                fail_index += 1
                true_action3 = f"fail_{datatype}({fail_index},x,{value})"
                fail_index += 1
                true_action4 = f"fail_{datatype}({fail_index},x,{value})"
            print(f"""    ; direct jump
        if x{comp}values[1]
            goto lbl{test_index}a
        goto skip{test_index}a
lbl{test_index}a:   {true_action1}
skip{test_index}a:
        ; indirect jump
        cx16.r3 = &lbl{test_index}b
        if x{comp}values[1]
            goto cx16.r3
        goto skip{test_index}b
lbl{test_index}b:   {true_action2}
skip{test_index}b:
        ; no else
        if x{comp}values[1]
            {true_action3}

        ; with else
        if x{comp}values[1]
            {true_action4}
        else
            cx16.r0L++
""")
    print(f"    verify_success({expected})\n}}")


def make_test_expr(datatype, comparison: C):
    numbers = testnumbers[datatype]
    print("    sub test_cmp_expr() {")
    print(f"""    {datatype} @shared x
        cx16.r4 = 1
        cx16.r5 = 1
        float @shared f4 = 1.0
        float @shared f5 = 1.0
        success = 0""")
    expected = 0
    test_index = 0
    global fail_index
    for x in numbers:
        print(f"    x={x}")
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
            result = comparison.compare(x, value)
            comp = comparison.operator
            test_index += 1
            if result:
                expected += 4  # there are 4 test types for every successful combo
                true_action1 = "success++"
                true_action2 = "success++"
                true_action3 = "success++"
                true_action4 = "success++"
            else:
                fail_index += 1
                true_action1 = f"fail_{datatype}({fail_index},x,{value})"
                fail_index += 1
                true_action2 = f"fail_{datatype}({fail_index},x,{value})"
                fail_index += 1
                true_action3 = f"fail_{datatype}({fail_index},x,{value})"
                fail_index += 1
                true_action4 = f"fail_{datatype}({fail_index},x,{value})"
            print(f"""    ; direct jump
        if x{comp}{expr}
            goto lbl{test_index}a
        goto skip{test_index}a
lbl{test_index}a:   {true_action1}
skip{test_index}a:
        ; indirect jump
        cx16.r3 = &lbl{test_index}b
        if x{comp}{expr}
            goto cx16.r3
        goto skip{test_index}b
lbl{test_index}b:   {true_action2}
skip{test_index}b:
        ; no else
        if x{comp}{expr}
            {true_action3}

        ; with else
        if x{comp}{expr}
            {true_action4}
        else
            cx16.r0L++
""")
    print(f"    verify_success({expected})\n}}")


def generate(datatype, comparison: C):
    global fail_index
    fail_index = 0
    header(datatype, comparison)
    make_test_number(datatype, comparison)
    make_test_var(datatype, comparison)
    make_test_array(datatype, comparison)
    make_test_expr(datatype, comparison)
    print("\n}\n")


if __name__ == '__main__':
    comparisons = [
        C("lt", "less-than", "<", lambda x,y: x < y),
        C("lte", "less-equal", "<=", lambda x,y: x <= y),
        C("gt", "greater-than", ">", lambda x,y: x > y),
        C("gte", "greater-equal", ">=", lambda x,y: x >= y),
    ]
    for comparison in comparisons:
        for dt in ["ubyte", "uword", "byte", "word", "float"]:
            sys.stdout = open(f"test_{dt}_{comparison.short}.p8", "wt")
            generate(dt, comparison)
