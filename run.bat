@echo off
REM Windows:  run.bat setup   (create tables + sample data)   |   run.bat   (start website)
if not exist out mkdir out
javac -encoding UTF-8 -cp "lib\*" -d out src\*.java || exit /b 1
if "%1"=="setup" (java -cp "out;lib\*" SetupDb) else (java -cp "out;lib\*" Main)
