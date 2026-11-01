;OCEAN_LOADER: ross's non-system floppy DOS\0 files loader

PAL3MS		equ	(709379*3)/1000-1
NTSC3MS		equ	(715909*3)/1000-1

_3MS		equ	PAL3MS

SYNCWORD	equ	$4489

CIABASE		equ	$bfd100

; CIAA
ciaapra		equ	$bfe001-CIABASE
ciaatalo	equ	$bfe401-CIABASE
ciaatahi	equ	$bfe501-CIABASE
ciaatblo	equ	$bfe601-CIABASE
ciaatbhi	equ	$bfe701-CIABASE
ciaacra		equ	$bfee01-CIABASE
ciaacrb		equ	$bfef01-CIABASE
; CIAB
ciabprb		equ	$bfd100-CIABASE
ciabtalo	equ	$bfd400-CIABASE
ciabtahi	equ	$bfd500-CIABASE
ciabtblo	equ	$bfd600-CIABASE
ciabtbhi	equ	$bfd700-CIABASE
ciabcra		equ	$bfde00-CIABASE
ciabcrb		equ	$bfdf00-CIABASE

CIATBASE	equ	ciabtblo
CIACBASE	equ	ciabcrb

CUSTOMBASE	equ	$dff024

; custom register
intreqr		equ	$dff01e-CUSTOMBASE
dskpth		equ	$dff020-CUSTOMBASE
dskptl		equ	$dff022-CUSTOMBASE
dsklen		equ	$dff024-CUSTOMBASE
dsksync		equ	$dff07e-CUSTOMBASE
dmacon		equ	$dff096-CUSTOMBASE
intena		equ	$dff09a-CUSTOMBASE
intreq		equ	$dff09c-CUSTOMBASE
adkcon		equ	$dff09e-CUSTOMBASE

A7S0		equ	4			;level0 stack usage (loadaddress)
A7S1		equ	A7S0+4+4		;level1 stack usage (rts+d0.l)
A7P		equ	11*4			;P=mfm sector pointers
A7B		equ	512			;B=buffer data sector
A7SKIP0		equ	A7S0+A7P+A7B		;0=level0 skip (to regs saved)
A7SKIP1		equ	A7S1+A7P+A7B		;1=level1 skip

SECTNUM		equ	11			;sector per track
SECTBYTE	equ	488			;DOS\0 sector



;****************************** OCEAN LOADER ******************************

;input:		a0=loadaddress (word aligned)
;		a1=filename\0 (""\0 safe)
;		a2=mfmbuffer (chip_ram, word aligned)
;		a3=callback (suba.l a3,a3 if unused)
;		d0=select drive (0,1,2,3)
;		  bit7=1 use previous status (drive/motor), add #-128
;		  bit6=1 no motor off at exit, add #+64
;		  -3 seek to early head position (special), moveq #-3
;		  (imply bit7=bit6=1, used for a clean exit to system)
;
;output:	Z=0->success, d1=file lenght (can be 0, file as a flag)
;		Z=1->error, static.retry=0->read error else file not found
;		[static] contain static data usable by subsequent calls
;
;register:	all preserved but Z and d1 as return code

loader:		movem.l	d0-a6,-(a7)
		lea	-(A7P+A7B)(a7),a7	;mfm *sectp[11] / byte buffer[512]
		move.l	a0,-(a7)		;fast access to loadaddress

;static.start:					;see below
;.earlypos:	(+0).b	-1{0:79}		;early head position (-1 not yet)
;.cylinder:	(+1).b	-1{0:79}		;cylinder (-1 error)
;.track:	(+2).b	-1{0:159}		;track (-1 error)
;.retry:	(+3).b	0(11)			;retry counter
;static.end:	(+4)

		lea	SYNCWORD.w,a4		;MFM sync pattern
		lea	CIABASE,a5		;ciabprb
		lea	CUSTOMBASE,a6		;dsklen
		move.l	#$55555555,d6		;d6=mfm mask (long term const)
		moveq	#0,d7			;d7=0 (long term const)

		move.w	#$8210,dmacon(a6)	;enable disk DMA (for #$7fff progs..)
