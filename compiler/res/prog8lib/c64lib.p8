; Prog8 definitions for the Commodore-64
; Including memory registers, I/O registers, Basic and Kernal subroutines.
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8


c64 {
		const   uword  ESTACK_LO	= $ce00		; evaluation stack (lsb)
		const   uword  ESTACK_HI	= $cf00		; evaluation stack (msb)
		&ubyte  SCRATCH_ZPB1		= $02		; scratch byte 1 in ZP
		&ubyte  SCRATCH_ZPREG		= $03		; scratch register in ZP
		&ubyte  SCRATCH_ZPREGX		= $fa		; temp storage for X register (stack pointer)
		&uword  SCRATCH_ZPWORD1		= $fb		; scratch word in ZP ($fb/$fc)
		&uword  SCRATCH_ZPWORD2		= $fd		; scratch word in ZP ($fd/$fe)


		&ubyte  TIME_HI			= $a0		; software jiffy clock, hi byte
		&ubyte  TIME_MID		= $a1		;  .. mid byte
		&ubyte  TIME_LO			= $a2		;    .. lo byte. Updated by IRQ every 1/60 sec
		&ubyte  STKEY			= $91		; various keyboard statuses (updated by IRQ)
		&ubyte  SFDX			= $cb		; current key pressed (matrix value) (updated by IRQ)

		&ubyte  COLOR			= $0286		; cursor color
		&ubyte  HIBASE			= $0288		; screen base address / 256 (hi-byte of screen memory address)
		&uword  CINV			= $0314		; IRQ vector
		&uword  NMI_VEC			= $FFFA		; 6502 nmi vector, determined by the kernal if banked in
		&uword  RESET_VEC		= $FFFC		; 6502 reset vector, determined by the kernal if banked in
		&uword  IRQ_VEC			= $FFFE		; 6502 interrupt vector, determined by the kernal if banked in

		; the default addresses for the character screen chars and colors
		const   uword  Screen		= $0400		; to have this as an array[40*25] the compiler would have to support array size > 255
		const   uword  Colors		= $d800		; to have this as an array[40*25] the compiler would have to support array size > 255

		; the default locations of the 8 sprite pointers (store address of sprite / 64)
		&ubyte  SPRPTR0			= 2040
		&ubyte  SPRPTR1			= 2041
		&ubyte  SPRPTR2			= 2042
		&ubyte  SPRPTR3			= 2043
		&ubyte  SPRPTR4			= 2044
		&ubyte  SPRPTR5			= 2045
		&ubyte  SPRPTR6			= 2046
		&ubyte  SPRPTR7			= 2047
		&ubyte[8]  SPRPTR		= 2040		; the 8 sprite pointers as an array.


; ---- VIC-II 6567/6569/856x registers ----

		&ubyte  SP0X		= $d000
		&ubyte  SP0Y		= $d001
		&ubyte  SP1X		= $d002
		&ubyte  SP1Y		= $d003
		&ubyte  SP2X		= $d004
		&ubyte  SP2Y		= $d005
		&ubyte  SP3X		= $d006
		&ubyte  SP3Y		= $d007
		&ubyte  SP4X		= $d008
		&ubyte  SP4Y		= $d009
		&ubyte  SP5X		= $d00a
		&ubyte  SP5Y		= $d00b
		&ubyte  SP6X		= $d00c
		&ubyte  SP6Y		= $d00d
		&ubyte  SP7X		= $d00e
		&ubyte  SP7Y		= $d00f
		&ubyte[16]  SPXY	= $d000		; the 8 sprite X and Y registers as an array.
		&uword[8]  SPXYW	= $d000		; the 8 sprite X and Y registers as a combined xy word array.

		&ubyte  MSIGX		= $d010
		&ubyte  SCROLY		= $d011
		&ubyte  RASTER		= $d012
		&ubyte  LPENX		= $d013
		&ubyte  LPENY		= $d014
		&ubyte  SPENA		= $d015
		&ubyte  SCROLX		= $d016
		&ubyte  YXPAND		= $d017
		&ubyte  VMCSB		= $d018
		&ubyte  VICIRQ		= $d019
		&ubyte  IREQMASK	= $d01a
		&ubyte  SPBGPR		= $d01b
		&ubyte  SPMC		= $d01c
		&ubyte  XXPAND		= $d01d
		&ubyte  SPSPCL		= $d01e
		&ubyte  SPBGCL		= $d01f

		&ubyte  EXTCOL		= $d020		; border color
		&ubyte  BGCOL0		= $d021		; screen color
		&ubyte  BGCOL1		= $d022
		&ubyte  BGCOL2		= $d023
		&ubyte  BGCOL4		= $d024
		&ubyte  SPMC0		= $d025
		&ubyte  SPMC1		= $d026
		&ubyte  SP0COL		= $d027
		&ubyte  SP1COL		= $d028
		&ubyte  SP2COL		= $d029
		&ubyte  SP3COL		= $d02a
		&ubyte  SP4COL		= $d02b
		&ubyte  SP5COL		= $d02c
		&ubyte  SP6COL		= $d02d
		&ubyte  SP7COL		= $d02e
		&ubyte[8]  SPCOL	= $d027


; ---- end of VIC-II registers ----

; ---- CIA 6526 1 & 2 registers ----

		&ubyte  CIA1PRA		= $DC00		; CIA 1 DRA, keyboard column drive (and joystick control port #2)
		&ubyte  CIA1PRB		= $DC01		; CIA 1 DRB, keyboard row port (and joystick control port #1)
		&ubyte  CIA1DDRA	= $DC02		; CIA 1 DDRA, keyboard column
		&ubyte  CIA1DDRB	= $DC03		; CIA 1 DDRB, keyboard row
		&ubyte  CIA1TAL		= $DC04		; CIA 1 timer A low byte
		&ubyte  CIA1TAH		= $DC05		; CIA 1 timer A high byte
		&ubyte  CIA1TBL		= $DC06		; CIA 1 timer B low byte
		&ubyte  CIA1TBH		= $DC07		; CIA 1 timer B high byte
		&ubyte  CIA1TOD10	= $DC08		; time of day, 1/10 sec.
		&ubyte  CIA1TODSEC	= $DC09		; time of day, seconds
		&ubyte  CIA1TODMMIN	= $DC0A		; time of day, minutes
		&ubyte  CIA1TODHR	= $DC0B		; time of day, hours
		&ubyte  CIA1SDR		= $DC0C		; Serial Data Register
		&ubyte  CIA1ICR		= $DC0D
		&ubyte  CIA1CRA		= $DC0E
		&ubyte  CIA1CRB		= $DC0F

		&ubyte  CIA2PRA		= $DD00		; CIA 2 DRA, serial port and video address
		&ubyte  CIA2PRB		= $DD01		; CIA 2 DRB, RS232 port / USERPORT
		&ubyte  CIA2DDRA	= $DD02		; CIA 2 DDRA, serial port and video address
		&ubyte  CIA2DDRB	= $DD03		; CIA 2 DDRB, RS232 port / USERPORT
		&ubyte  CIA2TAL		= $DD04		; CIA 2 timer A low byte
		&ubyte  CIA2TAH		= $DD05		; CIA 2 timer A high byte
		&ubyte  CIA2TBL		= $DD06		; CIA 2 timer B low byte
		&ubyte  CIA2TBH		= $DD07		; CIA 2 timer B high byte
		&ubyte  CIA2TOD10	= $DD08		; time of day, 1/10 sec.
		&ubyte  CIA2TODSEC	= $DD09		; time of day, seconds
		&ubyte  CIA2TODMIN	= $DD0A		; time of day, minutes
		&ubyte  CIA2TODHR	= $DD0B		; time of day, hours
		&ubyte  CIA2SDR		= $DD0C		; Serial Data Register
		&ubyte  CIA2ICR		= $DD0D
		&ubyte  CIA2CRA		= $DD0E
		&ubyte  CIA2CRB		= $DD0F

; ---- end of CIA registers ----

; ---- SID 6581/8580 registers ----

		&ubyte  FREQLO1		= $D400		; channel 1 freq lo
		&ubyte  FREQHI1		= $D401		; channel 1 freq hi
		&uword  FREQ1		= $D400		; channel 1 freq (word)
		&ubyte  PWLO1		= $D402		; channel 1 pulse width lo (7-0)
		&ubyte  PWHI1		= $D403		; channel 1 pulse width hi (11-8)
		&uword  PW1		= $D402		; channel 1 pulse width (word)
		&ubyte  CR1		= $D404		; channel 1 voice control register
		&ubyte  AD1		= $D405		; channel 1 attack & decay
		&ubyte  SR1		= $D406		; channel 1 sustain & release
		&ubyte  FREQLO2		= $D407		; channel 2 freq lo
		&ubyte  FREQHI2		= $D408		; channel 2 freq hi
		&uword  FREQ2		= $D407		; channel 2 freq (word)
		&ubyte  PWLO2		= $D409		; channel 2 pulse width lo (7-0)
		&ubyte  PWHI2		= $D40A		; channel 2 pulse width hi (11-8)
		&uword  PW2		= $D409		; channel 2 pulse width (word)
		&ubyte  CR2		= $D40B		; channel 2 voice control register
		&ubyte  AD2		= $D40C		; channel 2 attack & decay
		&ubyte  SR2		= $D40D		; channel 2 sustain & release
		&ubyte  FREQLO3		= $D40E		; channel 3 freq lo
		&ubyte  FREQHI3		= $D40F		; channel 3 freq hi
		&uword  FREQ3		= $D40E		; channel 3 freq (word)
		&ubyte  PWLO3		= $D410		; channel 3 pulse width lo (7-0)
		&ubyte  PWHI3		= $D411		; channel 3 pulse width hi (11-8)
		&uword  PW3		= $D410		; channel 3 pulse width (word)
		&ubyte  CR3		= $D412		; channel 3 voice control register
		&ubyte  AD3		= $D413		; channel 3 attack & decay
		&ubyte  SR3		= $D414		; channel 3 sustain & release
		&ubyte  FCLO		= $D415		; filter cutoff lo (2-0)
		&ubyte  FCHI		= $D416		; filter cutoff hi (10-3)
		&uword  FC		= $D415		; filter cutoff (word)
		&ubyte  RESFILT		= $D417		; filter resonance and routing
		&ubyte  MVOL		= $D418		; filter mode and main volume control
		&ubyte  POTX		= $D419		; potentiometer X
		&ubyte  POTY		= $D41A		; potentiometer Y
		&ubyte  OSC3		= $D41B		; channel 3 oscillator value read
		&ubyte  ENV3		= $D41C		; channel 3 envelope value read

; ---- end of SID registers ----



; ---- C64 basic routines ----

asmsub	CLEARSCR	() clobbers(A,X,Y)		= $E544		; clear the screen
asmsub	HOMECRSR	() clobbers(A,X,Y)		= $E566		; cursor to top left of screen


; ---- end of C64 basic routines ----


; ---- C64 kernal routines ----

asmsub	STROUT   (uword strptr @ AY) clobbers(A, X, Y)	= $AB1E		; print null-terminated string (use c64scr.print instead)
asmsub	IRQDFRT  () clobbers(A,X,Y)			= $EA31		; default IRQ routine
asmsub	IRQDFEND () clobbers(A,X,Y)			= $EA81		; default IRQ end/cleanup
asmsub	CINT     () clobbers(A,X,Y)			= $FF81		; (alias: SCINIT) initialize screen editor and video chip
asmsub	IOINIT   () clobbers(A, X)			= $FF84		; initialize I/O devices (CIA, SID, IRQ)
asmsub	RAMTAS   () clobbers(A,X,Y)			= $FF87		; initialize RAM, tape buffer, screen
asmsub	RESTOR   () clobbers(A,X,Y)			= $FF8A		; restore default I/O vectors
asmsub	VECTOR   (uword userptr @ XY, ubyte dir @ Pc) clobbers(A,Y)  = $FF8D		; read/set I/O vector table
asmsub	SETMSG   (ubyte value @ A)			= $FF90		; set Kernal message control flag
asmsub	SECOND   (ubyte address @ A) clobbers(A)	= $FF93		; (alias: LSTNSA) send secondary address after LISTEN
asmsub	TKSA     (ubyte address @ A) clobbers(A)	= $FF96		; (alias: TALKSA) send secondary address after TALK
asmsub	MEMTOP   (uword address @ XY, ubyte dir @ Pc) -> uword @ XY	= $FF99		; read/set top of memory  pointer
asmsub	MEMBOT   (uword address @ XY, ubyte dir @ Pc) -> uword @ XY	= $FF9C		; read/set bottom of memory  pointer
asmsub	SCNKEY   () clobbers(A,X,Y)			= $FF9F		; scan the keyboard
asmsub	SETTMO   (ubyte timeout @ A)			= $FFA2		; set time-out flag for IEEE bus
asmsub	ACPTR    () -> ubyte @ A			= $FFA5		; (alias: IECIN) input byte from serial bus
asmsub	CIOUT    (ubyte databyte @ A)			= $FFA8		; (alias: IECOUT) output byte to serial bus
asmsub	UNTLK    () clobbers(A)				= $FFAB		; command serial bus device to UNTALK
asmsub	UNLSN    () clobbers(A)				= $FFAE		; command serial bus device to UNLISTEN
asmsub	LISTEN   (ubyte device @ A) clobbers(A)		= $FFB1		; command serial bus device to LISTEN
asmsub	TALK     (ubyte device @ A) clobbers(A)		= $FFB4		; command serial bus device to TALK
asmsub	READST   () -> ubyte @ A			= $FFB7		; read I/O status word
asmsub	SETLFS   (ubyte logical @ A, ubyte device @ X, ubyte address @ Y) = $FFBA	; set logical file parameters
asmsub	SETNAM   (ubyte namelen @ A, str filename @ XY)	= $FFBD		; set filename parameters
asmsub	OPEN     () clobbers(A,X,Y)			= $FFC0		; (via 794 ($31A)) open a logical file
asmsub	CLOSE    (ubyte logical @ A) clobbers(A,X,Y)	= $FFC3		; (via 796 ($31C)) close a logical file
asmsub	CHKIN    (ubyte logical @ X) clobbers(A,X)	= $FFC6		; (via 798 ($31E)) define an input channel
asmsub	CHKOUT   (ubyte logical @ X) clobbers(A,X)	= $FFC9		; (via 800 ($320)) define an output channel
asmsub	CLRCHN   () clobbers(A,X)			= $FFCC		; (via 802 ($322)) restore default devices
asmsub	CHRIN    () clobbers(Y) -> ubyte @ A		= $FFCF		; (via 804 ($324)) input a character (for keyboard, read a whole line from the screen) A=byte read.
asmsub	CHROUT   (ubyte char @ A)			= $FFD2		; (via 806 ($326)) output a character
asmsub	LOAD     (ubyte verify @ A, uword address @ XY) -> ubyte @Pc, ubyte @ A, ubyte @ X, ubyte @ Y = $FFD5	; (via 816 ($330)) load from device
asmsub	SAVE     (ubyte zp_startaddr @ A, uword endaddr @ XY) -> ubyte @ Pc, ubyte @ A = $FFD8	; (via 818 ($332)) save to a device
asmsub	SETTIM   (ubyte low @ A, ubyte middle @ X, ubyte high @ Y)	= $FFDB		; set the software clock
asmsub	RDTIM    () -> ubyte @ A, ubyte @ X, ubyte @ Y	= $FFDE	; read the software clock
asmsub	STOP     () clobbers(A,X) -> ubyte @ Pz, ubyte @ Pc	= $FFE1		; (via 808 ($328)) check the STOP key
asmsub	GETIN    () clobbers(X,Y) -> ubyte @ A		= $FFE4		; (via 810 ($32A)) get a character
asmsub	CLALL    () clobbers(A,X)			= $FFE7		; (via 812 ($32C)) close all files
asmsub	UDTIM    () clobbers(A,X)			= $FFEA		; update the software clock
asmsub	SCREEN   () -> ubyte @ X, ubyte @ Y		= $FFED		; read number of screen rows and columns
asmsub	PLOT     (ubyte col @ Y, ubyte row @ X, ubyte dir @ Pc) -> ubyte @ X, ubyte @ Y	= $FFF0		; read/set position of cursor on screen.  Use c64scr.plot for a 'safe' wrapper that preserves X.
asmsub	IOBASE   () -> uword @ XY			= $FFF3		; read base address of I/O devices

; ---- end of C64 kernal routines ----

}
