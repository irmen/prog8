; A working m68k kernel for use in QEMU `-M virt` m68k machine.
; It parses the bootinfo chunks, prints stuff to the uart output, and reads input as well.


        section .text,code
        xref _end_of_code

        global _start
_start: move.l #$fffc,sp
        bsr bootinfo_parse
        move.l ram_top,d0
        beq 1$
        sub.l #256,d0
        move.l d0,sp
1$:

        movea.l #str_welcome,a0
        bsr puts

2$:     bsr getchar
        move.b d0,d1
        move.b d1,(a0)
        bsr putchar
        cmpi.b #13,d1
        bne 3$
        move.b #10,d0
        bsr putchar
3$:     cmpi.b #$1b,d1
        bne 2$
        bsr do_halt
        bra 2$

; ============================================================
; bootinfo_parse -- iterate bootinfo records and print them
; ============================================================
bootinfo_parse:
        move.l d2,-(sp)
        move.l d4,-(sp)
        moveq #0,d4
        move.l #_end_of_code,d0
        addq.l #1,d0
        lsr.l #1,d0
        lsl.l #1,d0
        movea.l d0,a2
bi_loop:
        clr.l d0
        move.w (a2)+,d0
        beq bi_done
        move.l d0,d2
        cmpi.w #2,d2
        bne 1$
        moveq #1,d4
1$:
        clr.l d1
        move.w (a2)+,d1
        move.l a2,a3
        lea (-4,a2,d1.l),a2
        subq.l #4,d1

        movea.l #str_indent,a0
        bsr puts
        move.l a3,d0
        subq.l #4,d0
        bsr puthex8
        movea.l #str_colon_space,a0
        bsr puts

        move.l d2,d0
        bsr bi_print_tag

        bsr bi_newline

        movea.l #str_pad,a0
        bsr puts
        move.l a3,-(sp)
        move.l d1,-(sp)
        move.l d2,-(sp)
        bsr bi_print_data
        add.w #12,sp

        bsr bi_newline
        bra bi_loop

bi_done:
        tst.l d4
        bne 1$
        movea.l #str_indent,a0
        bsr puts
        movea.l #str_no_cputype,a0
        bsr puts
        bsr bi_newline
1$:     move.l (sp)+,d4
        move.l (sp)+,d2
        rts

bi_newline:
        movea.l #str_newline,a0
        bra puts

; ============================================================
; bi_print_tag -- print tag name for value in d0
; ============================================================
bi_print_tag:
        cmpi.w #1,d0
        beq bi_tag_machtype
        cmpi.w #2,d0
        beq bi_tag_cputype
        cmpi.w #3,d0
        beq bi_tag_fputype
        cmpi.w #4,d0
        beq bi_tag_mmutype
        cmpi.w #5,d0
        beq bi_tag_memchunk
        cmpi.w #6,d0
        beq bi_tag_ramdisk
        cmpi.w #7,d0
        beq bi_tag_cmdline
        cmpi.w #8,d0
        beq bi_tag_rngseed
        cmpi.w #$8000,d0
        beq bi_tag_version
        cmpi.w #$8001,d0
        beq bi_tag_pic
        cmpi.w #$8002,d0
        beq bi_tag_rtc
        cmpi.w #$8003,d0
        beq bi_tag_tty
        cmpi.w #$8004,d0
        beq bi_tag_virtio
        cmpi.w #$8005,d0
        beq bi_tag_ctrl

        movea.l #str_hex_prefix,a0
        bsr puts
        bsr puthex8
        rts

bi_tag_machtype: movea.l #str_machtype,a0
                bra puts
bi_tag_cputype:  movea.l #str_cputype,a0
                bra puts
bi_tag_fputype:  movea.l #str_fputype,a0
                bra puts
bi_tag_mmutype:  movea.l #str_mmutype,a0
                bra puts
bi_tag_memchunk: movea.l #str_memchunk,a0
                bra puts
