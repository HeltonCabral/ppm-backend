# 📚 Referência Técnica - Repriorização de Rankings

## 📊 Estrutura de Dados

### Tabela: demands

```sql
-- Campos novos adicionados
ALTER TABLE demands ADD COLUMN reprioritization_reason VARCHAR(80);
ALTER TABLE demands ADD COLUMN reprioritization_justification TEXT;
ALTER TABLE demands ADD COLUMN reprioritized_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE demands ADD COLUMN reprioritized_by VARCHAR(150);
```

### Campos Relevantes da Tabela

| Campo | Tipo | Descrição | Exemplo |
|-------|------|-----------|---------|
| id | UUID | Identificador único | 123e4567-e89b-12d3-a456-426614174000 |
| code | VARCHAR(20) | Código único | DEMAND-001 |
| portfolio_rank | INTEGER | Ranking global | 1, 2, 3... |
| direction_rank | INTEGER | Ranking por Direção | 1, 2, 3... |
| committee_rank | INTEGER | Ranking por Comité | 1, 2, 3... |
| status | VARCHAR(50) | Status da demanda | APPROVED |
| committee_decision | VARCHAR(60) | Decisão do comité | CONDITIONALLY_APPROVED |
| converted_project_id | UUID | Projeto convertido | NULL (se não convertido) |
| reprioritization_reason | VARCHAR(80) | Motivo | CRITICAL_DEPENDENCY |
| reprioritization_justification | TEXT | Justificação | Texto até 10000 chars |
| reprioritized_at | TIMESTAMP | Data/hora | 2026-08-12T10:30:45.123Z |
| reprioritized_by | VARCHAR(150) | Utilizador | user@example.com |

---

## 🔧 Fluxo Interno de Dados

### Input (ReprioritizePortfolioRankRequest)
```java
{
  newPosition: 1,              // Integer (1 até N demandas)
  reprioritizationReason: "...", // String (enum)
  reprioritizationJustification: "..." // String (10-10000 chars)
}
```

### Processing
1. **Validação de Status**
   ```java
   status == "APPROVED" || committeeDecision == "CONDITIONALLY_APPROVED"
   ```

2. **Busca de Demandas**
   ```
   WHERE status = 'APPROVED' OR committee_decision = 'CONDITIONALLY_APPROVED'
   AND deleted_at IS NULL
   AND converted_project_id IS NULL
   AND portfolio_rank IS NOT NULL
   ORDER BY portfolio_rank ASC
   ```

3. **Reorganização**
   ```
   1. Remove demanda da posição atual
   2. Insere na nova posição (index = newPosition - 1)
   3. Reconstrói lista ordenada
   4. Recalcula ranks: 1, 2, 3, 4, ...
   ```

4. **Persistência**
   ```java
   demands.saveAll(affectedDemands) // Transacional
   ```

5. **Histórico**
   ```java
   historyService.log(demand, 
     eventType = "PORTFOLIO_RANK_CHANGED",
     description = "Ranking global repriorizado: 5 → 1",
     metadata = {previousRank: 5, newRank: 1, reason: "..."}
   )
   ```

### Output (ReprioritizePortfolioRankResponse)
```java
{
  id: UUID,
  code: String,
  title: String,
  previousPortfolioRank: Integer,
  newPortfolioRank: Integer,
  status: String,
  reprioritizationReason: String,
  reprioritizationJustification: String,
  reprioritizedAt: Instant,
  reprioritizedBy: String,
  affectedDemands: [
    { id, code, title, previousPortfolioRank, newPortfolioRank },
    ...
  ]
}
```

---

## 🎯 Casos de Uso

### Caso 1: Mover para Cima (Posição Anterior)
```
Demandas originais:
A: rank=1  →  A: rank=2
B: rank=2  →  C: rank=1  ← repriorizar para 1
C: rank=3  →  B: rank=3
D: rank=4  →  D: rank=4

Demandas afetadas: A, B
```

### Caso 2: Mover para Baixo (Posição Posterior)
```
Demandas originais:
A: rank=1  →  A: rank=1
B: rank=2  →  C: rank=2
C: rank=3  →  D: rank=3
D: rank=4  →  B: rank=4  ← repriorizar para 4

Demandas afetadas: C, D
```

### Caso 3: Mover para Primeira Posição
```
Demandas originais:
A: rank=1  →  D: rank=1  ← repriorizar para 1
B: rank=2  →  A: rank=2
C: rank=3  →  B: rank=3
D: rank=4  →  C: rank=4

Demandas afetadas: A, B, C
```

### Caso 4: Mover para Última Posição
```
Demandas originais:
A: rank=1  →  A: rank=1
B: rank=2  →  C: rank=2  ← repriorizar para 3
C: rank=3  →  B: rank=3

Demandas afetadas: B, C
```

---

## 🛡️ Validações

### Validação 1: Status
```
❌ IN_ANALYSIS
❌ IN_PRIORITIZATION
❌ PRIORITIZED
❌ IN_STRATEGIC_COMMITTEE
❌ REJECTED
❌ CONVERTED_TO_PROJECT
❌ ARCHIVED
❌ UNDER_PRIORITIZATION
❌ READY_FOR_COMMITTEE
❌ BACKLOG

✅ APPROVED (sempre)
✅ CONDITIONALLY_APPROVED (via committeeDecision)
```

