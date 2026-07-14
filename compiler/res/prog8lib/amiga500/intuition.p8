;; Auto-generated from intuition_lib.sfd and intuition_lib.i
;; Library base: _IntuitionBase  in prog8: sys.IntuitionBase
;; Bank: 4
;; Functions: 127

intuition {
    extsub @bank 4   -30 = OpenIntuition()
    extsub @bank 4   -36 = Intuition(pointer iEvent @A0)
    extsub @bank 4   -42 = AddGadget(pointer k_window @A0, pointer gadget @A1, uword position @D0) -> uword @D0
    extsub @bank 4   -48 = ClearDMRequest(pointer k_window @A0) -> bool @D0
    extsub @bank 4   -54 = ClearMenuStrip(pointer k_window @A0)
    extsub @bank 4   -60 = ClearPointer(pointer k_window @A0)
    extsub @bank 4   -66 = CloseScreen(pointer screen @A0) -> bool @D0
    extsub @bank 4   -72 = CloseWindow(pointer k_window @A0)
    extsub @bank 4   -78 = CloseWorkBench() -> long @D0
    extsub @bank 4   -84 = CurrentTime(long seconds @A0, long micros @A1)
    extsub @bank 4   -90 = DisplayAlert(long alertNumber @D0, str k_string @A0, uword height @D1) -> bool @D0
    extsub @bank 4   -96 = DisplayBeep(pointer screen @A0)
    extsub @bank 4   -102 = DoubleClick(long sSeconds @D0, long sMicros @D1, long cSeconds @D2, long cMicros @D3) -> bool @D0
    extsub @bank 4   -108 = DrawBorder(pointer rp @A0, pointer border @A1, word leftOffset @D0, word topOffset @D1)
    extsub @bank 4   -114 = DrawImage(pointer rp @A0, pointer image @A1, word leftOffset @D0, word topOffset @D1)
    extsub @bank 4   -120 = EndRequest(pointer k_requester @A0, pointer k_window @A1)
    extsub @bank 4   -126 = GetDefPrefs(pointer preferences @A0, word size @D0) -> pointer @D0
    extsub @bank 4   -132 = GetPrefs(pointer preferences @A0, word size @D0) -> pointer @D0
    extsub @bank 4   -138 = InitRequester(pointer k_requester @A0)
    extsub @bank 4   -144 = ItemAddress(pointer menuStrip @A0, uword menuNumber @D0) -> pointer @D0
    extsub @bank 4   -150 = ModifyIDCMP(pointer k_window @A0, long flags @D0) -> bool @D0
    extsub @bank 4   -156 = ModifyProp(pointer gadget @A0, pointer k_window @A1, pointer k_requester @A2, uword flags @D0, uword horizPot @D1, uword vertPot @D2, uword horizBody @D3, uword vertBody @D4)
    extsub @bank 4   -162 = MoveScreen(pointer screen @A0, word dx @D0, word dy @D1)
    extsub @bank 4   -168 = MoveWindow(pointer k_window @A0, word dx @D0, word dy @D1)
    extsub @bank 4   -174 = OffGadget(pointer gadget @A0, pointer k_window @A1, pointer k_requester @A2)
    extsub @bank 4   -180 = OffMenu(pointer k_window @A0, uword menuNumber @D0)
    extsub @bank 4   -186 = OnGadget(pointer gadget @A0, pointer k_window @A1, pointer k_requester @A2)
    extsub @bank 4   -192 = OnMenu(pointer k_window @A0, uword menuNumber @D0)
    extsub @bank 4   -198 = OpenScreen(pointer newScreen @A0) -> pointer @D0
    extsub @bank 4   -204 = OpenWindow(pointer newWindow @A0) -> pointer @D0
    extsub @bank 4   -210 = OpenWorkBench() -> long @D0
    extsub @bank 4   -216 = PrintIText(pointer rp @A0, pointer iText @A1, word left @D0, word top @D1)
    extsub @bank 4   -222 = RefreshGadgets(pointer gadgets @A0, pointer k_window @A1, pointer k_requester @A2)
    extsub @bank 4   -228 = RemoveGadget(pointer k_window @A0, pointer gadget @A1) -> uword @D0
    extsub @bank 4   -234 = ReportMouse(bool flag @D0, pointer k_window @A0)
    extsub @bank 4   -240 = Request(pointer k_requester @A0, pointer k_window @A1) -> bool @D0
    extsub @bank 4   -246 = ScreenToBack(pointer screen @A0)
    extsub @bank 4   -252 = ScreenToFront(pointer screen @A0)
    extsub @bank 4   -258 = SetDMRequest(pointer k_window @A0, pointer k_requester @A1) -> bool @D0
    extsub @bank 4   -264 = SetMenuStrip(pointer k_window @A0, pointer menu @A1) -> bool @D0
    extsub @bank 4   -270 = SetPointer(pointer k_window @A0, uword k_pointer @A1, word height @D0, word width @D1, word xOffset @D2, word yOffset @D3)
    extsub @bank 4   -276 = SetWindowTitles(pointer k_window @A0, str windowTitle @A1, str screenTitle @A2)
    extsub @bank 4   -282 = ShowTitle(pointer screen @A0, bool showIt @D0)
    extsub @bank 4   -288 = SizeWindow(pointer k_window @A0, word dx @D0, word dy @D1)
    extsub @bank 4   -294 = ViewAddress() -> pointer @D0
    extsub @bank 4   -300 = ViewPortAddress(pointer k_window @A0) -> pointer @D0
    extsub @bank 4   -306 = WindowToBack(pointer k_window @A0)
    extsub @bank 4   -312 = WindowToFront(pointer k_window @A0)
    extsub @bank 4   -318 = WindowLimits(pointer k_window @A0, long widthMin @D0, long heightMin @D1, long widthMax @D2, long heightMax @D3) -> bool @D0
    extsub @bank 4   -324 = SetPrefs(pointer preferences @A0, long size @D0, bool inform @D1) -> pointer @D0
    extsub @bank 4   -330 = IntuiTextLength(pointer iText @A0) -> long @D0
    extsub @bank 4   -336 = WBenchToBack() -> bool @D0
    extsub @bank 4   -342 = WBenchToFront() -> bool @D0
    extsub @bank 4   -348 = AutoRequest(pointer k_window @A0, pointer body @A1, pointer posText @A2, pointer negText @A3, long pFlag @D0, long nFlag @D1, uword width @D2, uword height @D3) -> bool @D0
    extsub @bank 4   -354 = BeginRefresh(pointer k_window @A0)
    extsub @bank 4   -360 = BuildSysRequest(pointer k_window @A0, pointer body @A1, pointer posText @A2, pointer negText @A3, long flags @D0, uword width @D1, uword height @D2) -> pointer @D0
    extsub @bank 4   -366 = EndRefresh(pointer k_window @A0, long complete @D0)
    extsub @bank 4   -372 = FreeSysRequest(pointer k_window @A0)
    extsub @bank 4   -378 = MakeScreen(pointer screen @A0) -> long @D0
    extsub @bank 4   -384 = RemakeDisplay() -> long @D0
    extsub @bank 4   -390 = RethinkDisplay() -> long @D0
    extsub @bank 4   -396 = AllocRemember(pointer rememberKey @A0, long size @D0, long flags @D1) -> pointer @D0
    extsub @bank 4   -402 = AlohaWorkbench(long wbport @A0)
    extsub @bank 4   -408 = FreeRemember(pointer rememberKey @A0, bool reallyForget @D0)
    extsub @bank 4   -414 = LockIBase(long dontknow @D0) -> long @D0
    extsub @bank 4   -420 = UnlockIBase(long ibLock @A0)
    extsub @bank 4   -426 = GetScreenData(pointer buffer @A0, uword size @D0, uword k_type @D1, pointer screen @A1) -> long @D0
    extsub @bank 4   -432 = RefreshGList(pointer gadgets @A0, pointer k_window @A1, pointer k_requester @A2, word numGad @D0)
    extsub @bank 4   -438 = AddGList(pointer k_window @A0, pointer gadget @A1, uword position @D0, word numGad @D1, pointer k_requester @A2) -> uword @D0
    extsub @bank 4   -444 = RemoveGList(pointer remPtr @A0, pointer gadget @A1, word numGad @D0) -> uword @D0
    extsub @bank 4   -450 = ActivateWindow(pointer k_window @A0)
    extsub @bank 4   -456 = RefreshWindowFrame(pointer k_window @A0)
    extsub @bank 4   -462 = ActivateGadget(pointer gadgets @A0, pointer k_window @A1, pointer k_requester @A2) -> bool @D0
    extsub @bank 4   -468 = NewModifyProp(pointer gadget @A0, pointer k_window @A1, pointer k_requester @A2, uword flags @D0, uword horizPot @D1, uword vertPot @D2, uword horizBody @D3, uword vertBody @D4, word numGad @D5)
    extsub @bank 4   -474 = QueryOverscan(long displayID @A0, pointer rect @A1, word oScanType @D0) -> long @D0
    extsub @bank 4   -480 = MoveWindowInFrontOf(pointer k_window @A0, pointer behindWindow @A1)
    extsub @bank 4   -486 = ChangeWindowBox(pointer k_window @A0, word left @D0, word top @D1, word width @D2, word height @D3)
    extsub @bank 4   -492 = SetEditHook(pointer hook @A0) -> pointer @D0
    extsub @bank 4   -498 = SetMouseQueue(pointer k_window @A0, uword queueLength @D0) -> long @D0
    extsub @bank 4   -504 = ZipWindow(pointer k_window @A0)
    extsub @bank 4   -510 = LockPubScreen(str name @A0) -> pointer @D0
    extsub @bank 4   -516 = UnlockPubScreen(str name @A0, pointer screen @A1)
    extsub @bank 4   -522 = LockPubScreenList() -> pointer @D0
    extsub @bank 4   -528 = UnlockPubScreenList()
    extsub @bank 4   -534 = NextPubScreen(pointer screen @A0, str namebuf @A1) -> str @D0
    extsub @bank 4   -540 = SetDefaultPubScreen(str name @A0)
    extsub @bank 4   -546 = SetPubScreenModes(uword modes @D0) -> uword @D0
    extsub @bank 4   -552 = PubScreenStatus(pointer screen @A0, uword statusFlags @D0) -> uword @D0
    extsub @bank 4   -558 = ObtainGIRPort(pointer gInfo @A0) -> pointer @D0
    extsub @bank 4   -564 = ReleaseGIRPort(pointer rp @A0)
    extsub @bank 4   -570 = GadgetMouse(pointer gadget @A0, pointer gInfo @A1, word mousePoint @A2)
    extsub @bank 4   -582 = GetDefaultPubScreen(str nameBuffer @A0)
    extsub @bank 4   -588 = EasyRequestArgs(pointer k_window @A0, pointer easyStruct @A1, long idcmpPtr @A2, pointer args @A3) -> long @D0
    extsub @bank 4   -594 = BuildEasyRequestArgs(pointer k_window @A0, pointer easyStruct @A1, long idcmp @D0, pointer args @A3) -> pointer @D0
    extsub @bank 4   -600 = SysReqHandler(pointer k_window @A0, long idcmpPtr @A1, bool waitInput @D0) -> long @D0
    extsub @bank 4   -606 = OpenWindowTagList(pointer newWindow @A0, pointer tagList @A1) -> pointer @D0
    extsub @bank 4   -612 = OpenScreenTagList(pointer newScreen @A0, pointer tagList @A1) -> pointer @D0
    extsub @bank 4   -618 = DrawImageState(pointer rp @A0, pointer image @A1, word leftOffset @D0, word topOffset @D1, long state @D2, pointer drawInfo @A2)
    extsub @bank 4   -624 = PointInImage(long point @D0, pointer image @A0) -> bool @D0
    extsub @bank 4   -630 = EraseImage(pointer rp @A0, pointer image @A1, word leftOffset @D0, word topOffset @D1)
    extsub @bank 4   -636 = NewObjectA(pointer classPtr @A0, str classID @A1, pointer tagList @A2) -> pointer @D0
    extsub @bank 4   -642 = DisposeObject(pointer object @A0)
    extsub @bank 4   -648 = SetAttrsA(pointer object @A0, pointer tagList @A1) -> long @D0
    extsub @bank 4   -654 = GetAttr(long attrID @D0, pointer object @A0, long storagePtr @A1) -> long @D0
    extsub @bank 4   -660 = SetGadgetAttrsA(pointer gadget @A0, pointer k_window @A1, pointer k_requester @A2, pointer tagList @A3) -> long @D0
    extsub @bank 4   -666 = NextObject(pointer objectPtrPtr @A0) -> pointer @D0
    extsub @bank 4   -678 = MakeClass(str classID @A0, str superClassID @A1, pointer superClassPtr @A2, uword instanceSize @D0, long flags @D1) -> pointer @D0
    extsub @bank 4   -684 = AddClass(pointer classPtr @A0)
    extsub @bank 4   -690 = GetScreenDrawInfo(pointer screen @A0) -> pointer @D0
    extsub @bank 4   -696 = FreeScreenDrawInfo(pointer screen @A0, pointer drawInfo @A1)
    extsub @bank 4   -702 = ResetMenuStrip(pointer k_window @A0, pointer menu @A1) -> bool @D0
    extsub @bank 4   -708 = RemoveClass(pointer classPtr @A0)
    extsub @bank 4   -714 = FreeClass(pointer classPtr @A0) -> bool @D0
    extsub @bank 4   -768 = AllocScreenBuffer(pointer sc @A0, pointer bm @A1, long flags @D0) -> pointer @D0
    extsub @bank 4   -774 = FreeScreenBuffer(pointer sc @A0, pointer sb @A1)
    extsub @bank 4   -780 = ChangeScreenBuffer(pointer sc @A0, pointer sb @A1) -> long @D0
    extsub @bank 4   -786 = ScreenDepth(pointer screen @A0, long flags @D0, pointer reserved @A1)
    extsub @bank 4   -792 = ScreenPosition(pointer screen @A0, long flags @D0, long x1 @D1, long y1 @D2, long x2 @D3, long y2 @D4)
    extsub @bank 4   -798 = ScrollWindowRaster(pointer win @A1, word dx @D0, word dy @D1, word xMin @D2, word yMin @D3, word xMax @D4, word yMax @D5)
    extsub @bank 4   -804 = LendMenus(pointer fromwindow @A0, pointer towindow @A1)
    extsub @bank 4   -810 = DoGadgetMethodA(pointer gad @A0, pointer win @A1, pointer req @A2, long message @A3) -> long @D0
    extsub @bank 4   -816 = SetWindowPointerA(pointer win @A0, pointer taglist @A1)
    extsub @bank 4   -822 = TimedDisplayAlert(long alertNumber @D0, str k_string @A0, uword height @D1, long time @A1) -> bool @D0
    extsub @bank 4   -828 = HelpControl(pointer win @A0, long flags @D0)
    extsub @bank 4   -834 = ShowWindow(pointer k_window @A0, pointer other @A1) -> bool @D0
    extsub @bank 4   -840 = HideWindow(pointer k_window @A0) -> bool @D0
    extsub @bank 4   -1212 = IntuitionControlA(pointer object @A0, pointer taglist @A1) -> long @D0

    ; ---- struct definitions ----

    struct Border {  ; total size: 16
        word LeftEdge  ; 0
        word TopEdge  ; 2
        ubyte FrontPen  ; 4
        ubyte BackPen  ; 5
        ubyte DrawMode  ; 6
        byte Count  ; 7
        pointer Xy  ; 8
        pointer NextBorder  ; 12
    }

    struct ColorSpec {  ; total size: 8
        word ColorIndex  ; 0
        uword Red  ; 2
        uword Green  ; 4
        uword Blue  ; 6
    }

    struct DrawInfo {  ; total size: 50
        uword Version  ; 0
        uword NumPens  ; 2
        pointer Pens  ; 4
        pointer Font  ; 8
        uword Depth  ; 12
        uword X  ; 14
        uword Y  ; 16
        long Flags  ; 18
        pointer CheckMark  ; 22
        pointer AmigaKey  ; 26
        pointer Screen  ; 30
        long[4] Reserved  ; 34
    }

    struct EasyStruct {  ; total size: 20
        long StructSize  ; 0
        long Flags  ; 4
        str Title  ; 8
        str TextFormat  ; 12
        str GadgetFormat  ; 16
    }

    struct ExtGadget {  ; total size: 56
        pointer NextGadget  ; 0
        word LeftEdge  ; 4
        word TopEdge  ; 6
        word Width  ; 8
        word Height  ; 10
        uword Flags  ; 12
        uword Activation  ; 14
        uword GadgetType  ; 16
        pointer GadgetRender  ; 18
        pointer SelectRender  ; 22
        pointer GadgetText  ; 26
        long MutualExclude  ; 30
        pointer SpecialInfo  ; 34
        uword GadgetID  ; 38
        pointer UserData  ; 40
        long MoreFlags  ; 44
        word BoundsLeftEdge  ; 48
        word BoundsTopEdge  ; 50
        word BoundsWidth  ; 52
        word BoundsHeight  ; 54
    }

    struct ExtIntuiMessage {  ; total size: 56
        ubyte[20] emb_ExecMessage  ; 0
        long Class  ; 20
        uword Code  ; 24
        uword Qualifier  ; 26
        pointer IAddress  ; 28
        word MouseX  ; 32
        word MouseY  ; 34
        long Seconds  ; 36
        long Micros  ; 40
        pointer IDCMPWindow  ; 44
        pointer SpecialLink  ; 48
        pointer TabletData  ; 52
    }

    struct Gadget {  ; total size: 44
        pointer NextGadget  ; 0
        word LeftEdge  ; 4
        word TopEdge  ; 6
        word Width  ; 8
        word Height  ; 10
        uword Flags  ; 12
        uword Activation  ; 14
        uword GadgetType  ; 16
        pointer GadgetRender  ; 18
        pointer SelectRender  ; 22
        pointer GadgetText  ; 26
        long MutualExclude  ; 30
        pointer SpecialInfo  ; 34
        uword GadgetID  ; 38
        pointer UserData  ; 40
    }

    struct IBox {  ; total size: 8
        word Left  ; 0
        word Top  ; 2
        word Width  ; 4
        word Height  ; 6
    }

    struct Image {  ; total size: 20
        word LeftEdge  ; 0
        word TopEdge  ; 2
        word Width  ; 4
        word Height  ; 6
        word Depth  ; 8
        pointer ImageData  ; 10
        ubyte PlanePick  ; 14
        ubyte PlaneOnOff  ; 15
        pointer NextImage  ; 16
    }

    struct IntuiMessage {  ; total size: 52
        ubyte[20] emb_ExecMessage  ; 0
        long Class  ; 20
        uword Code  ; 24
        uword Qualifier  ; 26
        pointer IAddress  ; 28
        word MouseX  ; 32
        word MouseY  ; 34
        long Seconds  ; 36
        long Micros  ; 40
        pointer IDCMPWindow  ; 44
        pointer SpecialLink  ; 48
    }

    struct IntuiText {  ; total size: 20
        ubyte FrontPen  ; 0
        ubyte BackPen  ; 1
        ubyte DrawMode  ; 2
        word LeftEdge  ; 4
        word TopEdge  ; 6
        pointer ITextFont  ; 8
        str IText  ; 12
        pointer NextText  ; 16
    }

    struct Menu {  ; total size: 30
        pointer NextMenu  ; 0
        word LeftEdge  ; 4
        word TopEdge  ; 6
        word Width  ; 8
        word Height  ; 10
        uword Flags  ; 12
        str MenuName  ; 14
        pointer FirstItem  ; 18
        word JazzX  ; 22
        word JazzY  ; 24
        word BeatX  ; 26
        word BeatY  ; 28
    }

    struct MenuItem {  ; total size: 34
        pointer NextItem  ; 0
        word LeftEdge  ; 4
        word TopEdge  ; 6
        word Width  ; 8
        word Height  ; 10
        uword Flags  ; 12
        long MutualExclude  ; 14
        pointer ItemFill  ; 18
        pointer SelectFill  ; 22
        byte Command  ; 26
        pointer SubItem  ; 28
        uword NextSelect  ; 32
    }

    struct NewScreen {  ; total size: 32
        word LeftEdge  ; 0
        word TopEdge  ; 2
        word Width  ; 4
        word Height  ; 6
        word Depth  ; 8
        ubyte DetailPen  ; 10
        ubyte BlockPen  ; 11
        uword ViewModes  ; 12
        uword Type  ; 14
        pointer Font  ; 16
        str DefaultTitle  ; 20
        pointer Gadgets  ; 24
        pointer CustomBitMap  ; 28
    }

    struct NewWindow {  ; total size: 48
        word LeftEdge  ; 0
        word TopEdge  ; 2
        word Width  ; 4
        word Height  ; 6
        ubyte DetailPen  ; 8
        ubyte BlockPen  ; 9
        long IDCMPFlags  ; 10
        long Flags  ; 14
        pointer FirstGadget  ; 18
        pointer CheckMark  ; 22
        str Title  ; 26
        pointer Screen  ; 30
        pointer BitMap  ; 34
        word MinWidth  ; 38
        word MinHeight  ; 40
        uword MaxWidth  ; 42
        uword MaxHeight  ; 44
        uword Type  ; 46
    }

    struct Requester {  ; total size: 112
        pointer OlderRequest  ; 0
        word LeftEdge  ; 4
        word TopEdge  ; 6
        word Width  ; 8
        word Height  ; 10
        word RelLeft  ; 12
        word RelTop  ; 14
        pointer ReqGadget  ; 16
        pointer ReqBorder  ; 20
        pointer ReqText  ; 24
        uword Flags  ; 28
        ubyte BackFill  ; 30
        pointer ReqLayer  ; 32
        ubyte[32] ReqPad1  ; 36
        pointer ImageBMap  ; 68
        pointer RWindow  ; 72
        pointer ReqImage  ; 76
        ubyte[32] ReqPad2  ; 80
    }

    struct Screen {  ; total size: 40
        pointer NextScreen  ; 0
        pointer FirstWindow  ; 4
        word LeftEdge  ; 8
        word TopEdge  ; 10
        word Width  ; 12
        word Height  ; 14
        word MouseY  ; 16
        word MouseX  ; 18
        uword Flags  ; 20
        str Title  ; 22
        str DefaultTitle  ; 26
        byte BarHeight  ; 30
        byte BarVBorder  ; 31
        byte BarHBorder  ; 32
        byte MenuVBorder  ; 33
        byte MenuHBorder  ; 34
        byte WBorTop  ; 35
        byte WBorLeft  ; 36
        byte WBorRight  ; 37
        byte WBorBottom  ; 38
    ; stripped: pointer UserData (4B), pointer ExtData (4B), pointer BarLayer (4B), uword SaveColor0 (2B), ubyte BlockPen (1B), ubyte DetailPen (1B), pointer FirstGadget (4B), ubyte[102] emb_LayerInfo (102B), ubyte[40] emb_BitMap (40B), ubyte[100] emb_RastPort (100B), ubyte[40] emb_ViewPort (40B), pointer Font (4B)
    }

    struct Window {  ; total size: 136
        pointer NextWindow  ; 0
        word LeftEdge  ; 4
        word TopEdge  ; 6
        word Width  ; 8
        word Height  ; 10
        word MouseY  ; 12
        word MouseX  ; 14
        word MinWidth  ; 16
        word MinHeight  ; 18
        uword MaxWidth  ; 20
        uword MaxHeight  ; 22
        long Flags  ; 24
        pointer MenuStrip  ; 28
        str Title  ; 32
        pointer FirstRequest  ; 36
        pointer DMRequest  ; 40
        word ReqCount  ; 44
        pointer WScreen  ; 46
        pointer RPort  ; 50
        byte BorderLeft  ; 54
        byte BorderTop  ; 55
        byte BorderRight  ; 56
        byte BorderBottom  ; 57
        pointer BorderRPort  ; 58
        pointer FirstGadget  ; 62
        pointer Parent  ; 66
        pointer Descendant  ; 70
        pointer Pointer  ; 74
        byte PtrHeight  ; 78
        byte PtrWidth  ; 79
        byte XOffset  ; 80
        byte YOffset  ; 81
        long IDCMPFlags  ; 82
        pointer UserPort  ; 86
        pointer WindowPort  ; 90
        pointer MessageKey  ; 94
        ubyte DetailPen  ; 98
        ubyte BlockPen  ; 99
        pointer CheckMark  ; 100
        str ScreenTitle  ; 104
        word GZZMouseX  ; 108
        word GZZMouseY  ; 110
        word GZZWidth  ; 112
        word GZZHeight  ; 114
        pointer ExtData  ; 116
        pointer UserData  ; 120
        pointer WLayer  ; 124
        pointer IFont  ; 128
        long MoreFlags  ; 132
    }

    ; ---- constants ----
    const ubyte CLB_INLIST = $0000
    const ubyte CLF_INLIST = $0001
    const uword OM_NEW = $0101
    const uword OM_DISPOSE = $0102
    const uword OM_SET = $0103
    const uword OM_GET = $0104
    const uword OM_ADDTAIL = $0105
    const uword OM_REMOVE = $0106
    const uword OM_NOTIFY = $0107
    const uword OM_UPDATE = $0108
    const uword OM_ADDMEMBER = $0109
    const uword OM_REMMEMBER = $010a
    const ubyte OPUB_INTERIM = $0000
    const ubyte OPUF_INTERIM = $0001
    const long GA_Dummy = $80030000
    const long PGA_Dummy = $80031000
    const long STRINGA_Dummy = $80032000
    const ubyte SG_DEFAULTMAXCHARS = $0080
    const long LAYOUTA_Dummy = $80038000
    const ubyte LORIENT_NONE = $0000
    const ubyte LORIENT_HORIZ = $0001
    const ubyte LORIENT_VERT = $0002
    const ubyte GM_HITTEST = $0000
    const ubyte GM_RENDER = $0001
    const ubyte GM_GOACTIVE = $0002
    const ubyte GM_HANDLEINPUT = $0003
    const ubyte GM_GOINACTIVE = $0004
    const ubyte GM_HELPTEST = $0005
    const ubyte GM_LAYOUT = $0006
    const ubyte GM_DOMAIN = $0007
    const ubyte GM_KEYTEST = $0008
    const ubyte GM_KEYGOACTIVE = $0009
    const ubyte GM_KEYGOINACTIVE = $000a
    const ubyte GMR_GADGETHIT = $0004
    const ubyte GMR_NOHELPHIT = $0000
    const long GMR_HELPHIT = $ffffffff
    const long GMR_HELPCODE = $00010000
    const ubyte GREDRAW_UPDATE = $0002
    const ubyte GREDRAW_REDRAW = $0001
    const ubyte GREDRAW_TOGGLE = $0000
    const ubyte GMR_MEACTIVE = $0000
    const ubyte GMR_NOREUSE = $0002
    const ubyte GMR_REUSE = $0004
    const ubyte GMR_VERIFY = $0008
    const ubyte GMR_NEXTACTIVE = $0010
    const ubyte GMR_PREVACTIVE = $0020
    const ubyte GMRB_NOREUSE = $0001
    const ubyte GMRB_REUSE = $0002
    const ubyte GMRB_VERIFY = $0003
    const ubyte GMRB_NEXTACTIVE = $0004
    const ubyte GMRB_PREVACTIVE = $0005
    const ubyte GMRF_NOREUSE = $0002
    const ubyte GMRF_REUSE = $0004
    const ubyte GMRF_VERIFY = $0008
    const ubyte GMRF_NEXTACTIVE = $0010
    const ubyte GMRF_PREVACTIVE = $0020
    const ubyte GDOMAIN_MINIMUM = $0000
    const ubyte GDOMAIN_NOMINAL = $0001
    const ubyte GDOMAIN_MAXIMUM = $0002
    const ubyte GMR_KEYACTIVE = $0010
    const ubyte GMR_KEYVERIFY = $0020
    const uword ICM_SETLOOP = $0402
    const uword ICM_CLEARLOOP = $0403
    const uword ICM_CHECKLOOP = $0404
    const long ICA_Dummy = $80040000
    const long ICTARGET_IDCMP = $ffffffff
    const long CUSTOMIMAGEDEPTH = -1
    const long IMAGE_ATTRIBUTES = $80020000
    const ubyte SYSISIZE_MEDRES = $0000
    const ubyte SYSISIZE_LOWRES = $0001
    const ubyte SYSISIZE_HIRES = $0002
    const ubyte DEPTHIMAGE = $0000
    const ubyte ZOOMIMAGE = $0001
    const ubyte SIZEIMAGE = $0002
    const ubyte CLOSEIMAGE = $0003
    const ubyte SDEPTHIMAGE = $0005
    const ubyte LEFTIMAGE = $000a
    const ubyte UPIMAGE = $000b
    const ubyte RIGHTIMAGE = $000c
    const ubyte DOWNIMAGE = $000d
    const ubyte CHECKIMAGE = $000e
    const ubyte MXIMAGE = $000f
    const ubyte MENUCHECK = $0010
    const ubyte AMIGAKEY = $0011
    const ubyte ICONIFYIMAGE = $0016
    const ubyte MENUMX = $001b
    const ubyte MENUSUB = $001c
    const ubyte SHIFTKEYIMAGE = $002a
    const ubyte FRAME_DEFAULT = $0000
    const ubyte FRAME_BUTTON = $0001
    const ubyte FRAME_RIDGE = $0002
    const ubyte FRAME_ICONDROPBOX = $0003
    const ubyte FRAME_PROPBORDER = $0004
    const ubyte FRAME_PROPKNOB = $0005
    const ubyte FRAME_DISPLAY = $0006
    const ubyte FRAME_CONTEXT = $0007
    const uword IM_DRAW = $0202
    const uword IM_HITTEST = $0203
    const uword IM_ERASE = $0204
    const uword IM_MOVE = $0205
    const uword IM_DRAWFRAME = $0206
    const uword IM_FRAMEBOX = $0207
    const uword IM_HITFRAME = $0208
    const uword IM_ERASEFRAME = $0209
    const uword IM_DOMAINFRAME = $020a
    const ubyte IDS_NORMAL = $0000
    const ubyte IDS_SELECTED = $0001
    const ubyte IDS_DISABLED = $0002
    const ubyte IDS_BUSY = $0003
    const ubyte IDS_INDETERMINATE = $0004
    const ubyte IDS_INACTIVENORMAL = $0005
    const ubyte IDS_INACTIVESELECTED = $0006
    const ubyte IDS_INACTIVEDISABLED = $0007
    const ubyte IDS_SELECTEDDISABLED = $0008
    const ubyte FRAMEB_SPECIFY = $0000
    const ubyte FRAMEF_SPECIFY = $0001
    const ubyte FRAMEB_MINIMAL = $0001
    const ubyte FRAMEF_MINIMAL = $0002
    const ubyte IDOMAIN_MINIMUM = $0000
    const ubyte IDOMAIN_NOMINAL = $0001
    const ubyte IDOMAIN_MAXIMUM = $0002
    const ubyte MENUENABLED = $0001
    const uword MIDRAWN = $0100
    const ubyte CHECKIT = $0001
    const ubyte ITEMTEXT = $0002
    const ubyte COMMSEQ = $0004
    const ubyte MENUTOGGLE = $0008
    const ubyte ITEMENABLED = $0010
    const ubyte HIGHFLAGS = $00c0
    const ubyte HIGHIMAGE = $0000
    const ubyte HIGHCOMP = $0040
    const ubyte HIGHBOX = $0080
    const ubyte HIGHNONE = $00c0
    const uword CHECKED = $0100
    const uword ISDRAWN = $1000
    const uword HIGHITEM = $2000
    const uword MENUTOGGLED = $4000
    const ubyte POINTREL = $0001
    const ubyte PREDRAWN = $0002
    const ubyte NOISYREQ = $0004
    const ubyte SIMPLEREQ = $0010
    const ubyte USEREQIMAGE = $0020
    const ubyte NOREQBACKFILL = $0040
    const uword REQOFFWINDOW = $1000
    const uword REQACTIVE = $2000
    const uword SYSREQUEST = $4000
    const uword DEFERREFRESH = $8000
    const long GFLG_GADGHIGHBITS = $0003
    const long GFLG_GADGHCOMP = $0000
    const long GFLG_GADGHBOX = $0001
    const long GFLG_GADGHIMAGE = $0002
    const long GFLG_GADGHNONE = $0003
    const long GFLG_GADGIMAGE = $0004
    const long GFLG_RELBOTTOM = $0008
    const long GFLG_RELRIGHT = $0010
    const long GFLG_RELWIDTH = $0020
    const long GFLG_RELHEIGHT = $0040
    const long GFLG_RELSPECIAL = $4000
    const long GFLG_SELECTED = $0080
    const long GFLG_DISABLED = $0100
    const long GFLG_LABELMASK = $3000
    const long GFLG_LABELITEXT = $0000
    const long GFLG_LABELSTRING = $1000
    const long GFLG_LABELIMAGE = $2000
    const long GFLG_TABCYCLE = $0200
    const long GFLG_STRINGEXTEND = $0400
    const long GFLG_IMAGEDISABLE = $0800
    const long GFLG_EXTENDED = $8000
    const long GACT_RELVERIFY = $0001
    const long GACT_IMMEDIATE = $0002
    const long GACT_ENDGADGET = $0004
    const long GACT_FOLLOWMOUSE = $0008
    const long GACT_RIGHTBORDER = $0010
    const long GACT_LEFTBORDER = $0020
    const long GACT_TOPBORDER = $0040
    const long GACT_BOTTOMBORDER = $0080
    const long GACT_BORDERSNIFF = $8000
    const long GACT_TOGGLESELECT = $0100
    const long GACT_BOOLEXTEND = $2000
    const long GACT_STRINGLEFT = $0000
    const long GACT_STRINGCENTER = $0200
    const long GACT_STRINGRIGHT = $0400
    const long GACT_LONGINT = $0800
    const long GACT_ALTKEYMAP = $1000
    const long GACT_STRINGEXTEND = $2000
    const long GACT_ACTIVEGADGET = $4000
    const long GTYP_GADGETTYPE = $fc00
    const long GTYP_SCRGADGET = $4000
    const long GTYP_GZZGADGET = $2000
    const long GTYP_REQGADGET = $1000
    const long GTYP_SYSGADGET = $8000
    const long GTYP_SYSTYPEMASK = $00f0
    const long GTYP_SIZING = $0010
    const long GTYP_WDRAGGING = $0020
    const long GTYP_SDRAGGING = $0030
    const long GTYP_WDEPTH = $0040
    const long GTYP_SDEPTH = $0050
    const long GTYP_WZOOM = $0060
    const long GTYP_SUNUSED = $0070
    const long GTYP_CLOSE = $0080
    const long GTYP_ICONIFY = $0090
    const long GTYP_GTYPEMASK = $0007
    const long GTYP_BOOLGADGET = $0001
    const long GTYP_GADGET0002 = $0002
    const long GTYP_PROPGADGET = $0003
    const long GTYP_STRGADGET = $0004
    const long GTYP_CUSTOMGADGET = $0005
    const long GMORE_BOUNDS = $0001
    const long GMORE_GADGETHELP = $0002
    const long GMORE_SCROLLRASTER = $0004
    const long GMORE_HIDDEN = $0010
    const long GMORE_BOOPSIGADGET = $0400
    const long GMORE_FREEIMAGE = $0800
    const long GMORE_PARENTHIDDEN = $01000000
    const ubyte BOOLMASK = $0001
    const ubyte AUTOKNOB = $0001
    const ubyte FREEHORIZ = $0002
    const ubyte FREEVERT = $0004
    const ubyte PROPBORDERLESS = $0008
    const uword KNOBHIT = $0100
    const ubyte PROPNEWLOOK = $0010
    const ubyte SMARTKNOBIMAGE = $0020
    const ubyte KNOBHMIN = $0006
    const ubyte KNOBVMIN = $0004
    const uword MAXBODY = $ffff
    const uword MAXPOT = $ffff
    const long IDCMP_SIZEVERIFY = $0001
    const long IDCMP_NEWSIZE = $0002
    const long IDCMP_REFRESHWINDOW = $0004
    const long IDCMP_MOUSEBUTTONS = $0008
    const long IDCMP_MOUSEMOVE = $0010
    const long IDCMP_GADGETDOWN = $0020
    const long IDCMP_GADGETUP = $0040
    const long IDCMP_REQSET = $0080
    const long IDCMP_MENUPICK = $0100
    const long IDCMP_CLOSEWINDOW = $0200
    const long IDCMP_RAWKEY = $0400
    const long IDCMP_REQVERIFY = $0800
    const long IDCMP_REQCLEAR = $1000
    const long IDCMP_MENUVERIFY = $2000
    const long IDCMP_NEWPREFS = $4000
    const long IDCMP_DISKINSERTED = $8000
    const long IDCMP_DISKREMOVED = $00010000
    const long IDCMP_WBENCHMESSAGE = $00020000
    const long IDCMP_ACTIVEWINDOW = $00040000
    const long IDCMP_INACTIVEWINDOW = $00080000
    const long IDCMP_DELTAMOVE = $00100000
    const long IDCMP_VANILLAKEY = $00200000
    const long IDCMP_INTUITICKS = $00400000
    const long IDCMP_IDCMPUPDATE = $00800000
    const long IDCMP_MENUHELP = $01000000
    const long IDCMP_CHANGEWINDOW = $02000000
    const long IDCMP_GADGETHELP = $04000000
    const long IDCMP_LONELYMESSAGE = $80000000
    const ubyte CWCODE_MOVESIZE = $0000
    const ubyte CWCODE_DEPTH = $0001
    const ubyte CWCODE_HIDE = $0002
    const ubyte CWCODE_SHOW = $0003
    const ubyte MENUHOT = $0001
    const ubyte MENUCANCEL = $0002
    const ubyte MENUWAITING = $0003
    const ubyte OKABORT = $0004
    const ubyte WBENCHOPEN = $0001
    const ubyte WBENCHCLOSE = $0002
    const long WFLG_SIZEGADGET = $0001
    const long WFLG_DRAGBAR = $0002
    const long WFLG_DEPTHGADGET = $0004
    const long WFLG_CLOSEGADGET = $0008
    const long WFLG_SIZEBRIGHT = $0010
    const long WFLG_SIZEBBOTTOM = $0020
    const long WFLG_REFRESHBITS = $00c0
    const long WFLG_SMART_REFRESH = $0000
    const long WFLG_SIMPLE_REFRESH = $0040
    const long WFLG_SUPER_BITMAP = $0080
    const long WFLG_OTHER_REFRESH = $00c0
    const long WFLG_BACKDROP = $0100
    const long WFLG_REPORTMOUSE = $0200
    const long WFLG_GIMMEZEROZERO = $0400
    const long WFLG_BORDERLESS = $0800
    const long WFLG_ACTIVATE = $1000
    const long WFLG_RMBTRAP = $00010000
    const long WFLG_NOCAREREFRESH = $00020000
    const long WFLG_NW_EXTENDED = $00040000
    const long WFLG_NEWLOOKMENUS = $00200000
    const long WFLG_WINDOWACTIVE = $2000
    const long WFLG_INREQUEST = $4000
    const long WFLG_MENUSTATE = $8000
    const long WFLG_WINDOWREFRESH = $01000000
    const long WFLG_WBENCHWINDOW = $02000000
    const long WFLG_WINDOWTICKED = $04000000
    const long WFLG_VISITOR = $08000000
    const long WFLG_ZOOMED = $10000000
    const long WFLG_HASZOOM = $20000000
    const long WFLG_HASICONIFY = $40000000
    const long SUPER_UNUSED = $fcfc0000
    const ubyte DEFAULTMOUSEQUEUE = $0005
    const long WA_Left = $80000064
    const long WA_Top = $80000065
    const long WA_Width = $80000066
    const long WA_Height = $80000067
    const long WA_DetailPen = $80000068
    const long WA_BlockPen = $80000069
    const long WA_IDCMP = $8000006a
    const long WA_Flags = $8000006b
    const long WA_Gadgets = $8000006c
    const long WA_Checkmark = $8000006d
    const long WA_Title = $8000006e
    const long WA_ScreenTitle = $8000006f
    const long WA_CustomScreen = $80000070
    const long WA_SuperBitMap = $80000071
    const long WA_MinWidth = $80000072
    const long WA_MinHeight = $80000073
    const long WA_MaxWidth = $80000074
    const long WA_MaxHeight = $80000075
    const long WA_InnerWidth = $80000076
    const long WA_InnerHeight = $80000077
    const long WA_PubScreenName = $80000078
    const long WA_PubScreen = $80000079
    const long WA_PubScreenFallBack = $8000007a
    const long WA_WindowName = $8000007b
    const long WA_Colors = $8000007c
    const long WA_Zoom = $8000007d
    const long WA_MouseQueue = $8000007e
    const long WA_BackFill = $8000007f
    const long WA_RptQueue = $80000080
    const long WA_SizeGadget = $80000081
    const long WA_DragBar = $80000082
    const long WA_DepthGadget = $80000083
    const long WA_CloseGadget = $80000084
    const long WA_Backdrop = $80000085
    const long WA_ReportMouse = $80000086
    const long WA_NoCareRefresh = $80000087
    const long WA_Borderless = $80000088
    const long WA_Activate = $80000089
    const long WA_RMBTrap = $8000008a
    const long WA_WBenchWindow = $8000008b
    const long WA_SimpleRefresh = $8000008c
    const long WA_SmartRefresh = $8000008d
    const long WA_SizeBRight = $8000008e
    const long WA_SizeBBottom = $8000008f
    const long WA_AutoAdjust = $80000090
    const long WA_GimmeZeroZero = $80000091
    const long WA_MenuHelp = $80000092
    const long WA_NewLookMenus = $80000093
    const long WA_AmigaKey = $80000094
    const long WA_NotifyDepth = $80000095
    const long WA_Obsolete = $80000096
    const long WA_Pointer = $80000097
    const long WA_BusyPointer = $80000098
    const long WA_PointerDelay = $80000099
    const long WA_TabletMessages = $8000009a
    const long WA_HelpGroup = $8000009b
    const long WA_HelpGroupWindow = $8000009c
    const long WA_Hidden = $8000009f
    const long WA_PointerType = $800000b3
    const long WA_IconifyGadget = $800000c3
    const ubyte HC_GADGETHELP = $0001
    const ubyte WINDOW_BACKMOST = $0000
    const ubyte WINDOW_FRONTMOST = $0001
    const ubyte NOMENU = $001f
    const ubyte NOITEM = $003f
    const ubyte NOSUB = $001f
    const uword MENUNULL = $ffff
    const ubyte CHECKWIDTH = $0013
    const ubyte COMMWIDTH = $001b
    const ubyte LOWCHECKWIDTH = $000d
    const ubyte LOWCOMMWIDTH = $0010
    const long ALERT_TYPE = $80000000
    const ubyte RECOVERY_ALERT = $0000
    const long DEADEND_ALERT = $80000000
    const ubyte AUTOFRONTPEN = $0000
    const ubyte AUTOBACKPEN = $0001
    const ubyte AUTOLEFTEDGE = $0006
    const ubyte AUTOTOPEDGE = $0003
    const ubyte AUTOITEXTFONT = $0000
    const ubyte AUTONEXTTEXT = $0000
    const ubyte CURSORUP = $004c
    const ubyte CURSORLEFT = $004f
    const ubyte CURSORRIGHT = $004e
    const ubyte CURSORDOWN = $004d
    const ubyte KEYCODE_Q = $0010
    const ubyte KEYCODE_Z = $0031
    const ubyte KEYCODE_X = $0032
    const ubyte KEYCODE_V = $0034
    const ubyte KEYCODE_B = $0035
    const ubyte KEYCODE_N = $0036
    const ubyte KEYCODE_M = $0037
    const ubyte KEYCODE_LESS = $0038
    const ubyte KEYCODE_GREATER = $0039
    const long TABLETA_Dummy = $8003a000
    const long POINTERA_Dummy = $80039000
    const ubyte POINTERXRESN_DEFAULT = $0000
    const ubyte POINTERXRESN_140NS = $0001
    const ubyte POINTERXRESN_70NS = $0002
    const ubyte POINTERXRESN_35NS = $0003
    const ubyte POINTERXRESN_SCREENRES = $0004
    const ubyte POINTERXRESN_LORES = $0005
    const ubyte POINTERXRESN_HIRES = $0006
    const ubyte POINTERYRESN_DEFAULT = $0000
    const ubyte POINTERYRESN_HIGH = $0002
    const ubyte POINTERYRESN_HIGHASPECT = $0003
    const ubyte POINTERYRESN_SCREENRES = $0004
    const ubyte POINTERYRESN_SCREENRESASPECT = $0005
    const ubyte FILENAME_SIZE = $001e
    const ubyte DEVNAME_SIZE = $0010
    const ubyte TOPAZ_EIGHTY = $0008
    const ubyte TOPAZ_SIXTY = $0009
    const ubyte LACEWB = $0001
    const uword SCREEN_DRAG = $4000
    const uword MOUSE_ACCEL = $8000
    const ubyte PARALLEL_PRINTER = $0000
    const ubyte SERIAL_PRINTER = $0001
    const ubyte BAUD_110 = $0000
    const ubyte BAUD_300 = $0001
    const ubyte BAUD_1200 = $0002
    const ubyte BAUD_2400 = $0003
    const ubyte BAUD_4800 = $0004
    const ubyte BAUD_9600 = $0005
    const ubyte BAUD_19200 = $0006
    const ubyte BAUD_MIDI = $0007
    const ubyte FANFOLD = $0000
    const ubyte SINGLE = $0080
    const ubyte PICA = $0000
    const uword ELITE = $0400
    const uword FINE = $0800
    const ubyte DRAFT = $0000
    const uword LETTER = $0100
    const ubyte SIX_LPI = $0000
    const uword EIGHT_LPI = $0200
    const ubyte IMAGE_POSITIVE = $0000
    const ubyte IMAGE_NEGATIVE = $0001
    const ubyte ASPECT_HORIZ = $0000
    const ubyte ASPECT_VERT = $0001
    const ubyte SHADE_BW = $0000
    const ubyte SHADE_GREYSCALE = $0001
    const ubyte SHADE_COLOR = $0002
    const ubyte US_LETTER = $0000
    const ubyte US_LEGAL = $0010
    const ubyte N_TRACTOR = $0020
    const ubyte W_TRACTOR = $0030
    const ubyte CUSTOM = $0040
    const ubyte EURO_A0 = $0050
    const ubyte EURO_A1 = $0060
    const ubyte EURO_A2 = $0070
    const ubyte EURO_A3 = $0080
    const ubyte EURO_A4 = $0090
    const ubyte EURO_A5 = $00a0
    const ubyte EURO_A6 = $00b0
    const ubyte EURO_A7 = $00c0
    const ubyte EURO_A8 = $00d0
    const ubyte CUSTOM_NAME = $0000
    const ubyte ALPHA_P_101 = $0001
    const ubyte BROTHER_15XL = $0002
    const ubyte CBM_MPS1000 = $0003
    const ubyte DIAB_630 = $0004
    const ubyte DIAB_ADV_D25 = $0005
    const ubyte DIAB_C_150 = $0006
    const ubyte EPSON = $0007
    const ubyte EPSON_JX_80 = $0008
    const ubyte OKIMATE_20 = $0009
    const ubyte QUME_LP_20 = $000a
    const ubyte HP_LASERJET = $000b
    const ubyte HP_LASERJET_PLUS = $000c
    const ubyte SBUF_512 = $0000
    const ubyte SBUF_1024 = $0001
    const ubyte SBUF_2048 = $0002
    const ubyte SBUF_4096 = $0003
    const ubyte SBUF_8000 = $0004
    const ubyte SBUF_16000 = $0005
    const ubyte SREAD_BITS = $00f0
    const ubyte SWRITE_BITS = $000f
    const ubyte SSTOP_BITS = $00f0
    const ubyte SBUFSIZE_BITS = $000f
    const ubyte SPARITY_BITS = $00f0
    const ubyte SHSHAKE_BITS = $000f
    const ubyte SPARITY_NONE = $0000
    const ubyte SPARITY_EVEN = $0001
    const ubyte SPARITY_ODD = $0002
    const ubyte SPARITY_MARK = $0003
    const ubyte SPARITY_SPACE = $0004
    const ubyte SHSHAKE_XON = $0000
    const ubyte SHSHAKE_RTS = $0001
    const ubyte SHSHAKE_NONE = $0002
    const ubyte CORRECT_RED = $0001
    const ubyte CORRECT_GREEN = $0002
    const ubyte CORRECT_BLUE = $0004
    const ubyte CENTER_IMAGE = $0008
    const ubyte IGNORE_DIMENSIONS = $0000
    const ubyte BOUNDED_DIMENSIONS = $0010
    const ubyte ABSOLUTE_DIMENSIONS = $0020
    const ubyte PIXEL_DIMENSIONS = $0040
    const ubyte MULTIPLY_DIMENSIONS = $0080
    const uword INTEGER_SCALING = $0100
    const ubyte ORDERED_DITHERING = $0000
    const uword HALFTONE_DITHERING = $0200
    const uword FLOYD_DITHERING = $0400
    const uword ANTI_ALIAS = $0800
    const uword GREY_SCALE2 = $1000
    const ubyte DRI_VERSION = $0002
    const ubyte DRIF_NEWLOOK = $0001
    const ubyte DRIB_NEWLOOK = $0000
    const uword PEN_C3 = $fefc
    const uword PEN_C2 = $fefd
    const uword PEN_C1 = $fefe
    const uword PEN_C0 = $feff
    const ubyte SCREENTYPE = $000f
    const ubyte WBENCHSCREEN = $0001
    const ubyte PUBLICSCREEN = $0002
    const ubyte CUSTOMSCREEN = $000f
    const ubyte SHOWTITLE = $0010
    const ubyte BEEPING = $0020
    const ubyte CUSTOMBITMAP = $0040
    const ubyte SCREENBEHIND = $0080
    const uword SCREENQUIET = $0100
    const uword SCREENHIRES = $0200
    const long STDSCREENHEIGHT = -1
    const long STDSCREENWIDTH = -1
    const uword NS_EXTENDED = $1000
    const uword AUTOSCROLL = $4000
    const uword PENSHARED = $0400
    const long SA_Left = $80000021
    const long SA_Top = $80000022
    const long SA_Width = $80000023
    const long SA_Height = $80000024
    const long SA_Depth = $80000025
    const long SA_DetailPen = $80000026
    const long SA_BlockPen = $80000027
    const long SA_Title = $80000028
    const long SA_Colors = $80000029
    const long SA_ErrorCode = $8000002a
    const long SA_Font = $8000002b
    const long SA_SysFont = $8000002c
    const long SA_Type = $8000002d
    const long SA_BitMap = $8000002e
    const long SA_PubName = $8000002f
    const long SA_PubSig = $80000030
    const long SA_PubTask = $80000031
    const long SA_DisplayID = $80000032
    const long SA_DClip = $80000033
    const long SA_Overscan = $80000034
    const long SA_Obsolete1 = $80000035
    const long SA_ShowTitle = $80000036
    const long SA_Behind = $80000037
    const long SA_Quiet = $80000038
    const long SA_AutoScroll = $80000039
    const long SA_Pens = $8000003a
    const long SA_FullPalette = $8000003b
    const long SA_ColorMapEntries = $8000003c
    const long SA_Parent = $8000003d
    const long SA_Draggable = $8000003e
    const long SA_Exclusive = $8000003f
    const long SA_SharePens = $80000040
    const long SA_BackFill = $80000041
    const long SA_Interleaved = $80000042
    const long SA_Colors32 = $80000043
    const long SA_VideoControl = $80000044
    const long SA_FrontChild = $80000045
    const long SA_BackChild = $80000046
    const long SA_LikeWorkbench = $80000047
    const long SA_Reserved = $80000048
    const long SA_MinimizeISG = $80000049
    const long SA_OffScreenDragging = $8000004a
    const ubyte OSERR_NOMONITOR = $0001
    const ubyte OSERR_NOCHIPS = $0002
    const ubyte OSERR_NOMEM = $0003
    const ubyte OSERR_NOCHIPMEM = $0004
    const ubyte OSERR_PUBNOTUNIQUE = $0005
    const ubyte OSERR_UNKNOWNMODE = $0006
    const ubyte OSERR_TOODEEP = $0007
    const ubyte OSERR_ATTACHFAIL = $0008
    const ubyte OSERR_NOTAVAILABLE = $0009
    const ubyte OSCAN_TEXT = $0001
    const ubyte OSCAN_STANDARD = $0002
    const ubyte OSCAN_MAX = $0003
    const ubyte OSCAN_VIDEO = $0004
    const ubyte PSNF_PRIVATE = $0001
    const ubyte MAXPUBSCREENNAME = $008b
    const ubyte SHANGHAI = $0001
    const ubyte POPPUBSCREEN = $0002
    const ubyte SDEPTH_TOFRONT = $0000
    const ubyte SDEPTH_TOBACK = $0001
    const ubyte SDEPTH_INFAMILY = $0002
    const ubyte SPOS_RELATIVE = $0000
    const ubyte SPOS_ABSOLUTE = $0001
    const ubyte SPOS_MAKEVISIBLE = $0002
    const ubyte SPOS_FORCEDRAG = $0004
    const ubyte SB_SCREEN_BITMAP = $0001
    const ubyte SB_COPY_BITMAP = $0002
    const ubyte EO_NOOP = $0001
    const ubyte EO_DELBACKWARD = $0002
    const ubyte EO_DELFORWARD = $0003
    const ubyte EO_MOVECURSOR = $0004
    const ubyte EO_ENTER = $0005
    const ubyte EO_RESET = $0006
    const ubyte EO_REPLACECHAR = $0007
    const ubyte EO_INSERTCHAR = $0008
    const ubyte EO_BADFORMAT = $0009
    const ubyte EO_BIGCHANGE = $000a
    const ubyte EO_UNDO = $000b
    const ubyte EO_CLEAR = $000c
    const ubyte EO_SPECIAL = $000d
    const ubyte SGM_REPLACE = $0001
    const ubyte SGMB_REPLACE = $0000
    const ubyte SGMF_REPLACE = $0001
    const ubyte SGM_FIXEDFIELD = $0002
    const ubyte SGMB_FIXEDFIELD = $0001
    const ubyte SGMF_FIXEDFIELD = $0002
    const ubyte SGM_NOFILTER = $0004
    const ubyte SGMB_NOFILTER = $0002
    const ubyte SGMF_NOFILTER = $0004
    const ubyte SGM_EXITHELP = $0080
    const ubyte SGMB_EXITHELP = $0007
    const ubyte SGMF_EXITHELP = $0080
    const ubyte SGA_USE = $0001
    const ubyte SGAB_USE = $0000
    const ubyte SGAF_USE = $0001
    const ubyte SGA_END = $0002
    const ubyte SGAB_END = $0001
    const ubyte SGAF_END = $0002
    const ubyte SGA_BEEP = $0004
    const ubyte SGAB_BEEP = $0002
    const ubyte SGAF_BEEP = $0004
    const ubyte SGA_REUSE = $0008
    const ubyte SGAB_REUSE = $0003
    const ubyte SGAF_REUSE = $0008
    const ubyte SGA_REDISPLAY = $0010
    const ubyte SGAB_REDISPLAY = $0004
    const ubyte SGAF_REDISPLAY = $0010
    const ubyte SGA_NEXTACTIVE = $0020
    const ubyte SGAB_NEXTACTIVE = $0005
    const ubyte SGAF_NEXTACTIVE = $0020
    const ubyte SGA_PREVACTIVE = $0040
    const ubyte SGAB_PREVACTIVE = $0006
    const ubyte SGAF_PREVACTIVE = $0040
    const ubyte SGH_KEY = $0001
    const ubyte SGH_CLICK = $0002
    const uword IECLASS_NULL = $0000
    const uword IECLASS_RAWKEY = $0001
    const uword IECLASS_RAWMOUSE = $0002
    const uword IECLASS_EVENT = $0003
    const uword IECLASS_POINTERPOS = $0004
    const uword IECLASS_TIMER = $0006
    const uword IECLASS_GADGETDOWN = $0007
    const uword IECLASS_GADGETUP = $0008
    const uword IECLASS_REQUESTER = $0009
    const uword IECLASS_MENULIST = $000a
    const uword IECLASS_CLOSEWINDOW = $000b
    const uword IECLASS_SIZEWINDOW = $000c
    const uword IECLASS_REFRESHWINDOW = $000d
    const uword IECLASS_NEWPREFS = $000e
    const uword IECLASS_DISKREMOVED = $000f
    const uword IECLASS_DISKINSERTED = $0010
    const uword IECLASS_ACTIVEWINDOW = $0011
    const uword IECLASS_INACTIVEWINDOW = $0012
    const uword IECLASS_NEWPOINTERPOS = $0013
    const uword IECLASS_MENUHELP = $0014
    const uword IECLASS_CHANGEWINDOW = $0015
    const uword IECLASS_MAX = $0015
    const uword IESUBCLASS_COMPATIBLE = $0000
    const uword IESUBCLASS_PIXEL = $0001
    const uword IESUBCLASS_TABLET = $0002
    const uword IESUBCLASS_NEWTABLET = $0003
    const uword IECODE_UP_PREFIX = $0080
    const uword IECODEB_UP_PREFIX = $0007
    const uword IECODE_KEY_CODE_FIRST = $0000
    const uword IECODE_KEY_CODE_LAST = $0077
    const uword IECODE_COMM_CODE_FIRST = $0078
    const uword IECODE_COMM_CODE_LAST = $007f
    const uword IECODE_C0_FIRST = $0000
    const uword IECODE_C0_LAST = $001f
    const uword IECODE_ASCII_FIRST = $0020
    const uword IECODE_ASCII_LAST = $007e
    const uword IECODE_ASCII_DEL = $007f
    const uword IECODE_C1_FIRST = $0080
    const uword IECODE_C1_LAST = $009f
    const uword IECODE_LATIN1_FIRST = $00a0
    const uword IECODE_LATIN1_LAST = $00ff
    const uword IECODE_LBUTTON = $0068
    const uword IECODE_RBUTTON = $0069
    const uword IECODE_MBUTTON = $006a
    const uword IECODE_NOBUTTON = $00ff
    const uword IECODE_NEWACTIVE = $0001
    const uword IECODE_NEWSIZE = $0002
    const uword IECODE_REFRESH = $0003
    const uword IECODE_REQSET = $0001
    const uword IECODE_REQCLEAR = $0000
    const uword IEQUALIFIER_LSHIFT = $0001
    const uword IEQUALIFIER_RSHIFT = $0002
    const uword IEQUALIFIER_CAPSLOCK = $0004
    const uword IEQUALIFIER_CONTROL = $0008
    const uword IEQUALIFIER_LALT = $0010
    const uword IEQUALIFIER_RALT = $0020
    const uword IEQUALIFIER_LCOMMAND = $0040
    const uword IEQUALIFIER_RCOMMAND = $0080
    const uword IEQUALIFIER_NUMERICPAD = $0100
    const uword IEQUALIFIER_REPEAT = $0200
    const uword IEQUALIFIER_INTERRUPT = $0400
    const uword IEQUALIFIER_MULTIBROADCAST = $0800
    const uword IEQUALIFIER_MIDBUTTON = $1000
    const uword IEQUALIFIER_RBUTTON = $2000
    const uword IEQUALIFIER_LEFTBUTTON = $4000
    const uword IEQUALIFIER_RELATIVEMOUSE = $8000
    const uword IEQUALIFIERB_LSHIFT = $0000
    const uword IEQUALIFIERB_RSHIFT = $0001
    const uword IEQUALIFIERB_CAPSLOCK = $0002
    const uword IEQUALIFIERB_CONTROL = $0003
    const uword IEQUALIFIERB_LALT = $0004
    const uword IEQUALIFIERB_RALT = $0005
    const uword IEQUALIFIERB_LCOMMAND = $0006
    const uword IEQUALIFIERB_RCOMMAND = $0007
    const uword IEQUALIFIERB_NUMERICPAD = $0008
    const uword IEQUALIFIERB_REPEAT = $0009
    const uword IEQUALIFIERB_INTERRUPT = $000a
    const uword IEQUALIFIERB_MULTIBROADCAST = $000b
    const uword IEQUALIFIERB_MIDBUTTON = $000c
    const uword IEQUALIFIERB_RBUTTON = $000d
    const uword IEQUALIFIERB_LEFTBUTTON = $000e
    const uword IEQUALIFIERB_RELATIVEMOUSE = $000f
    const long GA_Left = $80030001
    const long GA_RelRight = $80030002
    const long GA_Top = $80030003
    const long GA_RelBottom = $80030004
    const long GA_Width = $80030005
    const long GA_RelWidth = $80030006
    const long GA_Height = $80030007
    const long GA_RelHeight = $80030008
    const long GA_Text = $80030009
    const long GA_Image = $8003000a
    const long GA_Border = $8003000b
    const long GA_SelectRender = $8003000c
    const long GA_Highlight = $8003000d
    const long GA_Disabled = $8003000e
    const long GA_GZZGadget = $8003000f
    const long GA_ID = $80030010
    const long GA_UserData = $80030011
    const long GA_SpecialInfo = $80030012
    const long GA_Selected = $80030013
    const long GA_EndGadget = $80030014
    const long GA_Immediate = $80030015
    const long GA_RelVerify = $80030016
    const long GA_FollowMouse = $80030017
    const long GA_RightBorder = $80030018
    const long GA_LeftBorder = $80030019
    const long GA_TopBorder = $8003001a
    const long GA_BottomBorder = $8003001b
    const long GA_ToggleSelect = $8003001c
    const long GA_SysGadget = $8003001d
    const long GA_SysGType = $8003001e
    const long GA_Previous = $8003001f
    const long GA_Next = $80030020
    const long GA_DrawInfo = $80030021
    const long GA_IntuiText = $80030022
    const long GA_LabelImage = $80030023
    const long GA_TabCycle = $80030024
    const long GA_GadgetHelp = $80030025
    const long GA_Bounds = $80030026
    const long GA_RelSpecial = $80030027
    const long GA_TextAttr = $80030028
    const long GA_ReadOnly = $80030029
    const long GA_Underscore = $8003002a
    const long GA_ActivateKey = $8003002b
    const long GA_BackFill = $8003002c
    const long GA_GadgetHelpText = $8003002d
    const long GA_UserInput = $8003002e
    const long PGA_Freedom = $80031001
    const long PGA_Borderless = $80031002
    const long PGA_HorizPot = $80031003
    const long PGA_HorizBody = $80031004
    const long PGA_VertPot = $80031005
    const long PGA_VertBody = $80031006
    const long PGA_Total = $80031007
    const long PGA_Visible = $80031008
    const long PGA_Top = $80031009
    const long PGA_NewLook = $8003100a
    const long PGA_KnobImage = $8003100d
    const long STRINGA_MaxChars = $80032001
    const long STRINGA_Buffer = $80032002
    const long STRINGA_UndoBuffer = $80032003
    const long STRINGA_WorkBuffer = $80032004
    const long STRINGA_BufferPos = $80032005
    const long STRINGA_DispPos = $80032006
    const long STRINGA_AltKeyMap = $80032007
    const long STRINGA_Font = $80032008
    const long STRINGA_Pens = $80032009
    const long STRINGA_ActivePens = $8003200a
    const long STRINGA_EditHook = $8003200b
    const long STRINGA_EditModes = $8003200c
    const long STRINGA_ReplaceMode = $8003200d
    const long STRINGA_FixedFieldMode = $8003200e
    const long STRINGA_NoFilterMode = $8003200f
    const long STRINGA_Justification = $80032010
    const long STRINGA_LongVal = $80032011
    const long STRINGA_TextVal = $80032012
    const long STRINGA_ExitHelp = $80032013
    const long LAYOUTA_LayoutObj = $80038001
    const long LAYOUTA_Spacing = $80038002
    const long LAYOUTA_Orientation = $80038003
    const long LAYOUTA_ChildMaxWidth = $80038004
    const long LAYOUTA_ChildMaxHeight = $80038005
    const long ICA_TARGET = $80040001
    const long ICA_MAP = $80040002
    const long ICSPECIAL_CODE = $80040003
    const long IA_Left = $80020001
    const long IA_Top = $80020002
    const long IA_Width = $80020003
    const long IA_Height = $80020004
    const long IA_FGPen = $80020005
    const long IA_BGPen = $80020006
    const long IA_Data = $80020007
    const long IA_LineWidth = $80020008
    const long IA_Pens = $8002000e
    const long IA_Resolution = $8002000f
    const long IA_APattern = $80020010
    const long IA_APatSize = $80020011
    const long IA_Mode = $80020012
    const long IA_Font = $80020013
    const long IA_Outline = $80020014
    const long IA_Recessed = $80020015
    const long IA_DoubleEmboss = $80020016
    const long IA_EdgesOnly = $80020017
    const long SYSIA_Size = $8002000b
    const long SYSIA_Depth = $8002000c
    const long SYSIA_Which = $8002000d
    const long SYSIA_DrawInfo = $80020018
    const long SYSIA_Pens = $8002000e
    const long IA_ShadowPen = $80020009
    const long IA_HighlightPen = $8002000a
    const long SYSIA_ReferenceFont = $80020019
    const long IA_SupportsDisable = $8002001a
    const long IA_FrameType = $8002001b
    const long IA_Underscore = $8002001c
    const long IA_Scalable = $8002001d
    const long IA_ActivateKey = $8002001e
    const long IA_Screen = $8002001f
    const long IA_Precision = $80020020
    const long IA_Orientation = $80020023
    const long IA_Label = $80020028
    const long IA_EraseBackground = $8002002d
    const long IA_LabelPen = $80020038
    const ubyte IDS_INDETERMINANT = $0004
    const long GTYP_WUPFRONT = $0040
    const long GTYP_SUPFRONT = $0050
    const long GTYP_WDOWNBACK = $0060
    const long GTYP_SDOWNBACK = $0070
    const ubyte OKOK = $0001
    const ubyte OKCANCEL = $0002
    const uword SELECTUP = $00e8
    const uword SELECTDOWN = $0068
    const uword MENUUP = $00e9
    const uword MENUDOWN = $0069
    const uword MIDDLEUP = $00ea
    const uword MIDDLEDOWN = $006a
    const ubyte ALTLEFT = $0010
    const ubyte ALTRIGHT = $0020
    const ubyte AMIGALEFT = $0040
    const ubyte AMIGARIGHT = $0080
    const ubyte AMIGAKEYS = $00c0
    const long TABLETA_TabletZ = $8003a001
    const long TABLETA_RangeZ = $8003a002
    const long TABLETA_AngleX = $8003a003
    const long TABLETA_AngleY = $8003a004
    const long TABLETA_AngleZ = $8003a005
    const long TABLETA_Pressure = $8003a006
    const long TABLETA_ButtonBits = $8003a007
    const long TABLETA_InProximity = $8003a008
    const long TABLETA_ResolutionX = $8003a009
    const long TABLETA_ResolutionY = $8003a00a
    const long POINTERA_BitMap = $80039001
    const long POINTERA_XOffset = $80039002
    const long POINTERA_YOffset = $80039003
    const long POINTERA_WordWidth = $80039004
    const long POINTERA_XResolution = $80039005
    const long POINTERA_YResolution = $80039006
    const ubyte CORRECT_RGB_MASK = $0007
    const ubyte DIMENSIONS_MASK = $00f0
    const uword DITHERING_MASK = $0600
    const ubyte SDEPTH_CHILDONLY = $0002
}
;; End of auto-generated intuition_lib.sfd
