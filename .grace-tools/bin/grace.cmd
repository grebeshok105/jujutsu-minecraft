@echo off
rem Local GRACE CLI shim for this repository (project-local install).
rem Never install grace globally; call this shim or add .grace-tools\bin to PATH.
"%~dp0..\node_modules\.bin\grace.exe" %*