; -- Long-sized arena / bump allocator for m68k and virtual targets.
;    The allocator state is caller-owned: pass an Arena struct pointer to
;    every call, so any number of independent slabs can be managed at the
;    same time.
;    Allocations bump a pointer forward. Individual deallocations are not
;    supported; reset() frees everything in that arena at once.
;    Sizes are long values and odd sizes are rounded up to an even address.

%option ignore_unused

arena {
    %option merge

    struct Arena {
        pointer base
        pointer next
        pointer end
    }

    sub init(^^Arena state, pointer base_addr, long size) {
        ; -- initialize the arena over the range [base_addr, base_addr + size)
        state.base = base_addr
        state.next = base_addr
        state.end = base_addr + size
    }

    sub alloc(^^Arena state, long size) -> pointer {
        ; -- allocate size bytes from the arena. Returns 0 if the slab is exhausted.
        ;    Odd sizes are rounded up to an even number so word/long/struct accesses stay aligned.
        if size == 0
            return 0
        if (size & 1) != 0
            size++
        if state.next + size > state.end
            return 0
        pointer result = state.next
        state.next += size
        return result
    }

    sub reset(^^Arena state) {
        ; -- reset the arena so the slab can be reused from the start
        state.next = state.base
    }
}
