%import textio
%zeropage dontuse

main {

    sub start() {
        uword v
        v = test.get_value1()
        txt.print_uw(v)
        txt.nl()
        v = test.get_value2()
        txt.print_uw(v)
        txt.nl()
        v = test.get_value3()
        txt.print_uw(v)
        txt.nl()
        v = test.get_value4()
        txt.print_uw(v)
        v = test.get_value4()
        txt.print_uw(v)
        v = test.get_value4()
        txt.print_uw(v)
        v = test.get_value4()
        txt.print_uw(v)
        v = test.get_value4()
        txt.print_uw(v)
        v = test.get_value4()
        txt.print_uw(v)
        v = test.get_value4()
        txt.print_uw(v)
        v = test.get_value4()
        txt.print_uw(v)
        v = test.get_value4()
        txt.print_uw(v)
        v = test.get_value4()
        txt.print_uw(v)
        txt.nl()
        v = test.get_value5()
        txt.print_uw(v)
        txt.nl()
        v = test.get_value6()
        txt.print_uw(v)
        txt.nl()
    }
}


test {
    uword[] arr = [1111,2222,3333]
    uword value = 9999

    sub get_value1() -> uword {
        return &value
    }
    sub get_value2() -> uword {
        return arr[2]
    }
    sub get_value3() -> ubyte {
        return @($c000)
    }
    sub get_value4() -> uword {
        return value
    }
    sub get_value5() -> uword {
        return $c000
    }
    sub get_value6() -> uword {
        return "string"
    }
}