;		move.w	#$7f00,adkcon(a6)	;clear disk related bits
		move.w	a4,dsksync(a6)		;DSKSYNC (mandatory on KS1.x)

		move.b	#(_3MS)&$FF,CIATBASE(a5)	;L
		move.b	#(_3MS)>>08,CIATBASE+$0100(a5)	;H set 3ms prescaler

		addq.b	#3,d0			;select drive
		beq	._onlyseek
		bmi.s	._prevstatus		;see params

						;\MTR, unselect all drives
		move.b	#$7f,(a5)		;outward direction, lower head
		bclr	d0,(a5)			;start selected drive motor

		move.w	#500/3,d0		;d0=scratch/generic/counter/-1.w
		bsr	._3xmsdelay		;no RDY check, bads drives support..

._prevstatus:	bsr.s	.strupp			;input file name to uppercase
		move.l	d1,d0			;d0=lenght(filename)

		moveq	#880/16,d1		;root sector
		lsl.w	#4,d1
		bsr	.readsector

		move.l	d0,d1
		move.w	d0,d2
		movea.l A7SKIP0+9*4(a7),a0	;[a1]=original name

.hash:		mulu.w	#13,d1			;hash calc
		move.b	(a0)+,d5		;d5=scan (readsector zeroed .h bits)
		add.w	d5,d1
		andi.w	#$7FF,d1
		subq.w	#1,d2
		bhi.s	.hash			;loop and protect from \0 files

		divu.w	#(A7B/4)-56,d1
		swap	d1			;modulo
		lsl.w	#2,d1			;get first file sector
		move.l	A7S0+A7P+24(a7,d1.w),d1	;from hash[file] table
		bra.s	.filesector


;input:		a1=&filename
;output:	NAME, d1=lenght(filename)
;lost:		d2.b

.strupp:		moveq	#0,d1
._nextc:		move.b	(a1,d1.w),d2
		beq.s	._strx
		addq.w	#1,d1
		cmpi.b	#'z',d2
		bhi.s	._nextc
		cmpi.b	#'a',d2
		blo.s	._nextc
		subi.b	#$20,-1(a1,d1.w)
		bra.s	._nextc
._strx:		rts


._nextentry:	move.l	A7S0+A7P+496(a7),d1	;hash_chain
		beq.s	.nomore			;not found, D1=0->Z=1->error

.filesector:	bsr	.readsector
		cmp.b	A7S0+A7P+432(a7),d0	;same lenght(filename)?
		bne.s	._nextentry		;no, next entry

		lea	A7S0+A7P+433(a7),a1	;len(filename)
		bsr.s	.strupp

		move.w	d0,d1			;original lenght
		movea.l A7SKIP0+9*4(a7),a0	;[a1]=original name

._strcmp:	cmpm.b	(a0)+,(a1)+
		bne.s	._nextentry		;same hash, same lenght but
		subq.b	#1,d1
		bne.s	._strcmp

._found:		move.l	A7S0+A7P+324(a7),d0	;bytesize
		move.l	d0,A7SKIP0+1*4(a7)	;in [d1]
		divu.w	#SECTBYTE,d0		;d0={bytes:blocks}

.next:		move.l	A7S0+A7P+16(a7),d1	;first/next_data
		beq.s	.success			;end of stream, success
		bsr.s	.readsector		;else continue read sector

		lea	A7SKIP0-SECTBYTE(a7),a1	;data
		movea.l	(a7),a0			;a0=load address

		subq.w	#1,d0			;d0.w=blocks--
		bmi.s	._bytescopy

._blockscopy:	moveq	#SECTBYTE/8-1,d1
._8datacopy:	move.l	(a1)+,(a0)+		;copy 8 bytes at time
		move.l	(a1)+,(a0)+
		dbf	d1,._8datacopy
._updatea0:	move.l	a0,(a7)			;update load address
		bra.s	.next

._1datacopy:	move.b	(a1)+,(a0)+
._1sub:		dbf	d0,._1datacopy		;[d0.h]=bytes--
		bra.s	.next
._bytescopy:	swap	d0			;last bytes to copy
		bra.s	._1sub


._onlyseek:	moveq	#0,d6			;special case, only seek
		moveq	#0,d1			;no mask mean special
		move.b	.static+0(pc),d1		;static.earlypos
		mulu.w	#SECTNUM*2,d1		;fake sector
		bsr.s	.readsector		;seek

.success:	moveq	#-1,d1			;d1=-1->Z=0->success on exit

