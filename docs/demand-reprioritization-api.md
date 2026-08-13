# Reprioritização de Rankings de Demandas Aprovadas

## Endpoint Backend

### POST /api/demands/{demandId}/reprioritize-portfolio-rank

Reprioriza o ranking global de uma demanda aprovada ou aprovada condicionalmente.

**Headers:**
- `X-User-Id` (opcional): Identificador do utilizador

**Path Parameters:**
- `demandId` (UUID): Identificador da demanda

**Request Body:**
```json
{
  "newPosition": 1,
  "reprioritizationReason": "CRITICAL_DEPENDENCY",
  "reprioritizationJustification": "A demanda é dependência crítica para outras iniciativas aprovadas."
}
```

**Response 200 OK:**
```json
{
  "id": "d3f5e2c1-9a8b-4e6f-a1c2-b3d4e5f6a7b8",
  "code": "DEMAND-001",
  "title": "Implementar API REST",
  "previousPortfolioRank": 5,
  "newPortfolioRank": 1,
  "status": "APPROVED",
  "reprioritizationReason": "CRITICAL_DEPENDENCY",
  "reprioritizationJustification": "A demanda é dependência crítica para outras iniciativas aprovadas.",
  "reprioritizedAt": "2026-08-12T10:30:45.123Z",
  "reprioritizedBy": "user@example.com",
  "affectedDemands": [
    {
      "id": "a1b2c3d4-e5f6-47a8-b9c0-d1e2f3a4b5c6",
      "code": "DEMAND-002",
      "title": "Melhorar performance",
      "previousPortfolioRank": 1,
      "newPortfolioRank": 2
    },
    {
      "id": "f7g8h9i0-j1k2-43l4-m5n6-o7p8q9r0s1t2",
      "code": "DEMAND-003",
      "title": "Aumentar segurança",
      "previousPortfolioRank": 2,
      "newPortfolioRank": 3
    }
  ]
}
```

**Error 409 Conflict:**
```json
{
  "status": 409,
  "code": "DEMAND_NOT_APPROVED_FOR_REPRIORITIZATION",
  "message": "Apenas demandas aprovadas ou aprovadas condicionalmente podem ser repriorizadas",
  "details": {
    "status": "IN_ANALYSIS",
    "committeeDecision": null
  }
}
```

**Error 400 Bad Request:**
```json
{
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "newPosition deve estar entre 1 e 10"
}
```

## Enumerações

### ReprioritizationReason
- `CRITICAL_DEPENDENCY` - Dependência crítica
- `HIGHER_STRATEGIC_IMPACT` - Impacto estratégico superior
- `REGULATORY_URGENCY` - Urgência regulatória
- `BUDGET_CONSTRAINT` - Restrição orçamentária
- `CAPACITY_CONSTRAINT` - Restrição de capacidade
- `CURRENT_CYCLE_ALIGNMENT` - Alinhamento com ciclo atual
- `COMMITTEE_DECISION` - Decisão do comité
- `OTHER` - Outro

## Validações no Backend

1. ✅ Apenas demandas com status `APPROVED` ou `committeeDecision` = `CONDITIONALLY_APPROVED` podem ser repriorizadas
2. ✅ `newPosition` deve estar entre 1 e o total de demandas aprovadas/condicionadas não convertidas
3. ✅ `newPosition` não pode ser igual ao `portfolioRank` atual
4. ✅ `reprioritizationReason` é obrigatório (max 80 caracteres)
5. ✅ `reprioritizationJustification` é obrigatória (min 10, max 10000 caracteres)
6. ✅ Considera todas as demandas aprovadas do portfólio (sem filtros)
7. ✅ A operação é transacional

## Frontend - Modal de Repriorização

### Título do Modal
"Repriorizar ranking global"

### Campos do Formulário

1. **Posição Atual** (read-only)
   - Exibir valor de `portfolioRank` da demanda
   - Campo não-editável
   - Ex: "Posição atual: 5"

2. **Nova Posição** (required)
   - Input numérico
   - Min: 1
   - Max: total de demandas aprovadas (obtido dinamicamente)
   - Validar que não é igual à posição atual

