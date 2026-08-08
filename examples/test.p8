; Amiga custom screen example
; requires kickstart 2.0+ (A600, A1200, etc)

%import dos
%import graphics
%import intuition
%import iffparse
%import utility
%import textio

main {
    sub start() {

        long[] screentags = [
            intuition.SA_Left,      0,
            intuition.SA_Top,       0,
            intuition.SA_Width,     320,
            intuition.SA_Height,    256,
            intuition.SA_Depth,     5,  ;  5 bitplanes=32 colors
            intuition.SA_DisplayID, graphics.DEFAULT_MONITOR_ID | graphics.LORES_KEY,
            intuition.SA_Type,      intuition.CUSTOMSCREEN,
            intuition.SA_ShowTitle, false,
            utility.TAG_DONE,     0
        ]

        long[] windowtags = [
            intuition.WA_CustomScreen, 0,       ; set later
            intuition.WA_Left,         0 ,
            intuition.WA_Top,          0 ,
            intuition.WA_Width,        320 ,
            intuition.WA_Height,       256 ,
            intuition.WA_Backdrop,     true,
            intuition.WA_Borderless,   true,
            intuition.WA_Activate,     true,
            intuition.WA_IDCMP,        intuition.IDCMP_MOUSEBUTTONS | intuition.IDCMP_RAWKEY,
            utility.TAG_DONE,        0
        ]

        if not iffparse.openlib() {
            txt.print("no iffparse\n")
            sys.exit(1)
        }

        str imagefile = "PROGDIR:psygnosis.iff"

        ; open custom screen
        ^^intuition.Screen myScreen = intuition.OpenScreenTagList(0, screentags)
        if myScreen!=0 {

            ; now open a window to be able to set mouse cursor, handle mouse clicks, keyboard presses, etc.
            windowtags[1] = myScreen
            ^^intuition.Window myWindow = intuition.OpenWindowTagList(0, windowtags)

            if myWindow!=0 {
                loadIFFimage(imagefile, myScreen)
                sys.wait(200)

                intuition.CloseWindow(myWindow)
            }
            void intuition.CloseScreen(myScreen)
        }

        iffparse.closelib()
    }


    struct BMHDheader {
        uword w, h                  ; Raster width and height in pixels
        word  x, y                  ; Pixel position on screen (usually 0,0)
        ubyte nPlanes               ; Number of bitplanes (1 to 8)
        ubyte masking               ; Masking technique (0=None, 1=HasMask, 2=HasTransparentColor)
        ubyte compression           ; Compression algorithm (0=None, 1=ByteRun1)
        ubyte pad1                  ; Reserved byte / alignment
        uword transparentColor      ; Transparent color choice
        ubyte xAspect, yAspect      ; Pixel aspect ratio
        word  pageWidth, pageHeight ; Source page size in pixels
    }


    sub loadIFFimage(str filename, ^^intuition.Screen scr) {
        const long ID_ILBM = $494C424D  ; 'ILBM'
        const long ID_BMHD = $424D4844  ; 'BMHD'
        const long ID_CMAP = $434D4150  ; 'CMAP'
        const long ID_BODY = $424F4459  ; 'BODY'

        ^^graphics.RastPort rp = &scr.emb_RastPort
        ^^iffparse.IFFHandle iff = iffparse.AllocIFF()
        if iff!=0 {
            iff.Stream = dos.Open(filename, dos.MODE_OLDFILE)
            if iff.Stream!=0 {
                iffparse.InitIFFasDOS(iff)
                if iffparse.OpenIFF(iff, iffparse.IFFF_READ) == 0 {
                    void iffparse.PropChunk(iff, ID_ILBM, ID_BMHD)     ; extract image properties
                    void iffparse.PropChunk(iff, ID_ILBM, ID_CMAP)     ; extract palette
                    void iffparse.StopChunk(iff, ID_ILBM, ID_BODY)     ; stop at body data
                    if iffparse.ParseIFF(iff, iffparse.IFFPARSE_SCAN) ==0 {
                        ^^iffparse.StoredProperty bmhd = iffparse.FindProp(iff, ID_ILBM, ID_BMHD)
                        ^^iffparse.StoredProperty cmap = iffparse.FindProp(iff, ID_ILBM, ID_CMAP)

                        if bmhd!=0 and bmhd.Data!=0 {
                            ^^BMHDheader header = bmhd.Data
                            ; get size of BODY chunk and allocate ram to read it into
                            ^^iffparse.ContextNode cn = iffparse.CurrentChunk(iff)
                            pointer bodyBuffer = exec.AllocVec(cn.Size, exec.MEMF_PUBLIC)
                            if bodyBuffer!=0 {
                                if iffparse.ReadChunkBytes(iff, bodyBuffer, cn.Size) == cn.Size {

                                    if cmap!=0 and cmap.Data!=0
                                        setPalette(cmap)

                                    decode(header, bodyBuffer)
                                }
                                exec.FreeVec(bodyBuffer)
                            }
                        }
                    }
                    iffparse.CloseIFF(iff)
                }
                void dos.Close(iff.Stream)
            }
            iffparse.FreeIFF(iff)
        }

        sub decode(^^BMHDheader hdr, ^^ubyte body) {
            long bytesPerRow = ((hdr.w + 15) / 16) * 2
            ^^graphics.BitMap bm = rp.BitMap
            ubyte compression = hdr.compression

            uword y
            for y in 0 to hdr.h-1 {
                ubyte plane
                for plane in 0 to hdr.nPlanes-1 {
                    ^^ubyte target = bm.Planes[plane] + y*bytesPerRow
                    when compression {
                        0 -> {
                            ; non-compressed
                            sys.memcopy(body, target, bytesPerRow as uword)
                            body += bytesPerRow
                        }
                        1 -> {
                            ; byterun1 RLE
                            body = unpackRLErow(body, target, bytesPerRow as word)
                        }
                        ; any other compression values silently ignored
                    }
                }
            }

            sub unpackRLErow(^^ubyte src, ^^ubyte tgt, word amount) -> ^^ubyte {
                while amount > 0 {
                    byte n = @(src) as byte
                    src++
                    if n>=0 {
                        ; litaral run: copy n+1 bytes verbatim
 ;                        amount -= n+1
 ;                        repeat n+1 {
 ;                            @(tgt) = @(src)
 ;                            tgt++
 ;                            src++
 ;                        }
                         %asm {{
                            ; amount -= n+1
                            move.b  p8b_main.p8s_loadIFFimage.p8s_decode.p8s_unpackRLErow.p8v_n,d0
                            ext.w   d0
                            move.w  p8b_main.p8s_loadIFFimage.p8s_decode.p8s_unpackRLErow.p8v_amount,d1
                            sub.w   d0,d1
                            subq.w  #1,d1
                            move.w  d1,p8b_main.p8s_loadIFFimage.p8s_decode.p8s_unpackRLErow.p8v_amount

                            ; repeat n+1 copy bytes verbatim
                            move.l  p8b_main.p8s_loadIFFimage.p8s_decode.p8s_unpackRLErow.p8v_src,a0
                            move.l  p8b_main.p8s_loadIFFimage.p8s_decode.p8s_unpackRLErow.p8v_tgt,a1
.copy                       move.b  (a0)+,(a1)+
                            dbra    d0,.copy
                            move.l  a0,p8b_main.p8s_loadIFFimage.p8s_decode.p8s_unpackRLErow.p8v_src
                            move.l  a1,p8b_main.p8s_loadIFFimage.p8s_decode.p8s_unpackRLErow.p8v_tgt
                        }}
                   } else if n!=-128 {
                        ; repeat run: repeat next byte 1-n times
;                        amount -= 1-n
;                        ubyte b = @(src)
;                        src++
;                        repeat 1-n {
;                            @(tgt) = b
;                            tgt++
;                        }

                        %asm {{
                            ; amount -= 1-n  -->  amount += n, amount -= 1
                            move.b  p8b_main.p8s_loadIFFimage.p8s_decode.p8s_unpackRLErow.p8v_n,d0
                            ext.w   d0
                            move.w  p8b_main.p8s_loadIFFimage.p8s_decode.p8s_unpackRLErow.p8v_amount,d1
                            add.w   d0,d1
                            subq.w  #1,d1
                            move.w  d1,p8b_main.p8s_loadIFFimage.p8s_decode.p8s_unpackRLErow.p8v_amount

                            ; repeat copy next byte 1-n times
                            move.l  p8b_main.p8s_loadIFFimage.p8s_decode.p8s_unpackRLErow.p8v_src,a0
                            move.l  p8b_main.p8s_loadIFFimage.p8s_decode.p8s_unpackRLErow.p8v_tgt,a1
                            move.b  (a0)+,d1
                            neg.w   d0
.rep                        move.b  d1,(a1)+
                            dbra    d0,.rep
                            move.l  a0,p8b_main.p8s_loadIFFimage.p8s_decode.p8s_unpackRLErow.p8v_src
                            move.l  a1,p8b_main.p8s_loadIFFimage.p8s_decode.p8s_unpackRLErow.p8v_tgt
                        }}
                    }
                }
                return src
            }
        }

        sub setPalette(^^iffparse.StoredProperty iffcmap) {
            ^^ubyte rgb = iffcmap.Data
            uword numcolors = iffcmap.Size as uword /3
            pointer viewport = &scr.emb_ViewPort
            uword c
            for c in 0 to numcolors-1
                graphics.SetRGB4(viewport, c as word, rgb[c*3]>>4, rgb[c*3+1]>>4, rgb[c*3+2]>>4)
        }
    }
}
