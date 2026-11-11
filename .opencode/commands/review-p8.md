---
description: Prog8 code quality review for redundancy, idioms, builtins, library use, performance, structure, and modernization
agent: review
subagent: true
---

Perform the Prog8 code review described in `.agents/prompts/review-p8.md`.
Begin your reply with a short one-line status message stating what you are about to review.
Before starting, load the `prog8-coder` skill (via the skill tool) and apply its
guidance throughout the review.
Read that file and follow it exactly. If the current workspace does not contain
it, read `/home/irmen/Projects/prog8/.agents/prompts/review-p8.md` instead.

Reference material (prog8-coder skill, docs/source/*.rst, symboldumps) is expected
to come from the prog8 repository checkout. The offline `prog8c -libsearch` /
`prog8c -libdump` commands can confirm that a library routine exists and show
its source (which may include comments explaining its behavior), but they do not
always document its usage; for that, consult the local docs (and only as a last
resort the online Prog8 documentation at https://prog8.readthedocs.io/en/latest/).
Never invent a routine, builtin, or syntax feature that you could not verify
against one of these sources.

Source files or directories to review (empty: all `.p8` files in the
workspace): $ARGUMENTS

This is a REPORT-ONLY review. The reviewer must not edit, create, or delete any
file, and must not run the compiler in a mode that writes output next to the
sources (`-check`, `-libsearch`, `-libdump` only).

It is okay to run the compiler to check or validate findings, but do so in a temporary directory location.

When the subagent returns its report, show it to the user verbatim and stop
there: do not summarize it into a work plan, do not implement, stage, or offer to
implement any finding, and do not start a follow-up edit or a new task for it.
The report ends with its own "REPORT ONLY" notice; treat that as binding. Only
act on individual findings if the user explicitly asks for them afterwards.
