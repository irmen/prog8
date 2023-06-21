%import textio
%zeropage basicsafe

main {

    sub start() {
        str name = "name"
        uword nameptr = &name

        cx16.r0L= name=="foo"
        cx16.r1L= name!="foo"
        cx16.r2L= name<"foo"
        cx16.r3L= name>"foo"

        cx16.r0L= nameptr=="foo"
        cx16.r1L= nameptr!="foo"
        cx16.r2L= nameptr<"foo"
        cx16.r3L= nameptr>"foo"

        void compare(name, "foo")
        void compare(name, "name")
        void compare(nameptr, "foo")
        void compare(nameptr, "name")
    }

    sub compare(str s1, str s2) -> ubyte {
        if s1==s2
            return 42
        return 0
    }
}

