%import textio

cia {
    ubyte freq
    const ubyte CNT = 200

    sub calibrate() {
        txt.print("calibrating frequency: ")
        tod_init(0)
        txt.print_ub(tod_freq())
        txt.print(" hz\n")
        tod_reset()
    }

    sub print_time() {
        uword t = tod_get10()
        txt.print("(cia) time: ")
        txt.print_uw(t / 10)
        txt.chrout('.')
        txt.print_uw(t % 10)
        txt.print(" sec.\n")
    }

    sub tod_reset() {
        ; set the tod to 0
        c64.CIA2TODHR = 0
        c64.CIA2TODMIN = 0
        c64.CIA2TODSEC = 0
        c64.CIA2TOD10 = 0
    }

    sub tod_get10() -> uword {
        ubyte h, m, s, t
        uword time
        h = c64.CIA2TODHR
        m = c64.CIA2TODMIN
        s = c64.CIA2TODSEC
        t = c64.CIA2TOD10
        time = t
        time += (s & $0f) * (10 as uword)
        time += (s >> 4) * (100 as uword)
        time += (m & $0f) * 600
        time += (m >> 4) * 6000
        return time
    }

    sub tod_init(ubyte f) {
        if (f == 0)
            freq = tod_detect_freq()
        else
            freq = f

        if (freq == 50)
            c64.CIA2CRA |= $80
        else
            c64.CIA2CRA &= $7f
    }

    sub tod_freq() -> ubyte {
        return freq
    }

    sub tod_detect_freq() -> ubyte {
        uword cbl

        c64.CIA2CRB = $40  ; stop timer
        c64.CIA2CRA = $80  ; stop timer

        ; set ta to overflow every 10000 count (~= 10ms)
        c64.CIA2TAL = $10
        c64.CIA2TAH = $27
        c64.CIA2TBL = CNT
        c64.CIA2TBH = 0

        tod_reset()

        c64.CIA2CRB = $41  ; input from tim1 overflow, continuous, start timer
        c64.CIA2CRA = $81  ; start timer, continuous tod 50HZ


        while c64.CIA2TODSEC == 0 {
            ; wait for tod to count 1s
        }

        ;  cal=CIA2.ta_lo;
        ;  cah=CIA2.ta_hi;
        cbl = c64.CIA2TBL
        ;  cbh=CIA2.tb_hi;

        ;  printf("count2 = %d %d %d %d\n",cah, cal, cbh, cbl);
        ;  printf("elapsed ~= %d0ms\n",CNT-cbl);

        if CNT - cbl > 90
            return 50
        else
            return 60
    }
}