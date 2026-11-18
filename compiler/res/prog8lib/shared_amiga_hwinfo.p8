; hwinfo - hardware snapshot for bare-metal programs, shared by the amiga500
; and amiga1200 targets.
;
; All information is read from the running OS, so call gather() before
; custom.grab_system(): the exec memory list, the vector base and the floppy
; devices are out of reach once the system is taken over. hwinfo never grabs
; the machine itself and never writes a custom chip register.
;
; Detection degrades instead of failing: a source that is missing or
; implausible (vamos, for instance) leaves its fields at their defaults and
; gather() still reports success, so callers can program against ready alone.

%import custom
%import exec
%import syslib
%import trackdisk

hwinfo {
    %option merge, no_symbol_prefixing, ignore_unused

    ; ExecBase field offsets: AttnFlags, MemList, VBlankFrequency.
    private const uword EXECB_ATTNFLAGS = 296
    private const uword EXECB_MEMLIST = 322
    private const uword EXECB_VBLANKFREQ = 530

    struct MemHeader {    ; total size: 32
        ^^MemHeader Succ       ; 0
        ^^MemHeader Pred       ; 4
        ubyte Type            ; 8
        byte Pri              ; 9
        str Name              ; 10
        uword Attributes      ; 14
        pointer First         ; 16
        pointer Lower         ; 20
        pointer Upper         ; 24
        uword Free            ; 28
    }

    ; Zorro II RAM sits behind the chip bus: fast memory, but the slow kind.
    private const long SLOWRAM_FIRST = $c00000
    private const long SLOWRAM_LAST = $dfffff

    private const ubyte MAX_SLOW_REGIONS = 4
    private const ubyte MAX_FAST_REGIONS = 8
    private const ubyte MAX_MEM_NODES = 32      ; stops a corrupt MemList

    enum CPU {
        MC68000,
        MC68010,
        MC68020,
        MC68030,
        MC68040,
        MC68060
    }

    enum VIDEO {
        PAL,
        NTSC
    }

    struct MemRegion {
        pointer base
        long size
    }

    bool ready
    ubyte cpu               ; a CPU enum value
    bool fpu
    pointer @shared VBR     ; vector base register
    ubyte videomode
    ubyte chipset           ; a custom.CHIPSET value
    MemRegion[1] chipRegion    ; chip RAM segment
    MemRegion[4] slowRegions   ; SLOW RAM segments (slow trapdoor memory)
    ubyte slowCount
    MemRegion[8] fastRegions   ; FAST RAM segments
    ubyte fastCount
    ubyte floppyCount
    uword kickVersion

    sub gather() -> bool {
        ; -- Snapshot the hardware while the OS is still running.
        ; The scan runs once; later calls just report the cached result.
        if ready
            return true

        cpu = CPU::MC68000
        fpu = false
        VBR = 0
        videomode = VIDEO::NTSC
        chipset = custom.CHIPSET::OCS
        chipRegion[0].base = 0
        chipRegion[0].size = 0
        slowCount = 0
        fastCount = 0
        floppyCount = 0
        kickVersion = 0
        for ubyte s in 0 to 3 {
            slowRegions[s].base = 0
            slowRegions[s].size = 0
        }
        for ubyte f in 0 to 7 {
            fastRegions[f].base = 0
            fastRegions[f].size = 0
        }

        kickVersion = sys.exec_version()
        chipset = custom.detect_chipset()

        pointer execBase = peekp(4)
        if execBase != 0 {
            uword attnFlags = peekw(execBase + EXECB_ATTNFLAGS)
            cpu = detect_cpu(attnFlags)
            fpu = (attnFlags & (exec.AFF_68881 | exec.AFF_68882 | exec.AFF_FPU40 | exec.AFF_68060)) != 0
            if peek(execBase + EXECB_VBLANKFREQ)==50
                videomode = VIDEO::PAL
            VBR = read_VBR()
            scan_memory(execBase)
        }
        probe_floppies()
        ready = true
        return true
    }

    private sub detect_cpu(uword attnFlags) -> ubyte {
        ; AttnFlags has one bit per CPU class; the highest set bit names it.
        if (attnFlags & exec.AFF_68060) != 0
            return CPU::MC68060
        if (attnFlags & exec.AFF_68040) != 0
            return CPU::MC68040
        if (attnFlags & exec.AFF_68030) != 0
            return CPU::MC68030
        if (attnFlags & exec.AFF_68020) != 0
            return CPU::MC68020
        if (attnFlags & exec.AFF_68010) != 0
            return CPU::MC68010
        return CPU::MC68000
    }

    private asmsub read_VBR() -> pointer @D0 {
        ; MOVEC is privileged, so it runs as a temporary supervisor callback
        ; with A5 pointing at it, the way custom.grab_system() reads the VBR.
        %asm {{
hwinfo_AttnFlags = 296
            move.l  a5,-(sp)        ; a5 holds the callback address below
            move.l  d6,-(sp)        ; OS calls do not preserve d6
            move.l  4.w,a6
            btst.b  #0,hwinfo_AttnFlags+1(a6)  ; AFF_68010: before that there is no VBR
            beq.s   .no_VBR
            lea.l   .get_VBR(PC),a5
            jsr     exec.Supervisor(a6)
            bra.s   .VBR_done
.no_VBR:
            moveq   #0,d0
.VBR_done:
            move.l  (sp)+,d6
            move.l  (sp)+,a5
            rts

.get_VBR:
            dc.l    $4E7A0801       ; MOVEC VBR,d0
            rte
        }}
    }

    private sub scan_memory(pointer execBase) {
        ; Walk exec's MemList; Forbid() keeps it from changing while it is read.
        exec.Forbid()
        ^^MemHeader node = peekp(execBase + EXECB_MEMLIST)     ; lh_Head
        ubyte visited = 0
        while node != 0 and visited < MAX_MEM_NODES {
            visited += 1
            long size = (node.Upper as long) - (node.Lower as long)
            if size > 0 {
                if (node.Attributes & exec.MEMF_CHIP) != 0 {
                    if chipRegion[0].base == 0
                        chipRegion[0].base = node.Lower
                    chipRegion[0].size += size
                }
                else if (node.Attributes & exec.MEMF_FAST) != 0
                    add_fast_region(node.Lower, size)
            }
            node = node.Succ        ; the list tail has no successor
        }
        exec.Permit()
    }

    private sub add_fast_region(pointer base, long size) {
        ; Stored entries are counted; regions that no longer fit are only flagged.
        long address = base as long
        if address >= SLOWRAM_FIRST and address <= SLOWRAM_LAST {
            if slowCount < MAX_SLOW_REGIONS {
                slowRegions[slowCount].base = base
                slowRegions[slowCount].size = size
                slowCount += 1
            }
        } else {
            if fastCount < MAX_FAST_REGIONS {
                fastRegions[fastCount].base = base
                fastRegions[fastCount].size = size
                fastCount += 1
            }
        }
    }

    private sub probe_floppies() {
        ; Unit count only: open and close each unit in turn. This borrows
        ; trackdisk's global request, so it cannot run while a unit is open.
        for ubyte unit in 0 to trackdisk.NUMUNITS-1 {
            if trackdisk.opendevice(unit) {
                floppyCount += 1
                trackdisk.closedevice()
            }
        }
    }
}