3. **Motivo da Repriorização** (required, select)
   - Dropdown com enumeração `ReprioritizationReason`
   - Labels traduzidos:
     - `CRITICAL_DEPENDENCY` → "Dependência crítica"
     - `HIGHER_STRATEGIC_IMPACT` → "Impacto estratégico superior"
     - `REGULATORY_URGENCY` → "Urgência regulatória"
     - `BUDGET_CONSTRAINT` → "Restrição orçamentária"
     - `CAPACITY_CONSTRAINT` → "Restrição de capacidade"
     - `CURRENT_CYCLE_ALIGNMENT` → "Alinhamento com ciclo atual"
     - `COMMITTEE_DECISION` → "Decisão do comité"
     - `OTHER` → "Outro"

4. **Justificação** (required, textarea)
   - Mínimo 10 caracteres
   - Máximo 10000 caracteres
   - Placeholder: "Explique os motivos da repriorização..."
   - Mostrar contador de caracteres

### Nota Informativa
Exibir acima dos campos do formulário:
```
ℹ️ Esta alteração afeta apenas o ranking global do portfólio.
O ranking por Direção e por Comité não será alterado.
```

### Comportamento

1. **Ao abrir o modal:**
   - Carregar total de demandas aprovadas para saber o máximo de posições
   - Desabilitar "Nova Posição" se for inferior a 1 ou superior ao máximo

2. **Durante preenchimento:**
   - Validar em tempo real:
     - Nova posição entre 1 e máximo
     - Nova posição diferente de posição atual
     - Justificação com mínimo de caracteres
   - Desabilitar botão "Repriorizar" enquanto houver erros

3. **Após submissão:**
   - Enviar POST para `/api/demands/{demandId}/reprioritize-portfolio-rank`
   - Mostrar loading spinner
   - Se sucesso (200):
     - Fechar modal
     - Recarregar a lista de demandas a partir do backend
     - Mostrar notificação de sucesso: "Demanda repriorizada com sucesso"
   - Se erro:
     - Exibir mensagem de erro específica do backend
     - Manter modal aberto

4. **Lista após repriorização:**
   - Recarregar lista inteira do backend (GET /api/demands/...)
   - Os `portfolioRank` serão atualizado com novos valores
   - As demandas afetadas (vizinhos) também terão seus ranks atualizados

### Campos que NÃO devem ser alterados

Confirmação: Os seguintes campos NÃO são afetados por esta operação e permanecem inalterados:
- ❌ `directionRank` - Mantém ranking por Direção
- ❌ `committeeRank` - Mantém ranking por Comité
- ❌ `scoreTotal` - Score não é recalculado
- ❌ `preScore` - Pré-score não é recalculado
- ❌ `status` - Status permanece "APPROVED"
- ❌ `approvalType` - Tipo de aprovação mantém-se
- ❌ `committeeDecision` - Decisão do comité mantém-se
- ❌ `committeeId` / `responsibleCommitteeId` - Comité responsável mantém-se

## Histórico

Cada repriorização é registrada no histórico da demanda com:
- **eventType:** `PORTFOLIO_RANK_CHANGED`
- **description:** "Ranking global repriorizado: X → Y"
- **metadata:**
  - `previousRank`: Posição anterior
  - `newRank`: Nova posição
  - `reason`: Motivo da repriorização

## Sequência de Operação

```
Cliente Frontend
       │
       ├─→ GET /api/demands/{demandId}  [verificar status]
       │
       └─→ POST /api/demands/{demandId}/reprioritize-portfolio-rank
           {
             "newPosition": 1,
             "reprioritizationReason": "CRITICAL_DEPENDENCY",
             "reprioritizationJustification": "..."
           }
           │
           ├─→ Validar status (APPROVED ou CONDITIONALLY_APPROVED)
           ├─→ Buscar todas demandas aprovadas/condicionadas
           ├─→ Reordenar e recalcular portfolioRank
           ├─→ Guardar histórico
           │
           └─→ 200 OK + ReprioritizePortfolioRankResponse
               │
               └─→ GET /api/demands... [recarregar lista]
```