.nomore:		move.w	#$0400,adkcon(a6)	;disable WORDSYNC (required by KS1.x)

		lea	.static+2(pc),a0
		st	(a0)			;invalidate track [static.track]
		lea	A7SKIP0(a7),a7		;unlink stack buffer

		move.l	(a7),d0			;get initial d0
		lsl.b	#1,d0			;bit 6 in N
		bmi.s	._motoron

._stopmotors:	st	(a5)			;deselect all drives related bits
		andi.b	#$87,(a5)		;stop all motors
		st	(a5)			;rest settings

._motoron:	tst.l	d1			;success/error report
		movem.l (a7)+,d0-a6
		rts				;exit


;input:		d7=0, a5=CIABASE
;output:	head moved one step to DIR direction and 3ms delay

._step:		bclr	d7,(a5)			;bit0 (STEP)
		moveq	#1-1,d0			;set 3ms delay
		bset	d7,(a5)			;step pulse

;input:		d0.w, a5=CIABASE
;output:	3*(d0.w+1)ms delay

._3xmsdelay:	move.b	#%00011001,CIACBASE(a5)	;load, one-shot, start
._3ms:		btst	d7,CIACBASE(a5)		;bit0 (START)
		bne.s	._3ms			;timer running?
		dbf	d0,._3xmsdelay

		rts



;input: d1=sector to read, d6/d7 (long term const)
;output: sector in buffer (A7B), if error forced exit

.readsector:	move.l	d0,-(a7)
		lea	.static+4(pc),a0		;static.end position (use --setup)
		moveq	#SECTNUM,d0
		divu.w	d0,d1			;d1=sector:track (readsector: const)
		move.b	d0,-(a0)		;static.retry count

._retrysector:	move.l	d1,d5			;d5:SS:CC<-d1=SS:TT

		cmp.b	-(a0),d1		;right track?
		beq	.inbuffer

._notinbuffer:	moveq	#2,d2			;d2=2 (short term const)
		moveq	#1,d3			;d3=1 (short term const)

		moveq	#A7P-4,d0
._clearp:	clr.l	A7S1(a7,d0.w)		;clear sectp[]
		subq.w	#4,d0
		bpl.s	._clearp

		move.b	d1,(a0)			;save requested track
		bset	d2,(a5)
		lsr.w	#1,d5			;TT to CC
		bcc.s	._cylinder
		bchg	d2,(a5)			;select head

._cylinder:	bset	d3,(a5)			;d3=1, outward direction
		move.b	-(a0),d4		;d4=actual cyl, -1 mean no cyl
		bpl.s	._dist			;i know where i'm

		move.b	-(a0),d2		;d2=static.earlypos
._checkt0:	addq.b	#1,d2			;steps counter
		btst	#4,ciaapra(a5)		;t0?
		beq.s	._tzero			;yes, branch
		bsr.s	._step			;step towards t0
		bra.s	._checkt0		;loop

._tzero:		tst.b	(a0)+			;check .earlypos, repos. to .cylinder
		bpl.s	._settle0		;else only t0 seek, discard .earlypos
		move.b	d2,-1(a0)		;first time here, save .earlypos

._settle0:	moveq	#0,d4			;track=0
		moveq	#((18-3)/3)-1,d0	;wait 18ms before direction change
		bsr.s	._3xmsdelay		;(15ms + 3ms from previous step)

._dist:		move.b	d5,(a0)+		;save cylinder destination
		sub.w	d5,d4			;distance
		beq.s	._startdma		;in place!
		bcc.s	._stephead

._changedir:	bchg	d3,(a5)			;d3=1, change direction, inward
		neg.w	d4			;inward need a reverse

._stephead:	bsr.s	._step
		subq.w	#1,d4
		bne.s	._stephead

._settle:	moveq	#((15-3)/3)-1,d0	;wait 15ms settle time
		bsr.s	._3xmsdelay		;(12ms + 3ms from previous step)