bi_tag_ramdisk:  movea.l #str_ramdisk,a0
                bra puts
bi_tag_cmdline:  movea.l #str_cmdline,a0
                bra puts
bi_tag_rngseed:  movea.l #str_rngseed,a0
                bra puts
bi_tag_version:  movea.l #str_version,a0
                bra puts
bi_tag_pic:      movea.l #str_pic,a0
                bra puts
bi_tag_rtc:      movea.l #str_rtc,a0
                bra puts
bi_tag_tty:      movea.l #str_tty,a0
                bra puts
bi_tag_ctrl:     movea.l #str_ctrl,a0
                bra puts
bi_tag_virtio:   movea.l #str_virtio,a0
                bra puts

; ============================================================
; bi_print_data -- print data based on tag type
; stack args (low to high): tag, size, data_ptr
; ============================================================
bi_print_data:
        move.l 4(sp),d0
        move.l 8(sp),d1
        movea.l 12(sp),a3

        cmpi.w #1,d0
        beq bi_data_machtype
        cmpi.w #2,d0
        beq bi_data_cputype
        cmpi.w #3,d0
        beq bi_data_u32
        cmpi.w #4,d0
        beq bi_data_u32
        cmpi.w #$8000,d0
        beq bi_data_u32
        cmpi.w #5,d0
        beq bi_data_memchunk
        cmpi.w #6,d0
        beq bi_data_base_size
        cmpi.w #7,d0
        beq bi_data_str
        cmpi.w #8,d0
        beq bi_data_hex
        cmpi.w #$8001,d0
        beq bi_data_base_count
        cmpi.w #$8002,d0
        beq bi_data_base_count
        cmpi.w #$8003,d0
        beq bi_data_base_count
        cmpi.w #$8004,d0
        beq bi_data_base_count
        cmpi.w #$8005,d0
        beq bi_data_base_count

bi_data_hex:
        move.l d1,d3
1$:     move.b (a3)+,d0
        bsr puthex2
        subq.l #1,d3
        beq 2$
        movea.l #str_space,a0
        bsr puts
        bra 1$
2$:     rts

bi_data_u32:
        move.l (a3),d0
        bra puthex8

bi_data_memchunk:
        move.l (a3),-(sp)
        movea.l #str_mem_at,a0
        bsr puts
        move.l (sp)+,d0
        bsr puthex8
        movea.l #str_to,a0
        bsr puts
        move.l 4(a3),d0
        add.l (a3),d0
        move.l d0,ram_top
        bsr puthex8
        movea.l #str_size,a0
        bsr puts
        move.l 4(a3),d0
        bra puthex8

bi_data_base_size:
        movea.l #str_base,a0
        bsr puts
        move.l (a3),d0
        bsr puthex8
        movea.l #str_size,a0
        bsr puts
        move.l 4(a3),d0
        bra puthex8

bi_data_base_count:
        movea.l #str_base,a0
        bsr puts
        move.l (a3),d0
        bsr puthex8
        movea.l #str_count,a0
        bsr puts
        move.l 4(a3),d0
        bra puthex8

bi_data_str:
        movea.l a3,a0
        bra puts

bi_data_machtype:
        move.l (a3),d0
        cmpi.l #14,d0
        beq 1$
        bra puthex8
1$:     movea.l #str_mach_virt,a0
        bra puts

bi_data_cputype:
        move.l (a3),d0
        beq 6$
        btst #0,d0
        bne 1$
        btst #1,d0
        bne 2$
        btst #2,d0
        bne 3$
        btst #3,d0
        bne 4$
        btst #4,d0
        bne 5$
        bra puthex8
1$:     movea.l #str_cpu68020,a0
        bra puts
2$:     movea.l #str_cpu68030,a0
        bra puts
3$:     movea.l #str_cpu68040,a0
        bra puts
4$:     movea.l #str_cpu68060,a0
        bra puts
5$:     movea.l #str_coldfire,a0
        bra puts
