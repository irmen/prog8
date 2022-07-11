; Prog8 definitions for the Commodore-128
; Including memory registers, I/O registers, Basic and Kernal subroutines.

c64 {

        &ubyte  TIME_HI         = $a0       ; software jiffy clock, hi byte
        &ubyte  TIME_MID        = $a1       ;  .. mid byte
        &ubyte  TIME_LO         = $a2       ;    .. lo byte. Updated by IRQ every 1/60 sec
        &ubyte  STATUS          = $90       ; kernal status variable for I/O
        &ubyte  STKEY           = $91       ; various keyboard statuses (updated by IRQ)
        ;;&ubyte  SFDX            = $cb       ; current key pressed (matrix value) (updated by IRQ)     // TODO c128 ??

        &ubyte  COLOR           = $00f1     ; cursor color
        ;;&ubyte  HIBASE          = $0288     ; screen base address / 256 (hi-byte of screen memory address)        // TODO c128 ??
        &uword  CINV            = $0314     ; IRQ vector (in ram)
        &uword  CBINV           = $0316     ; BRK vector (in ram)
        &uword  NMINV           = $0318     ; NMI vector (in ram)
        &uword  NMI_VEC         = $FFFA     ; 6502 nmi vector, determined by the kernal if banked in
        &uword  RESET_VEC       = $FFFC     ; 6502 reset vector, determined by the kernal if banked in
        &uword  IRQ_VEC         = $FFFE     ; 6502 interrupt vector, determined by the kernal if banked in

        ; the default addresses for the character screen chars and colors
        const  uword  Screen    = $0400     ; to have this as an array[40*25] the compiler would have to support array size > 255
        const  uword  Colors    = $d800     ; to have this as an array[40*25] the compiler would have to support array size > 255

        ; the default locations of the 8 sprite pointers (store address of sprite / 64)
        &ubyte  SPRPTR0         = 2040
        &ubyte  SPRPTR1         = 2041
        &ubyte  SPRPTR2         = 2042
        &ubyte  SPRPTR3         = 2043
        &ubyte  SPRPTR4         = 2044
        &ubyte  SPRPTR5         = 2045
        &ubyte  SPRPTR6         = 2046
        &ubyte  SPRPTR7         = 2047
        &ubyte[8]  SPRPTR       = 2040      ; the 8 sprite pointers as an array.


; ---- VIC-II 6567/6569/856x registers ----

        &ubyte  SP0X            = $d000
        &ubyte  SP0Y            = $d001
        &ubyte  SP1X            = $d002
        &ubyte  SP1Y            = $d003
        &ubyte  SP2X            = $d004
        &ubyte  SP2Y            = $d005
        &ubyte  SP3X            = $d006
        &ubyte  SP3Y            = $d007
        &ubyte  SP4X            = $d008
        &ubyte  SP4Y            = $d009
        &ubyte  SP5X            = $d00a
        &ubyte  SP5Y            = $d00b
        &ubyte  SP6X            = $d00c
        &ubyte  SP6Y            = $d00d
        &ubyte  SP7X            = $d00e
        &ubyte  SP7Y            = $d00f
        &ubyte[16]  SPXY        = $d000        ; the 8 sprite X and Y registers as an array.
        &uword[8]  SPXYW        = $d000        ; the 8 sprite X and Y registers as a combined xy word array.

        &ubyte  MSIGX           = $d010
        &ubyte  SCROLY          = $d011
        &ubyte  RASTER          = $d012
        &ubyte  LPENX           = $d013
        &ubyte  LPENY           = $d014
        &ubyte  SPENA           = $d015
        &ubyte  SCROLX          = $d016
        &ubyte  YXPAND          = $d017
        &ubyte  VMCSB           = $d018
        &ubyte  VICIRQ          = $d019
        &ubyte  IREQMASK        = $d01a
        &ubyte  SPBGPR          = $d01b
        &ubyte  SPMC            = $d01c
        &ubyte  XXPAND          = $d01d
        &ubyte  SPSPCL          = $d01e
        &ubyte  SPBGCL          = $d01f

        &ubyte  EXTCOL          = $d020        ; border color
        &ubyte  BGCOL0          = $d021        ; screen color
        &ubyte  BGCOL1          = $d022
        &ubyte  BGCOL2          = $d023
        &ubyte  BGCOL4          = $d024
        &ubyte  SPMC0           = $d025
        &ubyte  SPMC1           = $d026
        &ubyte  SP0COL          = $d027
        &ubyte  SP1COL          = $d028
        &ubyte  SP2COL          = $d029
        &ubyte  SP3COL          = $d02a
        &ubyte  SP4COL          = $d02b
        &ubyte  SP5COL          = $d02c
        &ubyte  SP6COL          = $d02d
        &ubyte  SP7COL          = $d02e
        &ubyte[8]  SPCOL        = $d027


; ---- end of VIC-II registers ----

; ---- CIA 6526 1 & 2 registers ----

        &ubyte  CIA1PRA         = $DC00        ; CIA 1 DRA, keyboard column drive (and joystick control port #2)
        &ubyte  CIA1PRB         = $DC01        ; CIA 1 DRB, keyboard row port (and joystick control port #1)
        &ubyte  CIA1DDRA        = $DC02        ; CIA 1 DDRA, keyboard column
        &ubyte  CIA1DDRB        = $DC03        ; CIA 1 DDRB, keyboard row
        &ubyte  CIA1TAL         = $DC04        ; CIA 1 timer A low byte
        &ubyte  CIA1TAH         = $DC05        ; CIA 1 timer A high byte
        &ubyte  CIA1TBL         = $DC06        ; CIA 1 timer B low byte
        &ubyte  CIA1TBH         = $DC07        ; CIA 1 timer B high byte
        &ubyte  CIA1TOD10       = $DC08        ; time of day, 1/10 sec.
        &ubyte  CIA1TODSEC      = $DC09        ; time of day, seconds
        &ubyte  CIA1TODMMIN     = $DC0A        ; time of day, minutes
        &ubyte  CIA1TODHR       = $DC0B        ; time of day, hours
        &ubyte  CIA1SDR         = $DC0C        ; Serial Data Register
        &ubyte  CIA1ICR         = $DC0D
        &ubyte  CIA1CRA         = $DC0E
        &ubyte  CIA1CRB         = $DC0F

        &ubyte  CIA2PRA         = $DD00        ; CIA 2 DRA, serial port and video address
        &ubyte  CIA2PRB         = $DD01        ; CIA 2 DRB, RS232 port / USERPORT
        &ubyte  CIA2DDRA        = $DD02        ; CIA 2 DDRA, serial port and video address
        &ubyte  CIA2DDRB        = $DD03        ; CIA 2 DDRB, RS232 port / USERPORT
        &ubyte  CIA2TAL         = $DD04        ; CIA 2 timer A low byte
        &ubyte  CIA2TAH         = $DD05        ; CIA 2 timer A high byte
        &ubyte  CIA2TBL         = $DD06        ; CIA 2 timer B low byte
        &ubyte  CIA2TBH         = $DD07        ; CIA 2 timer B high byte
        &ubyte  CIA2TOD10       = $DD08        ; time of day, 1/10 sec.
        &ubyte  CIA2TODSEC      = $DD09        ; time of day, seconds
        &ubyte  CIA2TODMIN      = $DD0A        ; time of day, minutes
        &ubyte  CIA2TODHR       = $DD0B        ; time of day, hours
        &ubyte  CIA2SDR         = $DD0C        ; Serial Data Register
        &ubyte  CIA2ICR         = $DD0D
        &ubyte  CIA2CRA         = $DD0E
        &ubyte  CIA2CRB         = $DD0F

; ---- end of CIA registers ----

; ---- SID 6581/8580 registers ----

        &ubyte  FREQLO1         = $D400        ; channel 1 freq lo
        &ubyte  FREQHI1         = $D401        ; channel 1 freq hi
        &uword  FREQ1           = $D400        ; channel 1 freq (word)
        &ubyte  PWLO1           = $D402        ; channel 1 pulse width lo (7-0)
        &ubyte  PWHI1           = $D403        ; channel 1 pulse width hi (11-8)
        &uword  PW1             = $D402        ; channel 1 pulse width (word)
        &ubyte  CR1             = $D404        ; channel 1 voice control register
        &ubyte  AD1             = $D405        ; channel 1 attack & decay
        &ubyte  SR1             = $D406        ; channel 1 sustain & release
        &ubyte  FREQLO2         = $D407        ; channel 2 freq lo
        &ubyte  FREQHI2         = $D408        ; channel 2 freq hi
        &uword  FREQ2           = $D407        ; channel 2 freq (word)
        &ubyte  PWLO2           = $D409        ; channel 2 pulse width lo (7-0)
        &ubyte  PWHI2           = $D40A        ; channel 2 pulse width hi (11-8)
        &uword  PW2             = $D409        ; channel 2 pulse width (word)
        &ubyte  CR2             = $D40B        ; channel 2 voice control register
        &ubyte  AD2             = $D40C        ; channel 2 attack & decay
        &ubyte  SR2             = $D40D        ; channel 2 sustain & release
        &ubyte  FREQLO3         = $D40E        ; channel 3 freq lo
        &ubyte  FREQHI3         = $D40F        ; channel 3 freq hi
        &uword  FREQ3           = $D40E        ; channel 3 freq (word)
        &ubyte  PWLO3           = $D410        ; channel 3 pulse width lo (7-0)
        &ubyte  PWHI3           = $D411        ; channel 3 pulse width hi (11-8)
        &uword  PW3             = $D410        ; channel 3 pulse width (word)
        &ubyte  CR3             = $D412        ; channel 3 voice control register
        &ubyte  AD3             = $D413        ; channel 3 attack & decay
        &ubyte  SR3             = $D414        ; channel 3 sustain & release
        &ubyte  FCLO            = $D415        ; filter cutoff lo (2-0)
        &ubyte  FCHI            = $D416        ; filter cutoff hi (10-3)
        &uword  FC              = $D415        ; filter cutoff (word)
        &ubyte  RESFILT         = $D417        ; filter resonance and routing
        &ubyte  MVOL            = $D418        ; filter mode and main volume control
        &ubyte  POTX            = $D419        ; potentiometer X
        &ubyte  POTY            = $D41A        ; potentiometer Y
        &ubyte  OSC3            = $D41B        ; channel 3 oscillator value read
        &ubyte  ENV3            = $D41C        ; channel 3 envelope value read

    ; ---- end of SID registers ----

; ---- kernal routines, these are the same as on the Commodore-64 (hence the same block name) ----

; STROUT --> use txt.print
; CLEARSCR -> use txt.clear_screen
; HOMECRSR -> use txt.home or txt.plot

romsub $FA65 = IRQDFRT() clobbers(A,X,Y)                        ; default IRQ routine
romsub $FF33 = IRQDFEND() clobbers(A,X,Y)                       ; default IRQ end/cleanup

; TODO c128 a bunch of kernal routines are missing here that are specific to the c128

romsub $FF81 = CINT() clobbers(A,X,Y)                           ; (alias: SCINIT) initialize screen editor and video chip
romsub $FF84 = IOINIT() clobbers(A, X)                          ; initialize I/O devices (CIA, SID, IRQ)
romsub $FF87 = RAMTAS() clobbers(A,X,Y)                         ; initialize RAM, tape buffer, screen
romsub $FF8A = RESTOR() clobbers(A,X,Y)                         ; restore default I/O vectors
romsub $FF8D = VECTOR(uword userptr @ XY, ubyte dir @ Pc) clobbers(A,Y)     ; read/set I/O vector table
romsub $FF90 = SETMSG(ubyte value @ A)                          ; set Kernal message control flag
romsub $FF93 = SECOND(ubyte address @ A) clobbers(A)            ; (alias: LSTNSA) send secondary address after LISTEN
romsub $FF96 = TKSA(ubyte address @ A) clobbers(A)              ; (alias: TALKSA) send secondary address after TALK
romsub $FF99 = MEMTOP(uword address @ XY, ubyte dir @ Pc) -> uword @ XY     ; read/set top of memory  pointer
romsub $FF9C = MEMBOT(uword address @ XY, ubyte dir @ Pc) -> uword @ XY     ; read/set bottom of memory  pointer
romsub $FF9F = SCNKEY() clobbers(A,X,Y)                         ; scan the keyboard
romsub $FFA2 = SETTMO(ubyte timeout @ A)                        ; set time-out flag for IEEE bus
romsub $FFA5 = ACPTR() -> ubyte @ A                             ; (alias: IECIN) input byte from serial bus
romsub $FFA8 = CIOUT(ubyte databyte @ A)                        ; (alias: IECOUT) output byte to serial bus
romsub $FFAB = UNTLK() clobbers(A)                              ; command serial bus device to UNTALK
romsub $FFAE = UNLSN() clobbers(A)                              ; command serial bus device to UNLISTEN
romsub $FFB1 = LISTEN(ubyte device @ A) clobbers(A)             ; command serial bus device to LISTEN
romsub $FFB4 = TALK(ubyte device @ A) clobbers(A)               ; command serial bus device to TALK
romsub $FFB7 = READST() -> ubyte @ A                            ; read I/O status word
romsub $FFBA = SETLFS(ubyte logical @ A, ubyte device @ X, ubyte secondary @ Y)   ; set logical file parameters
romsub $FFBD = SETNAM(ubyte namelen @ A, str filename @ XY)     ; set filename parameters
romsub $FFC0 = OPEN() clobbers(X,Y) -> ubyte @Pc, ubyte @A      ; (via 794 ($31A)) open a logical file
romsub $FFC3 = CLOSE(ubyte logical @ A) clobbers(A,X,Y)         ; (via 796 ($31C)) close a logical file
romsub $FFC6 = CHKIN(ubyte logical @ X) clobbers(A,X) -> ubyte @Pc    ; (via 798 ($31E)) define an input channel
romsub $FFC9 = CHKOUT(ubyte logical @ X) clobbers(A,X)          ; (via 800 ($320)) define an output channel
romsub $FFCC = CLRCHN() clobbers(A,X)                           ; (via 802 ($322)) restore default devices
romsub $FFCF = CHRIN() clobbers(X, Y) -> ubyte @ A   ; (via 804 ($324)) input a character (for keyboard, read a whole line from the screen) A=byte read.
romsub $FFD2 = CHROUT(ubyte char @ A)                           ; (via 806 ($326)) output a character
romsub $FFD5 = LOAD(ubyte verify @ A, uword address @ XY) -> ubyte @Pc, ubyte @ A, uword @ XY     ; (via 816 ($330)) load from device
romsub $FFD8 = SAVE(ubyte zp_startaddr @ A, uword endaddr @ XY) -> ubyte @ Pc, ubyte @ A          ; (via 818 ($332)) save to a device
romsub $FFDB = SETTIM(ubyte low @ A, ubyte middle @ X, ubyte high @ Y)      ; set the software clock
romsub $FFDE = RDTIM() -> ubyte @ A, ubyte @ X, ubyte @ Y       ; read the software clock (A=lo,X=mid,Y=high)
romsub $FFE1 = STOP() clobbers(X) -> ubyte @ Pz, ubyte @ A      ; (via 808 ($328)) check the STOP key (and some others in A)
romsub $FFE4 = GETIN() clobbers(X,Y) -> ubyte @Pc, ubyte @ A    ; (via 810 ($32A)) get a character
romsub $FFE7 = CLALL() clobbers(A,X)                            ; (via 812 ($32C)) close all files
romsub $FFEA = UDTIM() clobbers(A,X)                            ; update the software clock
romsub $FFED = SCREEN() -> ubyte @ X, ubyte @ Y                 ; read number of screen rows and columns
romsub $FFF0 = PLOT(ubyte col @ Y, ubyte row @ X, ubyte dir @ Pc) -> ubyte @ X, ubyte @ Y       ; read/set position of cursor on screen.  Use txt.plot for a 'safe' wrapper that preserves X.
romsub $FFF3 = IOBASE() -> uword @ XY                           ; read base address of I/O devices

; ---- end of C64 compatible ROM kernal routines ----

; ---- utilities -----

asmsub STOP2() -> ubyte @A  {
    ; -- check if STOP key was pressed, returns true if so.  More convenient to use than STOP() because that only sets the carry status flag.
    %asm {{
        txa
        pha
        jsr  c64.STOP
        beq  +
        pla
        tax
        lda  #0
        rts
+       pla
        tax
        lda  #1
        rts
    }}
}

asmsub RDTIM16() -> uword @AY {
    ; --  like RDTIM() but only returning the lower 16 bits in AY for convenience
    %asm {{
        stx  P8ZP_SCRATCH_REG
        jsr  c64.RDTIM
        pha
        txa
        tay
        pla
        ldx  P8ZP_SCRATCH_REG
        rts
    }}
}


; ---- system utility routines that are essentially the same as on the C64: -----
asmsub  disable_runstop_and_charsetswitch() clobbers(A) {
    %asm {{
        lda  #$80
        sta  247    ; disable charset switching
        lda  #112
        sta  808    ; disable run/stop key
        rts
    }}
}

asmsub  enable_runstop_and_charsetswitch() clobbers(A) {
    %asm {{
        lda  #0
        sta  247    ; enable charset switching
        lda  #110
        sta  808    ; enable run/stop key
        rts
    }}
}

asmsub  set_irq(uword handler @AY, ubyte useKernal @Pc) clobbers(A)  {
	%asm {{
	        sta  _modified+1
	        sty  _modified+2
	        lda  #0
	        adc  #0
	        sta  _use_kernal
		sei
		lda  #<_irq_handler
		sta  c64.CINV
		lda  #>_irq_handler
		sta  c64.CINV+1
		cli
		rts
_irq_handler    jsr  _irq_handler_init
_modified	jsr  $ffff                      ; modified
		jsr  _irq_handler_end
		lda  _use_kernal
		bne  +
		lda  #$ff
		sta  c64.VICIRQ			; acknowledge raster irq
		lda  c64.CIA1ICR		; acknowledge CIA1 interrupt
		; end irq processing - don't use kernal's irq handling
		pla
		tay
		pla
		tax
		pla
		rti
+		jmp  c64.IRQDFRT		; continue with normal kernal irq routine

_use_kernal     .byte  0

_irq_handler_init
		; save all zp scratch registers and the X register as these might be clobbered by the irq routine
		stx  IRQ_X_REG
		lda  P8ZP_SCRATCH_B1
		sta  IRQ_SCRATCH_ZPB1
		lda  P8ZP_SCRATCH_REG
		sta  IRQ_SCRATCH_ZPREG
		lda  P8ZP_SCRATCH_W1
		sta  IRQ_SCRATCH_ZPWORD1
		lda  P8ZP_SCRATCH_W1+1
		sta  IRQ_SCRATCH_ZPWORD1+1
		lda  P8ZP_SCRATCH_W2
		sta  IRQ_SCRATCH_ZPWORD2
		lda  P8ZP_SCRATCH_W2+1
		sta  IRQ_SCRATCH_ZPWORD2+1
		; stack protector; make sure we don't clobber the top of the evaluation stack
		dex
		dex
		dex
		dex
		dex
		dex
		cld
		rts

_irq_handler_end
		; restore all zp scratch registers and the X register
		lda  IRQ_SCRATCH_ZPB1
		sta  P8ZP_SCRATCH_B1
		lda  IRQ_SCRATCH_ZPREG
		sta  P8ZP_SCRATCH_REG
		lda  IRQ_SCRATCH_ZPWORD1
		sta  P8ZP_SCRATCH_W1
		lda  IRQ_SCRATCH_ZPWORD1+1
		sta  P8ZP_SCRATCH_W1+1
		lda  IRQ_SCRATCH_ZPWORD2
		sta  P8ZP_SCRATCH_W2
		lda  IRQ_SCRATCH_ZPWORD2+1
		sta  P8ZP_SCRATCH_W2+1
		ldx  IRQ_X_REG
		rts

IRQ_X_REG		.byte  0
IRQ_SCRATCH_ZPB1	.byte  0
IRQ_SCRATCH_ZPREG	.byte  0
IRQ_SCRATCH_ZPWORD1	.word  0
IRQ_SCRATCH_ZPWORD2	.word  0

		}}
}

asmsub  restore_irq() clobbers(A) {
	%asm {{
		sei
		lda  #<c64.IRQDFRT
		sta  c64.CINV
		lda  #>c64.IRQDFRT
		sta  c64.CINV+1
		lda  #0
		sta  c64.IREQMASK	; disable raster irq
		lda  #%10000001
		sta  c64.CIA1ICR	; restore CIA1 irq
		cli
		rts
	}}
}

asmsub  set_rasterirq(uword handler @AY, uword rasterpos @R0, ubyte useKernal @Pc) clobbers(A) {
	%asm {{
	        sta  _modified+1
	        sty  _modified+2
	        lda  #0
	        adc  #0
	        sta  set_irq._use_kernal
		lda  cx16.r0
		ldy  cx16.r0+1
		sei
		jsr  _setup_raster_irq
		lda  #<_raster_irq_handler
		sta  c64.CINV
		lda  #>_raster_irq_handler
		sta  c64.CINV+1
		cli
		rts

_raster_irq_handler
		jsr  set_irq._irq_handler_init
_modified	jsr  $ffff              ; modified
		jsr  set_irq._irq_handler_end
                lda  #$ff
                sta  c64.VICIRQ			; acknowledge raster irq
		lda  set_irq._use_kernal
		bne  +
		; end irq processing - don't use kernal's irq handling
		pla
		tay
		pla
		tax
		pla
		rti
+		jmp  c64.IRQDFRT                ; continue with kernal irq routine

_setup_raster_irq
		pha
		lda  #%01111111
		sta  c64.CIA1ICR    ; "switch off" interrupts signals from cia-1
		sta  c64.CIA2ICR    ; "switch off" interrupts signals from cia-2
		and  c64.SCROLY
		sta  c64.SCROLY     ; clear most significant bit of raster position
		lda  c64.CIA1ICR    ; ack previous irq
		lda  c64.CIA2ICR    ; ack previous irq
		pla
		sta  c64.RASTER     ; set the raster line number where interrupt should occur
		cpy  #0
		beq  +
		lda  c64.SCROLY
		ora  #%10000000
		sta  c64.SCROLY     ; set most significant bit of raster position
+		lda  #%00000001
		sta  c64.IREQMASK   ;enable raster interrupt signals from vic
		rts
	}}
}

}

c128 {
; ---- C128 specific registers ----

    &ubyte  VM1     = $0A2C         ; shadow for VUC $d018 in text mode
    &ubyte  VM2     = $0A2D         ; shadow for VIC $d018 in bitmap screen mode
    &ubyte  VM3     = $0A2E         ; starting page for VDC screen mem
    &ubyte  VM4     = $0A2F         ; starting page for VDC attribute mem


; ---- C128 specific system utility routines: ----

asmsub  init_system()  {
    ; Initializes the machine to a sane starting state.
    ; Called automatically by the loader program logic.
    ; This means that the BASIC, KERNAL and CHARGEN ROMs are banked in,
    ; the VIC, SID and CIA chips are reset, screen is cleared, and the default IRQ is set.
    ; Also a different color scheme is chosen to identify ourselves a little.
    ; Uppercase charset is activated, and all three registers set to 0, status flags cleared.
    %asm {{
        sei
        cld
        ;;lda  #%00101111                     ; TODO c128 ram and rom bank selection how?
        ;;sta  $00
        ;;lda  #%00100111
        ;;sta  $01
        jsr  c64.IOINIT
        jsr  c64.RESTOR
        jsr  c64.CINT
        lda  #6
        sta  c64.EXTCOL
        lda  #7
        sta  c64.COLOR
        lda  #0
        sta  c64.BGCOL0
        jsr  c64.disable_runstop_and_charsetswitch
        clc
        clv
        cli
        rts
    }}
}

asmsub  init_system_phase2()  {
    %asm {{
        rts     ; no phase 2 steps on the C128
    }}
}

asmsub  cleanup_at_exit() {
    ; executed when the main subroutine does rts
    %asm {{
        jmp  c64.enable_runstop_and_charsetswitch
    }}
}

asmsub  disable_basic() clobbers(A) {
    %asm {{
        lda $0a04   ; disable BASIC shadow registers
        and #$fe
        sta $0a04

        lda #$01    ; disable BASIC IRQ service routine
        sta $12fd

        lda #$ff    ; disable screen editor IRQ setup
        sta $d8

        lda #$b7    ; skip programmable function key check
        sta $033c

        lda #$0e    ; bank out BASIC ROM
        sta $ff00
        rts
    }}
}

; ---- end of C128 specific system utility routines ----

}

sys {
    ; ------- lowlevel system routines --------

    const ubyte target = 128         ;  compilation target specifier.  64 = C64, 128 = C128,  16 = CommanderX16.


    asmsub  reset_system()  {
        ; Soft-reset the system back to initial power-on Basic prompt.
        %asm {{
            sei
            ;lda  #14
            ;sta  $01        ; bank the kernal in       TODO c128 how to do this?
            jmp  (c64.RESET_VEC)
        }}
    }

    sub wait(uword jiffies) {
        ; --- wait approximately the given number of jiffies (1/60th seconds)
        ;     note: the system irq handler has to be active for this to work as it depends on the system jiffy clock
        repeat jiffies {
            ubyte jiff = lsb(c64.RDTIM16())
            while jiff==lsb(c64.RDTIM16()) {
                ; wait until 1 jiffy has passed
            }
        }
    }

    asmsub waitvsync() clobbers(A) {
        ; --- busy wait till the next vsync has occurred (approximately), without depending on custom irq handling.
        ;     note: a more accurate way to wait for vsync is to set up a vsync irq handler instead.
        %asm {{
-           bit  c64.SCROLY
            bpl  -
-           bit  c64.SCROLY
            bmi  -
            rts
        }}
    }

    inline asmsub waitrastborder() {
        ; --- busy wait till the raster position has reached the bottom screen border (approximately)
        ;     note: a more accurate way to do this is by using a raster irq handler instead.
        %asm {{
-           bit  c64.SCROLY
            bpl  -
        }}
    }

    asmsub internal_stringcopy(uword source @R0, uword target @AY) clobbers (A,Y) {
        ; Called when the compiler wants to assign a string value to another string.
        %asm {{
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		lda  cx16.r0
		ldy  cx16.r0+1
		jmp  prog8_lib.strcpy
        }}
    }

    asmsub memcopy(uword source @R0, uword target @R1, uword count @AY) clobbers(A,X,Y) {
        ; note: only works for NON-OVERLAPPING memory regions!
        ; note: can't be inlined because is called from asm as well
        %asm {{
            ldx  cx16.r0
            stx  P8ZP_SCRATCH_W1        ; source in ZP
            ldx  cx16.r0+1
            stx  P8ZP_SCRATCH_W1+1
            ldx  cx16.r1
            stx  P8ZP_SCRATCH_W2        ; target in ZP
            ldx  cx16.r1+1
            stx  P8ZP_SCRATCH_W2+1
            cpy  #0
            bne  _longcopy

            ; copy <= 255 bytes
            tay
            bne  _copyshort
            rts     ; nothing to copy

_copyshort
            ; decrease source and target pointers so we can simply index by Y
            lda  P8ZP_SCRATCH_W1
            bne  +
            dec  P8ZP_SCRATCH_W1+1
+           dec  P8ZP_SCRATCH_W1
            lda  P8ZP_SCRATCH_W2
            bne  +
            dec  P8ZP_SCRATCH_W2+1
+           dec  P8ZP_SCRATCH_W2

-           lda  (P8ZP_SCRATCH_W1),y
            sta  (P8ZP_SCRATCH_W2),y
            dey
            bne  -
            rts

_longcopy
            sta  P8ZP_SCRATCH_B1        ; lsb(count) = remainder in last page
            tya
            tax                         ; x = num pages (1+)
            ldy  #0
-           lda  (P8ZP_SCRATCH_W1),y
            sta  (P8ZP_SCRATCH_W2),y
            iny
            bne  -
            inc  P8ZP_SCRATCH_W1+1
            inc  P8ZP_SCRATCH_W2+1
            dex
            bne  -
            ldy  P8ZP_SCRATCH_B1
            bne  _copyshort
            rts
        }}
    }

    asmsub memset(uword mem @R0, uword numbytes @R1, ubyte value @A) clobbers(A,X,Y) {
        %asm {{
            ldy  cx16.r0
            sty  P8ZP_SCRATCH_W1
            ldy  cx16.r0+1
            sty  P8ZP_SCRATCH_W1+1
            ldx  cx16.r1
            ldy  cx16.r1+1
            jmp  prog8_lib.memset
        }}
    }

    asmsub memsetw(uword mem @R0, uword numwords @R1, uword value @AY) clobbers(A,X,Y) {
        %asm {{
            ldx  cx16.r0
            stx  P8ZP_SCRATCH_W1
            ldx  cx16.r0+1
            stx  P8ZP_SCRATCH_W1+1
            ldx  cx16.r1
            stx  P8ZP_SCRATCH_W2
            ldx  cx16.r1+1
            stx  P8ZP_SCRATCH_W2+1
            jmp  prog8_lib.memsetw
        }}
    }

    inline asmsub read_flags() -> ubyte @A {
        %asm {{
        php
        pla
        }}
    }

    inline asmsub clear_carry() {
        %asm {{
        clc
        }}
    }

    inline asmsub set_carry() {
        %asm {{
        sec
        }}
    }

    inline asmsub clear_irqd() {
        %asm {{
        cli
        }}
    }

    inline asmsub set_irqd() {
        %asm {{
        sei
        }}
    }

    inline asmsub exit(ubyte returnvalue @A) {
        ; -- immediately exit the program with a return code in the A register
        %asm {{
            ;lda  #14
            ;sta  $01        ; bank the kernal in       TODO c128 how to do this?
            jsr  c64.CLRCHN		; reset i/o channels
            jsr  c64.enable_runstop_and_charsetswitch
            ldx  prog8_lib.orig_stackpointer
            txs
            rts		; return to original caller
        }}
    }

    inline asmsub progend() -> uword @AY {
        %asm {{
            lda  #<prog8_program_end
            ldy  #>prog8_program_end
        }}
    }

}

cx16 {

    ; the sixteen virtual 16-bit registers that the CX16 has defined in the zeropage
    ; they are simulated on the C128 as well but their location in memory is different
    ; (because there's no room for them in the zeropage)
    ; $1300-$1bff is unused RAM on C128. We'll use $1a00-$1bff as the lo/hi evalstack.
    ; the virtual registers are allocated at the bottom of the eval-stack (should be ample space unless
    ; you're doing insane nesting of expressions...)
    ; NOTE: the memory location of these registers can change based on the "-esa" compiler option
    &uword r0  = $1b00
    &uword r1  = $1b02
    &uword r2  = $1b04
    &uword r3  = $1b06
    &uword r4  = $1b08
    &uword r5  = $1b0a
    &uword r6  = $1b0c
    &uword r7  = $1b0e
    &uword r8  = $1b10
    &uword r9  = $1b12
    &uword r10 = $1b14
    &uword r11 = $1b16
    &uword r12 = $1b18
    &uword r13 = $1b1a
    &uword r14 = $1b1c
    &uword r15 = $1b1e

    &word r0s  = $1b00
    &word r1s  = $1b02
    &word r2s  = $1b04
    &word r3s  = $1b06
    &word r4s  = $1b08
    &word r5s  = $1b0a
    &word r6s  = $1b0c
    &word r7s  = $1b0e
    &word r8s  = $1b10
    &word r9s  = $1b12
    &word r10s = $1b14
    &word r11s = $1b16
    &word r12s = $1b18
    &word r13s = $1b1a
    &word r14s = $1b1c
    &word r15s = $1b1e

    &ubyte r0L  = $1b00
    &ubyte r1L  = $1b02
    &ubyte r2L  = $1b04
    &ubyte r3L  = $1b06
    &ubyte r4L  = $1b08
    &ubyte r5L  = $1b0a
    &ubyte r6L  = $1b0c
    &ubyte r7L  = $1b0e
    &ubyte r8L  = $1b10
    &ubyte r9L  = $1b12
    &ubyte r10L = $1b14
    &ubyte r11L = $1b16
    &ubyte r12L = $1b18
    &ubyte r13L = $1b1a
    &ubyte r14L = $1b1c
    &ubyte r15L = $1b1e

    &ubyte r0H  = $1b01
    &ubyte r1H  = $1b03
    &ubyte r2H  = $1b05
    &ubyte r3H  = $1b07
    &ubyte r4H  = $1b09
    &ubyte r5H  = $1b0b
    &ubyte r6H  = $1b0d
    &ubyte r7H  = $1b0f
    &ubyte r8H  = $1b11
    &ubyte r9H  = $1b13
    &ubyte r10H = $1b15
    &ubyte r11H = $1b17
    &ubyte r12H = $1b19
    &ubyte r13H = $1b1b
    &ubyte r14H = $1b1d
    &ubyte r15H = $1b1f

    &byte r0sL  = $1b00
    &byte r1sL  = $1b02
    &byte r2sL  = $1b04
    &byte r3sL  = $1b06
    &byte r4sL  = $1b08
    &byte r5sL  = $1b0a
    &byte r6sL  = $1b0c
    &byte r7sL  = $1b0e
    &byte r8sL  = $1b10
    &byte r9sL  = $1b12
    &byte r10sL = $1b14
    &byte r11sL = $1b16
    &byte r12sL = $1b18
    &byte r13sL = $1b1a
    &byte r14sL = $1b1c
    &byte r15sL = $1b1e

    &byte r0sH  = $1b01
    &byte r1sH  = $1b03
    &byte r2sH  = $1b05
    &byte r3sH  = $1b07
    &byte r4sH  = $1b09
    &byte r5sH  = $1b0b
    &byte r6sH  = $1b0d
    &byte r7sH  = $1b0f
    &byte r8sH  = $1b11
    &byte r9sH  = $1b13
    &byte r10sH = $1b15
    &byte r11sH = $1b17
    &byte r12sH = $1b19
    &byte r13sH = $1b1b
    &byte r14sH = $1b1d
    &byte r15sH = $1b1f
}
