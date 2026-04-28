# super simple Makefile to launch the main gradle targets to build and/or test the prog8 compiler

.PHONY: all test

# Use system gradle if available, otherwise fall back to gradlew wrapper
# Windows uses 'where' and gradlew.bat, Unix uses 'command -v' and ./gradlew
ifeq ($(OS),Windows_NT)
    GRADLE = $(shell where gradle >nul 2>&1 && echo gradle || echo gradlew.bat)
else
    GRADLE = $(shell command -v gradle >/dev/null 2>&1 && echo gradle || echo ./gradlew)
endif

all:
	$(GRADLE) installdist installshadowdist
	@echo "compiler launch script can be found here: compiler/build/install/compiler-shadow/bin/prog8c"

test:
	$(GRADLE) build
	@echo "compiler launch script can be found here: compiler/build/install/compiler-shadow/bin/prog8c"

