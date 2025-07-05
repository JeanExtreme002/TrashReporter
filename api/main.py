from fastapi import FastAPI, HTTPException, Depends, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel, EmailStr
from typing import List, Optional
from datetime import datetime, timedelta
import uvicorn
import json
import hashlib
import jwt
from passlib.context import CryptContext

# Configurações de segurança
SECRET_KEY = "trash_reporter_secret_key_2025"
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 30

# Configuração de hashing de senhas
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
security = HTTPBearer()

# Modelos de dados
class Coordinates(BaseModel):
    latitude: float
    longitude: float

class UserCreate(BaseModel):
    email: EmailStr
    password: str
    name: str

class UserLogin(BaseModel):
    email: EmailStr
    password: str

class User(BaseModel):
    id: str
    email: str
    name: str
    created_at: str
    is_active: bool = True

class Token(BaseModel):
    access_token: str
    token_type: str
    user: User

class ReportRequest(BaseModel):
    image: str  # Base64 encoded image
    coords: Coordinates
    id: str  # MAC address
    comment: Optional[str] = None  # User comment about the report

class ReportRecord(BaseModel):
    coords: str
    datetime: str
    status: str
    image: Optional[str] = None  # Base64 encoded image
    comment: Optional[str] = None  # User comment
    user_email: Optional[str] = None  # Email do usuário que fez o report

# Inicializa a aplicação FastAPI
app = FastAPI(
    title="TrashReporter API",
    description="API para o aplicativo TrashReporter - Sistema de Denúncias de Lixo",
    version="1.0.0"
)

# Base de dados em memória (mock)
reports_db = {}
users_db = {}  # Base de dados de usuários

# Funções de autenticação
def verify_password(plain_password, hashed_password):
    """Verifica se a senha está correta"""
    return pwd_context.verify(plain_password, hashed_password)

def get_password_hash(password):
    """Gera hash da senha"""
    return pwd_context.hash(password)

def create_access_token(data: dict, expires_delta: Optional[timedelta] = None):
    """Cria token JWT"""
    to_encode = data.copy()
    if expires_delta:
        expire = datetime.utcnow() + expires_delta
    else:
        expire = datetime.utcnow() + timedelta(minutes=15)
    to_encode.update({"exp": expire})
    encoded_jwt = jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)
    return encoded_jwt

def verify_token(credentials: HTTPAuthorizationCredentials = Depends(security)):
    """Verifica e decodifica o token JWT"""
    try:
        payload = jwt.decode(credentials.credentials, SECRET_KEY, algorithms=[ALGORITHM])
        email: str = payload.get("sub")
        if email is None:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Token inválido",
                headers={"WWW-Authenticate": "Bearer"},
            )
        return email
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token expirado",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except jwt.JWTError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token inválido",
            headers={"WWW-Authenticate": "Bearer"},
        )

def get_current_user(email: str = Depends(verify_token)):
    """Obtém o usuário atual baseado no token"""
    user = users_db.get(email)
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Usuário não encontrado",
        )
    return user

# Dados mockados para demonstração
mock_reports = {
    "02:00:00:00:00:00": [
        {
            "coords": "-23.5505, -46.6333",
            "datetime": "26/06/2025 14:30:00",
            "status": "Processado",
            "image": None,
            "comment": "Lixo acumulado na esquina da rua principal. Precisa de limpeza urgente.",
            "user_email": "demo@gmail.com"
        },
        {
            "coords": "-23.5489, -46.6388",
            "datetime": "25/06/2025 09:15:30",
            "status": "Em Análise",
            "image": None,
            "comment": "Entulho jogado no meio da calçada, dificultando a passagem de pedestres.",
            "user_email": "demo@gmail.com"
        },
        {
            "coords": "-23.5512, -46.6298",
            "datetime": "24/06/2025 16:45:12",
            "status": "Resolvido",
            "image": None,
            "comment": None,
            "user_email": "demo@gmail.com"
        }
    ]
}

# Usuário demo para testes
demo_user = {
    "id": "demo_user_001",
    "email": "demo@gmail.com",
    "name": "Usuário Demo",
    "password_hash": get_password_hash("123456"),
    "created_at": "01/07/2025 10:00:00",
    "is_active": True
}

