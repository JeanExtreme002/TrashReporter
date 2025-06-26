# TrashReporter API

API em Python usando FastAPI para o aplicativo TrashReporter - Sistema de Denúncias de Lixo Urbano.

## 🚀 Como Executar

### 1. Instalar Dependências
```bash
pip install -r requirements.txt
```

### 2. Executar a API
```bash
python main.py
```

A API estará disponível em: `http://localhost:2000`

## 📖 Documentação

- **Swagger UI**: http://localhost:2000/docs
- **ReDoc**: http://localhost:2000/redoc
- **Health Check**: http://localhost:2000/health

## 🛠️ Endpoints

### `POST /api`
Criar um novo report de lixo.

**Request Body:**
```json
{
  "image": "base64_encoded_image_string",
  "coords": {
    "latitude": -23.5505,
    "longitude": -46.6333
  },
  "id": "aa:bb:cc:dd:ee:ff"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Report enviado com sucesso!",
  "report_id": 1,
  "status": "Recebido",
  "estimated_processing_time_ms": 1250,
  "coords": "-23.5505, -46.6333",
  "timestamp": "26/06/2025 14:30:00"
}
```

### `GET /api/{mac_address}`
Buscar todos os reports de um dispositivo específico.

**Response:**
```json
[
  {
    "coords": "-23.5505, -46.6333",
    "datetime": "26/06/2025 14:30:00",
    "status": "Processado"
  },
  {
    "coords": "-23.5489, -46.6388",
    "datetime": "25/06/2025 09:15:30",
    "status": "Em Análise"
  }
]
```

### `GET /api/{mac_address}/count`
Obter contagem de reports de um dispositivo.

### `GET /api/stats`
Estatísticas gerais da API.

### `DELETE /api/{mac_address}`
Deletar todos os reports de um dispositivo (para testes).

## 🧪 Dados Mockados

A API vem com dados de exemplo para dois dispositivos:
- `02:00:00:00:00:00` - 3 reports
- `aa:bb:cc:dd:ee:ff` - 2 reports

## 📱 Status Possíveis

- **Recebido**: Report acabou de ser enviado
- **Em Análise**: Report está sendo processado
- **Processado**: Report foi analisado
- **Resolvido**: Problema foi solucionado
- **Pendente**: Aguardando ação

## 🔧 Testando a API

### Usando curl:

**Criar um report:**
```bash
curl -X POST "http://localhost:2000/api" \
  -H "Content-Type: application/json" \
  -d '{
    "image": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAAAAAAAD...",
    "coords": {
      "latitude": -23.5505,
      "longitude": -46.6333
    },
    "id": "aa:bb:cc:dd:ee:ff"
  }'
```

**Buscar reports:**
```bash
curl "http://localhost:2000/api/aa:bb:cc:dd:ee:ff"
```

**Health check:**
```bash
curl "http://localhost:2000/health"
```

## 🐳 Docker (Opcional)

Para executar com Docker:

```bash
# Criar Dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY main.py .
EXPOSE 2000
CMD ["python", "main.py"]

# Build e run
docker build -t trashreporter-api .
docker run -p 2000:2000 trashreporter-api
```

## 🔒 Segurança

⚠️ **Esta é uma API de desenvolvimento/mock. Para produção, implemente:**

- Autenticação/Autorização
- Validação rigorosa de dados
- Rate limiting
- Logs de segurança
- HTTPS
- Banco de dados persistente
- Backup de dados

## 🎯 Integração com App Android

O app Android TrashReporter está configurado para usar esta API:
- Endpoint de envio: `POST http://localhost:2000/api`
- Endpoint de histórico: `GET http://localhost:2000/api/{mac_address}`

Certifique-se de que a API esteja rodando antes de testar o aplicativo!
