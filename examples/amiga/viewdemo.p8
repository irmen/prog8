; Prototype: graphics.library View/ViewPort display (no Intuition screen).
; 320x256x8 on AGA, displaying all 256 palette entries.
; Picks the display path at runtime via sys.exec_version(): the Kickstart 1.3
; compatible OCS/ECS path (legacy vp.Modes only) below V36, and the Release 2
; path (VideoControl/GfxNew/OpenMonitor) on 2.0+.

%import exec
%import graphics

main {
    const uword WIDTH = 320
    const uword HEIGHT = 256
    const ubyte DEPTH = 8
    const uword VP_MODES = 0    ; legacy 1.3 Modes: 0=LORES, or HIRES and/or LACE
    const long MODEID = graphics.DEFAULT_MONITOR_ID | graphics.LORES_KEY
    const uword EXTEND_VSTRUCT = $1000
    const uword DIMQUERY_SIZE = 88

    ; Display state shared by setup_view(), paint_stuff() and return_view().
    ^^graphics.BitMap bm
    ^^graphics.RasInfo ri
    ^^graphics.ViewPort vp
    ^^graphics.ColorMap cm
    ^^graphics.View view
    ^^graphics.RastPort rp
    pointer oldview
    pointer vpextra
    ^^ubyte dimquery
    long[8] @shared vcTags
    pointer vextra
    pointer monspec
    bool use_videocontrol

    ; LoadRGB32 takes count/start-index, then RGB triples, then a zero terminator.
    long[2 + 256 * 3] @shared palette32

    sub start() {
        if setup_view() == false
            return
        paint_stuff()
        return_view()
    }

    sub setup_view() -> bool {
        ; Runtime Kickstart detection (exec V36 = 2.0). The 2.0+ path with
        ; VideoControl + ViewPortExtra is required for DBLPAL/DBLNTSC and
        ; other modeID-based modes; plain PAL/NTSC lores/hires uses the
        ; 1.3-compatible legacy vp.Modes path.
        use_videocontrol = sys.exec_version() >= 36

        bm = graphics.AllocBitMap(WIDTH as long, HEIGHT as long, DEPTH as long,
            graphics.BMF_CLEAR | graphics.BMF_DISPLAYABLE, 0)
        if bm == 0
            return false

        ri = exec.AllocMem(sizeof(graphics.RasInfo), exec.MEMF_CLEAR)
        if ri == 0
            return false
        ri.BitMap = bm

        vp = exec.AllocMem(sizeof(graphics.ViewPort), exec.MEMF_CLEAR)
        if vp == 0
            return false
        graphics.InitVPort(vp)
        vp.DWidth = WIDTH
        vp.DHeight = HEIGHT
        vp.Modes = VP_MODES
        vp.RasInfo = ri

        cm = graphics.GetColorMap(256)
        if cm == 0
            return false

        ; Extended (2.0+) handles, only used on the VideoControl path.
        vpextra = 0
        dimquery = 0

        if use_videocontrol {
            ; Release 2 display setup: attach ViewPortExtra + DisplayInfo + ColorMap.
            dimquery = exec.AllocMem(DIMQUERY_SIZE, exec.MEMF_CLEAR)
            if dimquery == 0
                return false
            if graphics.GetDisplayInfoData(0, dimquery, DIMQUERY_SIZE, graphics.DTAG_DIMS, MODEID) == 0
                return false

            vpextra = graphics.GfxNew(graphics.VIEWPORT_EXTRA_TYPE)
            if vpextra == 0
                return false
            ; ViewPortExtra.DisplayClip (offset 28) = DimensionInfo.Nominal (offset 26)
            ^^word dclip_src = (dimquery + 26) as ^^word
            ^^word dclip_dst = (vpextra + 28) as ^^word
            dclip_dst[0] = dclip_src[0]
            dclip_dst[1] = dclip_src[1]
            dclip_dst[2] = dclip_src[2]
            dclip_dst[3] = dclip_src[3]

            ; TagItem pairs laid out manually: tag, data, tag, data, ..., terminator.
            pointer dispinfo = graphics.FindDisplayInfo(MODEID)
            if dispinfo == 0
                return false
            vcTags[0] = graphics.VTAG_ATTACH_CM_SET
            vcTags[1] = vp as long
            vcTags[2] = graphics.VTAG_VIEWPORTEXTRA_SET
            vcTags[3] = vpextra as long
            vcTags[4] = graphics.VTAG_NORMAL_DISP_SET
            vcTags[5] = dispinfo as long
            vcTags[6] = graphics.VTAG_END_CM
            vcTags[7] = 0

            if graphics.VideoControl(cm, vcTags)
                return false
        }
        vp.ColorMap = cm

        palette32[0] = 256 << 16
        for c in 0 to 255 {
            uword ci = c as uword
            uword base = 1 + ci * 3
            ubyte r = c as ubyte
            ubyte g = ((c * 37) & 255) as ubyte
            ubyte b = ((c * 97) & 255) as ubyte
            palette32[base] = (r as long) << 24
            palette32[base + 1] = (g as long) << 24
            palette32[base + 2] = (b as long) << 24
        }
        palette32[1 + 256 * 3] = 0
        graphics.LoadRGB32(vp, palette32)

        view = exec.AllocMem(sizeof(graphics.View), exec.MEMF_CLEAR)
        if view == 0
            return false
        graphics.InitView(view)
        view.ViewPort = vp

        vextra = 0
        monspec = 0
        if use_videocontrol {
            vextra = graphics.GfxNew(graphics.VIEW_EXTRA_TYPE)
            if vextra == 0
                return false
            graphics.GfxAssociate(view, vextra)
            view.Modes = view.Modes | EXTEND_VSTRUCT
            monspec = graphics.OpenMonitor(0, MODEID)
            if monspec == 0
                return false
            ; ViewExtra.Monitor (offset 28) = monspec
            ^^pointer monitor_slot = (vextra + 28) as ^^pointer
            monitor_slot[0] = monspec
        }

        if graphics.MakeVPort(view, vp) != 0
            return false
        if graphics.MrgCop(view) != 0
            return false
        ^^pointer actiview_ptr = (sys.GfxBase + 34) as ^^pointer
        oldview = actiview_ptr[0]
        graphics.LoadView(view)
        graphics.WaitTOF()

        rp = exec.AllocMem(sizeof(graphics.RastPort), exec.MEMF_CLEAR)
        if rp == 0
            return false
        graphics.InitRastPort(rp)
        rp.BitMap = bm
        return true
    }

    sub paint_stuff() {
        for c in 0 to 255 {
            uword gx = (c & 15) * 8
            uword gy = (c >> 4) * 8
            graphics.SetAPen(rp, c as ubyte)
            graphics.RectFill(rp, gx as word, gy as word, (gx + 7) as word, (gy + 7) as word)
        }

        repeat 100 {
            graphics.WaitTOF()
        }
    }

    sub return_view() {
        ; restore everything back to normal
        graphics.LoadView(oldview as ^^graphics.View)
        graphics.WaitTOF()
        if view.LOFCprList != 0
            graphics.FreeCprList(view.LOFCprList)
        if view.SHFCprList != 0
            graphics.FreeCprList(view.SHFCprList)
        graphics.FreeVPortCopLists(vp)
        graphics.FreeColorMap(vp.ColorMap)
        graphics.FreeBitMap(bm)
        if use_videocontrol {
            if monspec != 0
                void graphics.CloseMonitor(monspec)
            graphics.GfxFree(vextra)
            graphics.GfxFree(vpextra)
            exec.FreeMem(dimquery, DIMQUERY_SIZE)
        }
        exec.FreeMem(rp, sizeof(graphics.RastPort))
        exec.FreeMem(view, sizeof(graphics.View))
        exec.FreeMem(vp, sizeof(graphics.ViewPort))
        exec.FreeMem(ri, sizeof(graphics.RasInfo))
    }
}
