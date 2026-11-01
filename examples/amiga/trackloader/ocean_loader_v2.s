;OCEAN LOADER v2 (FFS): ross's non-system floppy DOS\1\3 file loader

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


SECTNUM		equ	11		;sectors per track
SECTBYTE	equ	512		;FFS's sector bytes

; level0 = loader
S0		equ	(0)*4		;level0 stack usage
		RSRESET
sectp		rs.l	SECTNUM		;mfm sector pointers
sdata		rs.b	SECTBYTE	;data sector
sbuff		rs.b	(1731+1)*12/8+2	;file's sectors 12bit buffer
b_SIZEOF	rs.b 	0		;+level -> skip to regs saved

; level1 = readsector
S1		equ	S0+(1+7)*4	;level0 + level1 stack usage (rts+regs)



;****************************** OCEAN LOADER v2 ******************************

;input:		a0=loadaddress (word aligned)
;		a1=filename\0 (""\0 safe)
;		a2=mfmbuffer ($3200 bytes chip ram, word aligned)
;		a3=callback (suba.l a3,a3 if unused)
;		d0=select drive (0,1,2,3)
;		  bit7=1 use previous status (drive/motor), add #-128
;		  bit6=1 no motor off at exit, add #+64
;		  -3 seek to early head position (special), moveq #-3
;		  (imply bit7=bit6=1, used for a clean exit to system)
;		~3300 bytes stack required
;
;output:	Z=0->success, d1=file lenght (can be 0, file as a flag)
;		Z=1->error, static.retry=0->read error else file not found
;		[static] contain static data usable by subsequent calls
;
;register:	all preserved but Z and d1 as return code

;static.start:					;see below
;.earlypos:	(+0).b	-1{0:79}		;early head position (-1 not yet)
;.cylinder:	(+1).b	-1{0:79}		;cylinder (-1 error)
;.track:	(+2).b	-1{0:159}		;track (-1 error)
;.retry:	(+3).b	0(11)			;retry counter
;static.end:	(+4)


loader:		movem.l	d0-a6,-(sp)
		lea	-b_SIZEOF(sp),sp	;mfm *sectp[11]/sdata[512]/sbuff[]

		movea.w	#SYNCWORD,a4		;MFM sync pattern
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
		beq.b	.seek			;-3 special
		bmi.b	.prevstatus		;see params

						;\MTR, unselect all drives
		move.b	#$7f,(a5)		;outward direction, lower head
		bclr	d0,(a5)			;start selected drive motor

		move.w	#500/3,d0		;d0=scratch/generic/counter/-1.w
		bsr	._3xmsdelay		;no RDY check, bads drives support..

.prevstatus:	bsr.b	.intl_toupper		;input file name to uppercase

		moveq	#880/16,d1		;root sector
		lsl.w	#4,d1
		bsr	.readsector

		move.l	d0,d1			;d0=lenght(ifilename)
		move.l	d0,d2
		lea	(a1),a2			;a2=&ifilename

.hash:		mulu.w	#13,d1			;hash calc
		move.b	(a1)+,d5		;d5=scan (readsector zeroed .bh bits)
		add.w	d5,d1
		andi.w	#$7FF,d1
		subq.l	#1,d2
		bhi.b	.hash			;loop and protect from \0 files

		divu.w	#(SECTBYTE/4)-56,d1
		swap	d1				;modulo
;		lsl.w	#2,d1				;get first file sector
		move.l	S0+sdata+24(sp,d1.w*4),d1	;from hash[file] table
		bra.b	.filesector


;input:		a1=&filename
;output:	NAME, d0=lenght(filename)
;trashes:	d1.b

.intl_toupper:
		moveq	#0,d0
		bra.b	.nextc
.chkcl:		addq.l	#1,d0
		cmpi.b	#247,d1
		beq.b	.nextc
		addq.b	#1,d1
		cmpi.b	#224,d1
		bhi.b	.strup
		cmpi.b	#'z'+1,d1
		bhi.b	.nextc
		cmpi.b	#'a'+1,d1
		blo.b	.nextc
.strup:		subi.b	#$20,-1(a1,d0.l)
.nextc:		move.b	(a1,d0.l),d1
		bne.b	.chkcl
		rts


.seek:		moveq	#0,d6			;special case, only seek
		moveq	#0,d1			;no mask mean special
		move.b	.static+0(pc),d1		;static.earlypos
		mulu.w	#SECTNUM*2,d1		;seek to
		bsr	.readsector		;exit with z=1
		beq.b	._fex			;shortcut


.nextentry:	move.l	S0+sdata+496(sp),d1	;hash_chain
		beq.b	.nomore			;not found, D1=0->Z=1->error

.filesector:	bsr	.readsector
		cmp.b	S0+sdata+432(sp),d0	;same lenght(filename)?
		bne.b	.nextentry		;no, next entry

		lea	S0+sdata+433(sp),a1	;&filename
		bsr.b	.intl_toupper

		move.l	d0,d2			;lenght(ifilename)
		lea	(a2),a3			;&ifilename

