%option enable_floats



~ main {


sub sub1() -> byte {
    return 11
}


sub sub2() -> byte {
    return 22
}

    A=55

sub sub3() -> byte {
    return 33
}

    X=34

sub sub4() -> byte {
    return 44
}

    A=sub1()
    A=sub2()
    A=sub3()
    A=sub4()

sub start() {

    byte bvar = 128
    word wvar = 128
    float fvar = 128

    bvar = 1
    bvar = 2.0
    bvar = 2.w
    bvar = 255.w
    wvar = 1
    wvar = 2.w
    wvar = 2.0
    wvar = bvar
    fvar = 1
    fvar = 2.w
    fvar = 22.33
    fvar = bvar
    fvar = wvar

    return

}

    A=sub1()
    A=sub2()
    A=sub3()
    A=sub4()
    sub4()


}


~ test {

    sub test() -> byte {
        return 44
    }
    sub test2() -> byte {
        return 43
    }
}
