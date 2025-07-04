# 📱 TrashReporter - Configuração da API

## 🔧 Configuração Rápida

### Opção 1: Script Automático (Recomendado)
```bash
./configure-api.sh
```

### Opção 2: Configuração Manual

1. **Copie o arquivo de exemplo:**
   ```bash
   cp .env.example .env
   ```

2. **Edite o arquivo `.env`:**
   ```bash
   nano .env
   ```

3. **Configure o IP conforme seu setup:**
   
   **Para dispositivo físico:**
   ```
   API_HOST=192.168.0.13  # Seu IP real na rede
   API_PORT=2000
   ```
   
   **Para emulador Android:**
   ```
   API_HOST=10.0.2.2
   API_PORT=2000
   ```

4. **Copie para assets:**
   ```bash
   cp .env app/src/main/assets/
   ```

## 🚀 Como descobrir seu IP

```bash
# Linux/Mac
hostname -I | awk '{print $1}'

# Windows
ipconfig | findstr "IPv4"
```

## 📝 Estrutura do arquivo .env

```
# IP do servidor da API
API_HOST=192.168.0.13
API_PORT=2000
```

## ⚠️ Importante

- O arquivo `.env` não é versionado (está no .gitignore)
- Sempre copie para `app/src/main/assets/` após editar
- Recompile o app após mudanças no .env
- Para emulador, sempre use `10.0.2.2`
- Para dispositivo físico, use o IP real da sua máquina na rede

## 🔄 Aplicar mudanças

Após configurar o .env:
1. Recompile o aplicativo
2. Instale no dispositivo/emulador
3. As novas configurações serão aplicadas