# Inicializa o banco de dados mock
reports_db.update(mock_reports)
users_db["demo@gmail.com"] = demo_user

# ========== ENDPOINTS DE AUTENTICAÇÃO ==========

@app.post("/auth/register", response_model=Token)
async def register(user_data: UserCreate):
    """
    Endpoint para registrar um novo usuário
    """
    # Verificar se o email já existe
    if user_data.email in users_db:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Email já cadastrado"
        )
    
    # Criar novo usuário
    user_id = f"user_{len(users_db) + 1:03d}"
    hashed_password = get_password_hash(user_data.password)
    
    new_user = {
        "id": user_id,
        "email": user_data.email,
        "name": user_data.name,
        "password_hash": hashed_password,
        "created_at": datetime.now().strftime("%d/%m/%Y %H:%M:%S"),
        "is_active": True
    }
    
    # Salvar usuário
    users_db[user_data.email] = new_user
    
    # Criar token de acesso
    access_token_expires = timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    access_token = create_access_token(
        data={"sub": user_data.email}, expires_delta=access_token_expires
    )
    
    # Criar objeto User para resposta (sem senha)
    user_response = User(
        id=new_user["id"],
        email=new_user["email"],
        name=new_user["name"],
        created_at=new_user["created_at"],
        is_active=new_user["is_active"]
    )
    
    return {
        "access_token": access_token,
        "token_type": "bearer",
        "user": user_response
    }

@app.post("/auth/login", response_model=Token)
async def login(user_credentials: UserLogin):
    """
    Endpoint para login do usuário
    """
    # Verificar se o usuário existe
    user = users_db.get(user_credentials.email)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Email ou senha incorretos"
        )
    
    # Verificar senha
    if not verify_password(user_credentials.password, user["password_hash"]):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Email ou senha incorretos"
        )
    
    # Verificar se usuário está ativo
    if not user["is_active"]:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Usuário inativo"
        )
    
    # Criar token de acesso
    access_token_expires = timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    access_token = create_access_token(
        data={"sub": user_credentials.email}, expires_delta=access_token_expires
    )
    
    # Criar objeto User para resposta (sem senha)
    user_response = User(
        id=user["id"],
        email=user["email"],
        name=user["name"],
        created_at=user["created_at"],
        is_active=user["is_active"]
    )
    
    return {
        "access_token": access_token,
        "token_type": "bearer",
        "user": user_response
    }

@app.get("/auth/me", response_model=User)
async def get_me(current_user: dict = Depends(get_current_user)):
    """
    Endpoint para obter informações do usuário atual
    """
    user_response = User(
        id=current_user["id"],
        email=current_user["email"],
        name=current_user["name"],
        created_at=current_user["created_at"],
        is_active=current_user["is_active"]
    )
    return user_response

# ========== ENDPOINTS PRINCIPAIS ==========

@app.get("/")
async def root():
    """Endpoint raiz - informações da API"""
    return {
        "message": "TrashReporter API v2.0",
        "version": "2.0.0",
        "description": "API para sistema de denúncias de lixo urbano com autenticação",
        "features": ["Sistema de autenticação", "Reports com usuários", "JWT tokens"],
        "endpoints": {
            "POST /auth/register": "Registrar novo usuário",
            "POST /auth/login": "Fazer login",
            "GET /auth/me": "Informações do usuário atual",
            "POST /api": "Enviar novo report (requer auth)",
            "GET /api/{mac_address}": "Buscar reports por MAC (requer auth)",
            "GET /health": "Status da API"
        },
        "demo_user": {
            "email": "demo@gmail.com",
            "password": "123456",
            "note": "Use essas credenciais para testar a API"
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
async def create_report(report: ReportRequest, current_user: dict = Depends(get_current_user)):
    """
    Endpoint para criar um novo report (AUTENTICADO)
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
            "image": report.image,  # Armazena a imagem base64
            "comment": report.comment,  # Armazena o comentário do usuário
            "user_email": current_user["email"]  # Email do usuário logado
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
async def get_reports(mac_address: str, current_user: dict = Depends(get_current_user)) -> List[ReportRecord]:
    """
    Endpoint para buscar reports por MAC address (AUTENTICADO)
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
