main {
    sub start() {
        uword err1, err2
        uword val
        err1, val, err2 = eval("42")
        if err1 == 0 {
            val += 1
        }
    }

    sub eval(str expr) -> str, uword, str {
        if expr == 0 {
            return "empty", 0, "empty2"
        }
        return 0, 42, 0
    }
}
