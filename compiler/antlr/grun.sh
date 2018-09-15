#!/bin/sh

PROJECT=~/Projects/prog8/compiler/antlr

export CLASSPATH=".:${PROJECT}/lib/antlr-4.7.1-complete.jar:${CLASSPATH}"
java org.antlr.v4.gui.TestRig $*

