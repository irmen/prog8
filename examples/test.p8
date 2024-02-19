%import textio
%import floats
%zeropage basicsafe
%option no_sysinit

main {
    bool @shared staticbool1 = true
    bool @shared staticbool2
    word[4] words

    byte @shared sbb
    float @shared fl=3.3
    float @shared fl2=4.3

    sub start() {

        while not staticbool2
            cx16.r0L++

        while cx16.r0L==0
            cx16.r0L++

        ; TODO all this for uwords

;        if sbb<0
;            cx16.r0L++
;        if sbb>0
;            cx16.r0L++
;        if sbb<=0
;            cx16.r0L++
;        if sbb>=0
;            cx16.r0L++
;        if cx16.r0s<0
;            cx16.r0L++
;        if cx16.r0s>0
;            cx16.r0L++
;        if cx16.r0s>0
;            goto start
;        if cx16.r0s>0
;            goto cx16.r0
;        if cx16.r0s<=0
;            cx16.r0L++
;        else
;            cx16.r0L++
;        if cx16.r0s>=0
;            cx16.r0L++
;        if words[2]<0
;            cx16.r0L++
;        if words[2]>0
;            cx16.r0L++
;        if words[2]<=0
;            cx16.r0L++
;        if words[2]>=0
;            cx16.r0L++
;        if words[cx16.r0L]<0
;            cx16.r0L++
;        if words[cx16.r0L]>0
;            cx16.r0L++
;        if words[cx16.r0L]<=0
;            cx16.r0L++
;        if words[cx16.r0L]>=0
;            cx16.r0L++

;        staticbool2 = sbb < 0
;        staticbool2 = sbb > 0
;        staticbool1 = sbb <= 0
;        staticbool1 = sbb >= 0
;        staticbool2 = cx16.r0s <0
;        staticbool2 = cx16.r0s >0
;        staticbool1 = cx16.r0s <=0
;        staticbool1 = cx16.r0s >=0
;        staticbool2 = words[2]<0
;        staticbool2 = words[2]>0
;        staticbool1 = words[2]<=0
;        staticbool1 = words[2]>=0
;        staticbool2 = words[cx16.r0L]<0
;        staticbool2 = words[cx16.r0L]>0
;        staticbool1 = words[cx16.r0L]<=0
;        staticbool1 = words[cx16.r0L]>=0


;        if cx16.r0 > 1234
;            cx16.r0L++
;        if cx16.r0s > 1234
;            cx16.r0L++
;        if cx16.r0 < 1234
;            cx16.r0L++
;        if cx16.r0s < 1234
;            cx16.r0L++
;        if cx16.r0 >= 1234
;            cx16.r0L++
;        if cx16.r0s >= 1234
;            cx16.r0L++
;        if cx16.r0 <= 1234
;            cx16.r0L++
;        if cx16.r0s <= 1234
;            cx16.r0L++


;        boolean_const_and_var(true)
;        staticbool1 = boolean_arrays_and_return()
;        txt.print_ub(staticbool1 as ubyte)
;        txt.nl()
;        and_or_xor_not()
;        logical_operand_swap()
;        bitwise_on_bools_errors()
;        arith_on_bools_errors()
;        logical_on_ints_errors()
;        bools_in_intarray_errors()
;        ints_in_boolarray_errors()
;        while_until_int_errors()
;        bools_in_array_assigns()
;        bools_in_array_assigns_inplace()
;        while_bool_efficient()
;        efficient_compare_0()
;        efficient_compare_99()
;        efficient_compare_var()
;        efficient_assign_cmp_0()
;        efficient_assign_cmp_99()
;        efficient_assign_cmp_var()
;        if_gotos()
;        if_code()
  ;;sys.exit(1)
;        while_equiv()

;        bool[3] barr
;        bool @shared bb
;
;        barr[1] = barr[0] and barr[2]
;        barr[1] = barr[0] or barr[2]
;        barr[1] = barr[0] xor barr[2]
;        barr[1] = not barr[0]
;        barr[1] = not barr[1]
;        barr[1] = barr[1] and bb
;        barr[1] = barr[1] or bb
;        barr[1] = barr[1] xor bb
;
;        bb = bb and barr[1]
;        bb = bb or barr[1]
;        bb = bb xor barr[1]
;        bb = not bb
    }

    sub while_bool_efficient() {
        while staticbool1 {
            cx16.r0L++
        }
        while not staticbool1 {
            cx16.r0L++
        }
        while cx16.r0L==0 {
            cx16.r0L++
        }
        while cx16.r0L!=0 {
            cx16.r0L++
        }
    }


    sub efficient_assign_cmp_0() {
        ubyte @shared ub
        uword @shared uw
        float @shared fl
        bool @shared bb1, bb2

        bb1 = ub==0
        bb2 = ub!=0
        bb1 = uw==0
        bb2 = uw!=0
        bb1 = fl==0.0
        bb2 = fl!=0.0
    }


    sub efficient_assign_cmp_99() {
        ubyte @shared ub
        uword @shared uw
        float @shared fl
        bool @shared bb1, bb2

        bb1 = ub==99
        bb2 = ub!=99
        bb1 = uw==99
        bb2 = uw!=99
        bb1 = fl==99.0
        bb2 = fl!=99.0
    }

    sub efficient_assign_cmp_var() {
        ubyte @shared ub
        uword @shared uw
        float @shared fl
        bool @shared bb1, bb2
        float @shared fval

        bb1 = ub==cx16.r0L
        bb2 = ub!=cx16.r0L
        bb1 = uw==cx16.r0
        bb2 = uw!=cx16.r0
        bb1 = fl==0.0
        bb2 = fl!=0.0
        bb1 = fl==fval
        bb2 = fl!=fval

        bb1 = bb2==uw[cx16.r0L]
        bb2 = bb1!=uw[cx16.r1L]
    }

    sub efficient_compare_0() {
        ubyte @shared ub
        uword @shared uw
        float @shared fl

        if ub==0
            cx16.r0L++
        if uw==0
            cx16.r0L++
        if fl==0
            cx16.r0L++
        if ub!=0
            cx16.r0L++
        else
            cx16.r1L++
        if uw!=0
            cx16.r0L++
        else
            cx16.r1L++
        if fl!=0
            cx16.r0L++
        else
            cx16.r1L++
    }

    sub efficient_compare_99() {
        ubyte @shared ub
        uword @shared uw
        float @shared fl

        if ub==99
            cx16.r0L++
        if uw==99
            cx16.r0L++
        if fl==99.99
            cx16.r0L++
        if ub!=99
            cx16.r0L++
        else
            cx16.r1L++
        if uw!=99
            cx16.r0L++
        else
            cx16.r1L++
        if fl!=99.99
            cx16.r0L++
        else
            cx16.r1L++
    }

    sub efficient_compare_var() {
        ubyte @shared ub
        uword @shared uw
        float @shared fl
        float @shared fval

        if ub==cx16.r0L
            cx16.r0L++
        if uw==cx16.r0
            cx16.r0L++
        if fl==fval
            cx16.r0L++
        if fl==0.0
            cx16.r0L++
        if ub!=cx16.r0L
            cx16.r0L++
        else
            cx16.r1L++
        if uw!=cx16.r0
            cx16.r0L++
        else
            cx16.r1L++
        if fl!=fval
            cx16.r0L++
        else
            cx16.r1L++
        if fl!=0.0
            cx16.r0L++
        else
            cx16.r1L++

        if ub==uw[cx16.r0L]
            cx16.r0L++
        if ub!=uw[cx16.r0L]
            cx16.r0L++

        if staticbool2 or staticbool1
            cx16.r0L++
    }

    sub logical_operand_swap() {
        bool[] ba = [true, false, true]
        bool @shared bb = staticbool1 xor ba[0]
        ubyte @shared zz
        cx16.r0L = 99+zz
    }

    sub boolean_const_and_var(bool barg) {
        const bool bconst1 = true
        const bool bconst2 = false
        bool @shared bvar1 = bconst1 or bconst2
        bool @shared bvar2
        bool @shared bvar3 = true
        ; should print: 101101
        txt.print_ub(staticbool1 as ubyte)
        txt.print_ub(staticbool2 as ubyte)
        txt.print_ub(barg as ubyte)
        txt.print_ub(bvar1 as ubyte)
        txt.print_ub(bvar2 as ubyte)
        txt.print_ub(bvar3 as ubyte)
        txt.nl()
    }

    sub boolean_arrays_and_return() -> bool {
        bool[] barr1 = [ true, false, true ]
        bool[3] barr2 = true
        bool zz
        ; should print: 101, 111
        for zz in barr1
            txt.print_ub(zz as ubyte)
        txt.nl()
        for zz in barr2
            txt.print_ub(zz as ubyte)
        txt.nl()
        return false
    }

    sub and_or_xor_not() {
        bool @shared btrue1 = true
        bool @shared bfalse1 = false
        bool @shared btrue2 = true
        bool @shared bfalse2 = false
        bool @shared bb
        staticbool2 = staticbool1 and bb
        staticbool1 = staticbool2 or bb
        staticbool2 = staticbool1 xor bb
        staticbool1 = staticbool1 and bb
        staticbool2 = staticbool2 or bb
        staticbool1 = staticbool1 xor bb
        txt.print_ub((bb and true) as ubyte)
        txt.print_ub((bb or true) as ubyte)
        txt.print_ub((bb xor true) as ubyte)
        txt.nl()
        if not(btrue1 and btrue2)
            txt.print("fail1\n")
        if btrue1 and bfalse2
            txt.print("fail2\n")
        if bfalse1 or bfalse2
            txt.print("fail3\n")
        if not(btrue1 or btrue2)
            txt.print("fail4\n")
        if btrue1 xor btrue2
            txt.print("fail5\n")
        if not(bfalse1 xor btrue1)
            txt.print("fail6\n")

        bb = false
        bb = bb or btrue1
        if not bb
            txt.print("fail7\n")
        bb = bb and bfalse1
        if bb
            txt.print("fail8\n")
        bb = bb xor btrue1
        if not bb
            txt.print("fail9\n")
    }

;    sub bitwise_on_bools_errors() {
;        bool bb1
;        bool bb2
;        bb2 = bb1 | staticbool1
;        bb1 = bb2 & staticbool1
;        bb2 = bb1 ^ staticbool1
;        bb1 = bb1 | staticbool1
;        bb2 = bb2 & staticbool1
;        bb1 = bb1 ^ staticbool1
;        bb2 = ~ staticbool1
;        voidfuncub(bb1 | staticbool1)
;    }
;
;    sub arith_on_bools_errors() {
;        bool @shared bb1
;        bool @shared bb2
;        bb2 = bb1 + staticbool1
;        bb1 = bb2 * staticbool1
;        bb2 = staticbool1 * staticbool1
;        voidfuncub(bb1 + staticbool1)
;    }
;
;    sub logical_on_ints_errors() {
;        ubyte @shared ub1
;        ubyte @shared ub2
;        staticbool1 = ub1 and ub2
;        voidfuncub(ub1 xor ub2)
;    }
;
;    sub bools_in_intarray_errors() {
;        ubyte[3] arr1 = true
;        ubyte[3] arr2 = [1, true, 2]
;    }
;
;    sub ints_in_boolarray_errors() {
;        ;; bool[3] arr1 = 42
;        bool[3] arr2 = [1, true, 2]
;    }
;
;    sub while_until_int_errors() {
;;        while staticbool1==42 {
;;            cx16.r0L++
;;        }
;;
;;        do {
;;            cx16.r0L++
;;        } until staticbool1==42
;
;        ubyte @shared ub1
;
;        while not ub1 {
;            cx16.r0L++
;        }
;
;        while intfunc() {
;            cx16.r0L++
;        }
;
;        while not intfunc() {
;            cx16.r0L++
;        }
;
;;        while not cx16.mouse_pos() {
;;            cx16.r0L++
;;        }
;    }

    sub while_equiv() {
        ubyte @shared ub
        bool @shared bb

        while bb {
            cx16.r0L++
        }
        while ub!=0 {
            cx16.r0L++
        }
        while not bb {
            cx16.r0L++
        }
        while ub==0 {
            cx16.r0L++
        }
    }

    sub bools_in_array_assigns() {
        bool[] ba = [true, false, true]
        ba[1] = ba[0] xor staticbool2
        ba[2] = staticbool2 xor ba[0]
        ba[1] = ba[0] and staticbool2
        ba[2] = staticbool2 and ba[0]
        ba[1] = ba[0] or staticbool2
        ba[2] = staticbool2 or ba[0]
        ba[1] = not staticbool2

        ba[1] = ba[0] xor ba[2]
        ba[2] = ba[0] and ba[1]
        ba[1] = ba[0] or ba[2]
        ba[1] = not ba[2]
        ba[1] = ba[0] xor ba[cx16.r0L]
        ba[2] = ba[0] and ba[cx16.r0L]
        ba[1] = ba[0] or ba[cx16.r0L]
        ba[1] = not ba[cx16.r0L]

        ubyte[] uba = [11,22,33]
        uba[1] = uba[0] ^ uba[2]
        uba[2] = uba[0] & uba[1]
        uba[1] = uba[0] | uba[2]
        uba[1] = ~uba[2]
        uba[1] = uba[0] ^ uba[cx16.r0L]
        uba[2] = uba[0] & uba[cx16.r0L]
        uba[1] = uba[0] | uba[cx16.r0L]
        uba[1] = ~uba[cx16.r0L]
    }

    sub bools_in_array_assigns_inplace() {
        bool[] ba = [true, false, true]
        cx16.r0L++
        ba[1] = ba[1] xor staticbool2
        ba[2] = staticbool2 xor ba[2]
        ba[1] = ba[1] and staticbool2
        ba[2] = staticbool2 and ba[2]
        ba[1] = ba[1] or staticbool2
        ba[2] = staticbool2 or ba[2]

        ba[2] = ba[2] xor ba[1]
        ba[1] = ba[1] and ba[2]
        ba[2] = ba[2] or ba[1]
        ba[2] = not ba[2]
        ba[2] = ba[2] xor ba[cx16.r0L]
        ba[1] = ba[1] and ba[cx16.r0L]
        ba[2] = ba[2] or ba[cx16.r0L]

        ubyte[] uba = [11,22,33]
        uba[2] = uba[2] ^ uba[1]
        uba[1] = uba[1] & uba[2]
        uba[2] = uba[2] | uba[1]
        uba[2] = ~ uba[2]
        uba[2] = uba[2] ^ uba[cx16.r0L]
        uba[1] = uba[1] & uba[cx16.r0L]
        uba[2] = uba[2] | uba[cx16.r0L]
    }

    sub if_gotos() {
            ubyte @shared ub

            if_cc
                goto label
            if_cc
                goto label
            else
                cx16.r0L++
            if_cs
                goto label
            else
                cx16.r0L++


            if not ub==99
                goto label
            if ub==0
                goto label
            if ub!=0
                goto label

            if not ub==98
                goto label
            else
                cx16.r0L++
            if ub==0
                goto label
            else
                cx16.r0L++
            if ub!=0
                goto label
            else
                cx16.r0L++

            if staticbool1
                goto label
            if not staticbool2
                goto label
    label:
    }

    sub if_code() {
        ubyte @shared ub
        byte @shared sb
        bool @shared bb
        if ub==0
            cx16.r0L++
        if ub!=0
            cx16.r0L++
        if bb
            cx16.r0L++
        if not bb
            cx16.r0L++

        if ub==0
            cx16.r0L++
        else
            cx16.r0--
        if ub!=0
            cx16.r0L++
        else
            cx16.r0--
        if bb
            cx16.r0L++
        else
            cx16.r0--
        if not bb
            cx16.r0L++
        else
            cx16.r0--

        if ub>10
            cx16.r0++
        if ub>=10
            cx16.r0++
        if ub<10
            cx16.r0++
        if ub<=10
            cx16.r0++
        if sb>10
            cx16.r0++
        if sb>=10
            cx16.r0++
        if sb<10
            cx16.r0++
        if sb<=10
            cx16.r0++
    }

    sub intfunc() -> ubyte {
        return cx16.r0L
    }

    sub voidfuncub(ubyte arg) {
        cx16.r0L++
    }
}
