@echo off
setlocal

set JAR=%~dp0target\wordcount-parallel-1.0.0-jar-with-dependencies.jar
if not exist "%JAR%" (
    echo [ERRO] JAR nao encontrado em %JAR%.
    echo Rode: mvn clean package -DskipTests
    exit /b 1
)

echo Abrindo interface grafica...
java -jar "%JAR%" --gui
