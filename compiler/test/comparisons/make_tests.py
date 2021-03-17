import sys

index = 0


def minmaxvalues(dt):
    if dt == "ubyte":
        return 0, 255
    elif dt == "uword":
        return 0, 65535
    elif dt == "byte":
        return -128, 127
    elif dt == "word":
        return -32768, 32767
    elif dt == "float":
        return -99999999, 99999999
    else:
        raise ValueError(dt)


def gen_test(dt, comparison, left, right, expected):
    global index
    etxt = f"{left} {comparison} {right}"
    if eval(etxt) != expected:
        raise ValueError("invalid comparison: "+etxt+" for "+dt)
    if expected:
        stmt_ok = lambda ix: "num_successes++"
        stmt_else = lambda ix: f"error({ix})"
    else:
        stmt_ok = lambda ix: f"error({ix})"
        stmt_else = lambda ix: "num_successes++"

    print(
f"""        left = {left}
        right = {right}
"""
    )

    # const <op> const
    index += 1
    print(
f"""        ; test #{index}
        if {left} {comparison} {right} {{
            {stmt_ok(index)}
        }} else {{
            {stmt_else(index)}
        }}
""")
    # const <op> var
    index += 1
    print(
f"""        ; test #{index}
        if {left} {comparison} right {{
            {stmt_ok(index)}
        }} else {{
            {stmt_else(index)}
        }}
""")
    # const <op> expr
    index += 1
    print(
f"""        ; test #{index}
        if {left} {comparison} right+zero {{
            {stmt_ok(index)}
        }} else {{
            {stmt_else(index)}
        }}
""")
    # var <op> const
    index += 1
    print(
f"""        ; test #{index}
        if left {comparison} {right} {{
            {stmt_ok(index)}
        }} else {{
            {stmt_else(index)}
        }}
""")
    # var <op> var
    index += 1
    print(
f"""        ; test #{index}
        if left {comparison} right {{
            {stmt_ok(index)}
        }} else {{
            {stmt_else(index)}
        }}
""")
    # var <op> expr
    index += 1
    print(
f"""        ; test #{index}
        if left {comparison} right+zero {{
            {stmt_ok(index)}
        }} else {{
            {stmt_else(index)}
        }}
""")
    # expr <op> const
    index += 1
    print(
f"""        ; test #{index}
        if left+zero {comparison} {right} {{
            {stmt_ok(index)}
        }} else {{
            {stmt_else(index)}
        }}
""")
    # expr <op> var
    index += 1
    print(
f"""        ; test #{index}
        if left+zero {comparison} right {{
            {stmt_ok(index)}
        }} else {{
            {stmt_else(index)}
        }}
""")
    # expr <op> expr
    index += 1
    print(
f"""        ; test #{index}
        if left+zero {comparison} right+zero {{
            {stmt_ok(index)}
        }} else {{
            {stmt_else(index)}
        }}
""")


def gen_comp_equal(dt):
    minval, maxval = minmaxvalues(dt)
    print("        ; tests: ", dt, "==")
    print("        comparison = \"==\"")
    print("        txt.print(datatype)")
    print("        txt.spc()")
    print("        txt.print(comparison)")
    print("        txt.nl()")
    gen_test(dt, "==", 0, 0, True)
    gen_test(dt, "==", 0, 1, False)
    gen_test(dt, "==", 100, 100, True)
    gen_test(dt, "==", 100, 101, False)
    if maxval >= 200:
        gen_test(dt, "==", 200, 200, True)
        gen_test(dt, "==", 200, 201, False)
    if maxval >= 9999:
        gen_test(dt, "==", 9999, 9999, True)
        gen_test(dt, "==", 9999, 10000, False)
        gen_test(dt, "==", 0x5000, 0x5000, True)
        gen_test(dt, "==", 0x5000, 0x5001, False)
        gen_test(dt, "==", 0x5000, 0x4fff, False)
    if maxval >= 30000:
        gen_test(dt, "==", 30000, 30000, True)
        gen_test(dt, "==", 30000, 30001, False)
    if maxval >= 40000:
        gen_test(dt, "==", 0xf000, 0xf000, True)
        gen_test(dt, "==", 0xf000, 0xf001, False)
        gen_test(dt, "==", 0xf000, 0xffff, False)
    if minval < 0:
        gen_test(dt, "==", 0, -1, False)
        gen_test(dt, "==", -100, -100, True)
    if minval < -200:
        gen_test(dt, "==", -200, -200, True)
        gen_test(dt, "==", -200, -201, False)
    if minval < -9999:
        gen_test(dt, "==", -0x5000, -0x5000, True)
        gen_test(dt, "==", -0x5000, -0x5001, False)
        gen_test(dt, "==", -0x5000, -0x4fff, False)
        gen_test(dt, "==", -9999, -9999, True)
        gen_test(dt, "==", -9999, -10000, False)
    gen_test(dt, "==", minval, minval, True)
    gen_test(dt, "==", minval, minval+1, False)
    gen_test(dt, "==", maxval, maxval, True)
    gen_test(dt, "==", maxval, maxval-1, False)


