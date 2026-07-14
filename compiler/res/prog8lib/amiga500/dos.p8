;; Auto-generated from dos_lib.sfd and dos_lib.i
;; Library base: _DOSBase  in prog8: sys.DOSBase
;; Bank: 2
;; Functions: 161

dos {
    extsub @bank 2   -30 = Open(str name @D1, long accessMode @D2) -> pointer @D0
    extsub @bank 2   -36 = Close(pointer file @D1) -> long @D0
    extsub @bank 2   -42 = Read(pointer file @D1, pointer buffer @D2, long length @D3) -> long @D0
    extsub @bank 2   -48 = Write(pointer file @D1, pointer buffer @D2, long length @D3) -> long @D0
    extsub @bank 2   -54 = Input() -> pointer @D0
    extsub @bank 2   -60 = Output() -> pointer @D0
    extsub @bank 2   -66 = Seek(pointer file @D1, long position @D2, long offset @D3) -> long @D0
    extsub @bank 2   -72 = DeleteFile(str name @D1) -> long @D0
    extsub @bank 2   -78 = Rename(str oldName @D1, str newName @D2) -> long @D0
    extsub @bank 2   -84 = Lock(str name @D1, long k_type @D2) -> pointer @D0
    extsub @bank 2   -90 = UnLock(pointer lock @D1)
    extsub @bank 2   -96 = DupLock(pointer lock @D1) -> pointer @D0
    extsub @bank 2   -102 = Examine(pointer lock @D1, pointer fileInfoBlock @D2) -> long @D0
    extsub @bank 2   -108 = ExNext(pointer lock @D1, pointer fileInfoBlock @D2) -> long @D0
    extsub @bank 2   -114 = Info(pointer lock @D1, pointer parameterBlock @D2) -> long @D0
    extsub @bank 2   -120 = CreateDir(str name @D1) -> pointer @D0
    extsub @bank 2   -126 = CurrentDir(pointer lock @D1) -> pointer @D0
    extsub @bank 2   -132 = IoErr() -> long @D0
    extsub @bank 2   -138 = CreateProc(str name @D1, long pri @D2, pointer segList @D3, long stackSize @D4) -> pointer @D0
    extsub @bank 2   -144 = Exit(long returnCode @D1)
    extsub @bank 2   -150 = LoadSeg(str name @D1) -> pointer @D0
    extsub @bank 2   -156 = UnLoadSeg(pointer seglist @D1)
    extsub @bank 2   -174 = DeviceProc(str name @D1) -> pointer @D0
    extsub @bank 2   -180 = SetComment(str name @D1, str comment @D2) -> long @D0
    extsub @bank 2   -186 = SetProtection(str name @D1, long protect @D2) -> long @D0
    extsub @bank 2   -192 = DateStamp(pointer date @D1) -> pointer @D0
    extsub @bank 2   -198 = Delay(long timeout @D1)
    extsub @bank 2   -204 = WaitForChar(pointer file @D1, long timeout @D2) -> long @D0
    extsub @bank 2   -210 = ParentDir(pointer lock @D1) -> pointer @D0
    extsub @bank 2   -216 = IsInteractive(pointer file @D1) -> long @D0
    extsub @bank 2   -222 = Execute(str k_string @D1, pointer file @D2, pointer file2 @D3) -> long @D0
    extsub @bank 2   -228 = AllocDosObject(long k_type @D1, pointer tags @D2) -> pointer @D0
    extsub @bank 2   -234 = FreeDosObject(long k_type @D1, pointer ptr @D2)
    extsub @bank 2   -240 = DoPkt(pointer port @D1, long action @D2, long arg1 @D3, long arg2 @D4, long arg3 @D5, long arg4 @D6, long arg5 @D7) -> long @D0
    extsub @bank 2   -246 = SendPkt(pointer dp @D1, pointer port @D2, pointer replyport @D3)
    extsub @bank 2   -252 = WaitPkt() -> pointer @D0
    extsub @bank 2   -258 = ReplyPkt(pointer dp @D1, long res1 @D2, long res2 @D3)
    extsub @bank 2   -264 = AbortPkt(pointer port @D1, pointer pkt @D2)
    extsub @bank 2   -270 = LockRecord(pointer fh @D1, long offset @D2, long length @D3, long mode @D4, long timeout @D5) -> bool @D0
    extsub @bank 2   -276 = LockRecords(pointer recArray @D1, long timeout @D2) -> bool @D0
    extsub @bank 2   -282 = UnLockRecord(pointer fh @D1, long offset @D2, long length @D3) -> bool @D0
    extsub @bank 2   -288 = UnLockRecords(pointer recArray @D1) -> bool @D0
    extsub @bank 2   -294 = SelectInput(pointer fh @D1) -> pointer @D0
    extsub @bank 2   -300 = SelectOutput(pointer fh @D1) -> pointer @D0
    extsub @bank 2   -306 = FGetC(pointer fh @D1) -> long @D0
    extsub @bank 2   -312 = FPutC(pointer fh @D1, long ch @D2) -> long @D0
    extsub @bank 2   -318 = UnGetC(pointer fh @D1, long character @D2) -> long @D0
    extsub @bank 2   -324 = FRead(pointer fh @D1, pointer block @D2, long blocklen @D3, long number @D4) -> long @D0
    extsub @bank 2   -330 = FWrite(pointer fh @D1, pointer block @D2, long blocklen @D3, long number @D4) -> long @D0
    extsub @bank 2   -336 = FGets(pointer fh @D1, str buf @D2, long buflen @D3) -> str @D0
    extsub @bank 2   -342 = FPuts(pointer fh @D1, str k_str @D2) -> long @D0
    extsub @bank 2   -348 = VFWritef(pointer fh @D1, str format @D2, pointer argarray @D3)
    extsub @bank 2   -354 = VFPrintf(pointer fh @D1, str format @D2, pointer argarray @D3) -> long @D0
    extsub @bank 2   -360 = Flush(pointer fh @D1) -> long @D0
    extsub @bank 2   -366 = SetVBuf(pointer fh @D1, str buff @D2, long k_type @D3, long size @D4) -> long @D0
    extsub @bank 2   -372 = DupLockFromFH(pointer fh @D1) -> pointer @D0
    extsub @bank 2   -378 = OpenFromLock(pointer lock @D1) -> pointer @D0
    extsub @bank 2   -384 = ParentOfFH(pointer fh @D1) -> pointer @D0
    extsub @bank 2   -390 = ExamineFH(pointer fh @D1, pointer fib @D2) -> bool @D0
    extsub @bank 2   -396 = SetFileDate(str name @D1, pointer date @D2) -> long @D0
    extsub @bank 2   -402 = NameFromLock(pointer lock @D1, str buffer @D2, long len @D3) -> long @D0
    extsub @bank 2   -408 = NameFromFH(pointer fh @D1, str buffer @D2, long len @D3) -> long @D0
    extsub @bank 2   -414 = SplitName(str name @D1, ubyte separator @D2, str buf @D3, word oldpos @D4, long size @D5) -> word @D0
    extsub @bank 2   -420 = SameLock(pointer lock1 @D1, pointer lock2 @D2) -> long @D0
    extsub @bank 2   -426 = SetMode(pointer fh @D1, long mode @D2) -> long @D0
    extsub @bank 2   -432 = ExAll(pointer lock @D1, pointer buffer @D2, long size @D3, long data @D4, pointer control @D5) -> long @D0
    extsub @bank 2   -438 = ReadLink(pointer port @D1, pointer lock @D2, str path @D3, str buffer @D4, long size @D5) -> long @D0
    extsub @bank 2   -444 = MakeLink(str name @D1, long dest @D2, long soft @D3) -> long @D0
    extsub @bank 2   -450 = ChangeMode(long k_type @D1, pointer fh @D2, long newmode @D3) -> long @D0
    extsub @bank 2   -456 = SetFileSize(pointer fh @D1, long pos @D2, long mode @D3) -> long @D0
    extsub @bank 2   -462 = SetIoErr(long result @D1) -> long @D0
    extsub @bank 2   -468 = Fault(long code @D1, str header @D2, str buffer @D3, long len @D4) -> bool @D0
    extsub @bank 2   -474 = PrintFault(long code @D1, str header @D2) -> bool @D0
    extsub @bank 2   -480 = ErrorReport(long code @D1, long k_type @D2, long arg1 @D3, pointer device @D4) -> long @D0
    extsub @bank 2   -492 = Cli() -> pointer @D0
    extsub @bank 2   -498 = CreateNewProc(pointer tags @D1) -> pointer @D0
    extsub @bank 2   -504 = RunCommand(pointer seg @D1, long stack @D2, str paramptr @D3, long paramlen @D4) -> long @D0
    extsub @bank 2   -510 = GetConsoleTask() -> pointer @D0
    extsub @bank 2   -516 = SetConsoleTask(pointer task @D1) -> pointer @D0
    extsub @bank 2   -522 = GetFileSysTask() -> pointer @D0
    extsub @bank 2   -528 = SetFileSysTask(pointer task @D1) -> pointer @D0
    extsub @bank 2   -534 = GetArgStr() -> str @D0
    extsub @bank 2   -540 = SetArgStr(str k_string @D1) -> str @D0
    extsub @bank 2   -546 = FindCliProc(long num @D1) -> pointer @D0
    extsub @bank 2   -552 = MaxCli() -> long @D0
    extsub @bank 2   -558 = SetCurrentDirName(str name @D1) -> bool @D0
    extsub @bank 2   -564 = GetCurrentDirName(str buf @D1, long len @D2) -> bool @D0
    extsub @bank 2   -570 = SetProgramName(str name @D1) -> bool @D0
    extsub @bank 2   -576 = GetProgramName(str buf @D1, long len @D2) -> bool @D0
    extsub @bank 2   -582 = SetPrompt(str name @D1) -> bool @D0
    extsub @bank 2   -588 = GetPrompt(str buf @D1, long len @D2) -> bool @D0
    extsub @bank 2   -594 = SetProgramDir(pointer lock @D1) -> pointer @D0
    extsub @bank 2   -600 = GetProgramDir() -> pointer @D0
    extsub @bank 2   -606 = SystemTagList(str command @D1, pointer tags @D2) -> long @D0
    extsub @bank 2   -612 = AssignLock(str name @D1, pointer lock @D2) -> long @D0
    extsub @bank 2   -618 = AssignLate(str name @D1, str path @D2) -> bool @D0
    extsub @bank 2   -624 = AssignPath(str name @D1, str path @D2) -> bool @D0
    extsub @bank 2   -630 = AssignAdd(str name @D1, pointer lock @D2) -> bool @D0
    extsub @bank 2   -636 = RemAssignList(str name @D1, pointer lock @D2) -> long @D0
    extsub @bank 2   -642 = GetDeviceProc(str name @D1, pointer dp @D2) -> pointer @D0
    extsub @bank 2   -648 = FreeDeviceProc(pointer dp @D1)
    extsub @bank 2   -654 = LockDosList(long flags @D1) -> pointer @D0
    extsub @bank 2   -660 = UnLockDosList(long flags @D1)
    extsub @bank 2   -666 = AttemptLockDosList(long flags @D1) -> pointer @D0
    extsub @bank 2   -672 = RemDosEntry(pointer dlist @D1) -> bool @D0
    extsub @bank 2   -678 = AddDosEntry(pointer dlist @D1) -> long @D0
    extsub @bank 2   -684 = FindDosEntry(pointer dlist @D1, str name @D2, long flags @D3) -> pointer @D0
    extsub @bank 2   -690 = NextDosEntry(pointer dlist @D1, long flags @D2) -> pointer @D0
    extsub @bank 2   -696 = MakeDosEntry(str name @D1, long k_type @D2) -> pointer @D0
    extsub @bank 2   -702 = FreeDosEntry(pointer dlist @D1)
    extsub @bank 2   -708 = IsFileSystem(str name @D1) -> bool @D0
    extsub @bank 2   -714 = Format(str filesystem @D1, str volumename @D2, long dostype @D3) -> bool @D0
    extsub @bank 2   -720 = Relabel(str drive @D1, str newname @D2) -> long @D0
    extsub @bank 2   -726 = Inhibit(str name @D1, long onoff @D2) -> long @D0
    extsub @bank 2   -732 = AddBuffers(str name @D1, long number @D2) -> long @D0
    extsub @bank 2   -738 = CompareDates(pointer date1 @D1, pointer date2 @D2) -> long @D0
    extsub @bank 2   -744 = DateToStr(pointer datetime @D1) -> long @D0
    extsub @bank 2   -750 = StrToDate(pointer datetime @D1) -> long @D0
    extsub @bank 2   -756 = InternalLoadSeg(pointer fh @D0, pointer table @A0, pointer funcarray @A1, long stack @A2) -> pointer @D0
    extsub @bank 2   -762 = InternalUnLoadSeg(pointer seglist @D1, pointer freefunc @A1) -> bool @D0
    extsub @bank 2   -768 = NewLoadSeg(str file @D1, pointer tags @D2) -> pointer @D0
    extsub @bank 2   -774 = AddSegment(str name @D1, pointer seg @D2, long system @D3) -> long @D0
    extsub @bank 2   -780 = FindSegment(str name @D1, pointer seg @D2, long system @D3) -> pointer @D0
    extsub @bank 2   -786 = RemSegment(pointer seg @D1) -> long @D0
    extsub @bank 2   -792 = CheckSignal(long mask @D1) -> long @D0
    extsub @bank 2   -798 = ReadArgs(str arg_template @D1, long array @D2, pointer args @D3) -> pointer @D0
    extsub @bank 2   -804 = FindArg(str keyword @D1, str arg_template @D2) -> long @D0
    extsub @bank 2   -810 = ReadItem(str name @D1, long maxchars @D2, pointer cSource @D3) -> long @D0
    extsub @bank 2   -816 = StrToLong(str k_string @D1, long value @D2) -> long @D0
    extsub @bank 2   -822 = MatchFirst(str pat @D1, pointer anchor @D2) -> long @D0
    extsub @bank 2   -828 = MatchNext(pointer anchor @D1) -> long @D0
    extsub @bank 2   -834 = MatchEnd(pointer anchor @D1)
    extsub @bank 2   -840 = ParsePattern(str pat @D1, ubyte patbuf @D2, long patbuflen @D3) -> long @D0
    extsub @bank 2   -846 = MatchPattern(pointer patbuf @D1, str k_str @D2) -> bool @D0
    extsub @bank 2   -858 = FreeArgs(pointer args @D1)
    extsub @bank 2   -870 = FilePart(str path @D1) -> str @D0
    extsub @bank 2   -876 = PathPart(str path @D1) -> str @D0
    extsub @bank 2   -882 = AddPart(str dirname @D1, str filename @D2, long size @D3) -> bool @D0
    extsub @bank 2   -888 = StartNotify(pointer notify @D1) -> bool @D0
    extsub @bank 2   -894 = EndNotify(pointer notify @D1)
    extsub @bank 2   -900 = SetVar(str name @D1, str buffer @D2, long size @D3, long flags @D4) -> bool @D0
    extsub @bank 2   -906 = GetVar(str name @D1, str buffer @D2, long size @D3, long flags @D4) -> long @D0
    extsub @bank 2   -912 = DeleteVar(str name @D1, long flags @D2) -> long @D0
    extsub @bank 2   -918 = FindVar(str name @D1, long k_type @D2) -> pointer @D0
    extsub @bank 2   -930 = CliInitNewcli(pointer dp @A0) -> long @D0
    extsub @bank 2   -936 = CliInitRun(pointer dp @A0) -> long @D0
    extsub @bank 2   -942 = WriteChars(str buf @D1, long buflen @D2) -> long @D0
    extsub @bank 2   -948 = PutStr(str k_str @D1) -> long @D0
    extsub @bank 2   -954 = VPrintf(str format @D1, pointer argarray @D2) -> long @D0
    extsub @bank 2   -966 = ParsePatternNoCase(str pat @D1, ubyte patbuf @D2, long patbuflen @D3) -> long @D0
    extsub @bank 2   -972 = MatchPatternNoCase(pointer patbuf @D1, str k_str @D2) -> bool @D0
    extsub @bank 2   -984 = SameDevice(pointer lock1 @D1, pointer lock2 @D2) -> bool @D0
    extsub @bank 2   -990 = ExAllEnd(pointer lock @D1, pointer buffer @D2, long size @D3, long data @D4, pointer control @D5)
    extsub @bank 2   -996 = SetOwner(str name @D1, long owner_info @D2) -> bool @D0
    extsub @bank 2   -1014 = VolumeRequestHook(str vol @D1) -> long @D0
    extsub @bank 2   -1026 = GetCurrentDir() -> pointer @D0
    extsub @bank 2   -1128 = PutErrStr(str k_str @D1) -> long @D0
    extsub @bank 2   -1134 = ErrorOutput() -> long @D0
    extsub @bank 2   -1140 = SelectError(pointer fh @D1) -> long @D0
    extsub @bank 2   -1152 = DoShellMethodTagList(long method @D0, pointer tags @A0) -> pointer @D0
    extsub @bank 2   -1158 = ScanStackToken(pointer seg @D1, long defaultstack @D2) -> long @D0

    ; ---- struct definitions ----

    struct DateStamp {  ; total size: 12
        long Ds_Days  ; 0
        long Ds_Minute  ; 4
        long Ds_Tick  ; 8
    }

    struct DosPacket {  ; total size: 48
        pointer Link  ; 0
        pointer Port  ; 4
        long Type  ; 8
        long Res1  ; 12
        long Res2  ; 16
        long Arg1  ; 20
        long Arg2  ; 24
        long Arg3  ; 28
        long Arg4  ; 32
        long Arg5  ; 36
        long Arg6  ; 40
        long Arg7  ; 44
    }

    struct ErrorString {  ; total size: 8
        pointer Estr_Nums  ; 0
        pointer Estr_Strings  ; 4
    }

    struct ExAllControl {  ; total size: 16
        long Entries  ; 0
        long LastKey  ; 4
        str MatchString  ; 8
        pointer MatchFunc  ; 12
    }

    struct ExAllData {  ; total size: 40
        pointer Ed_Next  ; 0
        str Ed_Name  ; 4
        long Ed_Type  ; 8
        long Ed_Size  ; 12
        long Ed_Prot  ; 16
        long Ed_Days  ; 20
        long Ed_Mins  ; 24
        long Ed_Ticks  ; 28
        str Ed_Comment  ; 32
        uword Ed_OwnerUID  ; 36
        uword Ed_OwnerGID  ; 38
    }

    struct FileHandle {  ; total size: 44
        pointer Link  ; 0
        pointer Interactive  ; 4
        pointer Type  ; 8
        long Buf  ; 12
        long Pos  ; 16
        long End  ; 20
        long Funcs  ; 24
        long Func2  ; 28
        long Func3  ; 32
        long Args  ; 36
        long Arg2  ; 40
    }

    struct FileInfoBlock {  ; total size: 224
        long DiskKey  ; 0
        long DirEntryType  ; 4
        pointer emb_fib_FileName  ; TODO embedded 108  ; 8
        long Protection  ; 116
        long EntryType  ; 120
        long Size  ; 124
        long NumBlocks  ; 128
        pointer emb_fib_DateStamp  ; TODO embedded ds_SIZEOF  ; 132
        pointer emb_fib_Comment  ; TODO embedded 80  ; 136
        uword OwnerUID  ; 216
        uword OwnerGID  ; 218
        pointer emb_fib_Reserved  ; TODO embedded 32  ; 220
    }

    struct FileLock {  ; total size: 20
        pointer Link  ; 0
        long Key  ; 4
        long Access  ; 8
        pointer Task  ; 12
        pointer Volume  ; 16
    }

    struct IOStdReq {  ; total size: 48
        pointer Succ  ; 0
        pointer Pred  ; 4
        ubyte Type  ; 8
        byte Pri  ; 9
        str Name  ; 10
        pointer Replyport  ; 14
        uword IOStdReq_Length  ; 18
        pointer Device  ; 20
        pointer Unit  ; 24
        uword Command  ; 28
        ubyte Flags  ; 30
        byte Error  ; 31
        long Actual  ; 32
        long Length  ; 36
        pointer Data  ; 40
        long Offset  ; 44
    }

    struct IORequest {  ; total size: 32
        pointer Succ  ; 0
        pointer Pred  ; 4
        ubyte Type  ; 8
        byte Pri  ; 9
        str Name  ; 10
        pointer Replyport  ; 14
        uword IOStdReq_Length  ; 18
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

    struct InfoData {  ; total size: 36
        long Id_NumSoftErrors  ; 0
        long Id_UnitNumber  ; 4
        long Id_DiskState  ; 8
        long Id_NumBlocks  ; 12
        long Id_NumBlocksUsed  ; 16
        long Id_BytesPerBlock  ; 20
        long Id_DiskType  ; 24
        pointer Id_VolumeNode  ; 28
        long Id_InUse  ; 32
    }

    struct List {  ; total size: 14
        pointer Head  ; 0
        pointer Tail  ; 4
        pointer Tailpred  ; 8
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
        uword Negsize  ; 16
        uword Possize  ; 18
        uword Version  ; 20
        uword Revision  ; 22
        str Idstring  ; 24
        long Sum  ; 28
        uword Opencnt  ; 32
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
        pointer Tailpred  ; 8
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
        pointer Replyport  ; 14
        uword Length  ; 18
    }

    struct MsgPort {  ; total size: 34
        pointer Succ  ; 0
        pointer Pred  ; 4
        ubyte Type  ; 8
        byte Pri  ; 9
        str Name  ; 10
        ubyte Flags  ; 14
        ubyte Sigbit  ; 15
        pointer Sigtask  ; 16
        pointer Head  ; 20
        pointer Tail  ; 24
        pointer Tailpred  ; 28
        ubyte List_Type  ; 32
        ubyte Pad  ; 33
    }

    struct Process {  ; total size: 130
        pointer emb_pr_Task  ; TODO embedded TC_SIZE  ; 0
        pointer Succ  ; 4
        pointer Pred  ; 8
        ubyte Type  ; 12
        byte Pri  ; 13
        str Name  ; 14
        ubyte Process_Flags  ; 18
        ubyte Sigbit  ; 19
        pointer Sigtask  ; 20
        pointer Head  ; 24
        pointer Tail  ; 28
        pointer Tailpred  ; 32
        ubyte List_Type  ; 36
        ubyte Process_Pad  ; 37
        word Pad  ; 28
        pointer SegList  ; 30
        long StackSize  ; 34
        pointer GlobVec  ; 38
        long TaskNum  ; 42
        pointer StackBase  ; 46
        long Result2  ; 50
        pointer CurrentDir  ; 54
        pointer Cis  ; 58
        pointer Cos  ; 62
        pointer ConsoleTask  ; 66
        pointer FileSystemTask  ; 70
        pointer Cli  ; 74
        pointer ReturnAddr  ; 78
        pointer PktWait  ; 82
        pointer WindowPtr  ; 86
        pointer HomeDir  ; 90
        long Flags  ; 94
        pointer ExitCode  ; 98
        long ExitData  ; 102
        pointer Arguments  ; 106
        pointer MinList_Head  ; 110
        pointer MinList_Tail  ; 114
        pointer MinList_Tailpred  ; 118
        pointer ShellPrivate  ; 122
        pointer Ces  ; 126
    }

    struct StandardPacket {  ; total size: 24
        pointer Succ  ; 0
        pointer Pred  ; 4
        ubyte Type  ; 8
        byte Pri  ; 9
        str Name  ; 10
        pointer Replyport  ; 14
        uword Length  ; 18
        pointer emb_sp_Pkt  ; TODO embedded dp_SIZEOF  ; 20
    }

    struct Task {  ; total size: 84
        pointer Succ  ; 0
        pointer Pred  ; 4
        ubyte Type  ; 8
        byte Pri  ; 9
        str Name  ; 10
        ubyte Flags  ; 14
        ubyte State  ; 15
        byte Idnestcnt  ; 16
        byte Tdnestcnt  ; 17
        long Sigalloc  ; 18
        long Sigwait  ; 22
        long Sigrecvd  ; 26
        long Sigexcept  ; 30
        uword Trapalloc  ; 34
        uword Trapable  ; 36
        pointer Exceptdata  ; 38
        pointer Exceptcode  ; 42
        pointer Trapdata  ; 46
        pointer Trapcode  ; 50
        pointer Spreg  ; 54
        pointer Splower  ; 58
        pointer Spupper  ; 62
        pointer Head  ; 66
        pointer Tail  ; 70
        pointer Tailpred  ; 74
        ubyte List_Type  ; 78
        ubyte Pad  ; 79
        pointer Userdata  ; 80
    }

    ; ---- constants ----
    const ubyte LEN_DATSTRING = $0010
    const ubyte DTB_SUBST = 0
    const ubyte DTF_SUBST = $0001
    const ubyte DTB_FUTURE = 1
    const ubyte DTF_FUTURE = $0002
    const long DOSTRUE = -1
    const ubyte DOSFALSE = $0000
    const uword MODE_OLDFILE = $03ed
    const uword MODE_NEWFILE = $03ee
    const uword MODE_READWRITE = $03ec
    const long OFFSET_BEGINNING = -1
    const ubyte OFFSET_CURRENT = $0000
    const ubyte OFFSET_END = $0001
    const ubyte BITSPERBYTE = $0008
    const ubyte BYTESPERLONG = $0004
    const ubyte BITSPERLONG = $0020
    const long MAXINT = $7fffffff
    const long MININT = $80000000
    const long SHARED_LOCK = -2
    const long ACCESS_READ = -2
    const long EXCLUSIVE_LOCK = -1
    const long ACCESS_WRITE = -1
    const ubyte TICKS_PER_SECOND = $0032
    const ubyte FIBB_OTR_READ = 15
    const uword FIBF_OTR_READ = $8000
    const ubyte FIBB_OTR_WRITE = 14
    const uword FIBF_OTR_WRITE = $4000
    const ubyte FIBB_OTR_EXECUTE = 13
    const uword FIBF_OTR_EXECUTE = $2000
    const ubyte FIBB_OTR_DELETE = 12
    const uword FIBF_OTR_DELETE = $1000
    const ubyte FIBB_GRP_READ = 11
    const uword FIBF_GRP_READ = $0800
    const ubyte FIBB_GRP_WRITE = 10
    const uword FIBF_GRP_WRITE = $0400
    const ubyte FIBB_GRP_EXECUTE = 9
    const uword FIBF_GRP_EXECUTE = $0200
    const ubyte FIBB_GRP_DELETE = 8
    const uword FIBF_GRP_DELETE = $0100
    const ubyte FIBB_HOLD = 7
    const ubyte FIBF_HOLD = $0080
    const ubyte FIBB_SCRIPT = 6
    const ubyte FIBF_SCRIPT = $0040
    const ubyte FIBB_PURE = 5
    const ubyte FIBF_PURE = $0020
    const ubyte FIBB_ARCHIVE = 4
    const ubyte FIBF_ARCHIVE = $0010
    const ubyte FIBB_READ = 3
    const ubyte FIBF_READ = $0008
    const ubyte FIBB_WRITE = 2
    const ubyte FIBF_WRITE = $0004
    const ubyte FIBB_EXECUTE = 1
    const ubyte FIBF_EXECUTE = $0002
    const ubyte FIBB_DELETE = 0
    const ubyte FIBF_DELETE = $0001
    const ubyte FAULT_MAX = $0052
    const ubyte ID_WRITE_PROTECTED = $0050
    const ubyte ID_VALIDATING = $0051
    const ubyte ID_VALIDATED = $0052
    const long ID_NO_DISK_PRESENT = -1
    const ubyte ERROR_NO_FREE_STORE = $0067
    const ubyte ERROR_TASK_TABLE_FULL = $0069
    const ubyte ERROR_BAD_TEMPLATE = $0072
    const ubyte ERROR_BAD_NUMBER = $0073
    const ubyte ERROR_REQUIRED_ARG_MISSING = $0074
    const ubyte ERROR_KEY_NEEDS_ARG = $0075
    const ubyte ERROR_TOO_MANY_ARGS = $0076
    const ubyte ERROR_UNMATCHED_QUOTES = $0077
    const ubyte ERROR_LINE_TOO_LONG = $0078
    const ubyte ERROR_FILE_NOT_OBJECT = $0079
    const ubyte ERROR_INVALID_RESIDENT_LIBRARY = $007a
    const ubyte ERROR_NO_DEFAULT_DIR = $00c9
    const ubyte ERROR_OBJECT_IN_USE = $00ca
    const ubyte ERROR_OBJECT_EXISTS = $00cb
    const ubyte ERROR_DIR_NOT_FOUND = $00cc
    const ubyte ERROR_OBJECT_NOT_FOUND = $00cd
    const ubyte ERROR_BAD_STREAM_NAME = $00ce
    const ubyte ERROR_OBJECT_TOO_LARGE = $00cf
    const ubyte ERROR_ACTION_NOT_KNOWN = $00d1
    const ubyte ERROR_INVALID_COMPONENT_NAME = $00d2
    const ubyte ERROR_INVALID_LOCK = $00d3
    const ubyte ERROR_OBJECT_WRONG_TYPE = $00d4
    const ubyte ERROR_DISK_NOT_VALIDATED = $00d5
    const ubyte ERROR_DISK_WRITE_PROTECTED = $00d6
    const ubyte ERROR_RENAME_ACROSS_DEVICES = $00d7
    const ubyte ERROR_DIRECTORY_NOT_EMPTY = $00d8
    const ubyte ERROR_TOO_MANY_LEVELS = $00d9
    const ubyte ERROR_DEVICE_NOT_MOUNTED = $00da
    const ubyte ERROR_SEEK_ERROR = $00db
    const ubyte ERROR_COMMENT_TOO_BIG = $00dc
    const ubyte ERROR_DISK_FULL = $00dd
    const ubyte ERROR_DELETE_PROTECTED = $00de
    const ubyte ERROR_WRITE_PROTECTED = $00df
    const ubyte ERROR_READ_PROTECTED = $00e0
    const ubyte ERROR_NOT_A_DOS_DISK = $00e1
    const ubyte ERROR_NO_DISK = $00e2
    const ubyte ERROR_NO_MORE_ENTRIES = $00e8
    const ubyte ERROR_IS_SOFT_LINK = $00e9
    const ubyte ERROR_OBJECT_LINKED = $00ea
    const ubyte ERROR_BAD_HUNK = $00eb
    const ubyte ERROR_NOT_IMPLEMENTED = $00ec
    const ubyte ERROR_RECORD_NOT_LOCKED = $00f0
    const ubyte ERROR_LOCK_COLLISION = $00f1
    const ubyte ERROR_LOCK_TIMEOUT = $00f2
    const ubyte ERROR_UNLOCK_ERROR = $00f3
    const ubyte RETURN_OK = $0000
    const ubyte RETURN_WARN = $0005
    const ubyte RETURN_ERROR = $000a
    const ubyte RETURN_FAIL = $0014
    const ubyte SIGBREAKB_CTRL_C = 12
    const uword SIGBREAKF_CTRL_C = $1000
    const ubyte SIGBREAKB_CTRL_D = 13
    const uword SIGBREAKF_CTRL_D = $2000
    const ubyte SIGBREAKB_CTRL_E = 14
    const uword SIGBREAKF_CTRL_E = $4000
    const ubyte SIGBREAKB_CTRL_F = 15
    const uword SIGBREAKF_CTRL_F = $8000
    const long LOCK_DIFFERENT = -1
    const ubyte LOCK_SAME = $0000
    const ubyte LOCK_SAME_VOLUME = $0001
    const ubyte CHANGE_LOCK = $0000
    const ubyte CHANGE_FH = $0001
    const ubyte LINK_HARD = $0000
    const ubyte LINK_SOFT = $0001
    const long ITEM_EQUAL = -2
    const long ITEM_ERROR = -1
    const ubyte ITEM_NOTHING = $0000
    const ubyte ITEM_UNQUOTED = $0001
    const ubyte ITEM_QUOTED = $0002
    const ubyte DOS_FILEHANDLE = $0000
    const ubyte DOS_EXALLCONTROL = $0001
    const ubyte DOS_FIB = $0002
    const ubyte DOS_STDPKT = $0003
    const ubyte DOS_CLI = $0004
    const ubyte DOS_RDARGS = $0005
    const ubyte reserve = $0004
    const ubyte vsize = $0006
    const ubyte APB_DOWILD = 0
    const ubyte APF_DOWILD = $0001
    const ubyte APB_ITSWILD = 1
    const ubyte APF_ITSWILD = $0002
    const ubyte APB_DODIR = 2
    const ubyte APF_DODIR = $0004
    const ubyte APB_DIDDIR = 3
    const ubyte APF_DIDDIR = $0008
    const ubyte APB_NOMEMERR = 4
    const ubyte APF_NOMEMERR = $0010
    const ubyte APB_DODOT = 5
    const ubyte APF_DODOT = $0020
    const ubyte APB_DirChanged = 6
    const ubyte APF_DirChanged = $0040
    const ubyte APB_FollowHLinks = 7
    const ubyte APF_FollowHLinks = $0080
    const ubyte DDB_PatternBit = 0
    const ubyte DDF_PatternBit = $0001
    const ubyte DDB_ExaminedBit = 1
    const ubyte DDF_ExaminedBit = $0002
    const ubyte DDB_Completed = 2
    const ubyte DDF_Completed = $0004
    const ubyte DDB_AllBit = 3
    const ubyte DDF_AllBit = $0008
    const ubyte DDB_SINGLE = 4
    const ubyte DDF_SINGLE = $0010
    const ubyte P_ANY = $0080
    const ubyte P_SINGLE = $0081
    const ubyte P_ORSTART = $0082
    const ubyte P_ORNEXT = $0083
    const ubyte P_OREND = $0084
    const ubyte P_NOT = $0085
    const ubyte P_NOTEND = $0086
    const ubyte P_NOTCLASS = $0087
    const ubyte P_CLASS = $0088
    const ubyte P_REPBEG = $0089
    const ubyte P_REPEND = $008a
    const ubyte P_STOP = $008b
    const ubyte COMPLEX_BIT = $0001
    const ubyte EXAMINE_BIT = $0002
    const uword ERROR_BUFFER_OVERFLOW = $012f
    const uword ERROR_BREAK = $0130
    const uword ERROR_NOT_EXECUTABLE = $0131
    const ubyte PRB_FREESEGLIST = 0
    const ubyte PRF_FREESEGLIST = $0001
    const ubyte PRB_FREECURRDIR = 1
    const ubyte PRF_FREECURRDIR = $0002
    const ubyte PRB_FREECLI = 2
    const ubyte PRF_FREECLI = $0004
    const ubyte PRB_CLOSEINPUT = 3
    const ubyte PRF_CLOSEINPUT = $0008
    const ubyte PRB_CLOSEOUTPUT = 4
    const ubyte PRF_CLOSEOUTPUT = $0010
    const ubyte PRB_FREEARGS = 5
    const ubyte PRF_FREEARGS = $0020
    const ubyte PRB_CLOSEERROR = 6
    const ubyte PRF_CLOSEERROR = $0040
    const ubyte ACTION_NIL = $0000
    const ubyte ACTION_STARTUP = $0000
    const ubyte ACTION_GET_BLOCK = $0002
    const ubyte ACTION_SET_MAP = $0004
    const ubyte ACTION_DIE = $0005
    const ubyte ACTION_EVENT = $0006
    const ubyte ACTION_CURRENT_VOLUME = $0007
    const ubyte ACTION_LOCATE_OBJECT = $0008
    const ubyte ACTION_RENAME_DISK = $0009
    const ubyte ACTION_FREE_LOCK = $000f
    const ubyte ACTION_DELETE_OBJECT = $0010
    const ubyte ACTION_RENAME_OBJECT = $0011
    const ubyte ACTION_MORE_CACHE = $0012
    const ubyte ACTION_COPY_DIR = $0013
    const ubyte ACTION_WAIT_CHAR = $0014
    const ubyte ACTION_SET_PROTECT = $0015
    const ubyte ACTION_CREATE_DIR = $0016
    const ubyte ACTION_EXAMINE_OBJECT = $0017
    const ubyte ACTION_EXAMINE_NEXT = $0018
    const ubyte ACTION_DISK_INFO = $0019
    const ubyte ACTION_INFO = $001a
    const ubyte ACTION_FLUSH = $001b
    const ubyte ACTION_SET_COMMENT = $001c
    const ubyte ACTION_PARENT = $001d
    const ubyte ACTION_TIMER = $001e
    const ubyte ACTION_INHIBIT = $001f
    const ubyte ACTION_DISK_TYPE = $0020
    const ubyte ACTION_DISK_CHANGE = $0021
    const ubyte ACTION_SET_DATE = $0022
    const uword ACTION_UNDISK_INFO = $0201
    const uword ACTION_SCREEN_MODE = $03e2
    const uword ACTION_READ_RETURN = $03e9
    const uword ACTION_WRITE_RETURN = $03ea
    const uword ACTION_SEEK = $03f0
    const uword ACTION_FINDUPDATE = $03ec
    const uword ACTION_FINDINPUT = $03ed
    const uword ACTION_FINDOUTPUT = $03ee
    const uword ACTION_END = $03ef
    const uword ACTION_SET_FILE_SIZE = $03fe
    const uword ACTION_WRITE_PROTECT = $03ff
    const ubyte ACTION_SAME_LOCK = $0028
    const uword ACTION_CHANGE_SIGNAL = $03e3
    const uword ACTION_FORMAT = $03fc
    const uword ACTION_MAKE_LINK = $03fd
    const uword ACTION_READ_LINK = $0400
    const uword ACTION_FH_FROM_LOCK = $0402
    const uword ACTION_IS_FILESYSTEM = $0403
    const uword ACTION_CHANGE_MODE = $0404
    const uword ACTION_COPY_DIR_FH = $0406
    const uword ACTION_PARENT_FH = $0407
    const uword ACTION_EXAMINE_ALL = $0409
    const uword ACTION_EXAMINE_FH = $040a
    const uword ACTION_LOCK_RECORD = $07d8
    const uword ACTION_FREE_RECORD = $07d9
    const uword ACTION_ADD_NOTIFY = $1001
    const uword ACTION_REMOVE_NOTIFY = $1002
    const uword ACTION_EXAMINE_ALL_END = $040b
    const uword ACTION_SET_OWNER = $040c
    const uword ACTION_SERIALIZE_DISK = $1068
    const ubyte RNB_WILDSTAR = 24
    const long RNF_WILDSTAR = $01000000
    const ubyte RNB_PRIVATE1 = 1
    const ubyte RNF_PRIVATE1 = $0002
    const long CMD_SYSTEM = -1
    const long CMD_INTERNAL = -2
    const long CMD_DISABLED = -999
    const ubyte DLT_DEVICE = $0000
    const ubyte DLT_DIRECTORY = $0001
    const ubyte DLT_VOLUME = $0002
    const ubyte DLT_LATE = $0003
    const ubyte DLT_NONBINDING = $0004
    const long DLT_PRIVATE = -1
    const ubyte DVPB_UNLOCK = 0
    const ubyte DVPF_UNLOCK = $0001
    const ubyte DVPB_ASSIGN = 1
    const ubyte DVPF_ASSIGN = $0002
    const ubyte LDB_DEVICES = 2
    const ubyte LDF_DEVICES = $0004
    const ubyte LDB_VOLUMES = 3
    const ubyte LDF_VOLUMES = $0008
    const ubyte LDB_ASSIGNS = 4
    const ubyte LDF_ASSIGNS = $0010
    const ubyte LDB_ENTRY = 5
    const ubyte LDF_ENTRY = $0020
    const ubyte LDB_DELETE = 6
    const ubyte LDF_DELETE = $0040
    const ubyte LDB_READ = 0
    const ubyte LDF_READ = $0001
    const ubyte LDB_WRITE = 1
    const ubyte LDF_WRITE = $0002
    const ubyte REPORT_STREAM = $0000
    const ubyte REPORT_TASK = $0001
    const ubyte REPORT_LOCK = $0002
    const ubyte REPORT_VOLUME = $0003
    const ubyte REPORT_INSERT = $0004
    const uword ABORT_DISK_ERROR = $0128
    const uword ABORT_BUSY = $0120
    const long RUN_EXECUTE = -1
    const long RUN_SYSTEM = -2
    const long RUN_SYSTEM_ASYNCH = -3
    const ubyte ST_ROOT = $0001
    const ubyte ST_USERDIR = $0002
    const ubyte ST_SOFTLINK = $0003
    const ubyte ST_LINKDIR = $0004
    const long ST_FILE = -3
    const long ST_LINKFILE = -4
    const long ST_PIPEFILE = -5
    const uword HUNK_UNIT = $03e7
    const uword HUNK_NAME = $03e8
    const uword HUNK_CODE = $03e9
    const uword HUNK_DATA = $03ea
    const uword HUNK_BSS = $03eb
    const uword HUNK_RELOC32 = $03ec
    const uword HUNK_RELOC16 = $03ed
    const uword HUNK_RELOC8 = $03ee
    const uword HUNK_EXT = $03ef
    const uword HUNK_SYMBOL = $03f0
    const uword HUNK_DEBUG = $03f1
    const uword HUNK_END = $03f2
    const uword HUNK_HEADER = $03f3
    const uword HUNK_OVERLAY = $03f5
    const uword HUNK_BREAK = $03f6
    const uword HUNK_DREL32 = $03f7
    const uword HUNK_DREL16 = $03f8
    const uword HUNK_DREL8 = $03f9
    const uword HUNK_LIB = $03fa
    const uword HUNK_INDEX = $03fb
    const uword HUNK_RELOC32SHORT = $03fc
    const uword HUNK_RELRELOC32 = $03fd
    const uword HUNK_ABSRELOC16 = $03fe
    const ubyte HUNKB_ADVISORY = 29
    const long HUNKF_ADVISORY = $20000000
    const ubyte HUNKB_CHIP = 30
    const long HUNKF_CHIP = $40000000
    const ubyte HUNKB_FAST = 31
    const long HUNKF_FAST = $80000000
    const ubyte EXT_SYMB = $0000
    const ubyte EXT_DEF = $0001
    const ubyte EXT_ABS = $0002
    const ubyte EXT_RES = $0003
    const ubyte EXT_COMMONDEF = $0004
    const ubyte EXT_REF32 = $0081
    const ubyte EXT_COMMON = $0082
    const ubyte EXT_REF16 = $0083
    const ubyte EXT_REF8 = $0084
    const ubyte EXT_DEXT32 = $0085
    const ubyte EXT_DEXT16 = $0086
    const ubyte EXT_DEXT8 = $0087
    const ubyte EXT_RELREF32 = $0088
    const ubyte EXT_RELCOMMON = $0089
    const ubyte EXT_ABSREF16 = $008a
    const ubyte EXT_ABSREF8 = $008b
    const ubyte ED_NAME = $0001
    const ubyte ED_TYPE = $0002
    const ubyte ED_SIZE = $0003
    const ubyte ED_PROTECTION = $0004
    const ubyte ED_DATE = $0005
    const ubyte ED_COMMENT = $0006
    const ubyte ED_OWNER = $0007
    const ubyte DE_TABLESIZE = $0000
    const ubyte DE_SIZEBLOCK = $0001
    const ubyte DE_SECORG = $0002
    const ubyte DE_NUMHEADS = $0003
    const ubyte DE_SECSPERBLK = $0004
    const ubyte DE_BLKSPERTRACK = $0005
    const ubyte DE_RESERVEDBLKS = $0006
    const ubyte DE_PREFAC = $0007
    const ubyte DE_INTERLEAVE = $0008
    const ubyte DE_LOWCYL = $0009
    const ubyte DE_UPPERCYL = $000a
    const ubyte DE_NUMBUFFERS = $000b
    const ubyte DE_MEMBUFTYPE = $000c
    const ubyte DE_BUFMEMTYPE = $000c
    const ubyte DE_MAXTRANSFER = $000d
    const ubyte DE_MASK = $000e
    const ubyte DE_BOOTPRI = $000f
    const ubyte DE_DOSTYPE = $0010
    const ubyte DE_BAUD = $0011
    const ubyte DE_CONTROL = $0012
    const ubyte DE_BOOTBLOCKS = $0013
    const ubyte ENVB_SCSIDIRECT = 16
    const long ENVF_SCSIDIRECT = $00010000
    const ubyte ENVB_SUPERFLOPPY = 17
    const long ENVF_SUPERFLOPPY = $00020000
    const ubyte ENVB_DISABLENSD = 18
    const long ENVF_DISABLENSD = $00040000
    const long NOTIFY_CLASS = $40000000
    const uword NOTIFY_CODE = $1234
    const ubyte NRB_SEND_MESSAGE = 0
    const ubyte NRF_SEND_MESSAGE = $0001
    const ubyte NRB_SEND_SIGNAL = 1
    const ubyte NRF_SEND_SIGNAL = $0002
    const ubyte NRB_WAIT_REPLY = 3
    const ubyte NRF_WAIT_REPLY = $0008
    const ubyte NRB_NOTIFY_INITIAL = 4
    const ubyte NRF_NOTIFY_INITIAL = $0010
    const ubyte NRB_MAGIC = 31
    const long NRF_MAGIC = $80000000
    const long NR_HANDLER_FLAGS = $ffff0000
    const ubyte RDAB_STDIN = 0
    const ubyte RDAF_STDIN = $0001
    const ubyte RDAB_NOALLOC = 1
    const ubyte RDAF_NOALLOC = $0002
    const ubyte RDAB_NOPROMPT = 2
    const ubyte RDAF_NOPROMPT = $0004
    const ubyte REC_EXCLUSIVE = $0000
    const ubyte REC_EXCLUSIVE_IMMED = $0001
    const ubyte REC_SHARED = $0002
    const ubyte REC_SHARED_IMMED = $0003
    const ubyte DOS_STDIO_I = $0001
    const ubyte BUF_LINE = $0000
    const ubyte BUF_FULL = $0001
    const ubyte BUF_NONE = $0002
    const long ENDSTREAMCH = -1
    const ubyte LV_VAR = $0000
    const ubyte LV_ALIAS = $0001
    const ubyte LVB_IGNORE = $0007
    const ubyte LVF_IGNORE = $0080
    const ubyte GVB_GLOBAL_ONLY = 8
    const uword GVF_GLOBAL_ONLY = $0100
    const ubyte GVB_LOCAL_ONLY = 9
    const uword GVF_LOCAL_ONLY = $0200
    const ubyte GVB_BINARY_VAR = 10
    const uword GVF_BINARY_VAR = $0400
    const ubyte GVB_DONT_NULL_TERM = 11
    const uword GVF_DONT_NULL_TERM = $0800
    const ubyte GVB_SAVE_VAR = 12
    const uword GVF_SAVE_VAR = $1000
}
;; End of auto-generated dos_lib.sfd
