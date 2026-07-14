;; Auto-generated from graphics_lib.sfd and graphics_lib.i
;; Library base: _GfxBase  in prog8: sys.GfxBase
;; Bank: 3
;; Functions: 163

graphics {
    extsub @bank 3   -30 = BltBitMap(pointer srcBitMap @A0, word xSrc @D0, word ySrc @D1, pointer destBitMap @A1, word xDest @D2, word yDest @D3, word xSize @D4, word ySize @D5, ubyte minterm @D6, ubyte mask @D7, pointer tempA @A2) -> long @D0
    extsub @bank 3   -36 = BltTemplate(long source @A0, word xSrc @D0, word srcMod @D1, pointer destRP @A1, word xDest @D2, word yDest @D3, word xSize @D4, word ySize @D5)
    extsub @bank 3   -42 = ClearEOL(pointer rp @A1)
    extsub @bank 3   -48 = ClearScreen(pointer rp @A1)
    extsub @bank 3   -54 = TextLength(pointer rp @A1, str k_string @A0, uword count @D0) -> word @D0
    extsub @bank 3   -60 = Text(pointer rp @A1, str k_string @A0, uword count @D0) -> long @D0
    extsub @bank 3   -66 = SetFont(pointer rp @A1, pointer textFont @A0) -> long @D0
    extsub @bank 3   -72 = OpenFont(pointer textAttr @A0) -> pointer @D0
    extsub @bank 3   -78 = CloseFont(pointer textFont @A1)
    extsub @bank 3   -84 = AskSoftStyle(pointer rp @A1) -> long @D0
    extsub @bank 3   -90 = SetSoftStyle(pointer rp @A1, long style @D0, long enable @D1) -> long @D0
    extsub @bank 3   -96 = AddBob(pointer bob @A0, pointer rp @A1)
    extsub @bank 3   -102 = AddVSprite(pointer vSprite @A0, pointer rp @A1)
    extsub @bank 3   -108 = DoCollision(pointer rp @A1)
    extsub @bank 3   -114 = DrawGList(pointer rp @A1, pointer vp @A0)
    extsub @bank 3   -120 = InitGels(pointer head @A0, pointer tail @A1, pointer gelsInfo @A2)
    extsub @bank 3   -126 = InitMasks(pointer vSprite @A0)
    extsub @bank 3   -132 = RemIBob(pointer bob @A0, pointer rp @A1, pointer vp @A2)
    extsub @bank 3   -138 = RemVSprite(pointer vSprite @A0)
    extsub @bank 3   -144 = SetCollision(long num @D0, pointer routine @A0, pointer gelsInfo @A1)
    extsub @bank 3   -150 = SortGList(pointer rp @A1)
    extsub @bank 3   -156 = AddAnimOb(pointer anOb @A0, pointer anKey @A1, pointer rp @A2)
    extsub @bank 3   -162 = Animate(pointer anKey @A0, pointer rp @A1)
    extsub @bank 3   -168 = GetGBuffers(pointer anOb @A0, pointer rp @A1, bool flag @D0) -> bool @D0
    extsub @bank 3   -174 = InitGMasks(pointer anOb @A0)
    extsub @bank 3   -180 = DrawEllipse(pointer rp @A1, word xCenter @D0, word yCenter @D1, word a @D2, word b @D3)
    extsub @bank 3   -186 = AreaEllipse(pointer rp @A1, word xCenter @D0, word yCenter @D1, word a @D2, word b @D3) -> long @D0
    extsub @bank 3   -192 = LoadRGB4(pointer vp @A0, pointer colors @A1, word count @D0)
    extsub @bank 3   -198 = InitRastPort(pointer rp @A1)
    extsub @bank 3   -204 = InitVPort(pointer vp @A0)
    extsub @bank 3   -210 = MrgCop(pointer view @A1) -> long @D0
    extsub @bank 3   -216 = MakeVPort(pointer view @A0, pointer vp @A1) -> long @D0
    extsub @bank 3   -222 = LoadView(pointer view @A1)
    extsub @bank 3   -228 = WaitBlit()
    extsub @bank 3   -234 = SetRast(pointer rp @A1, ubyte pen @D0)
    extsub @bank 3   -240 = Move(pointer rp @A1, word x @D0, word y @D1)
    extsub @bank 3   -246 = Draw(pointer rp @A1, word x @D0, word y @D1)
    extsub @bank 3   -252 = AreaMove(pointer rp @A1, word x @D0, word y @D1) -> long @D0
    extsub @bank 3   -258 = AreaDraw(pointer rp @A1, word x @D0, word y @D1) -> long @D0
    extsub @bank 3   -264 = AreaEnd(pointer rp @A1) -> long @D0
    extsub @bank 3   -270 = WaitTOF()
    extsub @bank 3   -276 = QBlit(pointer blit @A1)
    extsub @bank 3   -282 = InitArea(pointer areaInfo @A0, pointer vectorBuffer @A1, word maxVectors @D0)
    extsub @bank 3   -288 = SetRGB4(pointer vp @A0, word index @D0, ubyte red @D1, ubyte green @D2, ubyte blue @D3)
    extsub @bank 3   -294 = QBSBlit(pointer blit @A1)
    extsub @bank 3   -300 = BltClear(pointer memBlock @A1, long byteCount @D0, long flags @D1)
    extsub @bank 3   -306 = RectFill(pointer rp @A1, word xMin @D0, word yMin @D1, word xMax @D2, word yMax @D3)
    extsub @bank 3   -312 = BltPattern(pointer rp @A1, long mask @A0, word xMin @D0, word yMin @D1, word xMax @D2, word yMax @D3, uword maskBPR @D4)
    extsub @bank 3   -318 = ReadPixel(pointer rp @A1, word x @D0, word y @D1) -> long @D0
    extsub @bank 3   -324 = WritePixel(pointer rp @A1, word x @D0, word y @D1) -> long @D0
    extsub @bank 3   -330 = Flood(pointer rp @A1, long mode @D2, word x @D0, word y @D1) -> bool @D0
    extsub @bank 3   -336 = PolyDraw(pointer rp @A1, word count @D0, pointer polyTable @A0)
    extsub @bank 3   -342 = SetAPen(pointer rp @A1, ubyte pen @D0)
    extsub @bank 3   -348 = SetBPen(pointer rp @A1, ubyte pen @D0)
    extsub @bank 3   -354 = SetDrMd(pointer rp @A1, ubyte drawMode @D0)
    extsub @bank 3   -360 = InitView(pointer view @A1)
    extsub @bank 3   -366 = CBump(pointer copList @A1)
    extsub @bank 3   -372 = CMove(pointer copList @A1, pointer destination @D0, word data @D1) -> long @D0
    extsub @bank 3   -378 = CWait(pointer copList @A1, word v @D0, word h @D1) -> long @D0
    extsub @bank 3   -384 = VBeamPos() -> long @D0
    extsub @bank 3   -390 = InitBitMap(pointer bitMap @A0, byte depth @D0, word width @D1, word height @D2)
    extsub @bank 3   -396 = ScrollRaster(pointer rp @A1, word dx @D0, word dy @D1, word xMin @D2, word yMin @D3, word xMax @D4, word yMax @D5)
    extsub @bank 3   -402 = WaitBOVP(pointer vp @A0)
    extsub @bank 3   -408 = GetSprite(pointer sprite @A0, word num @D0) -> word @D0
    extsub @bank 3   -414 = FreeSprite(word num @D0)
    extsub @bank 3   -420 = ChangeSprite(pointer vp @A0, pointer sprite @A1, uword newData @A2)
    extsub @bank 3   -426 = MoveSprite(pointer vp @A0, pointer sprite @A1, word x @D0, word y @D1)
    extsub @bank 3   -432 = LockLayerRom(pointer layer @A5)
    extsub @bank 3   -438 = UnlockLayerRom(pointer layer @A5)
    extsub @bank 3   -444 = SyncSBitMap(pointer layer @A0)
    extsub @bank 3   -450 = CopySBitMap(pointer layer @A0)
    extsub @bank 3   -456 = OwnBlitter()
    extsub @bank 3   -462 = DisownBlitter()
    extsub @bank 3   -468 = InitTmpRas(pointer tmpRas @A0, pointer buffer @A1, long size @D0) -> pointer @D0
    extsub @bank 3   -474 = AskFont(pointer rp @A1, pointer textAttr @A0)
    extsub @bank 3   -480 = AddFont(pointer textFont @A1)
    extsub @bank 3   -486 = RemFont(pointer textFont @A1)
    extsub @bank 3   -492 = AllocRaster(uword width @D0, uword height @D1) -> pointer @D0
    extsub @bank 3   -498 = FreeRaster(pointer p @A0, uword width @D0, uword height @D1)
    extsub @bank 3   -504 = AndRectRegion(pointer region @A0, pointer rectangle @A1)
    extsub @bank 3   -510 = OrRectRegion(pointer region @A0, pointer rectangle @A1) -> bool @D0
    extsub @bank 3   -516 = NewRegion() -> pointer @D0
    extsub @bank 3   -522 = ClearRectRegion(pointer region @A0, pointer rectangle @A1) -> bool @D0
    extsub @bank 3   -528 = ClearRegion(pointer region @A0)
    extsub @bank 3   -534 = DisposeRegion(pointer region @A0)
    extsub @bank 3   -540 = FreeVPortCopLists(pointer vp @A0)
    extsub @bank 3   -546 = FreeCopList(pointer copList @A0)
    extsub @bank 3   -552 = ClipBlit(pointer srcRP @A0, word xSrc @D0, word ySrc @D1, pointer destRP @A1, word xDest @D2, word yDest @D3, word xSize @D4, word ySize @D5, ubyte minterm @D6)
    extsub @bank 3   -558 = XorRectRegion(pointer region @A0, pointer rectangle @A1) -> bool @D0
    extsub @bank 3   -564 = FreeCprList(pointer cprList @A0)
    extsub @bank 3   -570 = GetColorMap(long entries @D0) -> pointer @D0
    extsub @bank 3   -576 = FreeColorMap(pointer colorMap @A0)
    extsub @bank 3   -582 = GetRGB4(pointer colorMap @A0, long entry @D0) -> long @D0
    extsub @bank 3   -588 = ScrollVPort(pointer vp @A0)
    extsub @bank 3   -594 = UCopperListInit(pointer uCopList @A0, word n @D0) -> pointer @D0
    extsub @bank 3   -600 = FreeGBuffers(pointer anOb @A0, pointer rp @A1, bool flag @D0)
    extsub @bank 3   -606 = BltBitMapRastPort(pointer srcBitMap @A0, word xSrc @D0, word ySrc @D1, pointer destRP @A1, word xDest @D2, word yDest @D3, word xSize @D4, word ySize @D5, ubyte minterm @D6)
    extsub @bank 3   -612 = OrRegionRegion(pointer srcRegion @A0, pointer destRegion @A1) -> bool @D0
    extsub @bank 3   -618 = XorRegionRegion(pointer srcRegion @A0, pointer destRegion @A1) -> bool @D0
    extsub @bank 3   -624 = AndRegionRegion(pointer srcRegion @A0, pointer destRegion @A1) -> bool @D0
    extsub @bank 3   -630 = SetRGB4CM(pointer colorMap @A0, word index @D0, ubyte red @D1, ubyte green @D2, ubyte blue @D3)
    extsub @bank 3   -636 = BltMaskBitMapRastPort(pointer srcBitMap @A0, word xSrc @D0, word ySrc @D1, pointer destRP @A1, word xDest @D2, word yDest @D3, word xSize @D4, word ySize @D5, ubyte minterm @D6, long bltMask @A2)
    extsub @bank 3   -654 = AttemptLockLayerRom(pointer layer @A5) -> bool @D0
    extsub @bank 3   -660 = GfxNew(long gfxNodeType @D0) -> pointer @D0
    extsub @bank 3   -666 = GfxFree(pointer gfxNodePtr @A0)
    extsub @bank 3   -672 = GfxAssociate(pointer associateNode @A0, pointer gfxNodePtr @A1)
    extsub @bank 3   -678 = BitMapScale(pointer bitScaleArgs @A0)
    extsub @bank 3   -684 = ScalerDiv(uword factor @D0, uword numerator @D1, uword denominator @D2) -> uword @D0
    extsub @bank 3   -690 = TextExtent(pointer rp @A1, str k_string @A0, word count @D0, pointer textExtent @A2) -> word @D0
    extsub @bank 3   -696 = TextFit(pointer rp @A1, str k_string @A0, uword strLen @D0, pointer textExtent @A2, pointer constrainingExtent @A3, word strDirection @D1, uword constrainingBitWidth @D2, uword constrainingBitHeight @D3) -> long @D0
    extsub @bank 3   -702 = GfxLookUp(pointer associateNode @A0) -> pointer @D0
    extsub @bank 3   -708 = VideoControl(pointer colorMap @A0, pointer tagarray @A1) -> bool @D0
    extsub @bank 3   -714 = OpenMonitor(str monitorName @A1, long displayID @D0) -> pointer @D0
    extsub @bank 3   -720 = CloseMonitor(pointer monitorSpec @A0) -> bool @D0
    extsub @bank 3   -726 = FindDisplayInfo(long displayID @D0) -> pointer @D0
    extsub @bank 3   -732 = NextDisplayInfo(long displayID @D0) -> long @D0
    extsub @bank 3   -756 = GetDisplayInfoData(pointer handle @A0, pointer buf @A1, long size @D0, long tagID @D1, long displayID @D2) -> long @D0
    extsub @bank 3   -762 = FontExtent(pointer font @A0, pointer fontExtent @A1)
    extsub @bank 3   -768 = ReadPixelLine8(pointer rp @A0, uword xstart @D0, uword ystart @D1, uword width @D2, ubyte array @A2, pointer tempRP @A1) -> long @D0
    extsub @bank 3   -774 = WritePixelLine8(pointer rp @A0, uword xstart @D0, uword ystart @D1, uword width @D2, ubyte array @A2, pointer tempRP @A1) -> long @D0
    extsub @bank 3   -780 = ReadPixelArray8(pointer rp @A0, uword xstart @D0, uword ystart @D1, uword xstop @D2, uword ystop @D3, ubyte array @A2, pointer temprp @A1) -> long @D0
    extsub @bank 3   -786 = WritePixelArray8(pointer rp @A0, uword xstart @D0, uword ystart @D1, uword xstop @D2, uword ystop @D3, ubyte array @A2, pointer temprp @A1) -> long @D0
    extsub @bank 3   -792 = GetVPModeID(pointer vp @A0) -> long @D0
    extsub @bank 3   -798 = ModeNotAvailable(long modeID @D0) -> long @D0
    extsub @bank 3   -804 = WeighTAMatch(pointer reqTextAttr @A0, pointer targetTextAttr @A1, pointer targetTags @A2) -> word @D0
    extsub @bank 3   -810 = EraseRect(pointer rp @A1, word xMin @D0, word yMin @D1, word xMax @D2, word yMax @D3)
    extsub @bank 3   -816 = ExtendFont(pointer font @A0, pointer fontTags @A1) -> long @D0
    extsub @bank 3   -822 = StripFont(pointer font @A0)
    extsub @bank 3   -828 = CalcIVG(pointer v @A0, pointer vp @A1) -> uword @D0
    extsub @bank 3   -834 = AttachPalExtra(pointer cm @A0, pointer vp @A1) -> long @D0
    extsub @bank 3   -840 = ObtainBestPenA(pointer cm @A0, long r @D1, long g @D2, long b @D3, pointer tags @A1) -> long @D0
    extsub @bank 3   -852 = SetRGB32(pointer vp @A0, long n @D0, long r @D1, long g @D2, long b @D3)
    extsub @bank 3   -858 = GetAPen(pointer rp @A0) -> long @D0
    extsub @bank 3   -864 = GetBPen(pointer rp @A0) -> long @D0
    extsub @bank 3   -870 = GetDrMd(pointer rp @A0) -> long @D0
    extsub @bank 3   -876 = GetOutlinePen(pointer rp @A0) -> long @D0
    extsub @bank 3   -882 = LoadRGB32(pointer vp @A0, pointer table @A1)
    extsub @bank 3   -888 = SetChipRev(long want @D0) -> long @D0
    extsub @bank 3   -894 = SetABPenDrMd(pointer rp @A1, long apen @D0, long bpen @D1, long drawmode @D2)
    extsub @bank 3   -900 = GetRGB32(pointer cm @A0, long firstcolor @D0, long ncolors @D1, long table @A1)
    extsub @bank 3   -918 = AllocBitMap(long sizex @D0, long sizey @D1, long depth @D2, long flags @D3, pointer friend_bitmap @A0) -> pointer @D0
    extsub @bank 3   -924 = FreeBitMap(pointer bm @A0)
    extsub @bank 3   -930 = GetExtSpriteA(pointer ss @A2, pointer tags @A1) -> long @D0
    extsub @bank 3   -936 = CoerceMode(pointer vp @A0, long monitorid @D0, long flags @D1) -> long @D0
    extsub @bank 3   -942 = ChangeVPBitMap(pointer vp @A0, pointer bm @A1, pointer db @A2)
    extsub @bank 3   -948 = ReleasePen(pointer cm @A0, long n @D0)
    extsub @bank 3   -954 = ObtainPen(pointer cm @A0, long n @D0, long r @D1, long g @D2, long b @D3, long f @D4) -> long @D0
    extsub @bank 3   -960 = GetBitMapAttr(pointer bm @A0, long attrnum @D1) -> long @D0
    extsub @bank 3   -966 = AllocDBufInfo(pointer vp @A0) -> pointer @D0
    extsub @bank 3   -972 = FreeDBufInfo(pointer dbi @A1)
    extsub @bank 3   -978 = SetOutlinePen(pointer rp @A0, long pen @D0) -> long @D0
    extsub @bank 3   -984 = SetWriteMask(pointer rp @A0, long msk @D0) -> long @D0
    extsub @bank 3   -990 = SetMaxPen(pointer rp @A0, long maxpen @D0)
    extsub @bank 3   -996 = SetRGB32CM(pointer cm @A0, long n @D0, long r @D1, long g @D2, long b @D3)
    extsub @bank 3   -1002 = ScrollRasterBF(pointer rp @A1, word dx @D0, word dy @D1, word xMin @D2, word yMin @D3, word xMax @D4, word yMax @D5)
    extsub @bank 3   -1008 = FindColor(pointer cm @A3, long r @D1, long g @D2, long b @D3, long maxcolor @D4) -> long @D0
    extsub @bank 3   -1020 = AllocSpriteDataA(pointer bm @A2, pointer tags @A1) -> pointer @D0
    extsub @bank 3   -1026 = ChangeExtSpriteA(pointer vp @A0, pointer oldsprite @A1, pointer newsprite @A2, pointer tags @A3) -> long @D0
    extsub @bank 3   -1032 = FreeSpriteData(pointer sp @A2)
    extsub @bank 3   -1038 = SetRPAttrsA(pointer rp @A0, pointer tags @A1)
    extsub @bank 3   -1044 = GetRPAttrsA(pointer rp @A0, pointer tags @A1)
    extsub @bank 3   -1050 = BestModeIDA(pointer tags @A0) -> long @D0
    extsub @bank 3   -1056 = WriteChunkyPixels(pointer rp @A0, uword xstart @D0, uword ystart @D1, uword xstop @D2, uword ystop @D3, ubyte array @A2, long bytesperrow @D4)

    ; ---- struct definitions ----

    struct AreaInfo {  ; total size: 24
        pointer VctrTbl  ; 0
        pointer VctrPtr  ; 4
        pointer FlagTbl  ; 8
        pointer FlagPtr  ; 12
        word Count  ; 16
        word MaxCount  ; 18
        word FirstX  ; 20
        word FirstY  ; 22
    }

    struct BitMap {  ; total size: 40
        uword BytesPerRow  ; 0
        uword Rows  ; 2
        ubyte Flags  ; 4
        ubyte Depth  ; 5
        uword Pad  ; 6
        pointer[8] Planes  ; 8
    }

    struct ClipRect {  ; total size: 36
        pointer Next  ; 0
        pointer Reservedlink  ; 4
        long Obscured  ; 8
        pointer BitMap  ; 12
        ubyte[8] emb_Bounds  ; 16
        pointer Vlink  ; 24
        pointer Home  ; 28
        pointer Reserved  ; 32
    }

    struct ColorMap {  ; total size: 52
        ubyte Flags  ; 0
        ubyte Type  ; 1
        uword Count  ; 2
        pointer ColorTable  ; 4
        pointer Vpe  ; 8
        pointer LowColorBits  ; 12
        ubyte TransparencyPlane  ; 16
        ubyte SpriteResolution  ; 17
        ubyte SpriteResDefault  ; 18
        ubyte AuxFlags  ; 19
        pointer Vp  ; 20
        pointer NormalDisplayInfo  ; 24
        pointer CoerceDisplayInfo  ; 28
        pointer Batch_items  ; 32
        long VPModeID  ; 36
        pointer PalExtra  ; 40
        uword Even  ; 44
        uword Odd  ; 46
        uword Bp_0_base  ; 48
        uword Bp_1_base  ; 50
    }

    struct Layer {  ; total size: 160
        pointer Front  ; 0
        pointer Back  ; 4
        pointer ClipRect  ; 8
        pointer Rp  ; 12
        ubyte[8] emb_Bounds  ; 16
        pointer Nlink  ; 24
        uword Priority  ; 28
        uword Flags  ; 30
        pointer SuperBitMap  ; 32
        pointer SuperClipRect  ; 36
        pointer Window  ; 40
        word X  ; 44
        word Y  ; 46
        pointer OnScreen  ; 48
        pointer OffScreen  ; 52
        pointer Backup  ; 56
        pointer SuperSaveClipRects  ; 60
        pointer Undamaged  ; 64
        pointer LayerInfo  ; 68
        ubyte[46] emb_Lock  ; 72
        pointer BackFill  ; 118
        long Reserved1  ; 122
        pointer ClipRegion  ; 126
        pointer Clipped  ; 130
        word Width  ; 134
        word Height  ; 136
        ubyte[18] Reserved2  ; 138
        pointer DamageList  ; 156
    }

    struct RastPort {  ; total size: 100
        pointer Layer  ; 0
        pointer BitMap  ; 4
        pointer AreaPtrn  ; 8
        pointer TmpRas  ; 12
        pointer AreaInfo  ; 16
        pointer GelsInfo  ; 20
        ubyte Mask  ; 24
        byte FgPen  ; 25
        byte BgPen  ; 26
        byte AOlPen  ; 27
        byte DrawMode  ; 28
        byte AreaPtSz  ; 29
        byte Linpatcnt  ; 30
        byte Dummy  ; 31
        uword Flags  ; 32
        uword LinePtrn  ; 34
        word X  ; 36
        word Y  ; 38
        ubyte[8] Minterms  ; 40
        word PenWidth  ; 48
        word PenHeight  ; 50
        pointer Font  ; 52
        ubyte AlgoStyle  ; 56
        ubyte TxFlags  ; 57
        uword TxHeight  ; 58
        uword TxWidth  ; 60
        uword TxBaseline  ; 62
        word TxSpacing  ; 64
        pointer User  ; 66
        long[2] Longreserved  ; 70
        uword[7] Wordreserved  ; 78
        ubyte[8] Reserved  ; 92
    }

    struct TextAttr {  ; total size: 8
        str Name  ; 0
        uword YSize  ; 4
        ubyte Style  ; 6
        ubyte Flags  ; 7
    }

    struct TextExtent {  ; total size: 12
        uword Width  ; 0
        uword Height  ; 2
        ubyte[8] emb_Extent  ; 4
    }

    struct TextFont {  ; total size: 52
        pointer Succ  ; 0
        pointer Pred  ; 4
        ubyte Type  ; 8
        byte Pri  ; 9
        str Name  ; 10
        pointer ReplyPort  ; 14
        uword Length  ; 18
        uword YSize  ; 20
        ubyte Style  ; 22
        ubyte Flags  ; 23
        uword XSize  ; 24
        uword Baseline  ; 26
        uword BoldSmear  ; 28
        uword Accessors  ; 30
        ubyte LoChar  ; 32
        ubyte HiChar  ; 33
        pointer CharData  ; 34
        uword Modulo  ; 38
        pointer CharLoc  ; 40
        pointer CharSpace  ; 44
        pointer CharKern  ; 48
    }

    struct TmpRas {  ; total size: 8
        pointer RasPtr  ; 0
        long Size  ; 4
    }

    struct View {  ; total size: 18
        pointer ViewPort  ; 0
        pointer LOFCprList  ; 4
        pointer SHFCprList  ; 8
        word DyOffset  ; 12
        word DxOffset  ; 14
        uword Modes  ; 16
    }

    ; ---- constants ----
    const ubyte PRESERVE_COLORS = $0001
    const ubyte AVOID_FLICKER = $0002
    const ubyte IGNORE_MCOMPAT = $0004
    const ubyte EXACT_LINE = $0001
    const ubyte HALF_LINE = $0002
    const ubyte SUSERFLAGS = $00ff
    const ubyte VSB_VSPRITE = 0
    const ubyte VSF_VSPRITE = $0001
    const ubyte VSB_SAVEBACK = 1
    const ubyte VSF_SAVEBACK = $0002
    const ubyte VSB_OVERLAY = 2
    const ubyte VSF_OVERLAY = $0004
    const ubyte VSB_MUSTDRAW = 3
    const ubyte VSF_MUSTDRAW = $0008
    const ubyte VSB_BACKSAVED = 8
    const uword VSF_BACKSAVED = $0100
    const ubyte VSB_BOBUPDATE = 9
    const uword VSF_BOBUPDATE = $0200
    const ubyte VSB_GELGONE = 10
    const uword VSF_GELGONE = $0400
    const ubyte VSB_VSOVERFLOW = 11
    const uword VSF_VSOVERFLOW = $0800
    const ubyte BUSERFLAGS = $00ff
    const ubyte BB_SAVEBOB = 0
    const ubyte BF_SAVEBOB = $0001
    const ubyte BB_BOBISCOMP = 1
    const ubyte BF_BOBISCOMP = $0002
    const ubyte BB_BWAITING = 8
    const uword BF_BWAITING = $0100
    const ubyte BB_BDRAWN = 9
    const uword BF_BDRAWN = $0200
    const ubyte BB_BOBSAWAY = 10
    const uword BF_BOBSAWAY = $0400
    const ubyte BB_BOBNIX = 11
    const uword BF_BOBNIX = $0800
    const ubyte BB_SAVEPRESERVE = 12
    const uword BF_SAVEPRESERVE = $1000
    const ubyte BB_OUTSTEP = 13
    const uword BF_OUTSTEP = $2000
    const ubyte ANFRACSIZE = $0006
    const ubyte ANIMHALF = $0020
    const ubyte RINGTRIGGER = $0001
    const ubyte BMB_CLEAR = 0
    const ubyte BMF_CLEAR = $0001
    const ubyte BMB_DISPLAYABLE = 1
    const ubyte BMF_DISPLAYABLE = $0002
    const ubyte BMB_INTERLEAVED = 2
    const ubyte BMF_INTERLEAVED = $0004
    const ubyte BMB_STANDARD = 3
    const ubyte BMF_STANDARD = $0008
    const ubyte BMB_MINPLANES = 4
    const ubyte BMF_MINPLANES = $0010
    const ubyte BMB_HIJACKED = 7
    const ubyte BMF_HIJACKED = $0080
    const ubyte BMB_RTGTAGS = 8
    const uword BMF_RTGTAGS = $0100
    const ubyte BMB_RTGCHECK = 9
    const uword BMF_RTGCHECK = $0200
    const ubyte BMB_FRIENDISTAG = 10
    const uword BMF_FRIENDISTAG = $0400
    const ubyte BMB_INVALID = 11
    const uword BMF_INVALID = $0800
    const ubyte GBFLAGSB_TIMER = 6
    const ubyte GBFLAGSF_TIMER = $0040
    const ubyte GBFLAGSB_LASTBLIT = 7
    const ubyte GBFLAGSF_LASTBLIT = $0080
    const ubyte GFXB_BIG_BLITS = 0
    const ubyte GFXF_BIG_BLITS = $0001
    const ubyte GFXB_HR_AGNUS = 0
    const ubyte GFXF_HR_AGNUS = $0001
    const ubyte GFXB_HR_DENISE = 1
    const ubyte GFXF_HR_DENISE = $0002
    const ubyte GFXB_AA_ALICE = 2
    const ubyte GFXF_AA_ALICE = $0004
    const ubyte GFXB_AA_LISA = 3
    const ubyte GFXF_AA_LISA = $0008
    const ubyte GFXB_AA_MLISA = 4
    const ubyte GFXF_AA_MLISA = $0010
    const ubyte GRAPHICS_GFXNODES_I = $0001
    const ubyte SS_GRAPHICS = $0002
    const ubyte VIEW_EXTRA_TYPE = $0001
    const ubyte VIEWPORT_EXTRA_TYPE = $0002
    const ubyte SPECIAL_MONITOR_TYPE = $0003
    const ubyte MONITOR_SPEC_TYPE = $0004
    const long MONITOR_ID_MASK = $ffff1000
    const ubyte DEFAULT_MONITOR_ID = $0000
    const long NTSC_MONITOR_ID = $00011000
    const long PAL_MONITOR_ID = $00021000
    const ubyte LORES_KEY = $0000
    const uword HIRES_KEY = $8000
    const uword SUPER_KEY = $8020
    const uword HAM_KEY = $0800
    const ubyte LORESLACE_KEY = $0004
    const uword HIRESLACE_KEY = $8004
    const uword SUPERLACE_KEY = $8024
    const uword HAMLACE_KEY = $0804
    const uword LORESDPF_KEY = $0400
    const uword HIRESDPF_KEY = $8400
    const uword SUPERDPF_KEY = $8420
    const uword LORESLACEDPF_KEY = $0404
    const uword HIRESLACEDPF_KEY = $8404
    const uword SUPERLACEDPF_KEY = $8424
    const uword LORESDPF2_KEY = $0440
    const uword HIRESDPF2_KEY = $8440
    const uword SUPERDPF2_KEY = $8460
    const uword LORESLACEDPF2_KEY = $0444
    const uword HIRESLACEDPF2_KEY = $8444
    const uword SUPERLACEDPF2_KEY = $8464
    const ubyte EXTRAHALFBRITE_KEY = $0080
    const ubyte EXTRAHALFBRITELACE_KEY = $0084
    const uword HIRESHAM_KEY = $8800
    const uword SUPERHAM_KEY = $8820
    const uword HIRESEHB_KEY = $8080
    const uword SUPEREHB_KEY = $80a0
    const uword HIRESHAMLACE_KEY = $8804
    const uword SUPERHAMLACE_KEY = $8824
    const uword HIRESEHBLACE_KEY = $8084
    const uword SUPEREHBLACE_KEY = $80a4
    const ubyte LORESSDBL_KEY = $0008
    const uword LORESHAMSDBL_KEY = $0808
    const ubyte LORESEHBSDBL_KEY = $0088
    const uword HIRESHAMSDBL_KEY = $8808
    const long VGA_MONITOR_ID = $00031000
    const long VGAEXTRALORES_KEY = $00031004
    const long VGALORES_KEY = $00039004
    const long VGAPRODUCT_KEY = $00039024
    const long VGAHAM_KEY = $00031804
    const long VGAEXTRALORESLACE_KEY = $00031005
    const long VGALORESLACE_KEY = $00039005
    const long VGAPRODUCTLACE_KEY = $00039025
    const long VGAHAMLACE_KEY = $00031805
    const long VGAEXTRALORESDPF_KEY = $00031404
    const long VGALORESDPF_KEY = $00039404
    const long VGAPRODUCTDPF_KEY = $00039424
    const long VGAEXTRALORESLACEDPF_KEY = $00031405
    const long VGALORESLACEDPF_KEY = $00039405
    const long VGAPRODUCTLACEDPF_KEY = $00039425
    const long VGAEXTRALORESDPF2_KEY = $00031444
    const long VGALORESDPF2_KEY = $00039444
    const long VGAPRODUCTDPF2_KEY = $00039464
    const long VGAEXTRALORESLACEDPF2_KEY = $00031445
    const long VGALORESLACEDPF2_KEY = $00039445
    const long VGAPRODUCTLACEDPF2_KEY = $00039465
    const long VGAEXTRAHALFBRITE_KEY = $00031084
    const long VGAEXTRAHALFBRITELACE_KEY = $00031085
    const long VGAPRODUCTHAM_KEY = $00039824
    const long VGALORESHAM_KEY = $00039804
    const long VGAPRODUCTHAMLACE_KEY = $00039825
    const long VGALORESHAMLACE_KEY = $00039805
    const long VGALORESEHB_KEY = $00039084
    const long VGALORESEHBLACE_KEY = $00039085
    const long VGAEHB_KEY = $000390a4
    const long VGAEHBLACE_KEY = $000390a5
    const long A2024_MONITOR_ID = $00041000
    const long A2024TENHERTZ_KEY = $00041000
    const long A2024FIFTEENHERTZ_KEY = $00049000
    const long PROTO_MONITOR_ID = $00051000
    const long EURO72_MONITOR_ID = $00061000
    const long EURO72EXTRALORES_KEY = $00061004
    const long EURO72LORES_KEY = $00069004
    const long EURO72PRODUCT_KEY = $00069024
    const long EURO72HAM_KEY = $00061804
    const long EURO72EXTRALORESLACE_KEY = $00061005
    const long EURO72LORESLACE_KEY = $00069005
    const long EURO72PRODUCTLACE_KEY = $00069025
    const long EURO72HAMLACE_KEY = $00061805
    const long EURO72EXTRALORESDPF_KEY = $00061404
    const long EURO72LORESDPF_KEY = $00069404
    const long EURO72PRODUCTDPF_KEY = $00069424
    const long EURO72EXTRALORESLACEDPF_KEY = $00061405
    const long EURO72LORESLACEDPF_KEY = $00069405
    const long EURO72PRODUCTLACEDPF_KEY = $00069425
    const long EURO72EXTRALORESDPF2_KEY = $00061444
    const long EURO72LORESDPF2_KEY = $00069444
    const long EURO72PRODUCTDPF2_KEY = $00069464
    const long EURO72EXTRALORESLACEDPF2_KEY = $00061445
    const long EURO72LORESLACEDPF2_KEY = $00069445
    const long EURO72PRODUCTLACEDPF2_KEY = $00069465
    const long EURO72EXTRAHALFBRITE_KEY = $00061084
    const long EURO72EXTRAHALFBRITELACE_KEY = $00061085
    const long EURO72PRODUCTHAM_KEY = $00069824
    const long EURO72PRODUCTHAMLACE_KEY = $00069825
    const long EURO72LORESHAM_KEY = $00069804
    const long EURO72LORESHAMLACE_KEY = $00069805
    const long EURO72LORESEHB_KEY = $00069084
    const long EURO72LORESEHBLACE_KEY = $00069085
    const long EURO72EHB_KEY = $000690a4
    const long EURO72EHBLACE_KEY = $000690a5
    const long EURO72EXTRALORESDBL_KEY = $00061000
    const long EURO72LORESDBL_KEY = $00069000
    const long EURO72PRODUCTDBL_KEY = $00069020
    const long EURO72EXTRALORESHAMDBL_KEY = $00061800
    const long EURO72LORESHAMDBL_KEY = $00069800
    const long EURO72PRODUCTHAMDBL_KEY = $00069820
    const long EURO72EXTRALORESEHBDBL_KEY = $00061080
    const long EURO72LORESEHBDBL_KEY = $00069080
    const long EURO72PRODUCTEHBDBL_KEY = $000690a0
    const long EURO36_MONITOR_ID = $00071000
    const long SUPER72_MONITOR_ID = $00081000
    const long SUPER72LORESDBL_KEY = $00081008
    const long SUPER72HIRESDBL_KEY = $00089008
    const long SUPER72SUPERDBL_KEY = $00089028
    const long SUPER72LORESHAMDBL_KEY = $00081808
    const long SUPER72HIRESHAMDBL_KEY = $00089808
    const long SUPER72SUPERHAMDBL_KEY = $00089828
    const long SUPER72LORESEHBDBL_KEY = $00081088
    const long SUPER72HIRESEHBDBL_KEY = $00089088
    const long SUPER72SUPEREHBDBL_KEY = $000890a8
    const long DBLNTSC_MONITOR_ID = $00091000
    const long DBLNTSCLORES_KEY = $00091000
    const long DBLNTSCLORESFF_KEY = $00091004
    const long DBLNTSCLORESHAM_KEY = $00091800
    const long DBLNTSCLORESHAMFF_KEY = $00091804
    const long DBLNTSCLORESEHB_KEY = $00091080
    const long DBLNTSCLORESEHBFF_KEY = $00091084
    const long DBLNTSCLORESLACE_KEY = $00091005
    const long DBLNTSCLORESHAMLACE_KEY = $00091805
    const long DBLNTSCLORESEHBLACE_KEY = $00091085
    const long DBLNTSCLORESDPF_KEY = $00091400
    const long DBLNTSCLORESDPFFF_KEY = $00091404
    const long DBLNTSCLORESDPFLACE_KEY = $00091405
    const long DBLNTSCLORESDPF2_KEY = $00091440
    const long DBLNTSCLORESDPF2FF_KEY = $00091444
    const long DBLNTSCLORESDPF2LACE_KEY = $00091445
    const long DBLNTSCHIRES_KEY = $00099000
    const long DBLNTSCHIRESFF_KEY = $00099004
    const long DBLNTSCHIRESHAM_KEY = $00099800
    const long DBLNTSCHIRESHAMFF_KEY = $00099804
    const long DBLNTSCHIRESLACE_KEY = $00099005
    const long DBLNTSCHIRESHAMLACE_KEY = $00099805
    const long DBLNTSCHIRESEHB_KEY = $00099080
    const long DBLNTSCHIRESEHBFF_KEY = $00099084
    const long DBLNTSCHIRESEHBLACE_KEY = $00099085
    const long DBLNTSCHIRESDPF_KEY = $00099400
    const long DBLNTSCHIRESDPFFF_KEY = $00099404
    const long DBLNTSCHIRESDPFLACE_KEY = $00099405
    const long DBLNTSCHIRESDPF2_KEY = $00099440
    const long DBLNTSCHIRESDPF2FF_KEY = $00099444
    const long DBLNTSCHIRESDPF2LACE_KEY = $00099445
    const long DBLNTSCEXTRALORES_KEY = $00091200
    const long DBLNTSCEXTRALORESHAM_KEY = $00091a00
    const long DBLNTSCEXTRALORESEHB_KEY = $00091280
    const long DBLNTSCEXTRALORESDPF_KEY = $00091600
    const long DBLNTSCEXTRALORESDPF2_KEY = $00091640
    const long DBLNTSCEXTRALORESFF_KEY = $00091204
    const long DBLNTSCEXTRALORESHAMFF_KEY = $00091a04
    const long DBLNTSCEXTRALORESEHBFF_KEY = $00091284
    const long DBLNTSCEXTRALORESDPFFF_KEY = $00091604
    const long DBLNTSCEXTRALORESDPF2FF_KEY = $00091644
    const long DBLNTSCEXTRALORESLACE_KEY = $00091205
    const long DBLNTSCEXTRALORESHAMLACE_KEY = $00091a05
    const long DBLNTSCEXTRALORESEHBLACE_KEY = $00091285
    const long DBLNTSCEXTRALORESDPFLACE_KEY = $00091605
    const long DBLNTSCEXTRALORESDPF2LACE_KEY = $00091645
    const long DBLPAL_MONITOR_ID = $000a1000
    const long DBLPALLORES_KEY = $000a1000
    const long DBLPALLORESFF_KEY = $000a1004
    const long DBLPALLORESHAM_KEY = $000a1800
    const long DBLPALLORESHAMFF_KEY = $000a1804
    const long DBLPALLORESEHB_KEY = $000a1080
    const long DBLPALLORESEHBFF_KEY = $000a1084
    const long DBLPALLORESLACE_KEY = $000a1005
    const long DBLPALLORESHAMLACE_KEY = $000a1805
    const long DBLPALLORESEHBLACE_KEY = $000a1085
    const long DBLPALLORESDPF_KEY = $000a1400
    const long DBLPALLORESDPFLACE_KEY = $000a1404
    const long DBLPALLORESDPF2_KEY = $000a1440
    const long DBLPALLORESDPF2LACE_KEY = $000a1444
    const long DBLPALHIRES_KEY = $000a9000
    const long DBLPALHIRESFF_KEY = $000a9004
    const long DBLPALHIRESHAM_KEY = $000a9800
    const long DBLPALHIRESHAMFF_KEY = $000a9804
    const long DBLPALHIRESLACE_KEY = $000a9005
    const long DBLPALHIRESHAMLACE_KEY = $000a9805
    const long DBLPALHIRESEHB_KEY = $000a9080
    const long DBLPALHIRESEHBFF_KEY = $000a9084
    const long DBLPALHIRESEHBLACE_KEY = $000a9085
    const long DBLPALHIRESDPF_KEY = $000a9400
    const long DBLPALHIRESDPFLACE_KEY = $000a9404
    const long DBLPALHIRESDPF2_KEY = $000a9440
    const long DBLPALHIRESDPF2LACE_KEY = $000a9444
    const long DBLPALEXTRALORES_KEY = $000a1200
    const long DBLPALEXTRALORESHAM_KEY = $000a1a00
    const long DBLPALEXTRALORESEHB_KEY = $000a1280
    const long DBLPALEXTRALORESDPF_KEY = $000a1600
    const long DBLPALEXTRALORESDPF2_KEY = $000a1640
    const long DBLPALEXTRALORESFF_KEY = $000a1204
    const long DBLPALEXTRALORESHAMFF_KEY = $000a1a04
    const long DBLPALEXTRALORESEHBFF_KEY = $000a1284
    const long DBLPALEXTRALORESDPFFF_KEY = $000a1604
    const long DBLPALEXTRALORESDPF2FF_KEY = $000a1644
    const long DBLPALEXTRALORESLACE_KEY = $000a1205
    const long DBLPALEXTRALORESHAMLACE_KEY = $000a1a05
    const long DBLPALEXTRALORESEHBLACE_KEY = $000a1285
    const long DBLPALEXTRALORESDPFLACE_KEY = $000a1605
    const long DBLPALEXTRALORESDPF2LACE_KEY = $000a1645
    const long BIDTAG_DIPFMustHave = $80000001
    const long BIDTAG_DIPFMustNotHave = $80000002
    const long BIDTAG_ViewPort = $80000003
    const long BIDTAG_NominalWidth = $80000004
    const long BIDTAG_NominalHeight = $80000005
    const long BIDTAG_DesiredWidth = $80000006
    const long BIDTAG_DesiredHeight = $80000007
    const long BIDTAG_Depth = $80000008
    const long BIDTAG_MonitorID = $80000009
    const long BIDTAG_SourceID = $8000000a
    const long BIDTAG_RedBits = $8000000b
    const long BIDTAG_BlueBits = $8000000c
    const long BIDTAG_GreenBits = $8000000d
    const long BIDTAG_GfxPrivate = $8000000e
    const ubyte MSB_REQUEST_NTSC = 0
    const ubyte MSF_REQUEST_NTSC = $0001
    const ubyte MSB_REQUEST_PAL = 1
    const ubyte MSF_REQUEST_PAL = $0002
    const ubyte MSB_REQUEST_SPECIAL = 2
    const ubyte MSF_REQUEST_SPECIAL = $0004
    const ubyte MSB_REQUEST_A2024 = 3
    const ubyte MSF_REQUEST_A2024 = $0008
    const ubyte MSB_DOUBLE_SPRITES = 4
    const ubyte MSF_DOUBLE_SPRITES = $0010
    const ubyte RPB_FRST_DOT = 0
    const ubyte RPF_FRST_DOT = $0001
    const ubyte RPB_ONE_DOT = 1
    const ubyte RPF_ONE_DOT = $0002
    const ubyte RPB_DBUFFER = 2
    const ubyte RPF_DBUFFER = $0004
    const ubyte RPB_AREAOUTLINE = 3
    const ubyte RPF_AREAOUTLINE = $0008
    const ubyte RPB_NOCROSSFILL = 5
    const ubyte RPF_NOCROSSFILL = $0020
    const ubyte RP_JAM1 = $0000
    const ubyte RP_JAM2 = $0001
    const ubyte RP_COMPLEMENT = $0002
    const ubyte RP_INVERSVID = $0004
    const ubyte RPB_TXSCALE = 0
    const ubyte RPF_TXSCALE = $0001
    const ubyte FS_NORMAL = $0000
    const ubyte FSB_UNDERLINED = 0
    const ubyte FSF_UNDERLINED = $0001
    const ubyte FSB_BOLD = 1
    const ubyte FSF_BOLD = $0002
    const ubyte FSB_ITALIC = 2
    const ubyte FSF_ITALIC = $0004
    const ubyte FSB_EXTENDED = 3
    const ubyte FSF_EXTENDED = $0008
    const ubyte FSB_COLORFONT = 6
    const ubyte FSF_COLORFONT = $0040
    const ubyte FSB_TAGGED = 7
    const ubyte FSF_TAGGED = $0080
    const ubyte FPB_ROMFONT = 0
    const ubyte FPF_ROMFONT = $0001
    const ubyte FPB_DISKFONT = 1
    const ubyte FPF_DISKFONT = $0002
    const ubyte FPB_REVPATH = 2
    const ubyte FPF_REVPATH = $0004
    const ubyte FPB_TALLDOT = 3
    const ubyte FPF_TALLDOT = $0008
    const ubyte FPB_WIDEDOT = 4
    const ubyte FPF_WIDEDOT = $0010
    const ubyte FPB_PROPORTIONAL = 5
    const ubyte FPF_PROPORTIONAL = $0020
    const ubyte FPB_DESIGNED = 6
    const ubyte FPF_DESIGNED = $0040
    const ubyte FPB_REMOVED = 7
    const ubyte FPF_REMOVED = $0080
    const ubyte TA_DeviceDPI = $0001
    const uword MAXFONTMATCHWEIGHT = $7fff
    const ubyte TE0B_NOREMFONT = 0
    const ubyte TE0F_NOREMFONT = $0001
    const ubyte CT_COLORFONT = $0001
    const ubyte CT_GREYFONT = $0002
    const ubyte CT_ANTIALIAS = $0004
    const ubyte CTB_MAPCOLOR = 0
    const ubyte CTF_MAPCOLOR = $0001
    const ubyte GENLOCK_VIDEO = $0002
    const ubyte V_LACE = $0004
    const ubyte V_DOUBLESCAN = $0008
    const ubyte V_SUPERHIRES = $0020
    const ubyte V_PFBA = $0040
    const ubyte V_EXTRA_HALFBRITE = $0080
    const uword GENLOCK_AUDIO = $0100
    const uword V_DUALPF = $0400
    const uword V_HAM = $0800
    const uword V_EXTENDED_MODE = $1000
    const uword V_VP_HIDE = $2000
    const uword V_SPRITES = $4000
    const uword V_HIRES = $8000
    const uword EXTEND_VSTRUCT = $1000
    const ubyte VPB_A2024 = 6
    const ubyte VPF_A2024 = $0040
    const ubyte VPB_TENHZ = 4
    const ubyte VPF_TENHZ = $0010
    const ubyte COLORMAP_TYPE_V1_2 = $0000
    const ubyte COLORMAP_TYPE_V1_4 = $0001
    const ubyte COLORMAP_TYPE_V39 = $0002
    const ubyte COLORMAP_TRANSPARENCY = $0001
    const ubyte COLORPLANE_TRANSPARENCY = $0002
    const ubyte BORDER_BLANKING = $0004
    const ubyte BORDER_NOTRANSPARENCY = $0008
    const ubyte VIDEOCONTROL_BATCH = $0010
    const ubyte USER_COPPER_CLIP = $0020
    const ubyte CMB_CMTRANS = 0
    const ubyte CMF_CMTRANS = $0001
    const ubyte CMB_CPTRANS = 1
    const ubyte CMF_CPTRANS = $0002
    const ubyte CMB_BRDRBLNK = 2
    const ubyte CMF_BRDRBLNK = $0004
    const ubyte CMB_BRDNTRAN = 3
    const ubyte CMF_BRDNTRAN = $0008
    const ubyte CMB_BRDRSPRT = 6
    const ubyte CMF_BRDRSPRT = $0040
    const ubyte CMAB_FULLPALETTE = 0
    const ubyte CMAF_FULLPALETTE = $0001
    const ubyte CMAB_NO_INTERMED_UPDATE = 1
    const ubyte CMAF_NO_INTERMED_UPDATE = $0002
    const ubyte CMAB_NO_COLOR_LOAD = 2
    const ubyte CMAF_NO_COLOR_LOAD = $0004
    const ubyte CMAB_DUALPF_DISABLE = 3
    const ubyte CMAF_DUALPF_DISABLE = $0008
    const ubyte PENB_EXCLUSIVE = 0
    const ubyte PENF_EXCLUSIVE = $0001
    const ubyte PENB_NO_SETCOLOR = 1
    const ubyte PENF_NO_SETCOLOR = $0002
    const long VGAEXTRALORESHAM_KEY = $00031804
    const long VGAEXTRALORESHAMLACE_KEY = $00031805
    const long VGAEXTRALORESEHB_KEY = $00031084
    const long VGAEXTRALORESEHBLACE_KEY = $00031085
    const long EURO72EXTRALORESHAM_KEY = $00061804
    const long EURO72EXTRALORESHAMLACE_KEY = $00061805
    const long EURO72EXTRALORESEHB_KEY = $00061084
    const long EURO72EXTRALORESEHBLACE_KEY = $00061085
    const ubyte COLORMAP_TYPE_V36 = $0001
}
;; End of auto-generated graphics_lib.sfd