6$:     movea.l #str_cpu68000,a0
        bra puts

; ============================================================
puts:   move.b (a0)+,d0
        beq puts_rts
        bsr putchar
        bra puts
puts_rts:
        rts

; ============================================================
puthex8:
        move.l d2,-(sp)
        move.l d3,-(sp)
        move.l d0,d2
        moveq #28,d3
1$:     move.l d2,d0
        lsr.l d3,d0
        and.b #$f,d0
        add.b #'0',d0
        cmp.b #'9',d0
        bls 2$
        add.b #7,d0
2$:     bsr putchar
        subq.l #4,d3
        bpl 1$
        move.l (sp)+,d3
        move.l (sp)+,d2
        rts

; ============================================================
puthex2:
        move.l d2,-(sp)
        move.l d3,-(sp)
        move.l d0,d2
        moveq #4,d3
1$:     move.l d2,d0
        lsr.l d3,d0
        and.b #$f,d0
        add.b #'0',d0
        cmp.b #'9',d0
        bls 2$
        add.b #7,d0
2$:     bsr putchar
        subq.l #4,d3
        bpl 1$
        move.l (sp)+,d3
        move.l (sp)+,d2
        rts

; ============================================================
putchar:
        movea.l #$ff008000,a1
        move.l d0,(a1)
        rts

; ============================================================
getchar:
        suba.l #16,sp
        movea.l #$ff008004,a1
1$:     move.l (a1),d0
        tst.l d0
        beq 1$
        movea.l #$ff008010,a1
        move.l sp,(a1)
        movea.l #$ff008014,a1
        move.l #1,(a1)
        movea.l #$ff008008,a1
        move.l #3,(a1)
        move.b (sp),d0
        adda.l #16,sp
        rts

; ============================================================
do_halt:
        movea.l #$ff009004,a1
        move.l #2,(a1)
        rts

; ============================================================
; Strings
; ============================================================
str_indent:      dc.b "  ",0
str_colon_space: dc.b ": ",0
str_pad:         dc.b "         ",0
str_newline:     dc.b 10,0
str_base:        dc.b "base=0x",0
str_size:        dc.b "  size=0x",0
str_count:       dc.b "  count=0x",0
str_mem_at:      dc.b "mem at 0x",0
str_to:          dc.b " - 0x",0
str_space:       dc.b " ",0
str_hex_prefix: dc.b "TAG_0x",0
str_mach_virt:  dc.b "MACH_VIRT",0
str_cpu68000:   dc.b "CPU_68000",0
str_cpu68020:   dc.b "CPU_68020",0
str_cpu68030:   dc.b "CPU_68030",0
str_cpu68040:   dc.b "CPU_68040",0
str_cpu68060:   dc.b "CPU_68060",0
str_coldfire:   dc.b "ColdFire",0
str_no_cputype: dc.b "(no BI_CPUTYPE - assuming CPU_68000)",0
str_machtype:   dc.b "BI_MACHTYPE",0
str_cputype:    dc.b "BI_CPUTYPE",0
str_fputype:    dc.b "BI_FPUTYPE",0
str_mmutype:    dc.b "BI_MMUTYPE",0
str_memchunk:   dc.b "BI_MEMCHUNK",0
str_ramdisk:    dc.b "BI_RAMDISK",0
str_cmdline:    dc.b "BI_COMMAND_LINE",0
str_rngseed:    dc.b "BI_RNG_SEED",0
str_version:    dc.b "BI_VIRT_QEMU_VER",0
str_pic:        dc.b "BI_VIRT_GF_PIC",0
str_rtc:        dc.b "BI_VIRT_GF_RTC",0
str_tty:        dc.b "BI_VIRT_GF_TTY",0
str_ctrl:       dc.b "BI_VIRT_CTRL",0
str_virtio:     dc.b "BI_VIRT_VIRTIO",0
str_welcome:    dc.b "Hello from m68k! (ESC to halt)",10,0
        align 4
ram_top:        dc.l 0
