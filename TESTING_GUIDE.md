# 🧪 Guia de Testes - Repriorização de Rankings

## Teste Manual com cURL/Postman

### 1️⃣ Listar Demandas Aprovadas

```bash
curl -X GET "http://localhost:8080/api/demands?status=APPROVED&status=CONDITIONALLY_APPROVED&page=0&size=20" \
  -H "X-User-Id: user@example.com"
```

**Esperado:** Lista de demandas com `portfolioRank` visível

---

### 2️⃣ Repriorizar uma Demanda

```bash
curl -X POST "http://localhost:8080/api/demands/{demandId}/reprioritize-portfolio-rank" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user@example.com" \
  -d '{
    "newPosition": 1,
    "reprioritizationReason": "CRITICAL_DEPENDENCY",
    "reprioritizationJustification": "Esta demanda é crítica porque bloqueia outras 5 iniciativas estratégicas."
  }'
```

**Esperado:** 
```json
{
  "id": "{demandId}",
  "code": "DEMAND-001",
  "title": "...",
  "previousPortfolioRank": 5,
  "newPortfolioRank": 1,
  "status": "APPROVED",
  "reprioritizationReason": "CRITICAL_DEPENDENCY",
  "reprioritizationJustification": "Esta demanda é crítica...",
  "reprioritizedAt": "2026-08-12T10:30:45.123Z",
  "reprioritizedBy": "user@example.com",
  "affectedDemands": [
    {
      "id": "...",
      "code": "DEMAND-002",
      "title": "...",
      "previousPortfolioRank": 1,
      "newPortfolioRank": 2
    },
    ...
  ]
}
```

---

## ❌ Testes de Erro

### Erro 1: Demanda não aprovada
```bash
curl -X POST "http://localhost:8080/api/demands/{demandId}/reprioritize-portfolio-rank" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user@example.com" \
  -d '{
    "newPosition": 1,
    "reprioritizationReason": "CRITICAL_DEPENDENCY",
    "reprioritizationJustification": "Teste de validação"
  }'
```

**Esperado (409 Conflict):**
```json
{
  "status": 409,
  "code": "DEMAND_NOT_APPROVED_FOR_REPRIORITIZATION",
  "message": "Apenas demandas aprovadas ou aprovadas condicionalmente podem ser repriorizadas",
  "details": {
    "status": "IN_ANALYSIS"
  }
}
```

---

### Erro 2: newPosition fora do intervalo
```bash
curl -X POST "http://localhost:8080/api/demands/{demandId}/reprioritize-portfolio-rank" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user@example.com" \
  -d '{
    "newPosition": 999,
    "reprioritizationReason": "CRITICAL_DEPENDENCY",
    "reprioritizationJustification": "Teste de validação"
  }'
```

**Esperado (400 Bad Request):**
```json
{
  "status": 400,
  "message": "newPosition deve estar entre 1 e 10"
}
```

---

### Erro 3: newPosition igual à posição atual
```bash
curl -X POST "http://localhost:8080/api/demands/{demandId}/reprioritize-portfolio-rank" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user@example.com" \
  -d '{
    "newPosition": 5,
    "reprioritizationReason": "CRITICAL_DEPENDENCY",
    "reprioritizationJustification": "Teste de validação"
  }'
```

**Esperado (400 Bad Request):**
```json
{
  "status": 400,
  "message": "newPosition não pode ser igual ao portfolioRank atual"
}
```

---

### Erro 4: Justificação muito curta
```bash
curl -X POST "http://localhost:8080/api/demands/{demandId}/reprioritize-portfolio-rank" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user@example.com" \
  -d '{
    "newPosition": 1,
    "reprioritizationReason": "CRITICAL_DEPENDENCY",
    "reprioritizationJustification": "Curta"
  }'
```

**Esperado (400 Bad Request):**
```json
{
  "status": 400,
  "message": "reprioritizationJustification deve ter entre 10 e 10000 caracteres"
}
```

---

