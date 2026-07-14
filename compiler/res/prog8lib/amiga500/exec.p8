;; Auto-generated from exec_lib.sfd and exec_lib.i
;; Library base: _SysBase  in prog8: sys.SysBase
;; Bank: 1
;; Functions: 115

exec {
    extsub @bank 1   -30 = Supervisor(pointer userFunction @A5) -> long @D0
    extsub @bank 1   -72 = InitCode(long startClass @D0, long version @D1)
    extsub @bank 1   -78 = InitStruct(pointer initTable @A1, pointer k_memory @A2, long size @D0)
    extsub @bank 1   -84 = MakeLibrary(pointer funcInit @A0, pointer structInit @A1, pointer libInit @A2, long dataSize @D0, long segList @D1) -> pointer @D0
    extsub @bank 1   -90 = MakeFunctions(pointer target @A0, pointer functionArray @A1, long funcDispBase @A2)
    extsub @bank 1   -96 = FindResident(str name @A1) -> pointer @D0
    extsub @bank 1   -102 = InitResident(pointer resident @A1, long segList @D1) -> pointer @D0
    extsub @bank 1   -108 = Alert(long alertNum @D7)
    extsub @bank 1   -114 = Debug(long flags @D0)
    extsub @bank 1   -120 = Disable()
    extsub @bank 1   -126 = Enable()
    extsub @bank 1   -132 = Forbid()
    extsub @bank 1   -138 = Permit()
    extsub @bank 1   -144 = SetSR(long newSR @D0, long mask @D1) -> long @D0
    extsub @bank 1   -150 = SuperState() -> pointer @D0
    extsub @bank 1   -156 = UserState(pointer sysStack @D0)
    extsub @bank 1   -162 = SetIntVector(long intNumber @D0, pointer interrupt @A1) -> pointer @D0
    extsub @bank 1   -168 = AddIntServer(long intNumber @D0, pointer interrupt @A1)
    extsub @bank 1   -174 = RemIntServer(long intNumber @D0, pointer interrupt @A1)
    extsub @bank 1   -180 = Cause(pointer interrupt @A1)
    extsub @bank 1   -186 = Allocate(pointer freeList @A0, long byteSize @D0) -> pointer @D0
    extsub @bank 1   -192 = Deallocate(pointer freeList @A0, pointer memoryBlock @A1, long byteSize @D0)
    extsub @bank 1   -198 = AllocMem(long byteSize @D0, long requirements @D1) -> pointer @D0
    extsub @bank 1   -204 = AllocAbs(long byteSize @D0, pointer location @A1) -> pointer @D0
    extsub @bank 1   -210 = FreeMem(pointer memoryBlock @A1, long byteSize @D0)
    extsub @bank 1   -216 = AvailMem(long requirements @D1) -> long @D0
    extsub @bank 1   -222 = AllocEntry(pointer entry @A0) -> pointer @D0
    extsub @bank 1   -228 = FreeEntry(pointer entry @A0)
    extsub @bank 1   -234 = Insert(pointer list @A0, pointer node @A1, pointer pred @A2)
    extsub @bank 1   -240 = AddHead(pointer list @A0, pointer node @A1)
    extsub @bank 1   -246 = AddTail(pointer list @A0, pointer node @A1)
    extsub @bank 1   -252 = Remove(pointer node @A1)
    extsub @bank 1   -258 = RemHead(pointer list @A0) -> pointer @D0
    extsub @bank 1   -264 = RemTail(pointer list @A0) -> pointer @D0
    extsub @bank 1   -270 = Enqueue(pointer list @A0, pointer node @A1)
    extsub @bank 1   -276 = FindName(pointer list @A0, str name @A1) -> pointer @D0
    extsub @bank 1   -282 = AddTask(pointer task @A1, pointer initPC @A2, pointer finalPC @A3) -> pointer @D0
    extsub @bank 1   -288 = RemTask(pointer task @A1)
    extsub @bank 1   -294 = FindTask(str name @A1) -> pointer @D0
    extsub @bank 1   -300 = SetTaskPri(pointer task @A1, long priority @D0) -> byte @D0
    extsub @bank 1   -306 = SetSignal(long newSignals @D0, long signalSet @D1) -> long @D0
    extsub @bank 1   -312 = SetExcept(long newSignals @D0, long signalSet @D1) -> long @D0
    extsub @bank 1   -318 = Wait(long signalSet @D0) -> long @D0
    extsub @bank 1   -324 = Signal(pointer task @A1, long signalSet @D0)
    extsub @bank 1   -330 = AllocSignal(byte signalNum @D0) -> byte @D0
    extsub @bank 1   -336 = FreeSignal(byte signalNum @D0)
    extsub @bank 1   -342 = AllocTrap(long trapNum @D0) -> long @D0
    extsub @bank 1   -348 = FreeTrap(long trapNum @D0)
    extsub @bank 1   -354 = AddPort(pointer port @A1)
    extsub @bank 1   -360 = RemPort(pointer port @A1)
    extsub @bank 1   -366 = PutMsg(pointer port @A0, pointer message @A1)
    extsub @bank 1   -372 = GetMsg(pointer port @A0) -> pointer @D0
    extsub @bank 1   -378 = ReplyMsg(pointer message @A1)
    extsub @bank 1   -384 = WaitPort(pointer port @A0) -> pointer @D0
    extsub @bank 1   -390 = FindPort(str name @A1) -> pointer @D0
    extsub @bank 1   -396 = AddLibrary(pointer library @A1)
    extsub @bank 1   -402 = RemLibrary(pointer library @A1)
    extsub @bank 1   -408 = OldOpenLibrary(str libName @A1) -> pointer @D0
    extsub @bank 1   -414 = CloseLibrary(pointer library @A1)
    extsub @bank 1   -420 = SetFunction(pointer library @A1, long funcOffset @A0, pointer newFunction @D0) -> pointer @D0
    extsub @bank 1   -426 = SumLibrary(pointer library @A1)
    extsub @bank 1   -432 = AddDevice(pointer device @A1)
    extsub @bank 1   -438 = RemDevice(pointer device @A1)
    extsub @bank 1   -444 = OpenDevice(str devName @A0, long unit @D0, pointer ioRequest @A1, long flags @D1) -> byte @D0
    extsub @bank 1   -450 = CloseDevice(pointer ioRequest @A1)
    extsub @bank 1   -456 = DoIO(pointer ioRequest @A1) -> byte @D0
    extsub @bank 1   -462 = SendIO(pointer ioRequest @A1)
    extsub @bank 1   -468 = CheckIO(pointer ioRequest @A1) -> pointer @D0
    extsub @bank 1   -474 = WaitIO(pointer ioRequest @A1) -> byte @D0
    extsub @bank 1   -480 = AbortIO(pointer ioRequest @A1)
    extsub @bank 1   -486 = AddResource(pointer resource @A1)
    extsub @bank 1   -492 = RemResource(pointer resource @A1)
    extsub @bank 1   -498 = OpenResource(str resName @A1) -> pointer @D0
    extsub @bank 1   -522 = RawDoFmt(str formatString @A0, pointer dataStream @A1, pointer putChProc @A2, pointer putChData @A3) -> pointer @D0
    extsub @bank 1   -528 = GetCC() -> long @D0
    extsub @bank 1   -534 = TypeOfMem(pointer address @A1) -> long @D0
    extsub @bank 1   -540 = Procure(pointer sigSem @A0, pointer bidMsg @A1) -> long @D0
    extsub @bank 1   -546 = Vacate(pointer sigSem @A0, pointer bidMsg @A1)
    extsub @bank 1   -552 = OpenLibrary(str libName @A1, long version @D0) -> pointer @D0
    extsub @bank 1   -558 = InitSemaphore(pointer sigSem @A0)
    extsub @bank 1   -564 = ObtainSemaphore(pointer sigSem @A0)
    extsub @bank 1   -570 = ReleaseSemaphore(pointer sigSem @A0)
    extsub @bank 1   -576 = AttemptSemaphore(pointer sigSem @A0) -> long @D0
    extsub @bank 1   -582 = ObtainSemaphoreList(pointer sigSem @A0)
    extsub @bank 1   -588 = ReleaseSemaphoreList(pointer sigSem @A0)
    extsub @bank 1   -594 = FindSemaphore(str name @A1) -> pointer @D0
    extsub @bank 1   -600 = AddSemaphore(pointer sigSem @A1)
    extsub @bank 1   -606 = RemSemaphore(pointer sigSem @A1)
    extsub @bank 1   -612 = SumKickData() -> long @D0
    extsub @bank 1   -618 = AddMemList(long size @D0, long attributes @D1, long pri @D2, pointer base @A0, str name @A1)
    extsub @bank 1   -624 = CopyMem(pointer source @A0, pointer dest @A1, long size @D0)
    extsub @bank 1   -630 = CopyMemQuick(pointer source @A0, pointer dest @A1, long size @D0)
    extsub @bank 1   -636 = CacheClearU()
    extsub @bank 1   -642 = CacheClearE(pointer address @A0, long length @D0, long caches @D1)
    extsub @bank 1   -648 = CacheControl(long cacheBits @D0, long cacheMask @D1) -> long @D0
    extsub @bank 1   -654 = CreateIORequest(pointer port @A0, long size @D0) -> pointer @D0
    extsub @bank 1   -660 = DeleteIORequest(pointer iorequest @A0)
    extsub @bank 1   -666 = CreateMsgPort() -> pointer @D0
    extsub @bank 1   -672 = DeleteMsgPort(pointer port @A0)
    extsub @bank 1   -678 = ObtainSemaphoreShared(pointer sigSem @A0)
    extsub @bank 1   -684 = AllocVec(long byteSize @D0, long requirements @D1) -> pointer @D0
    extsub @bank 1   -690 = FreeVec(pointer memoryBlock @A1)
    extsub @bank 1   -696 = CreatePool(long requirements @D0, long puddleSize @D1, long threshSize @D2) -> pointer @D0
    extsub @bank 1   -702 = DeletePool(pointer poolHeader @A0)
    extsub @bank 1   -708 = AllocPooled(pointer poolHeader @A0, long memSize @D0) -> pointer @D0
    extsub @bank 1   -714 = FreePooled(pointer poolHeader @A0, pointer k_memory @A1, long memSize @D0)
    extsub @bank 1   -720 = AttemptSemaphoreShared(pointer sigSem @A0) -> long @D0
    extsub @bank 1   -726 = ColdReboot()
    extsub @bank 1   -732 = StackSwap(pointer newStack @A0)
    extsub @bank 1   -762 = CachePreDMA(pointer address @A0, long length @A1, long flags @D0) -> pointer @D0
    extsub @bank 1   -768 = CachePostDMA(pointer address @A0, long length @A1, long flags @D0)
    extsub @bank 1   -774 = AddMemHandler(pointer memhand @A1)
    extsub @bank 1   -780 = RemMemHandler(pointer memhand @A1)
    extsub @bank 1   -786 = ObtainQuickVector(pointer interruptCode @A0) -> long @D0
    extsub @bank 1   -828 = NewMinList(pointer minlist @A0)

    ; ---- struct definitions ----

    struct IOStdReq {  ; total size: 48
        pointer Succ  ; 0
        pointer Pred  ; 4
        ubyte Type  ; 8
        byte Pri  ; 9
        str Name  ; 10
        pointer ReplyPort  ; 14
        uword Length  ; 18
        pointer Device  ; 20
        pointer Unit  ; 24
        uword Command  ; 28
        ubyte Flags  ; 30
        byte Error  ; 31
        long Actual  ; 32
        long IOStdReq_Length  ; 36
        pointer Data  ; 40
        long Offset  ; 44
    }

    struct IORequest {  ; total size: 32
        pointer Succ  ; 0
        pointer Pred  ; 4
        ubyte Type  ; 8
        byte Pri  ; 9
        str Name  ; 10
        pointer ReplyPort  ; 14
        uword Length  ; 18
        pointer Device  ; 20
        pointer Unit  ; 24
        uword Command  ; 28
        ubyte Flags  ; 30
        byte Error  ; 31
    }

    struct Interrupt {  ; total size: 22
        pointer Succ  ; 0
        pointer Pred  ; 4
        ubyte Type  ; 8
        byte Pri  ; 9
        str Name  ; 10
        pointer Data  ; 14
        pointer Code  ; 18
    }

    struct List {  ; total size: 14
        pointer Head  ; 0
        pointer Tail  ; 4
        pointer TailPred  ; 8
        ubyte Type  ; 12
        ubyte Pad  ; 13
    }

    struct Library {  ; total size: 34
        pointer Succ  ; 0
        pointer Pred  ; 4
        ubyte Type  ; 8
        byte Pri  ; 9
        str Name  ; 10
        ubyte Flags  ; 14
        ubyte Pad  ; 15
        uword NegSize  ; 16
        uword PosSize  ; 18
        uword Version  ; 20
        uword Revision  ; 22
        str IdString  ; 24
        long Sum  ; 28
        uword OpenCnt  ; 32
    }

    struct Node {  ; total size: 14
        pointer Succ  ; 0
        pointer Pred  ; 4
        ubyte Type  ; 8
        byte Pri  ; 9
        str Name  ; 10
    }

    struct MinList {  ; total size: 12
        pointer Head  ; 0
        pointer Tail  ; 4
        pointer TailPred  ; 8
    }

    struct MinNode {  ; total size: 8
        pointer Succ  ; 0
        pointer Pred  ; 4
    }

    struct Message {  ; total size: 20
        pointer Succ  ; 0
        pointer Pred  ; 4
        ubyte Type  ; 8
        byte Pri  ; 9
        str Name  ; 10
        pointer ReplyPort  ; 14
        uword Length  ; 18
    }

    struct MsgPort {  ; total size: 34
        pointer Succ  ; 0
        pointer Pred  ; 4
        ubyte Type  ; 8
        byte Pri  ; 9
        str Name  ; 10
        ubyte Flags  ; 14
        ubyte SigBit  ; 15
        pointer SigTask  ; 16
        pointer Head  ; 20
        pointer Tail  ; 24
        pointer TailPred  ; 28
        ubyte List_Type  ; 32
        ubyte Pad  ; 33
    }

    struct Task {  ; total size: 92
        pointer Succ  ; 0
        pointer Pred  ; 4
        ubyte Type  ; 8
        byte Pri  ; 9
        str Name  ; 10
        ubyte Flags  ; 14
        ubyte State  ; 15
        byte IDNestCnt  ; 16
        byte TDNestCnt  ; 17
        long SigAlloc  ; 18
        long SigWait  ; 22
        long SigRecvd  ; 26
        long SigExcept  ; 30
        uword TrapAlloc  ; 34
        uword TrapAble  ; 36
        pointer ExceptData  ; 38
        pointer ExceptCode  ; 42
        pointer TrapData  ; 46
        pointer TrapCode  ; 50
        pointer SPReg  ; 54
        pointer SPLower  ; 58
        pointer SPUpper  ; 62
        pointer Switch  ; 66
        pointer Launch  ; 70
        pointer Head  ; 74
        pointer Tail  ; 78
        pointer TailPred  ; 82
        ubyte List_Type  ; 86
        ubyte Pad  ; 87
        pointer UserData  ; 88
    }

    ; ---- constants ----
    const ubyte UNITB_ACTIVE = 0
    const ubyte UNITF_ACTIVE = $0001
    const ubyte UNITB_INTASK = 1
    const ubyte UNITF_INTASK = $0002
    const long IOERR_OPENFAIL = -1
    const long IOERR_ABORTED = -2
    const long IOERR_NOCMD = -3
    const long IOERR_BADLENGTH = -4
    const long IOERR_BADADDRESS = -5
    const long IOERR_UNITBUSY = -6
    const long IOERR_SELFTEST = -7
    const ubyte AFB_68010 = 0
    const ubyte AFF_68010 = $0001
    const ubyte AFB_68020 = 1
    const ubyte AFF_68020 = $0002
    const ubyte AFB_68030 = 2
    const ubyte AFF_68030 = $0004
    const ubyte AFB_68040 = 3
    const ubyte AFF_68040 = $0008
    const ubyte AFB_68881 = 4
    const ubyte AFF_68881 = $0010
    const ubyte AFB_68882 = 5
    const ubyte AFF_68882 = $0020
    const ubyte AFB_FPU40 = 6
    const ubyte AFF_FPU40 = $0040
    const ubyte AFB_68060 = 7
    const ubyte AFF_68060 = $0080
    const ubyte AFB_FPGA = 10
    const uword AFF_FPGA = $0400
    const ubyte AFB_PRIVATE = 15
    const uword AFF_PRIVATE = $8000
    const ubyte CACRB_EnableI = 0
    const ubyte CACRF_EnableI = $0001
    const ubyte CACRB_FreezeI = 1
    const ubyte CACRF_FreezeI = $0002
    const ubyte CACRB_ClearI = 3
    const ubyte CACRF_ClearI = $0008
    const ubyte CACRB_IBE = 4
    const ubyte CACRF_IBE = $0010
    const ubyte CACRB_EnableD = 8
    const uword CACRF_EnableD = $0100
    const ubyte CACRB_FreezeD = 9
    const uword CACRF_FreezeD = $0200
    const ubyte CACRB_ClearD = 11
    const uword CACRF_ClearD = $0800
    const ubyte CACRB_DBE = 12
    const uword CACRF_DBE = $1000
    const ubyte CACRB_WriteAllocate = 13
    const uword CACRF_WriteAllocate = $2000
    const ubyte CACRB_EnableE = 30
    const long CACRF_EnableE = $40000000
    const ubyte CACRB_CopyBack = 31
    const long CACRF_CopyBack = $80000000
    const ubyte DMAB_Continue = 1
    const ubyte DMAF_Continue = $0002
    const ubyte DMAB_NoModify = 2
    const ubyte DMAF_NoModify = $0004
    const ubyte DMAB_ReadFromRAM = 3
    const ubyte DMAF_ReadFromRAM = $0008
    const ubyte SB_SAR = 15
    const uword SF_SAR = $8000
    const ubyte SB_TQE = 14
    const uword SF_TQE = $4000
    const ubyte SB_SINT = 13
    const uword SF_SINT = $2000
    const ubyte SIH_PRIMASK = $00f0
    const ubyte SIH_QUEUES = $0005
    const ubyte INTB_NMI = 15
    const uword INTF_NMI = $8000
    const ubyte IOB_QUICK = 0
    const ubyte IOF_QUICK = $0001
    const ubyte LIB_VECTSIZE = $0006
    const ubyte LIB_RESERVED = $0004
    const ubyte LIBB_SUMMING = 0
    const ubyte LIBF_SUMMING = $0001
    const ubyte LIBB_CHANGED = 1
    const ubyte LIBF_CHANGED = $0002
    const ubyte LIBB_SUMUSED = 2
    const ubyte LIBF_SUMUSED = $0004
    const ubyte LIBB_DELEXP = 3
    const ubyte LIBF_DELEXP = $0008
    const ubyte LIBB_EXP0CNT = 4
    const ubyte LIBF_EXP0CNT = $0010
    const ubyte MEMF_ANY = $0000
    const ubyte MEMB_PUBLIC = 0
    const ubyte MEMF_PUBLIC = $0001
    const ubyte MEMB_CHIP = 1
    const ubyte MEMF_CHIP = $0002
    const ubyte MEMB_FAST = 2
    const ubyte MEMF_FAST = $0004
    const ubyte MEMB_LOCAL = 8
    const uword MEMF_LOCAL = $0100
    const ubyte MEMB_24BITDMA = 9
    const uword MEMF_24BITDMA = $0200
    const ubyte MEMB_KICK = 10
    const uword MEMF_KICK = $0400
    const ubyte MEMB_CLEAR = 16
    const long MEMF_CLEAR = $00010000
    const ubyte MEMB_LARGEST = 17
    const long MEMF_LARGEST = $00020000
    const ubyte MEMB_REVERSE = 18
    const long MEMF_REVERSE = $00040000
    const ubyte MEMB_TOTAL = 19
    const long MEMF_TOTAL = $00080000
    const ubyte MEMB_NO_EXPUNGE = 31
    const long MEMF_NO_EXPUNGE = $80000000
    const ubyte MEM_BLOCKSIZE = $0008
    const ubyte MEMHB_RECYCLE = 0
    const ubyte MEMHF_RECYCLE = $0001
    const ubyte MEM_DID_NOTHING = $0000
    const long MEM_ALL_DONE = -1
    const ubyte MEM_TRY_AGAIN = $0001
    const ubyte NT_UNKNOWN = $0000
    const ubyte NT_TASK = $0001
    const ubyte NT_INTERRUPT = $0002
    const ubyte NT_DEVICE = $0003
    const ubyte NT_MSGPORT = $0004
    const ubyte NT_MESSAGE = $0005
    const ubyte NT_FREEMSG = $0006
    const ubyte NT_REPLYMSG = $0007
    const ubyte NT_RESOURCE = $0008
    const ubyte NT_LIBRARY = $0009
    const ubyte NT_MEMORY = $000a
    const ubyte NT_SOFTINT = $000b
    const ubyte NT_FONT = $000c
    const ubyte NT_PROCESS = $000d
    const ubyte NT_SEMAPHORE = $000e
    const ubyte NT_SIGNALSEM = $000f
    const ubyte NT_BOOTNODE = $0010
    const ubyte NT_KICKMEM = $0011
    const ubyte NT_GRAPHICS = $0012
    const ubyte NT_DEATHMESSAGE = $0013
    const ubyte NT_USER = $00fe
    const ubyte NT_EXTENDED = $00ff
    const ubyte PF_ACTION = $0003
    const ubyte PA_SIGNAL = $0000
    const ubyte PA_SOFTINT = $0001
    const ubyte PA_IGNORE = $0002
    const uword RTC_MATCHWORD = $4afc
    const ubyte RTB_COLDSTART = 0
    const ubyte RTF_COLDSTART = $0001
    const ubyte RTB_SINGLETASK = 1
    const ubyte RTF_SINGLETASK = $0002
    const ubyte RTB_AFTERDOS = 2
    const ubyte RTF_AFTERDOS = $0004
    const ubyte RTB_AUTOINIT = 7
    const ubyte RTF_AUTOINIT = $0080
    const ubyte RTW_NEVER = $0000
    const ubyte RTW_COLDSTART = $0001
    const ubyte EOS = $0000
    const ubyte BELL = $0007
    const ubyte LF = $000a
    const ubyte CR = $000d
    const ubyte BS = $0008
    const ubyte DEL = $007f
    const ubyte TB_PROCTIME = 0
    const ubyte TF_PROCTIME = $0001
    const ubyte TB_ETASK = 3
    const ubyte TF_ETASK = $0008
    const ubyte TB_STACKCHK = 4
    const ubyte TF_STACKCHK = $0010
    const ubyte TB_EXCEPT = 5
    const ubyte TF_EXCEPT = $0020
    const ubyte TB_SWITCH = 6
    const ubyte TF_SWITCH = $0040
    const ubyte TB_LAUNCH = 7
    const ubyte TF_LAUNCH = $0080
    const ubyte TS_INVALID = $0000
    const ubyte SIGB_ABORT = 0
    const ubyte SIGF_ABORT = $0001
    const ubyte SIGB_CHILD = 1
    const ubyte SIGF_CHILD = $0002
    const ubyte SIGB_BLIT = 4
    const ubyte SIGF_BLIT = $0010
    const ubyte SIGB_SINGLE = 4
    const ubyte SIGF_SINGLE = $0010
    const ubyte SIGB_INTUITION = 5
    const ubyte SIGF_INTUITION = $0020
    const ubyte SIGB_NET = 7
    const ubyte SIGF_NET = $0080
    const ubyte SIGB_DOS = 8
    const uword SIGF_DOS = $0100
    const uword SYS_SIGALLOC = $ffff
    const uword SYS_TRAPALLOC = $8000
    const ubyte INCLUDE_VERSION = $002f
    const ubyte LIBRARY_MINIMUM = $0021
    const long ERR_OPENDEVICE = -1
    const ubyte MEM_BLOCKMASK = $0007
    const ubyte NL = $000a
    const ubyte TS_ADDED = $0001
    const ubyte TS_RUN = $0002
    const ubyte TS_READY = $0003
    const ubyte TS_WAIT = $0004
    const ubyte TS_EXCEPT = $0005
    const ubyte TS_REMOVED = $0006
}
;; End of auto-generated exec_lib.sfd
