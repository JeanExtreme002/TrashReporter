# TrashReporter - Sistema de Autenticação

## 🔐 Funcionalidades Implementadas

### API (Backend)
✅ **Sistema de Autenticação JWT**
- Registro de usuários com email e senha
- Login com validação de credenciais
- Tokens JWT com expiração (30 minutos)
- Hash seguro de senhas com bcrypt
- Middleware de autenticação para endpoints protegidos

✅ **Endpoints de Autenticação**
- `POST /auth/register` - Cadastro de novos usuários
- `POST /auth/login` - Login e geração de token
- `GET /auth/me` - Informações do usuário atual

✅ **Endpoints Protegidos**
- `POST /api` - Criar reports (requer autenticação)
- `GET /api/{mac_address}` - Listar reports (requer autenticação)

✅ **Recursos Avançados**
- Usuário demo para testes (demo@gmail.com / 123456)
- Reports agora incluem email do usuário que fez o report
- Documentação automática em `/docs`
- Validação de dados com Pydantic

### Android App (Frontend)
✅ **Telas de Autenticação**
- SplashActivity com verificação automática de sessão
- LoginActivity com validação de campos e UX moderna
- RegisterActivity com confirmação de senha
- Redirecionamento automático baseado no estado de autenticação

✅ **Integração com API**
- Envio de tokens JWT em todas as requisições autenticadas
- Armazenamento seguro de sessão em SharedPreferences
- Verificação automática de expiração de token
- Fallback para login quando token expira

✅ **UX/UI Modernas**
- Design material com dark theme
- Campos de entrada com validação em tempo real
- Botões com estados de loading
- Mensagens de erro e sucesso informativas
- Informações de usuário demo visíveis

✅ **Funcionalidades de Sessão**
- Logout com confirmação
- Limpeza automática de dados de sessão
- Verificação de token em todas as operações
- Redirecionamento automático para login quando necessário

## 🧪 Como Testar

### 1. API
```bash
# Iniciar a API
cd api && python3 main.py

# Testar registro
curl -X POST "http://localhost:2000/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"name": "Seu Nome", "email": "seu@email.com", "password": "123456"}'

# Testar login
curl -X POST "http://localhost:2000/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email": "demo@gmail.com", "password": "123456"}'

# Documentação interativa
http://localhost:2000/docs
```

### 2. Usuário Demo
- **Email**: demo@gmail.com
- **Senha**: 123456

### 3. Fluxo no App
1. Abrir app → SplashScreen
2. Se não logado → Tela de Login
3. Fazer login ou registro
4. Ser redirecionado para MainActivity
5. Reportar lixo (agora requer autenticação)
6. Ver histórico (agora requer autenticação)
7. Logout pelo botão no canto superior direito

## 🔒 Segurança Implementada

- ✅ Senhas hasheadas com bcrypt
- ✅ Tokens JWT com expiração
- ✅ Validação de dados na API
- ✅ Headers de autorização nas requisições
- ✅ Verificação de token em endpoints protegidos
- ✅ Limpeza de sessão no logout
- ✅ Redirecionamento automático quando não autenticado

## 📱 Próximas Melhorias Possíveis

- [ ] Recuperação de senha por email
- [ ] Refresh tokens para sessões mais longas
- [ ] Perfil de usuário editável
- [ ] Histórico pessoal de reports por usuário
- [ ] Notificações push
- [ ] Biometria para login
- [ ] Backup de dados na nuvem

## 🎯 Status Atual
**✅ SISTEMA COMPLETO E FUNCIONAL**

O sistema de autenticação está totalmente implementado e testado. Usuários podem se registrar, fazer login, reportar lixo e ver histórico de forma segura. A API e o app trabalham em conjunto com tokens JWT para garantir que apenas usuários autenticados possam usar as funcionalidades principais.
