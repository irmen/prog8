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
    uword success = 0
    str datatype = "{dt}"
    uword @shared comparison

    sub start() {{
        txt.print("\\n{comparison.long} split words array tests for: ")
        txt.print(datatype)
        txt.nl()
        test_stack.test()
        txt.print("\\n{comparison.operator}array[]: ")
        test_cmp_array()
        test_stack.test()
    }}
    
    sub verify_success(uword expected) {{
        if success==expected {{
            txt.print("ok")
        }} else {{
            txt.print(" **failed** ")
            txt.print_uw(success)
            txt.print(" success, expected ")
            txt.print_uw(expected)
        }}
    }}
    
    sub fail_word(uword idx) {{
        txt.print(" **fail#")
        txt.print_uw(idx)
        txt.print(" **")
    }}

    sub fail_uword(uword idx) {{
        txt.print(" **fail#")
        txt.print_uw(idx)
        txt.print(" **")
    }}
    
""")


def tc(value):
    if value < 0x8000:
        return value
    else:
        return -(65536 - value)


testnumbers = {
    "word": [tc(0xaabb), -1, 0, 1, 0x00aa, 0x7700, 0x7fff],
    "uword": [0, 1, 0x7700, 0xffff],
}


def make_test_array(datatype, comparison: C):
    numbers = testnumbers[datatype]
    print("    sub test_cmp_array() {")
    print(f"""    {datatype} @shared x
        {datatype}[] values = [0, 0]
        {datatype}[] sources = [0, 0]
        success = 0""")
    expected = 0
    test_index = 0
    global fail_index
    for x in numbers:
        print(f"    x={x}")
        print(f"    sources[1]={x}")
        for value in numbers:
            print(f"    values[1]={value}")
            result = comparison.compare(x, value)
            comp = comparison.operator
            test_index += 1
            if result:
                expected += 8  # there are 4 test types for every successful combo
                true_action1 = "success++"
                true_action2 = "success++"
                true_action3 = "success++"
                true_action4 = "success++"
                fail_action4 = "cx16.r0L++"
                true_action5 = "success++"
                true_action6 = "success++"
                true_action7 = "success++"
                true_action8 = "success++"
                fail_action8 = "cx16.r0L++"
            else:
                fail_index += 1
                true_action1 = f"fail_{datatype}({fail_index})"
                fail_index += 1
                true_action2 = f"fail_{datatype}({fail_index})"
                fail_index += 1
                true_action3 = f"fail_{datatype}({fail_index})"
                fail_index += 1
                true_action4 = f"fail_{datatype}({fail_index})"
                fail_action4 = "success++"
                fail_index += 1
                true_action5 = f"fail_{datatype}({fail_index})"
                fail_index += 1
                true_action6 = f"fail_{datatype}({fail_index})"
                fail_index += 1
                true_action7 = f"fail_{datatype}({fail_index})"
                fail_index += 1
                true_action8 = f"fail_{datatype}({fail_index})"
                fail_action8 = "success++"
                expected += 2
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
            {fail_action4}
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
            {fail_action8}
""")
    print(f"    verify_success({expected})\n}}")


def generate(datatype, comparison: C):
    global fail_index
    fail_index = 0
    header(datatype, comparison)
    make_test_array(datatype, comparison)
    print("\n}\n")


if __name__ == '__main__':
    comparisons = [
        C("lt", "less-than", "<", lambda x,y: x < y),
        C("lte", "less-equal", "<=", lambda x,y: x <= y),
        C("gt", "greater-than", ">", lambda x,y: x > y),
        C("gte", "greater-equal", ">=", lambda x,y: x >= y),
    ]
    for comparison in comparisons:
        for dt in ["uword", "word"]:
            sys.stdout = open(f"test_{dt}_splitw_{comparison.short}.p8", "wt")
            generate(dt, comparison)