.strcmp:	cmpm.b	(a1)+,(a3)+
		bne.b	.nextentry		;same hash, same lenght but
		subq.l	#1,d2
		bne.b	.strcmp


._found:		move.l	S0+sdata+324(sp),d4	;byte_size
		move.l	d4,S0+b_SIZEOF+1*4(sp)	;in [d1]
._fex:		beq.b	.success			;null length files
		divu.w	#SECTBYTE,d4		;d4={bytes:blocks}

		;moveq	#0,d2			;12b position
		moveq	#12,d0			;bits (const)
		lea	S0+sbuff(sp),a2

.seq:		lea	S0+sdata+SECTBYTE-200(sp),a1	;-data_blocks[]
		move.l	S0+sdata+8(sp),d3		;high_seq
.fdb:		move.l	-(a1),d5			;read data sector(s)
		bfins	d5,(a2){d2:d0}			;pack bits
		add.l	d0,d2
		subq.l	#1,d3
		bne.b	.fdb

		move.l	S0+sdata+504(sp),d1	;extension
		beq.b	.next			;end of sequence
		bsr.b	.readsector		;else read next extension block
		bra.b	.seq

.next:		;moveq	#0,d3
		bfextu	(a2){d3:d0},d1		;first/next_data (one block minimum)
		add.l	d0,d3			;next
		cmp.l	d2,d3
		bhi.b	.success			;end of stream, success
		bsr.b	.readsector		;read data sector

		lea	S0+sdata(sp),a1		;sector data
		subq.w	#1,d4			;[d4.w]=blocks--
		bmi.b	.bytescopy

		moveq	#SECTBYTE/16-1,d1
.c16:		move.l	(a1)+,(a0)+		;copy 16 bytes at time
		move.l	(a1)+,(a0)+
		move.l	(a1)+,(a0)+
		move.l	(a1)+,(a0)+
		dbf	d1,.c16
		bra.b	.next

.bytescopy	swap	d4			;last bytes to copy
		bra.b	.b1l
.b1copy:	move.b	(a1)+,(a0)+
.b1l:		dbf	d4,.b1copy		;[d4.h]=bytes--

.success:	moveq	#-1,d1			;d1=-1->Z=0->success on exit

.nomore:		move.w	#$0400,adkcon(a6)	;disable WORDSYNC (required by KS1.x)

		lea	.static+2(pc),a0
		st	(a0)			;invalidate track [static.track]
		lea	S0+b_SIZEOF(sp),sp	;unlink stack buffer

		move.l	(sp),d0			;get initial d0
		add.b	d0,d0			;bit 6 in N
		bmi.b	.motoron

.stopmotors:	st	(a5)			;deselect all drives related bits
		andi.b	#$87,(a5)		;stop all motors
		st	(a5)			;rest settings

.motoron:	tst.l	d1			;success/error report
		movem.l (sp)+,d0-a6
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
		bne.b	._3ms			;timer running?
		dbf	d0,._3xmsdelay
		rts


;input: d1=sector to read, d6/d7 (long term const)
;output: sector in buffer (sbuff), if error forced exit

.readsector:	movem.l	d0/d2-d4/a0-a2,-(sp)
		lea	.static+4(pc),a0		;static.end position (use --setup)
		moveq	#SECTNUM,d0
		divu.w	d0,d1			;d1=sector:track (readsector: const)
		move.b	d0,-(a0)		;static.retry count

.retrysector:	move.l	d1,d5			;d5:SS:CC<-d1=SS:TT
		cmp.b	-(a0),d1		;right track?
		beq	.inbuffer

.notinbuffer:
		moveq	#2,d2			;d2=2 (short term const)
		moveq	#1,d3			;d3=1 (short term const)
;		moveq	#SECTNUM*4-4,d0
;.clearp:	clr.l	S1+sectp(sp,d0.l)	;clear sectp[]
;		subq.l	#4,d0
;		bpl.b	.clearp

		move.b	d1,(a0)			;save requested track
		bset	d2,(a5)
		lsr.w	#1,d5			;TT to CC
		bcc.b	.cylinder
		bchg	d2,(a5)			;select head

.cylinder:	bset	d3,(a5)			;d3=1, outward direction
		move.b	-(a0),d4		;d4=actual cyl, -1 mean no cyl
		bpl.b	.dist			;i know where i'm

		move.b	-(a0),d2		;d2=static.earlypos
.checkt0:	addq.l	#1,d2			;steps counter
		btst	#4,ciaapra(a5)		;t0?
		beq.b	.tzero			;yes, branch
		bsr.b	._step			;step towards t0
		bra.b	.checkt0		;loop

.tzero:		tst.b	(a0)+			;check .earlypos, repos. to .cylinder
		bpl.b	.settle0		;else only t0 seek, discard .earlypos
		move.b	d2,-1(a0)		;first time here, save .earlypos

.settle0:	moveq	#0,d4			;track=0
		moveq	#((18-3)/3)-1,d0	;wait 18ms before direction change
		bsr.b	._3xmsdelay		;(15ms + 3ms from previous step)

.dist:		move.b	d5,(a0)+		;save cylinder destination
		sub.b	d5,d4			;distance
		beq.b	.startdma		;in place!
		bcc.b	.stephead