._startdma:	tst.w	d6			;if not MFM mask
		beq	._rsexit			;then only a seek is requested

		movea.l	A7SKIP1+8*4+2*4(a7),a2	;[a2]=mfm buffer
		lea	$3200-400-40(a2),a1	;protect from overrun (estimate)

		;intreq= 0DSKSYN, 0DSKBLK / adkcon= MFM, WORDSYNC, 2us cells
		move.l	#$10029500,intreq(a6)
		move.w	a4,(a2)+		;one sync mark minimum in buffer
		move.l	a2,dskpth(a6)		;mfm buffer

		move.w	#$98ff,(a6)		;dsklen
		move.w	#$98ff,(a6)		;start DMA
		subq.l	#2,a2			;realign to first scan

._diskdma:	moveq	#33/3-1,d0		;wait 33ms.. (estimate)
		bsr	._3xmsdelay
		move.l	A7SKIP1+8*4+3*4(a7),d2	;[a3]=callback
		beq.s	._dskblk
		movea.l	d2,a3			;please max 160ms (8-1 PAL frame)
		jsr	(a3)			;or check yourself for DSKBLK

._dskblk:	move.w	intreqr(a6),d2		;interrupt requests
		lsl.w	#3,d2			;no sync mark?
		bpl.s	._error			;error
		lsl.b	#3,d2			;wait DSKBLK
		bpl.s	._dskblk

		moveq	#SECTNUM-1,d2		;sectors to scan
._scan:		cmpa.l	a1,a2
		bhi.s	._error			;protect from buffer overflow
		cmpa.w	(a2)+,a4
		bne.s	._scan			;scan for first word after sync mark
		cmpa.w	(a2),a4
		beq.s	._scan

._checksum:	moveq	#(4+1)*2-1,d0		;header (1P+4R+1C)*(even+odd)
		move.l	(a2)+,d4
		eor.l	d4,d7			;d7=checksum
		and.l	d6,d4
		move.l	(a2)+,d3
		add.l	d4,d4
		eor.l	d3,d7
		and.l	d6,d3
		or.l	d3,d4			;d4=FF:TT:SS:SG
._cloop:		move.l	(a2)+,d3
		eor.l	d3,d7
		dbf	d0,._cloop
		and.l	d6,d7			;d7 need to be 0, so recover in error
		beq.s	._checkok

._error:		moveq	#0,d7			;recover d7
		lea	.static+1(pc),a0		;recover a0 (in ++order)
		move.w	d7,(a6)			;break disk DMA (dsklen zeroed)
		st	(a0)+			;cylinder=-1 (error)
		st	(a0)+			;track=-1 (error)
		subq.b	#1,(a0)			;retry?
		bne	._retrysector
		addq.l	#A7S1-A7S0,a7		;clear low stack
;		st	A7SKIP0+1*4(a7)		;[d1]=error
		moveq	#0,d1			;d1=0->Z=1->error
		jmp	.nomore(pc)		;force exit..

._checkok:	addq.l	#4,a2			;skip unused header/data checksum
		swap	d4			;header is ok
		cmp.b	(a0),d4			;but i'm on the right track?
		bne.s	._error

		swap	d4
		move.w	d4,d3			;sector before gap
		lsr.w	#6,d4
		move.l	a2,A7S1(a7,d4.w)	;save to (mfm *sectp[sector])
		lea	4+A7B*2+4(a2),a2	;advance to next sector
		subq.b	#1,d3
		bne.s	._normalskip
		lea	300*2(a2),a2		;skip gap
._normalskip:	dbf	d2,._scan

.inbuffer:	swap	d5
		lsl.w	#2,d5
		movea.l	A7S1(a7,d5.w),a2	;a2=mfm coded data (even)
		lea	A7SKIP1-A7B(a7),a0	;a0=512 bytes decode buffer
		lea	A7B+4(a2),a3		;a3=mfm coded data (odd) (+skip chk)
		move.l	(a2)+,d4		;data checksum (odd), half
		moveq	#(A7B/4)-1,d0
._sectordecode:	move.l	(a2)+,d2
		move.l	(a3)+,d3
		eor.l	d2,d4			;checksum
		eor.l	d3,d4			;checksum
		and.l	d6,d2			;decode
		and.l	d6,d3			;decode
		add.l	d2,d2
		or.l	d3,d2
		move.l	d2,(a0)+		;decoded long in buffer
		dbf	d0,._sectordecode
		and.l	d6,d4			;checksum ok?
		bne.s	._error
._rsexit:	move.l	(a7)+,d0
		rts

.static:		dc.b	-1,-1,-1,'R'

    ; !notreached!
