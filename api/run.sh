#!/bin/bash

# Script para executar a TrashReporter API

echo "🚀 TrashReporter API Setup"
echo "=========================="

# Verifica se Python está instalado
if ! command -v python3 &> /dev/null; then
    echo "❌ Python3 não encontrado. Instale Python 3.8+ para continuar."
    exit 1
fi

echo "✅ Python3 encontrado: $(python3 --version)"

# Verifica se pip está instalado
if ! command -v pip3 &> /dev/null; then
    echo "❌ pip3 não encontrado. Instale pip para continuar."
    exit 1
fi

echo "✅ pip3 encontrado"

# Instala dependências
echo "📦 Instalando dependências..."
pip3 install -r requirements.txt

if [ $? -eq 0 ]; then
    echo "✅ Dependências instaladas com sucesso!"
else
    echo "❌ Erro ao instalar dependências."
    exit 1
fi

echo ""
echo "🚀 Iniciando TrashReporter API..."
echo "================================"
echo "📡 URL da API: http://localhost:2000"
echo "📖 Documentação: http://localhost:2000/docs"
echo "🔍 Health Check: http://localhost:2000/health"
echo ""
echo "Para parar a API, pressione Ctrl+C"
echo ""

# Executa a API
python3 main.py
