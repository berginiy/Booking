@echo off

set MAIL_ENABLED=true
set MAIL_HOST=smtp.gmail.com
set MAIL_PORT=587
set MAIL_AUTH=true
set MAIL_STARTTLS=true
set MAIL_USERNAME=pirozokaltynbek@gmail.com
set MAIL_PASSWORD=odki axnb mnuw wemr
set MAIL_FROM=pirozokaltynbek@gmail.com
set MAIL_FROM_NAME=Бронирование залов

start "backend" cmd /k "cd /d %~dp0backend && mvn spring-boot:run"
start "frontend" cmd /k "cd /d %~dp0frontend && (if not exist node_modules npm install) && npm run dev"