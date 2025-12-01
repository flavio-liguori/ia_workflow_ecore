#!/bin/bash

# Couleurs pour faire pro
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}==============================================${NC}"
echo -e "${BLUE}   PIPELINE IDM : EXTRACTION - RCA - GENAI    ${NC}"
echo -e "${BLUE}==============================================${NC}"

# 1. Vérification de l'argument
FILE_INPUT=$1

# Si aucun fichier n'est donné, on propose ceux disponibles dans resources
if [ -z "$FILE_INPUT" ]; then
    echo -e "${RED}[INFO] Aucun fichier spécifié.${NC}"
    echo "Fichiers disponibles dans src/main/resources/ :"
    ls src/main/resources/*.ecore
    echo ""
    read -p "Entrez le nom du fichier (ex: transport.ecore) : " FILE_INPUT

    # Si l'utilisateur appuie juste sur Entrée, on met une valeur par défaut
    if [ -z "$FILE_INPUT" ]; then
        FILE_INPUT="transport.ecore"
    fi
fi

echo -e "\n${GREEN}[STEP 0] Compilation du projet Java...${NC}"
# On compile silencieusement (-q)
mvn -q compile

if [ $? -ne 0 ]; then
    echo -e "${RED}[ERREUR] La compilation a échoué.${NC}"
    exit 1
fi

echo -e "${GREEN}[STEP 1-3] Lancement du Workflow sur : $FILE_INPUT ${NC}"
# Exécution via Maven (gère le classpath automatiquement)
mvn -q exec:java -Dexec.mainClass="org.example.MainWorkflow" -Dexec.args="$FILE_INPUT"

if [ $? -eq 0 ]; then
    echo -e "\n${BLUE}==============================================${NC}"
    echo -e "${GREEN}   SUCCÈS : Refactoring terminé.${NC}"
    echo -e "${BLUE}==============================================${NC}"
else
    echo -e "\n${RED}[ECHEC] Une erreur est survenue pendant l'exécution.${NC}"
fi