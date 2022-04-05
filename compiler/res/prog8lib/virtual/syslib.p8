; Prog8 definitions for the Virtual Machine
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;

sys {
    ; ------- lowlevel system routines --------

    const ubyte target = 255         ;  compilation target specifier.  64 = C64, 128 = C128,  16 = CommanderX16, 8 = atari800XL, 255 = virtual

    ; SYSCALLS
    ; 0 = reset ; resets system
    ; 1 = exit ; stops program and returns statuscode from r0.w
    ; 2 = print_c ; print single character
    ; 3 = print_s ; print 0-terminated string from memory
    ; 4 = print_u8 ; print unsigned int byte
    ; 5 = print_u16 ; print unsigned int word
    ; 6 = input ; reads a line of text entered by the user, r0.w = memory buffer, r1.b = maxlength (0-255, 0=unlimited).  Zero-terminates the string. Returns length in r65535.w
    ; 7 = sleep ; sleep amount of milliseconds
    ; 8 = gfx_enable  ; enable graphics window  r0.b = 0 -> lores 320x240,  r0.b = 1 -> hires 640x480
    ; 9 = gfx_clear   ; clear graphics window with shade in r0.b
    ; 10 = gfx_plot   ; plot pixel in graphics window, r0.w/r1.w contain X and Y coordinates, r2.b contains brightness

    const ubyte SC_RESET = 0
    const ubyte SC_EXIT = 1
    const ubyte SC_PRINT_C = 2
    const ubyte SC_PRINT_S = 3
    const ubyte SC_PRINT_U8 = 4
    const ubyte SC_PRINT_u16 = 5
    const ubyte SC_INPUT = 6
    const ubyte SC_SLEEP = 7
    const ubyte SC_GFX_ENABLE = 8
    const ubyte SC_GFX_CLEAR = 9
    const ubyte SC_GFX_PLOT = 10
    const ubyte SC_RND = 11
    const ubyte SC_WAIT = 12
    const ubyte SC_WAITVSYNC = 13


    sub  reset_system()  {
        ; Soft-reset the system back to initial power-on Basic prompt.
        void syscall(SC_RESET)
    }

    sub wait(uword jiffies) {
        ; --- wait approximately the given number of jiffies (1/60th seconds)
        void syscall1(SC_WAIT, jiffies)
    }

    sub waitvsync() {
        ; --- busy wait till the next vsync has occurred (approximately), without depending on custom irq handling.
        void syscall(SC_WAITVSYNC)
    }

    sub memcopy(uword source, uword target, uword count)  {
        repeat count {
            @(target) = @(source)
            source++
            target++
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
        void syscall1(SC_EXIT, returnvalue)
    }
}

cx16 {

    ; the sixteen virtual 16-bit registers that the CX16 has defined in the zeropage
    ; they are simulated on the VirtualMachine as well but their location in memory is different
; the sixteen virtual 16-bit registers in both normal unsigned mode and signed mode (s)
    &uword r0  = $0002
    &uword r1  = $0004
    &uword r2  = $0006
    &uword r3  = $0008
    &uword r4  = $000a
    &uword r5  = $000c
    &uword r6  = $000e
    &uword r7  = $0010
    &uword r8  = $0012
    &uword r9  = $0014
    &uword r10 = $0016
    &uword r11 = $0018
    &uword r12 = $001a
    &uword r13 = $001c
    &uword r14 = $001e
    &uword r15 = $0020

    &word r0s  = $0002
    &word r1s  = $0004
    &word r2s  = $0006
    &word r3s  = $0008
    &word r4s  = $000a
    &word r5s  = $000c
    &word r6s  = $000e
    &word r7s  = $0010
    &word r8s  = $0012
    &word r9s  = $0014
    &word r10s = $0016
    &word r11s = $0018
    &word r12s = $001a
    &word r13s = $001c
    &word r14s = $001e
    &word r15s = $0020

    &ubyte r0L  = $0002
    &ubyte r1L  = $0004
    &ubyte r2L  = $0006
    &ubyte r3L  = $0008
    &ubyte r4L  = $000a
    &ubyte r5L  = $000c
    &ubyte r6L  = $000e
    &ubyte r7L  = $0010
    &ubyte r8L  = $0012
    &ubyte r9L  = $0014
    &ubyte r10L = $0016
    &ubyte r11L = $0018
    &ubyte r12L = $001a
    &ubyte r13L = $001c
    &ubyte r14L = $001e
    &ubyte r15L = $0020

    &ubyte r0H  = $0003
    &ubyte r1H  = $0005
    &ubyte r2H  = $0007
    &ubyte r3H  = $0009
    &ubyte r4H  = $000b
    &ubyte r5H  = $000d
    &ubyte r6H  = $000f
    &ubyte r7H  = $0011
    &ubyte r8H  = $0013
    &ubyte r9H  = $0015
    &ubyte r10H = $0017
    &ubyte r11H = $0019
    &ubyte r12H = $001b
    &ubyte r13H = $001d
    &ubyte r14H = $001f
    &ubyte r15H = $0021

    &byte r0sL  = $0002
    &byte r1sL  = $0004
    &byte r2sL  = $0006
    &byte r3sL  = $0008
    &byte r4sL  = $000a
    &byte r5sL  = $000c
    &byte r6sL  = $000e
    &byte r7sL  = $0010
    &byte r8sL  = $0012
    &byte r9sL  = $0014
    &byte r10sL = $0016
    &byte r11sL = $0018
    &byte r12sL = $001a
    &byte r13sL = $001c
    &byte r14sL = $001e
    &byte r15sL = $0020

    &byte r0sH  = $0003
    &byte r1sH  = $0005
    &byte r2sH  = $0007
    &byte r3sH  = $0009
    &byte r4sH  = $000b
    &byte r5sH  = $000d
    &byte r6sH  = $000f
    &byte r7sH  = $0011
    &byte r8sH  = $0013
    &byte r9sH  = $0015
    &byte r10sH = $0017
    &byte r11sH = $0019
    &byte r12sH = $001b
    &byte r13sH = $001d
    &byte r14sH = $001f
    &byte r15sH = $0021
}
