# Minimal makefile for Sphinx documentation
#

# You can set these variables from the command line.
SPHINXOPTS    =
SPHINXBUILD   = sphinx-build
SPHINXPROJ    = Prog8
SOURCEDIR     = source
BUILDDIR      = build

# Put it first so that "make" without argument is like "make help".
help:
	@$(SPHINXBUILD) -M help "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(O)

.PHONY: help Makefile symboldumps


symboldumps:
	p8compile -target atari import-all-atari.p8 -dumpsymbols > skeletons-atari.txt
	p8compile -target c64 import-all-c64.p8 -dumpsymbols > skeletons-c64.txt
	p8compile -target c128 import-all-c128.p8 -dumpsymbols > skeletons-c128.txt
	p8compile -target cx16 import-all-cx16.p8 -dumpsymbols > skeletons-cx16.txt
	p8compile -target pet32 import-all-pet32.p8 -dumpsymbols > skeletons-pet32.txt
	p8compile -target virtual import-all-virtual.p8 -dumpsymbols > skeletons-virtual.txt


# Catch-all target: route all unknown targets to Sphinx using the new
# "make mode" option.  $(O) is meant as a shortcut for $(SPHINXOPTS).
%: Makefile
	@$(SPHINXBUILD) -M $@ "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(O)
