; Prog8 definitions for the Virtual Machine

%option ignore_unused

sys {
    ; ------- lowlevel system routines --------

    const ubyte target = 255         ;  compilation target specifier.  255=virtual, 128=C128, 64=C64, 32=PET, 16=CommanderX16, 8=atari800XL, 7=Neo6502

    const ubyte SIZEOF_BOOL  = sizeof(bool)
    const ubyte SIZEOF_BYTE  = sizeof(byte)
    const ubyte SIZEOF_UBYTE = sizeof(ubyte)
    const ubyte SIZEOF_WORD  = sizeof(word)
    const ubyte SIZEOF_UWORD = sizeof(uword)
    const ubyte SIZEOF_LONG  = sizeof(long)
    const ubyte SIZEOF_POINTER = sizeof(&sys.wait)
    const ubyte SIZEOF_FLOAT = sizeof(float)
    const byte  MIN_BYTE     = -128
    const byte  MAX_BYTE     = 127
    const ubyte MIN_UBYTE    = 0
    const ubyte MAX_UBYTE    = 255
    const word  MIN_WORD     = -32768
    const word  MAX_WORD     = 32767
    const uword MIN_UWORD    = 0
    const uword MAX_UWORD    = 65535
    ; MIN_FLOAT and MAX_FLOAT are defined in the floats module if imported


    sub  reset_system()  {
        ; Soft-reset the system back to initial power-on Basic prompt.
        %ir {{
            syscall 0 ()
        }}
    }

    sub wait(uword jiffies) {
        ; --- wait approximately the given number of jiffies (1/60th seconds)
        %ir {{
            loadm.w r99000,sys.wait.jiffies
            syscall 13 (r99000.w)
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
        %ir {{
            loadm.w r99000,sys.internal_stringcopy.source
            loadm.w r99001,sys.internal_stringcopy.tgt
            syscall 39 (r99000.w, r99001.w): r99100.b
        }}
    }

    sub memcopy(uword source, uword tgt, uword count)  {
        %ir {{
            loadm.w r99000,sys.memcopy.source
            loadm.w r99001,sys.memcopy.tgt
            loadm.w r99002,sys.memcopy.count
            syscall 36 (r99000.w, r99001.w, r99002.w)
        }}
    }

    sub memset(uword mem, uword numbytes, ubyte value)  {
        %ir {{
            loadm.w r99000,sys.memset.mem
            loadm.w r99001,sys.memset.numbytes
            loadm.b r99100,sys.memset.value
            syscall 37 (r99000.w, r99001.w, r99100.b)
        }}
    }

    sub memsetw(uword mem, uword numwords, uword value)  {
        %ir {{
            loadm.w r99000,sys.memsetw.mem
            loadm.w r99001,sys.memsetw.numwords
            loadm.w r99002,sys.memsetw.value
            syscall 38 (r99000.w, r99001.w, r99002.w)
        }}
    }

    sub memcmp(uword address1, uword address2, uword size) -> byte {
        ; Compares two blocks of memory of up to 65535 bytes in size
        ; Returns -1 (255), 0 or 1, meaning: block 1 sorts before, equal or after block 2.
        %ir {{
            loadm.w r99000,sys.memcmp.address1
            loadm.w r99001,sys.memcmp.address2
            loadm.w r99002,sys.memcmp.size
            syscall 47 (r99000.w, r99001.w, r99002.w) : r99100.b
            returnr.b r99100
        }}
    }

    sub exit(ubyte returnvalue) {
        ; -- immediately exit the program with a return code in the A register
        %ir {{
            loadm.b r99100,sys.exit.returnvalue
            syscall 1 (r99100.b)
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

    sub set_irqd() {
        %ir {{
            sei
        }}
    }

    sub clear_irqd() {
        %ir {{
            cli
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
            loadm.b r99100,sys.gfx_enable.mode
            syscall 8 (r99100.b)
        }}
    }

    sub gfx_clear(ubyte color) {
        %ir {{
            loadm.b r99100,sys.gfx_clear.color
            syscall 9 (r99100.b)
        }}
    }

    sub gfx_plot(uword xx, uword yy, ubyte color) {
        %ir {{
            loadm.w r99000,sys.gfx_plot.xx
            loadm.w r99001,sys.gfx_plot.yy
            loadm.b r99100,sys.gfx_plot.color
            syscall 10 (r99000.w, r99001.w, r99100.b)
        }}
    }

    sub gfx_getpixel(uword xx, uword yy) -> ubyte {
        %ir {{
            loadm.w r99000,sys.gfx_getpixel.xx
            loadm.w r99001,sys.gfx_getpixel.yy
            syscall 17 (r99000.w, r99001.w): r99100.b
            returnr.b r99100
        }}
    }

    sub push(ubyte b) {
        ; note: this *should* be inlined, however since the VM has separate program counter and value stacks, this also works
        %ir {{
            loadm.b r99100,sys.push.b
            push.b r99100
        }}
    }

    sub pushw(uword w) {
        ; note: this *should* be inlined, however since the VM has separate program counter and value stacks, this also works
        %ir {{
            loadm.w r99000,sys.pushw.w
            push.w r99000
        }}
    }

    sub push_returnaddress(uword w) {
        ; note: this actually doesn't do anything useful on the VM because the code execution doesn't use the simulated cpu stack
        %ir {{
            loadm.w r99000,sys.pushw.w
            push.w r99000
        }}
    }

    sub get_as_returnaddress(uword address) -> uword {
        ; return the address like JSR would push onto the stack:  address-1,  MSB first then LSB
        address--
        return mkword(lsb(address), msb(address))
    }

    sub pop() -> ubyte {
        ; note: this *should* be inlined, however since the VM has separate program counter and value stacks, this also works
        %ir {{
            pop.b r99100
            returnr.b r99100
        }}
    }

    sub popw() -> uword {
        ; note: this *should* be inlined, however since the VM has separate program counter and value stacks, this also works
        %ir {{
            pop.w r99000
            returnr.w r99000
        }}
    }

    sub read_flags() -> ubyte {
        ; "simulate" the 6502 status register a little bit
        if_neg {
            if_z
                cx16.r0L = %10000010
            else
                cx16.r0L = %10000000
        }
        else {
            if_z
                cx16.r0L = %00000010
            else
                cx16.r0L = %00000000
        }

        if_cs
            cx16.r0L |= 1
        if_vs
            cx16.r0L |= %01000000

        return cx16.r0L
    }
}

cx16 {

    ; the sixteen virtual 16-bit registers that the Commander X16 has defined in the zeropage
    ; they are on the VirtualMachine as well, but their location in memory is different
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