### Validação 2: newPosition
```
Min: 1
Max: total de demandas aprovadas/condicionadas não convertidas com portfolioRank
Diferente: currentRank da demanda
```

### Validação 3: reprioritizationReason
```
Obrigatório: sim
Max length: 80 caracteres
Valores válidos:
  - CRITICAL_DEPENDENCY
  - HIGHER_STRATEGIC_IMPACT
  - REGULATORY_URGENCY
  - BUDGET_CONSTRAINT
  - CAPACITY_CONSTRAINT
  - CURRENT_CYCLE_ALIGNMENT
  - COMMITTEE_DECISION
  - OTHER
```

### Validação 4: reprioritizationJustification
```
Obrigatória: sim
Min length: 10 caracteres
Max length: 10000 caracteres
```

---

## 🔐 Campos Imutáveis

Durante a repriorização, os seguintes campos **NÃO** são alterados:

```
❌ directionRank          - Permanece inalterado
❌ committeeRank          - Permanece inalterado
❌ scoreTotal             - Permanece inalterado
❌ scoreStatus            - Permanece inalterado
❌ scoreCalculatedAt      - Permanece inalterado
❌ preScore               - Permanece inalterado
❌ preScoreClassification - Permanece inalterado
❌ status                 - Permanece "APPROVED"
❌ approvalType           - Permanece inalterado
❌ committeeDecision      - Permanece inalterado
❌ committeeId            - Permanece inalterado
❌ responsibleCommitteeId - Permanece inalterado
❌ createdAt              - Permanece inalterado
❌ createdBy              - Permanece inalterado
```

**Campos alterados:**
```
✅ portfolioRank            - Recalculado (demanda e afetadas)
✅ reprioritizationReason   - Guardado
✅ reprioritizationJustification - Guardado
✅ reprioritizedAt          - Guardado (Instant.now())
✅ reprioritizedBy          - Guardado (actorId)
✅ updatedAt                - Atualizado (UpdateTimestamp)
✅ updatedBy                - Atualizado (actorId)
```

---

## 📍 Índices e Performance

### Índices Utilizados
```sql
idx_demands_status          -- Para filtrar por status
idx_demands_created_at      -- Para ordenação
-- Composite: (status, deleted_at, converted_project_id, portfolio_rank)
```

### Consulta Base (Query Specification)
```java
WHERE deleted_at IS NULL
  AND converted_project_id IS NULL
  AND (status = 'APPROVED' OR committee_decision = 'CONDITIONALLY_APPROVED')
  AND portfolio_rank IS NOT NULL
ORDER BY portfolio_rank ASC
```

---

## 🔄 Transação

```
@Transactional
Nível: REQUIRED (padrão)
Isolamento: READ_COMMITTED (padrão)
Timeout: nenhum

Operações atómicas:
1. Fetch demanda
2. Validate status
3. Fetch todas demandas aprovadas
4. Save all (com novo order)
5. Log history
```

Se qualquer operação falhar, toda a transação é revertida (ROLLBACK).

---

## 📈 Exemplo de Histórico

### Tabela: demand_history

```
event_type: PORTFOLIO_RANK_CHANGED
previous_status: APPROVED
new_status: APPROVED
description: Ranking global repriorizado: 5 → 1
actor_id: user@example.com
actor_name: João Silva
occurred_at: 2026-08-12T10:30:45.123Z
metadata: {
  "previousRank": "5",
  "newRank": "1",
  "reason": "CRITICAL_DEPENDENCY"
}
```

---

## 🐛 Troubleshooting

### Problema: "newPosition deve estar entre 1 e X"
**Causa:** 
- newPosition < 1 ou newPosition > total demandas
- Total demandas = count(status='APPROVED' OR committee_decision='CONDITIONALLY_APPROVED')

**Solução:**
- Validar que existe ao menos 1 demanda aprovada
- Calcular total dinâmico

### Problema: "newPosition não pode ser igual ao portfolioRank atual"
**Causa:** 
- newPosition == demanda.portfolioRank

**Solução:**
- Escolher posição diferente da atual

### Problema: "reprioritizationJustification deve ter entre 10 e 10000 caracteres"
**Causa:**
- Justificação vazia, nula ou < 10 chars ou > 10000 chars

**Solução:**
- Preencher com texto mínimo 10 caracteres

### Problema: Demanda não aparece na lista
**Causa:**
- Status não é APPROVED ou CONDITIONALLY_APPROVED
- Demanda já foi convertida em projeto (converted_project_id != NULL)
- Demanda foi deletada (deleted_at != NULL)
- portfolio_rank é NULL

**Solução:**
- Garantir que demanda cumpre critérios

---

## 📝 Documentação Relacionada

- `demand-reprioritization-api.md` - Especificação da API
- `IMPLEMENTATION_SUMMARY.md` - Resumo de implementação
- `TESTING_GUIDE.md` - Guia de testes

---

**Última atualização:** 2026-08-12
**Versão:** 1.0
