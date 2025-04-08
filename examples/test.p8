%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        unsigned()
        signed()
    }

    sub print_bool(bool b) {
        txt.print_ub(b as ubyte)
    }

    ubyte[2] ubarray
    uword[2] uwarray
    byte[2] barray
    word[2] warray

    sub value_and_carry(ubyte value) -> ubyte {
        sys.set_carry()
        return value
    }

    sub unsigned() {
        txt.print("rol_ub\n")
        test_rol_ub(%00000000, false, %00000000, false)
        test_rol_ub(%00000000, true,  %00000001, false)
        test_rol_ub(%01000000, false, %10000000, false)
        test_rol_ub(%01000000, true,  %10000001, false)
        test_rol_ub(%10000000, false, %00000000, true)
        test_rol_ub(%10000000, true,  %00000001, true)

        txt.print("ror_ub\n")
        test_ror_ub(%00000000, false, %00000000, false)
        test_ror_ub(%00000000, true,  %10000000, false)
        test_ror_ub(%01000000, false, %00100000, false)
        test_ror_ub(%01000000, true,  %10100000, false)
        test_ror_ub(%00000001, false, %00000000, true)
        test_ror_ub(%00000001, true,  %10000000, true)

        txt.print("rol2_ub\n")
        test_rol2_ub(%00000000, %00000000)
        test_rol2_ub(%01000001, %10000010)
        test_rol2_ub(%10000010, %00000101)
        test_rol2_ub(%11111110, %11111101)

        txt.print("ror2_ub\n")
        test_ror2_ub(%00000000, %00000000)
        test_ror2_ub(%01000001, %10100000)
        test_ror2_ub(%10000010, %01000001)
        test_ror2_ub(%11111110, %01111111)

        txt.print("rol_uw\n")
        test_rol_uw(%0000000010000000, false, %0000000100000000, false)
        test_rol_uw(%0000000010000000, true,  %0000000100000001, false)
        test_rol_uw(%0100000010000000, false, %1000000100000000, false)
        test_rol_uw(%0100000010000000, true,  %1000000100000001, false)
        test_rol_uw(%1000000010000000, false, %0000000100000000, true)
        test_rol_uw(%1000000010000000, true,  %0000000100000001, true)

        txt.print("ror_uw\n")
        test_ror_uw(%0000000100000000, false, %0000000010000000, false)
        test_ror_uw(%0000000100000000, true,  %1000000010000000, false)
        test_ror_uw(%0100000100000000, false, %0010000010000000, false)
        test_ror_uw(%0100000100000000, true,  %1010000010000000, false)
        test_ror_uw(%0000000100000001, false, %0000000010000000, true)
        test_ror_uw(%0000000100000001, true,  %1000000010000000, true)

        txt.print("rol2_uw\n")
        test_rol2_uw(%0000000010000000, %0000000100000000)
        test_rol2_uw(%0100000110000000, %1000001100000000)
        test_rol2_uw(%1000001010000000, %0000010100000001)
        test_rol2_uw(%1111111010000000, %1111110100000001)

        txt.print("ror2_uw\n")
        test_ror2_uw(%0000000100000000, %0000000010000000)
        test_ror2_uw(%0100000100000000, %0010000010000000)
        test_ror2_uw(%1000001100000001, %1100000110000000)
        test_ror2_uw(%1111111100000011, %1111111110000001)

        txt.print("<< ub\n")
        test_shiftl_ub(%00000000, %00000000, false)
        test_shiftl_ub(%00000001, %00000010, false)
        test_shiftl_ub(%01000000, %10000000, false)
        test_shiftl_ub(%10000000, %00000000, true)

        txt.print(">> ub\n")
        test_shiftr_ub(%00000000, %00000000, false)
        test_shiftr_ub(%00000001, %00000000, true)
        test_shiftr_ub(%10000000, %01000000, false)
        test_shiftr_ub(%10000001, %01000000, true)

        txt.print("<< uw\n")
        test_shiftl_uw(%0000000000000000, %0000000000000000, false)
        test_shiftl_uw(%0000000000000001, %0000000000000010, false)
        test_shiftl_uw(%0000000010000001, %0000000100000010, false)
        test_shiftl_uw(%0100000010000000, %1000000100000000, false)
        test_shiftl_uw(%1100000010000000, %1000000100000000, true)
        test_shiftl_uw(%1000000000000000, %0000000000000000, true)

        txt.print(">> uw\n")
        test_shiftr_uw(%0000000000000000, %0000000000000000, false)
        test_shiftr_uw(%0000000000000001, %0000000000000000, true)
        test_shiftr_uw(%0000001100000010, %0000000110000001, false)
        test_shiftr_uw(%0000001100000011, %0000000110000001, true)
        test_shiftr_uw(%1000000000000010, %0100000000000001, false)
    }

    sub signed() {
        txt.print("<< b\n")
        test_shiftl_b(%00000000 as byte, %00000000 as byte, false)
        test_shiftl_b(%00000001 as byte, %00000010 as byte, false)
        test_shiftl_b(%01000000 as byte, %10000000 as byte, false)
        test_shiftl_b(%10000000 as byte, %00000000 as byte, true)

        txt.print(">> b\n")
        test_shiftr_b(%00000000 as byte, %00000000 as byte, false)
        test_shiftr_b(%00000001 as byte, %00000000 as byte, true)
        test_shiftr_b(%10000000 as byte, %11000000 as byte, false)
        test_shiftr_b(%10000010 as byte, %11000001 as byte, false)
        test_shiftr_b(%10000001 as byte, %11000000 as byte, true)

        txt.print("<< w\n")
        test_shiftl_w(%0000000000000000 as word, %0000000000000000 as word, false)
        test_shiftl_w(%0000000000000001 as word, %0000000000000010 as word, false)
        test_shiftl_w(%0000000010000001 as word, %0000000100000010 as word, false)
        test_shiftl_w(%0100000010000000 as word, %1000000100000000 as word, false)
        test_shiftl_w(%1100000010000000 as word, %1000000100000000 as word, true)
        test_shiftl_w(%1000000000000000 as word, %0000000000000000 as word, true)

        txt.print(">> w\n")
        test_shiftr_w(%0000000000000000 as word, %0000000000000000 as word, false)
        test_shiftr_w(%0000000000000001 as word, %0000000000000000 as word, true)
        test_shiftr_w(%0000001100000010 as word, %0000000110000001 as word, false)
        test_shiftr_w(%0000001100000011 as word, %0000000110000001 as word, true)
        test_shiftr_w(%1000000000000010 as word, %1100000000000001 as word, false)
        test_shiftr_w(%1000000000000001 as word, %1100000000000000 as word, true)
    }


    sub test_rol2_ub(ubyte value, ubyte test) {
        ubyte original = value
        sys.set_carry()
        rol2(value)
        if value!=test {
            txt.print("rol2_ub error ")
            txt.print_ub(original)
            txt.spc()
            txt.print_ub(value)
            txt.print(" exp: ")
            txt.print_ub(test)
            txt.nl()
        }
        ubarray[1]=original
        sys.set_carry()
        rol2(ubarray[1])
        if ubarray[1]!=test {
            txt.print("rol2_ub array error ")
            txt.print_ub(original)
            txt.spc()
            txt.print_ub(ubarray[1])
            txt.print(" exp: ")
            txt.print_ub(test)
            txt.nl()
        }
        @($8000)=original
        sys.set_carry()
        rol2(@($8000))
        if @($8000)!=test {
            txt.print("rol2_ub mem error ")
            txt.print_ub(original)
            txt.spc()
            txt.print_ub(@($8000))
            txt.print(" exp: ")
            txt.print_ub(test)
            txt.nl()
        }
    }

    sub test_ror2_ub(ubyte value, ubyte test) {
        ubyte original = value
        sys.set_carry()
        ror2(value)
        if value!=test {
            txt.print("ror2_ub error ")
            txt.print_ub(original)
            txt.spc()
            txt.print_ub(value)
            txt.print(" exp: ")
            txt.print_ub(test)
            txt.nl()
        }
        ubarray[1] = original
        sys.set_carry()
        ror2(ubarray[1])
        if ubarray[1]!=test {
            txt.print("ror2_ub array error ")
            txt.print_ub(original)
            txt.spc()
            txt.print_ub(ubarray[1])
            txt.print(" exp: ")
            txt.print_ub(test)
            txt.nl()
        }
        @($8000) = original
        sys.set_carry()
        ror2(@($8000))
        if @($8000)!=test {
            txt.print("ror2_ub mem error ")
            txt.print_ub(original)
            txt.spc()
            txt.print_ub(@($8000))
            txt.print(" exp: ")
            txt.print_ub(test)
            txt.nl()
        }
    }

    sub test_rol_ub(ubyte value, bool carry, ubyte test, bool newcarry) {
        bool carrycheck = false
        ubyte original = value
        if carry
            sys.set_carry()
        else
            sys.clear_carry()
        rol(value)
        if_cs
            carrycheck=true
        if  value!=test or carrycheck!=newcarry{
            txt.print("rol_ub error ")
            txt.print_ub(original)
            txt.spc()
            print_bool(carry)
            txt.spc()
            txt.print_ub(value)
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_ub(test)
            txt.spc()
            print_bool(newcarry)
            txt.nl()
        }

        ubarray[1] = original
        carrycheck = false
        if carry
            sys.set_carry()
        else
            sys.clear_carry()
        rol(ubarray[value_and_carry(1)])
        if_cs
            carrycheck=true
        if  ubarray[1]!=test or carrycheck!=newcarry{
            txt.print("rol_ub array error ")
            txt.print_ub(original)
            txt.spc()
            print_bool(carry)
            txt.spc()
            txt.print_ub(ubarray[1])
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_ub(test)
            txt.spc()
            print_bool(newcarry)
            txt.nl()
        }
    }

    sub test_ror_ub(ubyte value, bool carry, ubyte test, bool newcarry) {
        bool carrycheck = false
        ubyte original = value
        if carry
            sys.set_carry()
        else
            sys.clear_carry()
        ror(value)
        if_cs
            carrycheck=true
        if  value!=test or carrycheck!=newcarry{
            txt.print("ror_ub error ")
            txt.print_ub(original)
            txt.spc()
            print_bool(carry)
            txt.spc()
            txt.print_ub(value)
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_ub(test)
            txt.spc()
            print_bool(newcarry)
            txt.nl()
        }

        ubarray[1] = original
        carrycheck = false
        if carry
            sys.set_carry()
        else
            sys.clear_carry()
        ror(ubarray[value_and_carry(1)])
        if_cs
            carrycheck=true
        if  ubarray[1]!=test or carrycheck!=newcarry {
            txt.print("ror_ub array error ")
            txt.print_ub(original)
            txt.spc()
            print_bool(carry)
            txt.spc()
            txt.print_ub(ubarray[1])
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_ub(test)
            txt.spc()
            print_bool(newcarry)
            txt.nl()
        }
    }

    sub test_rol2_uw(uword value, uword test) {
        uword original = value
        sys.set_carry()
        rol2(value)
        if value!=test {
            txt.print("rol2_uw error ")
            txt.print_uw(original)
            txt.spc()
            txt.print_uw(value)
            txt.print(" exp: ")
            txt.print_uw(test)
            txt.nl()
        }

        uwarray[1] = original
        sys.set_carry()
        rol2(uwarray[1])
        if uwarray[1]!=test {
            txt.print("rol2_uw array error ")
            txt.print_uw(original)
            txt.spc()
            txt.print_uw(uwarray[1])
            txt.print(" exp: ")
            txt.print_uw(test)
            txt.nl()
        }
    }

    sub test_ror2_uw(uword value, uword test) {
        uword original = value
        sys.set_carry()
        ror2(value)
        if value!=test {
            txt.print("ror2_uw error ")
            txt.print_uw(original)
            txt.spc()
            txt.print_uw(value)
            txt.print(" exp: ")
            txt.print_uw(test)
            txt.nl()
        }

        uwarray[1] = original
        sys.set_carry()
        ror2(uwarray[1])
        if uwarray[1]!=test {
            txt.print("ror2_uw array error ")
            txt.print_uw(original)
            txt.spc()
            txt.print_uw(uwarray[1])
            txt.print(" exp: ")
            txt.print_uw(test)
            txt.nl()
        }
    }

    sub test_rol_uw(uword value, bool carry, uword test, bool newcarry) {
        bool carrycheck = false
        uword original = value
        if carry
            sys.set_carry()
        else
            sys.clear_carry()
        rol(value)
        if_cs
            carrycheck=true
        if  value!=test or carrycheck!=newcarry{
            txt.print("rol_uw error ")
            txt.print_uw(original)
            txt.spc()
            print_bool(carry)
            txt.spc()
            txt.print_uw(value)
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_uw(test)
            txt.spc()
            print_bool(carrycheck)
            txt.nl()
        }

        uwarray[1] = original
        carrycheck = false
        if carry
            sys.set_carry()
        else
            sys.clear_carry()
        rol(uwarray[value_and_carry(1)])
        if_cs
            carrycheck=true
        if  uwarray[1]!=test or carrycheck!=newcarry{
            txt.print("rol_uw array error ")
            txt.print_uw(original)
            txt.spc()
            print_bool(carry)
            txt.spc()
            txt.print_uw(uwarray[1])
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_uw(test)
            txt.spc()
            print_bool(carrycheck)
            txt.nl()
        }
    }

    sub test_ror_uw(uword value, bool carry, uword test, bool newcarry) {
        bool carrycheck = false
        uword original = value
        if carry
            sys.set_carry()
        else
            sys.clear_carry()
        ror(value)
        if_cs
            carrycheck=true
        if  value!=test or carrycheck!=newcarry{
            txt.print("ror_uw error ")
            txt.print_uw(original)
            txt.spc()
            print_bool(carry)
            txt.spc()
            txt.print_uw(value)
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_uw(test)
            txt.spc()
            print_bool(carrycheck)
            txt.nl()
        }

        uwarray[1] = original
        carrycheck = false
        if carry
            sys.set_carry()
        else
            sys.clear_carry()
        ror(uwarray[value_and_carry(1)])
        if_cs
            carrycheck=true
        if  uwarray[1]!=test or carrycheck!=newcarry{
            txt.print("ror_uw array error ")
            txt.print_uw(original)
            txt.spc()
            print_bool(carry)
            txt.spc()
            txt.print_uw(uwarray[1])
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_uw(test)
            txt.spc()
            print_bool(carrycheck)
            txt.nl()
        }
    }

    sub test_shiftl_ub(ubyte value, ubyte test, bool newcarry) {
        bool carrycheck = false
        ubyte original = value
        sys.set_carry()
        value <<= 1
        if_cs
            carrycheck=true
        if value!=test or carrycheck!=newcarry {
            txt.print("<< ub error ")
            txt.print_ub(original)
            txt.spc()
            txt.print_ub(value)
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_ub(test)
            txt.spc()
            print_bool(newcarry)
            txt.nl()
        }

        ubarray[1] = original
        sys.set_carry()
        carrycheck = false
        ubarray[1] <<= 1
        if_cs
            carrycheck=true
        if ubarray[1]!=test or carrycheck!=newcarry {
            txt.print("<< ub array error ")
            txt.print_ub(original)
            txt.spc()
            txt.print_ub(ubarray[1])
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_ub(test)
            txt.spc()
            print_bool(newcarry)
            txt.nl()
        }
    }

    sub test_shiftr_ub(ubyte value, ubyte test, bool newcarry) {
        bool carrycheck = false
        ubyte original = value
        sys.set_carry()
        value >>= 1
        if_cs
            carrycheck=true
        if value!=test or carrycheck!=newcarry {
            txt.print(">> ub error ")
            txt.print_ub(original)
            txt.spc()
            txt.print_ub(value)
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_ub(test)
            txt.spc()
            print_bool(newcarry)
            txt.nl()
        }

        ubarray[1] = original
        sys.set_carry()
        carrycheck = false
        ubarray[1] >>= 1
        if_cs
            carrycheck=true
        if ubarray[1]!=test or carrycheck!=newcarry {
            txt.print(">> ub array error ")
            txt.print_ub(original)
            txt.spc()
            txt.print_ub(ubarray[1])
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_ub(test)
            txt.spc()
            print_bool(newcarry)
            txt.nl()
        }
    }

    sub test_shiftl_uw(uword value, uword test, bool newcarry) {
        bool carrycheck = false
        uword original = value
        sys.set_carry()
        value <<= 1
        if_cs
            carrycheck=true
        if value!=test or carrycheck!=newcarry {
            txt.print("<< uw error ")
            txt.print_uw(original)
            txt.spc()
            txt.print_uw(value)
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_uw(test)
            txt.spc()
            print_bool(carrycheck)
            txt.nl()
        }

        uwarray[1] = original
        sys.set_carry()
        carrycheck = false
        uwarray[1] <<= 1
        if_cs
            carrycheck=true
        if uwarray[1]!=test or carrycheck!=newcarry {
            txt.print("<< uw array error ")
            txt.print_uw(original)
            txt.spc()
            txt.print_uw(uwarray[1])
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_uw(test)
            txt.spc()
            print_bool(carrycheck)
            txt.nl()
        }
    }

    sub test_shiftr_uw(uword value, uword test, bool newcarry) {
        bool carrycheck = false
        uword original = value
        sys.set_carry()
        value >>= 1
        if_cs
            carrycheck=true
        if value!=test or carrycheck!=newcarry {
            txt.print(">> uw error ")
            txt.print_uw(original)
            txt.spc()
            txt.print_uw(value)
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_uw(test)
            txt.spc()
            print_bool(carrycheck)
            txt.nl()
        }

        uwarray[1] = original
        sys.set_carry()
        carrycheck = false
        uwarray[1] >>= 1
        if_cs
            carrycheck=true
        if uwarray[1]!=test or carrycheck!=newcarry {
            txt.print(">> uw array error ")
            txt.print_uw(original)
            txt.spc()
            txt.print_uw(uwarray[1])
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_uw(test)
            txt.spc()
            print_bool(carrycheck)
            txt.nl()
        }
    }

    sub test_shiftl_b(byte value, byte test, bool newcarry) {
        bool carrycheck = false
        byte original = value
        sys.set_carry()
        value <<= 1
        if_cs
            carrycheck=true
        if value!=test or carrycheck!=newcarry {
            txt.print("<< b error ")
            txt.print_b(original)
            txt.spc()
            txt.print_b(value)
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_b(test)
            txt.spc()
            print_bool(carrycheck)
            txt.nl()
        }

        barray[1] = original
        sys.set_carry()
        carrycheck = false
        barray[1] <<= 1
        if_cs
            carrycheck=true
        if barray[1]!=test or carrycheck!=newcarry {
            txt.print("<< b array error ")
            txt.print_b(original)
            txt.spc()
            txt.print_b(barray[1])
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_b(test)
            txt.spc()
            print_bool(carrycheck)
            txt.nl()
        }
    }

    sub test_shiftr_b(byte value, byte test, bool newcarry) {
        bool carrycheck = false
        byte original = value
        sys.set_carry()
        value >>= 1
        if_cs
            carrycheck=true
        if value!=test or carrycheck!=newcarry {
            txt.print(">> b error ")
            txt.print_b(original)
            txt.spc()
            txt.print_b(value)
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_b(test)
            txt.spc()
            print_bool(carrycheck)
            txt.nl()
        }

        barray[1] = original
        sys.set_carry()
        carrycheck = false
        barray[1] >>= 1
        if_cs
            carrycheck=true
        if barray[1]!=test or carrycheck!=newcarry {
            txt.print(">> b array error ")
            txt.print_b(original)
            txt.spc()
            txt.print_b(barray[1])
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_b(test)
            txt.spc()
            print_bool(carrycheck)
            txt.nl()
        }
    }

    sub test_shiftl_w(word value, word test, bool newcarry) {
        bool carrycheck = false
        word original = value
        sys.set_carry()
        value <<= 1
        if_cs
            carrycheck=true
        if value!=test or carrycheck!=newcarry {
            txt.print("<< w error ")
            txt.print_w(original)
            txt.spc()
            txt.print_w(value)
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_w(test)
            txt.spc()
            print_bool(carrycheck)
            txt.nl()
        }

        warray[1] = original
        sys.set_carry()
        carrycheck = false
        warray[1] <<= 1
        if_cs
            carrycheck=true
        if warray[1]!=test or carrycheck!=newcarry {
            txt.print("<< w array error ")
            txt.print_w(original)
            txt.spc()
            txt.print_w(warray[1])
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_w(test)
            txt.spc()
            print_bool(carrycheck)
            txt.nl()
        }
    }

    sub test_shiftr_w(word value, word test, bool newcarry) {
        bool carrycheck = false
        word original = value
        sys.set_carry()
        value >>= 1
        if_cs
            carrycheck=true
        if value!=test or carrycheck!=newcarry {
            txt.print(">> w error ")
            txt.print_w(original)
            txt.spc()
            txt.print_w(value)
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_w(test)
            txt.spc()
            print_bool(carrycheck)
            txt.nl()
        }

        warray[1] = original
        sys.set_carry()
        carrycheck = false
        warray[1] >>= 1
        if_cs
            carrycheck=true
        if warray[1]!=test or carrycheck!=newcarry {
            txt.print(">> w array error ")
            txt.print_w(original)
            txt.spc()
            txt.print_w(warray[1])
            txt.spc()
            print_bool(carrycheck)
            txt.print(" exp: ")
            txt.print_w(test)
            txt.spc()
            print_bool(carrycheck)
            txt.nl()
        }
    }
}
