%import textio
%zeropage basicsafe
%option no_sysinit


; NOTE: This is a small example of the Kernal's AUDIO routines.
;       There is also the "psg" library module that implements some Vera PSG routines
;       of its own, but that's not used here at all. See the "cx16/bdmusic" example for that.


main {
    sub start() {
        txt.print("\n\nsimple demonstration of the audio kernal routines.\n")
        txt.print("fm demo...\n")
        fm_demo()
        sys.wait(30)

        txt.print("psg demo...\n")
        psg_demo()
        sys.wait(30)

        txt.print("done!\n")
    }

    sub psg_demo() {
    /*

    10 PSGINIT
    20 PSGCHORD 15,"O3G>CE" : REM STARTS PLAYING A CHORD ON VOICES 15, 0, AND 1
    30 PSGPLAY 14,">C<DGB>CDE" : REM PLAYS A SERIES OF NOTES ON VOICE 14
    40 PSGCHORD 15,"RRR" : REM RELEASES CHORD ON VOICES 15, 0, AND 1
    50 PSGPLAY 14,"O4CAG>C<A" : REM PLAYS A SERIES OF NOTES ON VOICE 14
    60 PSGCHORD 0,"O3A>CF" : REM STARTS PLAYING A CHORD ON VOICES 0, 1, AND 2
    70 PSGPLAY 14,"L16FGAB->CDEF4" : REM PLAYS A SERIES OF NOTES ON VOICE
    80 PSGCHORD 0,"RRR" : REM RELEASES CHORD ON VOICES 0, 1, AND 2

    */

        cx16.psg_init()

        cx16.bas_playstringvoice(15)
        cx16.bas_psgchordstring(6, "o3g>ce")

        cx16.bas_playstringvoice(14)
        cx16.bas_psgplaystring(10, ">c<dgb>cde")

        cx16.bas_playstringvoice(15)
        cx16.bas_psgchordstring(3, "rrr")

        cx16.bas_playstringvoice(14)
        cx16.bas_psgplaystring(9, "o4cag>c<a")

        cx16.bas_playstringvoice(0)
        cx16.bas_psgchordstring(6, "o3a>cf")

        cx16.bas_playstringvoice(14)
        cx16.bas_psgplaystring(14, "l16fgab->cdef4")

        cx16.bas_playstringvoice(0)
        cx16.bas_psgchordstring(3, "rrr")

    }

    sub fm_demo() {
    /*
10 FMINIT
20 FMVIB 195,10
30 FMINST 1,16:FMINST 2,16:FMINST 3,16 : REM ORGAN
40 FMVOL 1,50:FMVOL 2,50:FMVOL 3,50 : REM MAKE ORGAN QUIETER
50 FMINST 0,11 : REM VIBRAPHONE
60 FMCHORD 1,"O3CG>E T90" : REM START SOME ORGAN CHORDS (CHANNELS 1,2,3)
70 FMPLAY 0,"O4G4.A8G4E2." : REM PLAY MELODY (CHANNEL 0)
80 FMPLAY 0,"O4G4.A8G4E2."
90 FMCHORD 1,"O2G>DB" : REM SWITCH ORGAN CHORDS (CHANNELS 1,2,3)
100 FMPLAY 0,"O5D2D4<B2" : REM PLAY MORE MELODY
110 FMCHORD 1,"O2F" : REM SWITCH ONE OF THE ORGAN CHORD NOTES
120 FMPLAY 0,"R4" : REM PAUSE FOR THE LENGTH OF ONE QUARTER NOTE
130 FMCHORD 1,"O3CEG" : REM SWITCH ALL THREE CHORD NOTES
140 FMPLAY 0,"O5C2C4<G2." : REM PLAY THE REST OF THE MELODY
150 FMCHORD 1,"RRR" : REM RELEASE THE CHANNELS THAT ARE PLAYING THE CHORD
*/

        void cx16.ym_init()
        void cx16.bas_fmvib(195, 10)
        cx16.ym_loadpatch(1,16,true)
        cx16.ym_loadpatch(2,16,true)
        cx16.ym_loadpatch(3,16,true)
        void cx16.ym_setatten(1,64-50)
        void cx16.ym_setatten(2,64-50)
        void cx16.ym_setatten(3,64-50)
        cx16.ym_loadpatch(0,11,true)

        cx16.bas_playstringvoice(1)
        cx16.bas_fmchordstring(11, "o3cg>e t130")

        cx16.bas_playstringvoice(0)
        cx16.bas_fmplaystring(12, "o4g4.a8g4e2.")
        cx16.bas_playstringvoice(0)
        cx16.bas_fmplaystring(12, "o4g4.a8g4e2.")

        cx16.bas_playstringvoice(1)
        cx16.bas_fmchordstring(6, "o2g>db")

        cx16.bas_playstringvoice(0)
        cx16.bas_fmplaystring(9, "o5d2d4<b2")

        cx16.bas_playstringvoice(1)
        cx16.bas_fmchordstring(3, "o2f")

        cx16.bas_playstringvoice(0)
        cx16.bas_fmplaystring(2, "r4")

        cx16.bas_playstringvoice(1)
        cx16.bas_fmchordstring(5, "o3ceg")

        cx16.bas_playstringvoice(0)
        cx16.bas_fmplaystring(10, "o5c2c4<g2.")

        cx16.bas_playstringvoice(1)
        cx16.bas_fmchordstring(3, "rrr")
    }

}