.changedir:	bchg	d3,(a5)			;d3=1, change direction, inward
		neg.b	d4			;inward need a reverse

.stephead:	bsr.b	._step
		subq.b	#1,d4
		bne.b	.stephead

.settle:	moveq	#((15-3)/3)-1,d0	;wait 15ms settle time
		bsr.b	._3xmsdelay		;(12ms + 3ms from previous step)


.startdma:	tst.l	d6			;if not MFM mask
		beq	.rsexit			;then only a seek is requested

		movea.l	S1+b_SIZEOF+10*4(sp),a2	;[a2]=mfm buffer
		lea	$3200-400-40(a2),a1	;protect from overrun (estimate)

		;intreq= 0DSKSYN, 0DSKBLK / adkcon= MFM, WORDSYNC, 2us cells
		move.l	#$10029500,intreq(a6)
		move.w	a4,(a2)+		;one sync mark minimum in buffer
		move.l	a2,dskpth(a6)		;mfm buffer

		move.w	#$98ff,(a6)		;dsklen
		move.w	#$98ff,(a6)		;start DMA
		subq.l	#2,a2			;realign to first scan

.diskdma:	moveq	#33/3-1,d0		;wait 33ms.. (estimate)
		bsr	._3xmsdelay
		move.l	S1+b_SIZEOF+11*4(sp),d2	;[a3]=callback
		beq.b	.dskblk
		movea.l	d2,a3			;please max 160ms (8-1 PAL frame)
		jsr	(a3)			;or check yourself for DSKBLK

.dskblk:	move.w	intreqr(a6),d2		;interrupt requests
		lsl.w	#3,d2			;no sync mark?
		bpl.b	.error			;error
		lsl.b	#3,d2			;wait DSKBLK
		bpl.b	.dskblk

		moveq	#SECTNUM-1,d2		;sectors to scan
.scan:		cmpa.l	a1,a2
		bhi.b	.error			;protect from buffer overflow
		cmpa.w	(a2)+,a4
		bne.b	.scan			;scan for first word after sync mark
		cmpa.w	(a2),a4
		beq.b	.scan

.checksum:	moveq	#(4+1)*2-1,d0		;header (1P+4R+1C)*(even+odd)
		move.l	(a2)+,d4
		eor.l	d4,d7			;d7=checksum
		and.l	d6,d4
		move.l	(a2)+,d3
		add.l	d4,d4
		eor.l	d3,d7
		and.l	d6,d3
		or.l	d3,d4			;d4=FF:TT:SS:SG
.cloop:		move.l	(a2)+,d3
		eor.l	d3,d7
		dbf	d0,.cloop
		and.l	d6,d7			;d7 need to be 0, so recover in error
		beq.b	.checkok

.error:		moveq	#0,d7			;recover d7
		lea	.static+1(pc),a0		;recover a0 (in ++order)
		move.w	d7,(a6)			;break disk DMA (dsklen zeroed)
		st	(a0)+			;cylinder=-1 (error)
		st	(a0)+			;track=-1 (error)
		subq.b	#1,(a0)			;retry?
		bne	.retrysector
		lea	S1-S0(sp),sp		;clear low stack
;		st	S1+b_SIZEOF+1*4(sp)	;[d1]=error
		moveq	#0,d1			;d1=0->Z=1->error
		jmp	.nomore(pc)		;force exit..

.checkok:	addq.l	#4,a2			;skip unused header/data checksum
		swap	d4			;header is ok
		cmp.b	(a0),d4			;but i'm on the right track?
		bne.b	.error

		swap	d4
		move.w	d4,d3			;sector before gap
		lsr.w	#6,d4
		move.l	a2,S1+sectp(sp,d4.w)	;save to (mfm *sectp[sector])
		lea	4+SECTBYTE*2+4(a2),a2	;advance to next sector
		subq.b	#1,d3
		bne.b	.normalskip
		lea	300(a2),a2		;skip gap (relaxed, was 300*2)
.normalskip:	dbf	d2,.scan

.inbuffer:	swap	d5
;		lsl.w	#2,d5
		movea.l	S1+sectp(sp,d5.w*4),a2	;a2=mfm coded data (even)
		lea	S1+sdata(sp),a0		;a0=512 bytes decode buffer
		lea	SECTBYTE+4(a2),a3	;a3=mfm coded data (odd) (+skip chk)
		move.l	(a2)+,d4		;data checksum (odd), half
		moveq	#(SECTBYTE/4)-1,d0
.sectordecode:	move.l	(a2)+,d2
		move.l	(a3)+,d3
		eor.l	d2,d4			;checksum
		eor.l	d3,d4			;checksum
		and.l	d6,d2			;decode
		and.l	d6,d3			;decode
		add.l	d2,d2
		or.l	d3,d2
		move.l	d2,(a0)+		;decoded long in buffer
		dbf	d0,.sectordecode
		and.l	d6,d4			;checksum ok?
		bne.b	.error
.rsexit:	movem.l	(sp)+,d0/d2-d4/a0-a2
		rts

.static:		dc.b	-1,-1,-1,'r'

    ; !notreached!
