@echo.>stderr.txt
@for %%a in (*.spc) do konspc --loop 2 %%a %%~na.mid %%~na.html 2>>stderr.txt
