from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime
import uvicorn
import json

# Modelos de dados
class Coordinates(BaseModel):
    latitude: float
    longitude: float

class ReportRequest(BaseModel):
    image: str  # Base64 encoded image
    coords: Coordinates
    id: str  # MAC address

class ReportRecord(BaseModel):
    coords: str
    datetime: str
    status: str
    image: Optional[str] = None  # Base64 encoded image

# Inicializa a aplicação FastAPI
app = FastAPI(
    title="TrashReporter API",
    description="API para o aplicativo TrashReporter - Sistema de Denúncias de Lixo",
    version="1.0.0"
)

# Base de dados em memória (mock)
reports_db = {}

# Dados mockados para demonstração
mock_reports = {
#     "02:00:00:00:00:00": [
#         {
#             "coords": "-23.5505, -46.6333",
#             "datetime": "26/06/2025 14:30:00",
#             "status": "Processado",
#             "image": None
#         },
#         {
#             "coords": "-23.5489, -46.6388",
#             "datetime": "25/06/2025 09:15:30",
#             "status": "Em Análise",
#             "image": None
#         },
#         {
#             "coords": "-23.5512, -46.6298",
#             "datetime": "24/06/2025 16:45:12",
#             "status": "Resolvido",
#             "image": None
#         }
#     ],
#     "aa:bb:cc:dd:ee:ff": [
#         {
#             "coords": "-23.5520, -46.6350",
#             "datetime": "26/06/2025 11:20:45",
#             "status": "Pendente",
#             "image": None
#         },
#         {
#             "coords": "-23.5480, -46.6400",
#             "datetime": "23/06/2025 13:10:22",
#             "status": "Processado",
#             "image": None
#         }
#     ]
}

# Inicializa o banco de dados mock
reports_db.update(mock_reports)

@app.get("/")
async def root():
    """Endpoint raiz - informações da API"""
    return {
        "message": "TrashReporter API",
        "version": "1.0.0",
        "description": "API para sistema de denúncias de lixo urbano",
        "endpoints": {
            "POST /api": "Enviar novo report",
            "GET /api/{mac_address}": "Buscar reports por MAC address",
            "GET /health": "Status da API"
        }
    }

@app.get("/health")
async def health_check():
    """Endpoint de health check"""
    return {
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "total_reports": sum(len(reports) for reports in reports_db.values())
    }

@app.post("/api")
async def create_report(report: ReportRequest):
    """
    Endpoint para criar um novo report
    Recebe imagem em base64, coordenadas e MAC address
    """
    try:
        # Simula processamento da imagem
        if not report.image:
            raise HTTPException(status_code=400, detail="Imagem é obrigatória")
        
        if len(report.image) < 10:  # Validação básica do base64 (reduzida)
            raise HTTPException(status_code=400, detail="Imagem inválida")
        
        # Simula validação de coordenadas
        if not (-90 <= report.coords.latitude <= 90):
            raise HTTPException(status_code=400, detail="Latitude inválida")
        
        if not (-180 <= report.coords.longitude <= 180):
            raise HTTPException(status_code=400, detail="Longitude inválida")
        
        # Cria novo registro
        coords_str = f"{report.coords.latitude}, {report.coords.longitude}"
        current_time = datetime.now().strftime("%d/%m/%Y %H:%M:%S")
        
        new_record = {
            "coords": coords_str,
            "datetime": current_time,
            "status": "Recebido",
            "image": report.image  # Armazena a imagem base64
        }
        
        # Adiciona ao banco de dados
        if report.id not in reports_db:
            reports_db[report.id] = []
        
        reports_db[report.id].append(new_record)
        
        # Simula diferentes tempos de processamento
        import random
        processing_time = random.randint(500, 2000)
        
        return {
            "success": True,
            "message": "Report enviado com sucesso!",
            "report_id": len(reports_db[report.id]),
            "status": "Recebido",
            "estimated_processing_time_ms": processing_time,
            "coords": coords_str,
            "timestamp": current_time
        }
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Erro interno: {str(e)}")

@app.get("/api/{mac_address}")
async def get_reports(mac_address: str) -> List[ReportRecord]:
    """
    Endpoint para buscar reports por MAC address
    Retorna lista de reports do dispositivo
    """
    try:
        # Valida formato do MAC address (básico)
        if len(mac_address) < 12:
            raise HTTPException(status_code=400, detail="MAC address inválido")
        
        # Busca reports do dispositivo
        if mac_address not in reports_db:
            return []  # Retorna lista vazia se não encontrar
        
        reports = reports_db[mac_address]
        
        # Ordena por data (mais recente primeiro)
        sorted_reports = sorted(
            reports, 
            key=lambda x: datetime.strptime(x["datetime"], "%d/%m/%Y %H:%M:%S"),
            reverse=True
        )
        
        return sorted_reports
        
    except ValueError as e:
        raise HTTPException(status_code=400, detail="Formato de data inválido")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Erro interno: {str(e)}")

@app.get("/api/{mac_address}/count")
async def get_reports_count(mac_address: str):
    """
    Endpoint para obter contagem de reports por MAC address
    """
    if mac_address not in reports_db:
        return {"count": 0, "mac_address": mac_address}
    
    return {
        "count": len(reports_db[mac_address]),
        "mac_address": mac_address,
        "last_report": reports_db[mac_address][-1]["datetime"] if reports_db[mac_address] else None
    }

@app.delete("/api/{mac_address}")
async def delete_all_reports(mac_address: str):
    """
    Endpoint para deletar todos os reports de um dispositivo (para testes)
    """
    if mac_address not in reports_db:
        raise HTTPException(status_code=404, detail="Dispositivo não encontrado")
    
    count = len(reports_db[mac_address])
    del reports_db[mac_address]
    
    return {
        "success": True,
        "message": f"Todos os {count} reports do dispositivo {mac_address} foram removidos",
        "deleted_count": count
    }

@app.get("/api/stats")
async def get_stats():
    """
    Endpoint para estatísticas gerais da API
    """
    total_devices = len(reports_db)
    total_reports = sum(len(reports) for reports in reports_db.values())
    
    status_count = {}
    for reports in reports_db.values():
        for report in reports:
            status = report["status"]
            status_count[status] = status_count.get(status, 0) + 1
    
    return {
        "total_devices": total_devices,
        "total_reports": total_reports,
        "status_distribution": status_count,
        "active_devices": list(reports_db.keys()),
        "api_uptime": "Disponível"
    }

# Middleware para CORS (permite requisições do app Android)
from fastapi.middleware.cors import CORSMiddleware

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Em produção, especificar origens
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

if __name__ == "__main__":
    print("🚀 Iniciando TrashReporter API...")
    print("📡 Servidor rodando em: http://localhost:2000")
    print("📖 Documentação em: http://localhost:2000/docs")
    print("🔍 Health check em: http://localhost:2000/health")
    
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=2000,
        reload=True,
        log_level="info"
    )
