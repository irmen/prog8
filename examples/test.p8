%zeropage basicsafe
%option enable_floats

main {
    sub start() {
        ubyte[3] values
        func1(22 in values)
        func2(22 in values)
        ubyte @shared qq = 22 in values
        byte @shared ww = 22 in values
    }
    sub func1(ubyte arg) {
        arg++
    }
    sub func2(byte arg) {
        arg++
    }


/*    sub start2() {

        float @shared fl
        ubyte @shared flags
        byte @shared flagss
        uword @shared flagsw
        word @shared flagssw
        bool @shared bflags = 123
        cx16.r0++
        bflags = 123
        cx16.r0++
        bflags = 123 as bool

        flags = bflags
        flagss = bflags
        flagsw = bflags
        flagssw = bflags
        fl = bflags
        bflags = flags
        bflags = flagss
        bflags = flagsw
        bflags = flagssw
        bflags = fl

        flags = bflags as ubyte
        flagss = bflags as byte
        flagsw = bflags as uword
        flagssw = bflags as word
        fl = bflags as float
        bflags = flags as bool
        bflags = flagss as bool
        bflags = flagsw as bool
        bflags = flagssw as bool
        bflags = fl as bool
    }*/
}
