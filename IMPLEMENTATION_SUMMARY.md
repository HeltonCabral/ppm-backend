# 🎯 Resumo da Implementação: Repriorização de Rankings de Demandas

## ✅ Backend - Implementado e Compilado com Sucesso

### 📝 Arquivos Criados

#### 1. **Enum ReprioritizationReason.java**
```
🔹 CRITICAL_DEPENDENCY
🔹 HIGHER_STRATEGIC_IMPACT
🔹 REGULATORY_URGENCY
🔹 BUDGET_CONSTRAINT
🔹 CAPACITY_CONSTRAINT
🔹 CURRENT_CYCLE_ALIGNMENT
🔹 COMMITTEE_DECISION
🔹 OTHER
```

#### 2. **Migration BD: V20260812_01__demand_reprioritization.sql**
```sql
ALTER TABLE demands ADD COLUMN reprioritization_reason VARCHAR(80);
ALTER TABLE demands ADD COLUMN reprioritization_justification TEXT;
ALTER TABLE demands ADD COLUMN reprioritized_at TIMESTAMP;
ALTER TABLE demands ADD COLUMN reprioritized_by VARCHAR(150);
```

#### 3. **Documentação Frontend: demand-reprioritization-api.md**
- Especificação completa da API
- Instruções de integração
- Exemplos de requisição/resposta
- Validações e comportamentos

### 📝 Arquivos Modificados

#### 1. **Demand.java** - Adicionados 4 campos
```java
private String reprioritizationReason;
private String reprioritizationJustification;
private Instant reprioritizedAt;
private String reprioritizedBy;
```

#### 2. **DemandDtos.java** - Adicionados 3 registos
```java
record ReprioritizePortfolioRankRequest {
  Integer newPosition
  String reprioritizationReason
  String reprioritizationJustification
}

record ReprioritizePortfolioRankResponse {
  UUID id, code, title
  Integer previousPortfolioRank, newPortfolioRank
  String status
  String reprioritizationReason, reprioritizationJustification
  Instant reprioritizedAt
  String reprioritizedBy
  List<RankedDemandInfo> affectedDemands
}

record RankedDemandInfo {
  UUID id, code, title
  Integer previousPortfolioRank, newPortfolioRank
}
```

#### 3. **DemandService.java** - Novo método público
```java
@Transactional
public ReprioritizePortfolioRankResponse reprioritizePortfolioRank(
  UUID id, 
  ReprioritizePortfolioRankRequest req, 
  String actorId
)
```

**Funcionalidade:**
- ✅ Valida status (APPROVED | CONDITIONALLY_APPROVED)
- ✅ Busca demandas aprovadas não convertidas
- ✅ Valida newPosition (1 ≤ x ≤ max, ≠ current)
- ✅ Reorganiza e recalcula portfolioRank sequencialmente
- ✅ Guarda motivo, justificação, utilizador, data
- ✅ Registra no histórico
- ✅ Retorna resposta com demandas afetadas

#### 4. **DemandController.java** - Novo endpoint
```java
@PostMapping("/{id}/reprioritize-portfolio-rank")
public ReprioritizePortfolioRankResponse reprioritizePortfolioRank(
  @PathVariable UUID id,
  @Valid @RequestBody ReprioritizePortfolioRankRequest req,
  @RequestHeader(value = "X-User-Id", required = false) String user
)
```

## 🎨 Frontend - Instruções de Integração

### Modal "Repriorizar ranking global"

**Campos do Formulário:**
1. **Posição Atual** (read-only)
   - Exibe `portfolioRank` atual
   - Ex: "Posição atual: 5"

2. **Nova Posição** (input numérico obrigatório)
   - Min: 1
   - Max: total de demandas aprovadas
   - Validação: ≠ posição atual

3. **Motivo da Repriorização** (dropdown obrigatório)
   - 8 opções com labels traduzidos

4. **Justificação** (textarea obrigatório)
   - Min: 10 caracteres
   - Max: 10000 caracteres
   - Contador de caracteres

**Nota Informativa:**
```
ℹ️ Esta alteração afeta apenas o ranking global do portfólio.
   O ranking por Direção e por Comité não será alterado.
```

**Comportamento:**
- ✅ Validação em tempo real
- ✅ POST para `/api/demands/{id}/reprioritize-portfolio-rank`
- ✅ Sucesso: fechar modal + recarregar lista + notificação
- ✅ Erro: exibir mensagem específica, manter modal aberto

## 🔒 Campos Protegidos (Não Alterados)

```
❌ directionRank        - Ranking por Direção
❌ committeeRank        - Ranking por Comité
❌ scoreTotal           - Score total
❌ preScore             - Pré-score
❌ status               - Status da demanda
❌ approvalType         - Tipo de aprovação
❌ committeeDecision    - Decisão do comité
❌ committeeId          - Comité responsável
❌ responsibleCommitteeId
```

## ✨ Funcionalidades Adicionais

### Histórico
Cada repriorização é registrada com:
- Event Type: `PORTFOLIO_RANK_CHANGED`
- Description: "Ranking global repriorizado: 5 → 1"
- Metadata: previousRank, newRank, reason

### Resposta Afetadas
Além da demanda reprioritizada, a resposta inclui:
```json
"affectedDemands": [
  {
    "id": "...",
    "code": "DEMAND-002",
    "title": "Melhorar performance",
    "previousPortfolioRank": 1,
    "newPortfolioRank": 2
  },
  ...
]
```

## 🧪 Validações Implementadas

| Validação | Status |
|-----------|--------|
| Apenas APPROVED ou CONDITIONALLY_APPROVED | ✅ |
| newPosition entre 1 e máximo | ✅ |
| newPosition ≠ currentRank | ✅ |
| reprioritizationReason obrigatório | ✅ |
| reprioritizationJustification obrigatória (min 10) | ✅ |
| Busca todas demandas (sem filtros) | ✅ |
| Operação transacional | ✅ |
| Recalcula portfolioRank sequencialmente | ✅ |

## 📊 Fluxo de Execução

```
1. Frontend submete POST /api/demands/{id}/reprioritize-portfolio-rank
   ↓
2. Backend busca demanda
   ↓
3. Valida status e campos obrigatórios
   ↓
4. Busca todas demandas aprovadas não convertidas
   ↓
5. Reorganiza lista na nova posição
   ↓
6. Recalcula portfolioRank sequencialmente (1, 2, 3, ...)
   ↓
7. Salva todas as demandas (transacional)
   ↓
8. Registra no histórico
   ↓
9. Retorna ReprioritizePortfolioRankResponse com demandas afetadas
   ↓
10. Frontend recarrega lista inteira do backend
```

## 📦 Arquivos Gerados

| Arquivo | Tipo | Status |
|---------|------|--------|
| ReprioritizationReason.java | Enum | ✅ Criado |
| V20260812_01__demand_reprioritization.sql | Migration | ✅ Criado |
| demand-reprioritization-api.md | Documentação | ✅ Criado |
| Demand.java | Entity | ✅ Modificado |
| DemandDtos.java | DTOs | ✅ Modificado |
| DemandService.java | Service | ✅ Modificado |
| DemandController.java | Controller | ✅ Modificado |

## ✨ Próximos Passos (Frontend)

1. Criar modal "Repriorizar ranking global"
2. Implementar formulário com validações
3. Consumir endpoint POST
4. Exibir nota informativa
5. Recarregar lista após sucesso
6. Testes unitários e integração

---

**Compilação:** ✅ Sucesso
**Pronto para testes:** ✅ Sim
**Documentação:** ✅ Completa