def gen_comp_header(dt):
    print("        ; tests: ", dt, "!=")
    print("        comparison = \"!=\"")
    print("        txt.print(datatype)")
    print("        txt.spc()")
    print("        txt.print(comparison)")
    print("        txt.nl()")


def gen_comp_notequal(dt):
    minval, maxval = minmaxvalues(dt)
    gen_comp_header(dt)
    gen_test(dt, "!=", 0, 0, False)
    gen_test(dt, "!=", 0, 1, True)
    gen_test(dt, "!=", 100, 100, False)
    gen_test(dt, "!=", 100, 101, True)
    if maxval >= 200:
        gen_test(dt, "!=", 200, 200, False)
        gen_test(dt, "!=", 200, 201, True)
    if maxval >= 9999:
        gen_test(dt, "!=", 9999, 9999, False)
        gen_test(dt, "!=", 9999, 10000, True)
        gen_test(dt, "!=", 0x5000, 0x5000, False)
        gen_test(dt, "!=", 0x5000, 0x5001, True)
        gen_test(dt, "!=", 0x5000, 0x4fff, True)
    if maxval >= 30000:
        gen_test(dt, "!=", 30000, 30000, False)
        gen_test(dt, "!=", 30000, 30001, True)
    if maxval >= 40000:
        gen_test(dt, "!=", 0xf000, 0xf000, False)
        gen_test(dt, "!=", 0xf000, 0xf001, True)
        gen_test(dt, "!=", 0xf000, 0xffff, True)
    if minval < 0:
        gen_test(dt, "!=", 0, -1, True)
        gen_test(dt, "!=", -100, -100, False)
    if minval < -200:
        gen_test(dt, "!=", -200, -200, False)
        gen_test(dt, "!=", -200, -201, True)
    if minval < -9999:
        gen_test(dt, "!=", -0x5000, -0x5000, False)
        gen_test(dt, "!=", -0x5000, -0x5001, True)
        gen_test(dt, "!=", -0x5000, -0x4fff, True)
        gen_test(dt, "!=", -9999, -9999, False)
        gen_test(dt, "!=", -9999, -10000, True)
    gen_test(dt, "!=", minval, minval, False)
    gen_test(dt, "!=", minval, minval+1, True)
    gen_test(dt, "!=", maxval, maxval, False)
    gen_test(dt, "!=", maxval, maxval-1, True)


def gen_comp_less(dt):
    minval, maxval = minmaxvalues(dt)
    print("        ; tests: ", dt, "<")


def gen_comp_greater(dt):
    minval, maxval = minmaxvalues(dt)
    print("        ; tests: ", dt, ">")


def gen_comp_lessequal(dt):
    minval, maxval = minmaxvalues(dt)
    print("        ; tests: ", dt, "<=")


def gen_comp_greaterequal(dt):
    minval, maxval = minmaxvalues(dt)
    print("        ; tests: ", dt, ">=")


def generate_test_routine_equalsnotequals(dt):
    print(f"""
    sub test_comparisons() {{
        {dt}  left
        {dt}  right
        {dt}  zero = 0
""")
    gen_comp_equal(dt)
    gen_comp_notequal(dt)
    print("    }")


def generate_test_routine_lessgreater(dt):
    print(f"""
    sub test_comparisons() {{
        {dt}  left
        {dt}  right
        {dt}  zero = 0
""")
    gen_comp_less(dt)
    gen_comp_greater(dt)
    print("    }")


def generate_test_routine_lessequalsgreaterequals(dt):
    print(f"""
    sub test_comparisons() {{
        {dt}  left
        {dt}  right
        {dt}  zero = 0
""")
    gen_comp_lessequal(dt)
    gen_comp_greaterequal(dt)
    print("    }")


def generate(dt, operators):
    global index
    index = 0
    print(f"""
%import textio
%import floats
%import test_stack
%zeropage basicsafe

main {{
    uword num_errors = 0
    uword num_successes = 0
    str datatype = "{dt}"
    uword comparison

    sub start() {{
        test_comparisons()
        print_results()
        test_stack.test()
    }}

    sub error(uword index) {{
        txt.print(" ! error in test ")
        txt.print_uw(index)
        txt.chrout(' ')
        txt.print(datatype)
        txt.chrout(' ')
        txt.print(comparison)
        txt.nl()
        num_errors++
    }}
""")

    if operators=="eq":
        generate_test_routine_equalsnotequals(dt)
    elif operators=="lt":
        generate_test_routine_lessgreater(dt)
    elif operators=="lteq":
        generate_test_routine_lessequalsgreaterequals(dt)
    else:
        raise ValueError(operators)

    print(f"""
    sub print_results() {{
        txt.nl()
        txt.print("total {index}: ")
        txt.print_uw(num_successes)
        txt.print(" good, ")
        txt.print_uw(num_errors)
        txt.print(" errors!\\n")
    }}
}}
""")


if __name__ == '__main__':
    for dt in ["ubyte", "uword", "byte", "word", "float"]:
        sys.stdout = open(f"test_{dt}_eq.p8", "wt")
        generate(dt, "eq")
        sys.stdout = open(f"test_{dt}_lt.p8", "wt")
        generate(dt, "lt")
        sys.stdout = open(f"test_{dt}_lteq.p8", "wt")
        generate(dt, "lteq")
