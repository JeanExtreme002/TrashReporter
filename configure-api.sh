#!/bin/bash

# Script para configurar o IP da API do TrashReporter

echo "🔧 Configurador do TrashReporter API"
echo "===================================="

# Detecta o IP atual da máquina
CURRENT_IP=$(hostname -I | awk '{print $1}' 2>/dev/null || ip route get 1 | awk '{print $7}' | head -1 2>/dev/null)

if [ -z "$CURRENT_IP" ]; then
    CURRENT_IP="192.168.0.13"
fi

echo "📡 IP atual detectado: $CURRENT_IP"
echo ""

# Pergunta qual configuração usar
echo "Escolha a configuração:"
echo "1) Dispositivo físico (usar IP atual: $CURRENT_IP)"
echo "2) Emulador Android (usar 10.0.2.2)"
echo "3) Configurar IP manualmente"
echo ""

read -p "Digite sua opção (1-3): " choice

case $choice in
    1)
        API_HOST="$CURRENT_IP"
        ;;
    2)
        API_HOST="10.0.2.2"
        ;;
    3)
        read -p "Digite o IP do servidor: " API_HOST
        ;;
    *)
        echo "❌ Opção inválida"
        exit 1
        ;;
esac

# Cria o arquivo .env
cat > .env << EOF
# Configurações do TrashReporter
# IP do servidor da API
API_HOST=$API_HOST
API_PORT=2000

# Para emulador use: API_HOST=10.0.2.2
# Para dispositivo físico use o IP real da sua máquina na rede local
EOF

# Copia para assets
cp .env app/src/main/assets/

echo "✅ Configuração salva!"
echo "📝 API configurada para: http://$API_HOST:2000"
echo ""
echo "⚠️  IMPORTANTE: Recompile e reinstale o aplicativo para aplicar as mudanças!"
echo "🔄 As mudanças nos assets só são aplicadas após rebuild do APK."
