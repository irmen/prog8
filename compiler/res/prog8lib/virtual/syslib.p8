; Prog8 definitions for the Virtual Machine

sys {
    ; ------- lowlevel system routines --------

    const ubyte target = 255         ;  compilation target specifier.  64 = C64, 128 = C128,  16 = CommanderX16, 8 = atari800XL, 255 = virtual

    sub  reset_system()  {
        ; Soft-reset the system back to initial power-on Basic prompt.
        %ir {{
            syscall 0 ()
        }}
    }

    sub wait(uword jiffies) {
        ; --- wait approximately the given number of jiffies (1/60th seconds)
        %ir {{
            loadm.w r65535,sys.wait.jiffies
            syscall 13 (r65535.w)
        }}
    }

    sub waitvsync() {
        ; --- busy wait till the next vsync has occurred (approximately), without depending on custom irq handling.
        %ir {{
            syscall 14()
        }}
    }

    sub internal_stringcopy(uword source, uword tgt) {
        ; Called when the compiler wants to assign a string value to another string.
        while @(source) {
            @(tgt) = @(source)
            source++
            tgt++
        }
        @(tgt)=0
    }

    sub memcopy(uword source, uword tgt, uword count)  {
        repeat count {
            @(tgt) = @(source)
            source++
            tgt++
        }
    }

    sub memset(uword mem, uword numbytes, ubyte value)  {
        repeat numbytes {
            @(mem) = value
            mem++
        }
    }

    sub memsetw(uword mem, uword numwords, uword value)  {
        repeat numwords {
            pokew(mem, value)
            mem+=2
        }
    }

    sub exit(ubyte returnvalue) {
        ; -- immediately exit the program with a return code in the A register
        %ir {{
            loadm.b r65535,sys.exit.returnvalue
            syscall 1 (r65535.b)
        }}
    }

    sub set_carry() {
        %ir {{
            sec
        }}
    }

    sub clear_carry() {
        %ir {{
            clc
        }}
    }

    sub disable_caseswitch() {
        ; no-op
    }

    sub enable_caseswitch() {
        ; no-op
    }

    sub save_prog8_internals() {
        ; no-op
    }

    sub restore_prog8_internals() {
        ; no-op
    }

    sub gfx_enable(ubyte mode) {
        %ir {{
            loadm.b r65535,sys.gfx_enable.mode
            syscall 8 (r65535.b)
        }}
    }

    sub gfx_clear(ubyte color) {
        %ir {{
            loadm.b r65535,sys.gfx_clear.color
            syscall 9 (r65535.b)
        }}
    }

    sub gfx_plot(uword xx, uword yy, ubyte color) {
        %ir {{
            loadm.w r65533,sys.gfx_plot.xx
            loadm.w r65534,sys.gfx_plot.yy
            loadm.b r65535,sys.gfx_plot.color
            syscall 10 (r65533.w, r65534.w, r65535.b)
        }}
    }

    sub gfx_getpixel(uword xx, uword yy) -> ubyte {
        %ir {{
            loadm.w r65534,sys.gfx_getpixel.xx
            loadm.w r65535,sys.gfx_getpixel.yy
            syscall 30 (r65534.w, r65535.w): r0.b
            returnr.b r0
        }}
    }

    sub push(ubyte b) {
        ; note: this *should* be inlined, however since the VM has separate program counter and value stacks, this also works
        %ir {{
            loadm.b r65535,sys.push.b
            push.b r65535
        }}
    }

    sub pushw(uword w) {
        ; note: this *should* be inlined, however since the VM has separate program counter and value stacks, this also works
        %ir {{
            loadm.w r65535,sys.pushw.w
            push.w r65535
        }}
    }

    sub pop() -> ubyte {
        ; note: this *should* be inlined, however since the VM has separate program counter and value stacks, this also works
        %ir {{
            pop.b r65535
            returnr.b r65535
        }}
    }

    sub popw() -> uword {
        ; note: this *should* be inlined, however since the VM has separate program counter and value stacks, this also works
        %ir {{
            pop.w r65535
            returnr.w r65535
        }}
    }
}

