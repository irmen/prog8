; Prog8 definitions for the Commodore-64
; Including memory registers, I/O registers, Basic and Kernal subroutines.
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8


~ c64 {
		memory  byte  SCRATCH_ZP1	= $02		; scratch register #1 in ZP
		memory  byte  SCRATCH_ZP2	= $03		; scratch register #2 in ZP
		memory  word  SCRATCH_ZPWORD1	= $fb		; scratch word in ZP ($fb/$fc)
		memory  word  SCRATCH_ZPWORD2	= $fd		; scratch word in ZP ($fd/$fe)


		memory  byte  TIME_HI		= $a0		; software jiffy clock, hi byte
		memory  byte  TIME_MID		= $a1		;  .. mid byte
		memory  byte  TIME_LO		= $a2		;    .. lo byte. Updated by IRQ every 1/60 sec
		memory  byte  STKEY		= $91		; various keyboard statuses (updated by IRQ)
		memory  byte  SFDX		= $cb		; current key pressed (matrix value) (updated by IRQ)

		memory  byte  COLOR		= $0286		; cursor color
		memory  byte  HIBASE		= $0288         ; screen base address / 256 (hi-byte of screen memory address)
		memory  word  CINV		= $0314		; IRQ vector
		memory  word  NMI_VEC		= $FFFA  	; 6502 nmi vector, determined by the kernal if banked in
		memory  word  RESET_VEC		= $FFFC  	; 6502 reset vector, determined by the kernal if banked in
		memory  word  IRQ_VEC		= $FFFE  	; 6502 interrupt vector, determined by the kernal if banked in

		memory  byte[40, 25]  Screen	= $0400		; default character screen matrix
		memory  byte[40, 25]  Colors	= $d800		; character screen colors


; ---- VIC-II registers ----

		memory  byte SP0X		= $d000
		memory  byte SP0Y		= $d001
		memory  byte SP1X		= $d002
		memory  byte SP1Y		= $d003
		memory  byte SP2X		= $d004
		memory  byte SP2Y		= $d005
		memory  byte SP3X		= $d006
		memory  byte SP3Y		= $d007
		memory  byte SP4X		= $d008
		memory  byte SP4Y		= $d009
		memory  byte SP5X		= $d00a
		memory  byte SP5Y		= $d00b
		memory  byte SP6X		= $d00c
		memory  byte SP6Y		= $d00d
		memory  byte SP7X		= $d00e
		memory  byte SP7Y		= $d00f

		memory  byte MSIGX		= $d010
		memory  byte SCROLY		= $d011
		memory  byte RASTER		= $d012
		memory  byte LPENX		= $d013
		memory  byte LPENY		= $d014
		memory  byte SPENA		= $d015
		memory  byte SCROLX		= $d016
		memory  byte YXPAND		= $d017
		memory  byte VMCSB		= $d018
		memory  byte VICIRQ		= $d019
		memory  byte IREQMASK		= $d01a
		memory  byte SPBGPR		= $d01b
		memory  byte SPMC		= $d01c
		memory  byte XXPAND		= $d01d
		memory  byte SPSPCL		= $d01e
		memory  byte SPBGCL		= $d01f

		memory  byte EXTCOL		= $d020		; border color
		memory  byte BGCOL0		= $d021		; screen color
		memory  byte BGCOL1		= $d022
		memory  byte BGCOL2		= $d023
		memory  byte BGCOL4		= $d024
		memory  byte SPMC0		= $d025
		memory  byte SPMC1		= $d026
		memory  byte SP0COL		= $d027
		memory  byte SP1COL		= $d028
		memory  byte SP2COL		= $d029
		memory  byte SP3COL		= $d02a
		memory  byte SP4COL		= $d02b
		memory  byte SP5COL		= $d02c
		memory  byte SP6COL		= $d02d
		memory  byte SP7COL		= $d02e

; ---- end of VIC-II registers ----

; ---- C64 basic and kernal ROM float constants and functions ----

		; note: the fac1 and fac2 are working registers and take 6 bytes each,
		; floats in memory  (and rom) are stored in 5-byte MFLPT packed format.

		; constants in five-byte "mflpt" format in the BASIC ROM
		memory  float  FL_PIVAL		= $aea8  ; 3.1415926...
		memory  float  FL_N32768	= $b1a5  ; -32768
		memory  float  FL_FONE		= $b9bc  ; 1
		memory  float  FL_SQRHLF	= $b9d6  ; SQR(2) / 2
		memory  float  FL_SQRTWO	= $b9db  ; SQR(2)
		memory  float  FL_NEGHLF	= $b9e0  ; -.5
		memory  float  FL_LOG2		= $b9e5  ; LOG(2)
		memory  float  FL_TENC		= $baf9  ; 10
		memory  float  FL_NZMIL		= $bdbd  ; 1e9 (1 billion)
		memory  float  FL_FHALF		= $bf11  ; .5
		memory  float  FL_LOGEB2	= $bfbf  ; 1 / LOG(2)
		memory  float  FL_PIHALF	= $e2e0  ; PI / 2
		memory  float  FL_TWOPI		= $e2e5  ; 2 * PI
		memory  float  FL_FR4		= $e2ea  ; .25


; note: fac1/2 might get clobbered even if not mentioned in the function's name.
; note: for subtraction and division, the left operand is in fac2, the right operand in fac1.

; checked functions below:
asmsub	MOVFM		(mflpt: word @ AY) -> clobbers(A,Y) -> ()	= $bba2		; load mflpt value from memory  in A/Y into fac1
asmsub	FREADMEM	() -> clobbers(A,Y) -> ()			= $bba6		; load mflpt value from memory  in $22/$23 into fac1
asmsub	CONUPK		(mflpt: word @ AY) -> clobbers(A,Y) -> ()	= $ba8c		; load mflpt value from memory  in A/Y into fac2
asmsub	FAREADMEM	() -> clobbers(A,Y) -> ()			= $ba90		; load mflpt value from memory  in $22/$23 into fac2
asmsub	MOVFA		() -> clobbers(A,X) -> ()			= $bbfc		; copy fac2 to fac1
asmsub	MOVAF		() -> clobbers(A,X) -> ()			= $bc0c		; copy fac1 to fac2  (rounded)
asmsub	MOVEF		() -> clobbers(A,X) -> ()			= $bc0f		; copy fac1 to fac2
asmsub	FTOMEMXY	(mflpt: word @ XY) -> clobbers(A,Y) -> ()	= $bbd4		; store fac1 to memory  X/Y as 5-byte mflpt

; fac1-> signed word in Y/A (might throw ILLEGAL QUANTITY)
; (use c64flt.FTOSWRDAY to get A/Y output; lo/hi switched to normal order)
asmsub	FTOSWORDYA	() -> clobbers(X) -> (byte @ Y, byte @ A)	= $b1aa

; fac1 -> unsigned word in Y/A (might throw ILLEGAL QUANTITY) (result also in $14/15)
; (use c64flt.GETADRAY to get A/Y output; lo/hi switched to normal order)
asmsub	GETADR		() -> clobbers(X) -> (byte @ Y, byte @ A)	= $b7f7

asmsub	QINT		() -> clobbers(A,X,Y) -> ()			= $bc9b		; fac1 -> 4-byte signed integer in 98-101 ($62-$65), with the MSB FIRST.
asmsub	AYINT		() -> clobbers(A,X,Y) -> ()			= $b1bf		; fac1-> signed word in 100-101 ($64-$65) MSB FIRST. (might throw ILLEGAL QUANTITY)

; signed word in Y/A -> float in fac1
; (use c64flt.GIVAYFAY to use A/Y input; lo/hi switched to normal order)
; there is also c64flt.GIVUAYF - unsigned word in A/Y (lo/hi) to fac1
; there is also c64flt.FREADS32  that reads from 98-101 ($62-$65) MSB FIRST
; there is also c64flt.FREADUS32  that reads from 98-101 ($62-$65) MSB FIRST
; there is also c64flt.FREADS24AXY  that reads signed int24 into fac1 from A/X/Y (lo/mid/hi bytes)
asmsub	GIVAYF		(lo: byte @ Y, hi: byte @ A) -> clobbers(A,X,Y) -> ()	= $b391

asmsub	FREADUY		(ubyte: byte @ Y) -> clobbers(A,X,Y) -> ()	= $b3a2		; 8 bit unsigned Y -> float in fac1
asmsub	FREADSA		(sbyte: byte @ A) -> clobbers(A,X,Y) -> ()	= $bc3c		; 8 bit signed A -> float in fac1
asmsub	FREADSTR	(length: byte @ A) -> clobbers(A,X,Y) -> ()	= $b7b5		; str -> fac1, $22/23 must point to string, A=string length
asmsub	FPRINTLN	() -> clobbers(A,X,Y) -> ()			= $aabc		; print string of fac1, on one line (= with newline)
asmsub	FOUT		() -> clobbers(X) -> (word @ AY)		= $bddd		; fac1 -> string, address returned in AY ($0100)

asmsub	FADDH		() -> clobbers(A,X,Y) -> ()			= $b849		; fac1 += 0.5, for rounding- call this before INT
asmsub	MUL10		() -> clobbers(A,X,Y) -> ()			= $bae2		; fac1 *= 10
asmsub	DIV10		() -> clobbers(A,X,Y) -> ()			= $bafe		; fac1 /= 10 , CAUTION: result is always positive!
asmsub	FCOMP		(mflpt: word @ AY) -> clobbers(X,Y) -> (byte @ A) = $bc5b		; A = compare fac1 to mflpt in A/Y, 0=equal 1=fac1 is greater, 255=fac1 is less than

asmsub	FADDT		() -> clobbers(A,X,Y) -> ()			= $b86a		; fac1 += fac2
asmsub	FADD		(mflpt: word @ AY) -> clobbers(A,X,Y) -> ()	= $b867		; fac1 += mflpt value from A/Y
asmsub	FSUBT		() -> clobbers(A,X,Y) -> ()			= $b853		; fac1 = fac2-fac1   mind the order of the operands
asmsub	FSUB		(mflpt: word @ AY) -> clobbers(A,X,Y) -> ()	= $b850		; fac1 = mflpt from A/Y - fac1
asmsub	FMULTT 		() -> clobbers(A,X,Y) -> ()			= $ba2b		; fac1 *= fac2
asmsub	FMULT		(mflpt: word @ AY) -> clobbers(A,X,Y) -> ()	= $ba28		; fac1 *= mflpt value from A/Y
asmsub	FDIVT 		() -> clobbers(A,X,Y) -> ()			= $bb12		; fac1 = fac2/fac1   mind the order of the operands
asmsub	FDIV  		(mflpt: word @ AY) -> clobbers(A,X,Y) -> ()	= $bb0f		; fac1 = mflpt in A/Y / fac1
asmsub	FPWRT		() -> clobbers(A,X,Y) -> ()			= $bf7b		; fac1 = fac2 ** fac1
asmsub	FPWR		(mflpt: word @ AY) -> clobbers(A,X,Y) -> ()	= $bf78		; fac1 = fac2 ** mflpt from A/Y

asmsub	NOTOP		() -> clobbers(A,X,Y) -> ()			= $aed4		; fac1 = NOT(fac1)
asmsub	INT		() -> clobbers(A,X,Y) -> ()			= $bccc		; INT() truncates, use FADDH first to round instead of trunc
asmsub	LOG		() -> clobbers(A,X,Y) -> ()			= $b9ea		; fac1 = LN(fac1)  (natural log)
asmsub	SGN		() -> clobbers(A,X,Y) -> ()			= $bc39		; fac1 = SGN(fac1), result of SIGN (-1, 0 or 1)
asmsub	SIGN		() -> clobbers() -> (byte @ A)			= $bc2b		; SIGN(fac1) to A, $ff, $0, $1 for negative, zero, positive
asmsub	ABS		() -> clobbers() -> ()				= $bc58		; fac1 = ABS(fac1)
asmsub	SQR		() -> clobbers(A,X,Y) -> ()			= $bf71		; fac1 = SQRT(fac1)
asmsub	EXP		() -> clobbers(A,X,Y) -> ()			= $bfed		; fac1 = EXP(fac1)  (e ** fac1)
asmsub	NEGOP		() -> clobbers(A) -> ()				= $bfb4		; switch the sign of fac1
asmsub	RND		() -> clobbers(A,X,Y) -> ()			= $e097		; fac1 = RND()   (use RNDA instead)
asmsub	RNDA		(acc: byte @ A) -> clobbers(A,X,Y) -> ()	= $e09a		; fac1 = RND(A)
asmsub	COS		() -> clobbers(A,X,Y) -> ()			= $e264		; fac1 = COS(fac1)
asmsub	SIN		() -> clobbers(A,X,Y) -> ()			= $e26b		; fac1 = SIN(fac1)
asmsub	TAN		() -> clobbers(A,X,Y) -> ()			= $e2b4		; fac1 = TAN(fac1)
asmsub	ATN		() -> clobbers(A,X,Y) -> ()			= $e30e		; fac1 = ATN(fac1)


; ---- C64 basic routines ----

asmsub	CLEARSCR	() -> clobbers(A,X,Y) -> ()		= $E544		; clear the screen
asmsub	HOMECRSR	() -> clobbers(A,X,Y) -> ()		= $E566		; cursor to top left of screen


; ---- end of C64 basic routines ----



; ---- C64 kernal routines ----

asmsub	IRQDFRT  () -> clobbers(A,X,Y) -> ()			= $EA31		; default IRQ routine
asmsub	IRQDFEND () -> clobbers(A,X,Y) -> ()			= $EA81		; default IRQ end/cleanup
asmsub	CINT     () -> clobbers(A,X,Y) -> ()			= $FF81		; (alias: SCINIT) initialize screen editor and video chip
asmsub	IOINIT   () -> clobbers(A, X) -> ()			= $FF84		; initialize I/O devices (CIA, SID, IRQ)
asmsub	RAMTAS   () -> clobbers(A,X,Y) -> ()			= $FF87		; initialize RAM, tape buffer, screen
asmsub	RESTOR   () -> clobbers(A,X,Y) -> ()			= $FF8A		; restore default I/O vectors
asmsub	VECTOR   (dir: byte @ Pc, userptr: word @ XY) -> clobbers(A,Y) -> ()	= $FF8D		; read/set I/O vector table
asmsub	SETMSG   (value: byte @ A) -> clobbers() -> ()		= $FF90		; set Kernal message control flag
asmsub	SECOND   (address: byte @ A) -> clobbers(A) -> ()	= $FF93		; (alias: LSTNSA) send secondary address after LISTEN
asmsub	TKSA     (address: byte @ A) -> clobbers(A) -> ()	= $FF96		; (alias: TALKSA) send secondary address after TALK
asmsub	MEMTOP   (dir: byte @ Pc, address: word @ XY) -> clobbers() -> (word @ XY)	= $FF99		; read/set top of memory  pointer
asmsub	MEMBOT   (dir: byte @ Pc, address: word @ XY) -> clobbers() -> (word @ XY)	= $FF9C		; read/set bottom of memory  pointer
asmsub	SCNKEY   () -> clobbers(A,X,Y) -> ()			= $FF9F		; scan the keyboard
asmsub	SETTMO   (timeout: byte @ A) -> clobbers() -> ()	= $FFA2		; set time-out flag for IEEE bus
asmsub	ACPTR    () -> clobbers() -> (byte @ A)			= $FFA5		; (alias: IECIN) input byte from serial bus
asmsub	CIOUT    (databyte: byte @ A) -> clobbers() -> ()	= $FFA8		; (alias: IECOUT) output byte to serial bus
asmsub	UNTLK    () -> clobbers(A) -> ()			= $FFAB		; command serial bus device to UNTALK
asmsub	UNLSN    () -> clobbers(A) -> ()			= $FFAE		; command serial bus device to UNLISTEN
asmsub	LISTEN   (device: byte @ A) -> clobbers(A) -> ()	= $FFB1		; command serial bus device to LISTEN
asmsub	TALK     (device: byte @ A) -> clobbers(A) -> ()	= $FFB4		; command serial bus device to TALK
asmsub	READST   () -> clobbers() -> (byte @ A)			= $FFB7		; read I/O status word
asmsub	SETLFS   (logical: byte @ A, device: byte @ X, address: byte @ Y) -> clobbers() -> () = $FFBA	; set logical file parameters
asmsub	SETNAM   (namelen: byte @ A, filename: word @ XY) -> clobbers() -> ()	= $FFBD		; set filename parameters
asmsub	OPEN     () -> clobbers(A,X,Y) -> ()			= $FFC0		; (via 794 ($31A)) open a logical file
asmsub	CLOSE    (logical: byte @ A) -> clobbers(A,X,Y) -> ()	= $FFC3		; (via 796 ($31C)) close a logical file
asmsub	CHKIN    (logical: byte @ X) -> clobbers(A,X) -> ()	= $FFC6		; (via 798 ($31E)) define an input channel
asmsub	CHKOUT   (logical: byte @ X) -> clobbers(A,X) -> ()	= $FFC9		; (via 800 ($320)) define an output channel
asmsub	CLRCHN   () -> clobbers(A,X) -> ()			= $FFCC		; (via 802 ($322)) restore default devices
asmsub	CHRIN    () -> clobbers(Y) -> (byte @ A)		= $FFCF		; (via 804 ($324)) input a character (for keyboard, read a whole line from the screen) A=byte read.
asmsub	CHROUT   (char: byte @ A) -> clobbers() -> ()		= $FFD2		; (via 806 ($326)) output a character
asmsub	LOAD     (verify: byte @ A, address: word @ XY) -> clobbers() -> (byte @Pc, byte @ A, byte @ X, byte @ Y) = $FFD5	; (via 816 ($330)) load from device
asmsub	SAVE     (zp_startaddr: byte @ A, endaddr: word @ XY) -> clobbers() -> (byte @ Pc, byte @ A) = $FFD8	; (via 818 ($332)) save to a device
asmsub	SETTIM   (low: byte @ A, middle: byte @ X, high: byte @ Y) -> clobbers() -> ()	= $FFDB		; set the software clock
asmsub	RDTIM    () -> clobbers() -> (byte @ A, byte @ X, byte @ Y) = $FFDE	; read the software clock
asmsub	STOP     () -> clobbers(A,X) -> (byte @ Pz, byte @ Pc)	= $FFE1		; (via 808 ($328)) check the STOP key
asmsub	GETIN    () -> clobbers(X,Y) -> (byte @ A)		= $FFE4		; (via 810 ($32A)) get a character
asmsub	CLALL    () -> clobbers(A,X) -> ()			= $FFE7		; (via 812 ($32C)) close all files
asmsub	UDTIM    () -> clobbers(A,X) -> ()			= $FFEA		; update the software clock
asmsub	SCREEN   () -> clobbers() -> (byte @ X, byte @ Y)	= $FFED		; read number of screen rows and columns
asmsub	PLOT     (dir: byte @ Pc, col: byte @ Y, row: byte @ X) -> clobbers() -> (byte @ X, byte @ Y)	= $FFF0		; read/set position of cursor on screen
asmsub	IOBASE   () -> clobbers() -> (byte @ X, byte @ Y)	= $FFF3		; read base address of I/O devices

; ---- end of C64 kernal routines ----

}
