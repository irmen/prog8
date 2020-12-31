%import test_stack
%import textio
%import diskio
%zeropage basicsafe
%option no_sysinit

main {

    sub start () {

        uword large_buffer_ptr = progend()
        ubyte[256] buffer
        uword size

        if diskio.f_open(8, "rom.asm2") {
            txt.print("read-exact\n")
            c64.SETTIM(0,0,0)
            size = 0
            while not c64.READST() {
                diskio.f_read_exact(&buffer, len(buffer))
                size += 256
            }

            diskio.f_close()
            txt.print_uw(size)
            txt.chrout('\n')
            print_time()
            txt.chrout('\n')
        } else
            txt.print("can't open file!\n")
        txt.print(diskio.status(8))
        txt.chrout('\n')


        if diskio.f_open(8, "rom.asm2") {
            txt.print("read-all\n")
            c64.SETTIM(0,0,0)
            size = 0
            size = diskio.f_read_all(large_buffer_ptr)
            diskio.f_close()
            txt.print_uw(size)
            txt.chrout('\n')
            print_time()
            txt.chrout('\n')
        } else
            txt.print("can't open file!\n")
        txt.print(diskio.status(8))
        txt.chrout('\n')

;        if diskio.f_open(8, "rom.asm") {
;            txt.print("read\n")
;            c64.SETTIM(0,0,0)
;            size = 0
;            while not c64.READST() {
;                size += diskio.f_read(&buffer, len(buffer))
;            }
;
;            diskio.f_close()
;            txt.print_uw(size)
;            txt.chrout('\n')
;            print_time()
;            txt.chrout('\n')
;        }

        test_stack.test()

    }


    sub print_time() {
        ubyte time_lo
        ubyte time_mid
        ubyte time_hi

        %asm {{
            stx  P8ZP_SCRATCH_REG
            jsr  c64.RDTIM      ; A/X/Y
            sta  time_lo
            stx  time_mid
            sty  time_hi
            ldx  P8ZP_SCRATCH_REG
        }}

        txt.print_ub(time_hi)
        txt.chrout(':')
        txt.print_ub(time_mid)
        txt.chrout(':')
        txt.print_ub(time_lo)
        txt.chrout('\n')
    }
}
