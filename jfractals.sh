#!/bin/sh
# calls compiled (jfractals) from the class files under linux64 
java -cp ./bin:../JCommandLineParser/bin:./lib/gluegen-rt.jar:./lib/gluegen-rt-natives-linux-amd64.jar:./lib/jocl.jar:./lib/jocl-natives-linux-amd64.jar:./lib/jogl-all.jar:./lib/jogl-all-natives-linux-amd64.jar de.thiesgerken.fractals.cli.Main $*