### Erro 5: Motivo obrigatório
```bash
curl -X POST "http://localhost:8080/api/demands/{demandId}/reprioritize-portfolio-rank" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user@example.com" \
  -d '{
    "newPosition": 1,
    "reprioritizationJustification": "Teste de validação"
  }'
```

**Esperado (400 Bad Request):**
```json
{
  "status": 400,
  "message": "reprioritizationReason é obrigatório"
}
```

---

## ✅ Verificações Pós-Teste

### 1. Verificar que apenas portfolioRank foi alterado

```bash
curl -X GET "http://localhost:8080/api/demands/{demandId}" \
  -H "X-User-Id: user@example.com"
```

**Verificar no response:**
- ✅ `portfolioRank` alterado para nova posição
- ❌ `directionRank` não alterado
- ❌ `committeeRank` não alterado
- ❌ `scoreTotal` não alterado
- ❌ `preScore` não alterado
- ❌ `status` continua "APPROVED"
- ❌ `committeeDecision` não alterado

---

### 2. Verificar histórico

```bash
curl -X GET "http://localhost:8080/api/demands/{demandId}/history" \
  -H "X-User-Id: user@example.com"
```

**Esperado:** 
- Primeiro evento com `eventType: "PORTFOLIO_RANK_CHANGED"`
- `description: "Ranking global repriorizado: 5 → 1"`
- `metadata` contém `previousRank`, `newRank`, `reason`

---

### 3. Verificar que demandas afetadas foram recalculadas

```bash
curl -X GET "http://localhost:8080/api/demands?status=APPROVED&page=0&size=20" \
  -H "X-User-Id: user@example.com"
```

**Esperado:**
- Demanda reprioritizada: `portfolioRank: 1`
- Demanda que estava em 1: `portfolioRank: 2`
- Demanda que estava em 2: `portfolioRank: 3`
- ... e assim sucessivamente

---

## 📋 Checklist de Validação

| Cenário | Status | Notas |
|---------|--------|-------|
| Repriorizar para posição anterior | ✅ | Move para cima |
| Repriorizar para posição posterior | ✅ | Move para baixo |
| Repriorizar para primeira posição | ✅ | Desloca todas |
| Repriorizar para última posição | ✅ | Compacta topo |
| Rejeitar demanda não aprovada | ✅ | 409 Conflict |
| Rejeitar posição inválida | ✅ | 400 Bad Request |
| Rejeitar posição igual à atual | ✅ | 400 Bad Request |
| Rejeitar justificação curta | ✅ | 400 Bad Request |
| Registar no histórico | ✅ | PORTFOLIO_RANK_CHANGED |
| Manter campos protegidos | ✅ | directionRank, committeeRank, etc |
| Retornar demandas afetadas | ✅ | Lista com previousRank e newRank |
| Manter transação | ✅ | Tudo ou nada |

---

## 🚀 Teste Completo (Passo a Passo)

1. ✅ Criar 3 demandas aprovadas (A, B, C)
2. ✅ Atribuir portfolioRank: A=1, B=2, C=3
3. ✅ Repriorizar B para posição 1
4. ✅ Verificar: B=1, A=2, C=3
5. ✅ Repriorizar C para posição 2
6. ✅ Verificar: B=1, C=2, A=3
7. ✅ Verificar histórico de B e C
8. ✅ Verificar que directionRank e committeeRank não foram alterados
9. ✅ Verificar resposta com affectedDemands corretos

---

## 🔍 Debugging

### Verificar logs
```bash
tail -f target/logs/application.log | grep -i "portfolio"
```

### Verificar BD
```sql
-- Últimas repriorificações
SELECT id, code, portfolio_rank, reprioritized_at, reprioritized_by, reprioritization_reason
FROM demands
WHERE reprioritized_at IS NOT NULL
ORDER BY reprioritized_at DESC
LIMIT 10;
```

### Verificar histórico
```sql
SELECT event_type, previous_status, new_status, description, metadata
FROM demand_history
WHERE event_type = 'PORTFOLIO_RANK_CHANGED'
ORDER BY occurred_at DESC
LIMIT 10;
```

---

**Pronto para testar! 🎉**
