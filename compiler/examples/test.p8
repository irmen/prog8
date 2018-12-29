%import c64utils
%option enable_floats

~ main {

    ubyte[3] ubarray = [11,55,222]
    byte[3] barray = [-11,-22,-33]
    uword[3] uwarray = [111,2222,55555]
    word[3] warray = [-111,-222,-555]
    float[3] farray = [1.11, 2.22, -3.33]
    str text = "hello\n"

    sub start()  {


rpt:
        vm_write_str("\nregular for loop byte\n")
        for ubyte x in 10 to 15 {
            vm_write_num(x)
            vm_write_char(',')
        }
        vm_write_str("\nregular for loop word\n")
        for uword y in 500 to 505 {
            vm_write_num(y)
            vm_write_char(',')
        }

        vm_write_str("\nloop str\n")
        for ubyte c in text {
            vm_write_num(c)
            vm_write_char(',')
        }

        vm_write_str("\nloop ub\n")
        for ubyte ub in ubarray{
            vm_write_num(ub)
            vm_write_char(',')
        }

        vm_write_str("\nloop b\n")
        for byte b in barray {
            vm_write_num(b)
            vm_write_char(',')
        }

        vm_write_str("\nloop uw\n")
        for uword uw in uwarray {
            vm_write_num(uw)
            vm_write_char(',')
        }

        vm_write_str("\nloop w\n")
        for word w in warray {
            vm_write_num(w)
            vm_write_char(',')
        }

        vm_write_str("\nloop f\n")
        for float f in farray {
            vm_write_num(f)
            vm_write_char(',')
        }

        goto rpt


ending:
        vm_write_str("\nending\n")
    }
}
