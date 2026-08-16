main {

    sub start() {
        str name = "name"
        pointer nameptr = &name
        bool result

        result = name=="foo"
        result = name!="foo"
        result = name<"foo"
        result = name>"foo"

        result = nameptr=="foo"
        result = nameptr!="foo"
        result = nameptr<"foo"
        result = nameptr>"foo"

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
