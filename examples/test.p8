main
{
    sub start()
    {
        ubyte variable=55
        when variable
        {
            33 -> cx16.r0++
            else -> cx16.r1++
        }

        if variable {
            cx16.r0++
        } else {
            cx16.r1++
        }

        if variable { cx16.r0++ }
        else { cx16.r1++ }

        if variable
        {
            cx16.r0++
        }
        else
        {
            cx16.r1++
        }

        other.othersub()
    }
}


other {
    sub othersub() {
        cx16.r0++
    }
}