cx16 {

    ; the sixteen virtual 16-bit registers that the CX16 has defined in the zeropage
    ; they are simulated on the VirtualMachine as well but their location in memory is different
    &uword r0  = $ff02
    &uword r1  = $ff04
    &uword r2  = $ff06
    &uword r3  = $ff08
    &uword r4  = $ff0a
    &uword r5  = $ff0c
    &uword r6  = $ff0e
    &uword r7  = $ff10
    &uword r8  = $ff12
    &uword r9  = $ff14
    &uword r10 = $ff16
    &uword r11 = $ff18
    &uword r12 = $ff1a
    &uword r13 = $ff1c
    &uword r14 = $ff1e
    &uword r15 = $ff20

    &word r0s  = $ff02
    &word r1s  = $ff04
    &word r2s  = $ff06
    &word r3s  = $ff08
    &word r4s  = $ff0a
    &word r5s  = $ff0c
    &word r6s  = $ff0e
    &word r7s  = $ff10
    &word r8s  = $ff12
    &word r9s  = $ff14
    &word r10s = $ff16
    &word r11s = $ff18
    &word r12s = $ff1a
    &word r13s = $ff1c
    &word r14s = $ff1e
    &word r15s = $ff20

    &ubyte r0L  = $ff02
    &ubyte r1L  = $ff04
    &ubyte r2L  = $ff06
    &ubyte r3L  = $ff08
    &ubyte r4L  = $ff0a
    &ubyte r5L  = $ff0c
    &ubyte r6L  = $ff0e
    &ubyte r7L  = $ff10
    &ubyte r8L  = $ff12
    &ubyte r9L  = $ff14
    &ubyte r10L = $ff16
    &ubyte r11L = $ff18
    &ubyte r12L = $ff1a
    &ubyte r13L = $ff1c
    &ubyte r14L = $ff1e
    &ubyte r15L = $ff20

    &ubyte r0H  = $ff03
    &ubyte r1H  = $ff05
    &ubyte r2H  = $ff07
    &ubyte r3H  = $ff09
    &ubyte r4H  = $ff0b
    &ubyte r5H  = $ff0d
    &ubyte r6H  = $ff0f
    &ubyte r7H  = $ff11
    &ubyte r8H  = $ff13
    &ubyte r9H  = $ff15
    &ubyte r10H = $ff17
    &ubyte r11H = $ff19
    &ubyte r12H = $ff1b
    &ubyte r13H = $ff1d
    &ubyte r14H = $ff1f
    &ubyte r15H = $ff21

    &byte r0sL  = $ff02
    &byte r1sL  = $ff04
    &byte r2sL  = $ff06
    &byte r3sL  = $ff08
    &byte r4sL  = $ff0a
    &byte r5sL  = $ff0c
    &byte r6sL  = $ff0e
    &byte r7sL  = $ff10
    &byte r8sL  = $ff12
    &byte r9sL  = $ff14
    &byte r10sL = $ff16
    &byte r11sL = $ff18
    &byte r12sL = $ff1a
    &byte r13sL = $ff1c
    &byte r14sL = $ff1e
    &byte r15sL = $ff20

    &byte r0sH  = $ff03
    &byte r1sH  = $ff05
    &byte r2sH  = $ff07
    &byte r3sH  = $ff09
    &byte r4sH  = $ff0b
    &byte r5sH  = $ff0d
    &byte r6sH  = $ff0f
    &byte r7sH  = $ff11
    &byte r8sH  = $ff13
    &byte r9sH  = $ff15
    &byte r10sH = $ff17
    &byte r11sH = $ff19
    &byte r12sH = $ff1b
    &byte r13sH = $ff1d
    &byte r14sH = $ff1f
    &byte r15sH = $ff21

    sub save_virtual_registers() {
        uword[32] storage
        storage[0] = r0
        storage[1] = r1
        storage[2] = r2
        storage[3] = r3
        storage[4] = r4
        storage[5] = r5
        storage[6] = r6
        storage[7] = r7
        storage[8] = r8
        storage[9] = r9
        storage[10] = r10
        storage[11] = r11
        storage[12] = r12
        storage[13] = r13
        storage[14] = r14
        storage[15] = r15
    }

    sub restore_virtual_registers() {
        r0 = cx16.save_virtual_registers.storage[0]
        r1 = cx16.save_virtual_registers.storage[1]
        r2 = cx16.save_virtual_registers.storage[2]
        r3 = cx16.save_virtual_registers.storage[3]
        r4 = cx16.save_virtual_registers.storage[4]
        r5 = cx16.save_virtual_registers.storage[5]
        r6 = cx16.save_virtual_registers.storage[6]
        r7 = cx16.save_virtual_registers.storage[7]
        r8 = cx16.save_virtual_registers.storage[8]
        r9 = cx16.save_virtual_registers.storage[9]
        r10 = cx16.save_virtual_registers.storage[10]
        r11 = cx16.save_virtual_registers.storage[11]
        r12 = cx16.save_virtual_registers.storage[12]
        r13 = cx16.save_virtual_registers.storage[13]
        r14 = cx16.save_virtual_registers.storage[14]
        r15 = cx16.save_virtual_registers.storage[15]
    }
}
