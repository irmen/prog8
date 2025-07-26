main {
    ubyte @shared @requirezp zpvar
    bool @shared @requirezp zpbool
    ubyte @shared @requirezp @dirty dirtyzpvar
    bool @shared @requirezp @dirty dirtyzpbool

    sub start() {
        ubyte @shared @requirezp zpvar2
        bool @shared @requirezp zpbool2
        ubyte @shared @requirezp @dirty dirtyzpvar2
        bool @shared @requirezp @dirty dirtyzpbool2
    }
}
