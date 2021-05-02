@echo off
REM calls compiled (jfractals) from the class files under win64 
chcp 28591 > NUL
java -cp %~dp0bin;%~dp0..\JCommandLineParser\bin;%~dp0lib\gluegen-rt.jar;%~dp0lib\gluegen-rt-natives-windows-amd64.jar;%~dp0lib\jocl.jar;%~dp0lib\jocl-natives-windows-amd64.jar;%~dp0lib\jogl-all.jar;%~dp0lib\jogl-all-natives-windows-amd64.jar de.thiesgerken.fractals.cli.Main %*