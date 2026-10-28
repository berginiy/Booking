@echo off
start "mock-mail" cmd /k "cd /d %~dp0mock-mail && node server.js"
