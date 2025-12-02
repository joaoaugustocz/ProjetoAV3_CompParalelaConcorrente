@echo off
setlocal enabledelayedexpansion

rem Executa o benchmark. Se forem passados argumentos, eles substituem os padroes.
set JAR=%~dp0target\wordcount-parallel-1.0.0-jar-with-dependencies.jar
if not exist "%JAR%" (
    echo [ERRO] JAR nao encontrado em %JAR%.
    echo Rode: mvn clean package -DskipTests
    exit /b 1
)

rem Monta a lista de inputs automaticamente (todos os .txt em data/)
set INPUTS=
for %%F in ("%~dp0data\*.txt") do (
    set REL=data/%%~nxF
    if "!INPUTS!"=="" (
        set INPUTS=!REL!
    ) else (
        set INPUTS=!INPUTS!,!REL!
    )
)

rem Se nenhum arquivo for encontrado, usa os tres samples menores.
if "!INPUTS!"=="" (
    set INPUTS=data/sample_small.txt,data/sample_medium.txt,data/sample_large.txt
)

if "%~1"=="" (
    set ARGS=--word paralelismo --threads 2,4 --runs 3 --inputs !INPUTS!
) else (
    set ARGS=%*
)

echo Executando: java -jar "%JAR%" %ARGS%
java -jar "%JAR%" %ARGS%

echo.
pause
