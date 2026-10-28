@echo off
start "mock-outlook" cmd /k "cd /d %~dp0mock-outlook && node server.js"
