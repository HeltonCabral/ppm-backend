# API de Deliberação em Mesa de Comité

## Endpoint

**POST** `/api/committee-decisions/bulk-deliberation`

## Descrição

Permite realizar deliberação em massa de demandas no comité com base em filtros específicos.

## Headers

- `X-User-Id` (opcional): Identificador do utilizador que está a realizar a deliberação

## Request Body

```json
{
  "filters": {
    "scoreMin": 70,
    "scoreMax": 100,
    "committeeId": "uuid-opcional",
    "directionCode": "DIR-COMERCIAL",
    "priority": "HIGH",
    "urgency": "HIGH",
    "riskLevel": "HIGH"
  },
  "decision": "APPROVE",
  "condition": "Condição para aprovação condicional (opcional)",
  "justification": "Justificação (obrigatória para REVISION_REQUESTED)"
}
```

### Campos do Request

#### Filters (obrigatório)

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `scoreMin` | BigDecimal | Não | Score mínimo para filtrar demandas |
| `scoreMax` | BigDecimal | Não | Score máximo para filtrar demandas |
| `committeeId` | UUID | Não | ID do comité (se não informado, considera todos) |
| `directionCode` | String(60) | Não | Código da direção (se não informado, considera todas) |
| `priority` | String(40) | Não | Prioridade inicial (LOW, MEDIUM, HIGH, CRITICAL) |
| `urgency` | String(40) | Não | Urgência (LOW, MEDIUM, HIGH, CRITICAL) |
| `riskLevel` | String(40) | Não | Nível de risco (NOT_EVALUATED, LOW, MEDIUM, HIGH, CRITICAL) |

#### Decision (obrigatório)

Decisões permitidas:
- `APPROVE` - Aprovar demanda
- `CONDITIONALLY_APPROVE` - Aprovar condicionalmente
- `REVISION_REQUESTED` - Solicitar revisão (justificação obrigatória)
- `BACKLOG` - Mover para backlog

#### Condition (opcional)

String(10000) - Condição para aprovação condicional

#### Justification (condicional)

String(10000) - Justificação da decisão
- **Obrigatória** quando `decision = REVISION_REQUESTED`
- Opcional para outras decisões

## Response

```json
{
  "totalProcessed": 25,
  "successCount": 23,
  "skipCount": 2,
  "processed": [
    {
      "id": "uuid",
      "code": "DEM-2024-001",
      "title": "Título da demanda",
      "previousStatus": "IN_STRATEGIC_COMMITTEE",
      "newStatus": "APPROVED",
      "decision": "APPROVE",
      "condition": null,
      "justification": null,
      "decidedAt": "2026-08-12T15:30:00Z",
      "decidedBy": "username"
    }
  ],
  "skipped": [
    {
      "id": "uuid",
      "code": "DEM-2024-002",
      "title": "Título da demanda",
      "reason": "Status não elegível para deliberação: ARCHIVED"
    }
  ]
}
```

### Campos do Response

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `totalProcessed` | Integer | Total de demandas processadas |
| `successCount` | Integer | Número de demandas deliberadas com sucesso |
| `skipCount` | Integer | Número de demandas ignoradas |
| `processed` | Array | Lista de demandas processadas com sucesso |
| `skipped` | Array | Lista de demandas ignoradas com motivo |

## Regras de Negócio

### Estados Elegíveis

Apenas demandas nos seguintes estados podem ser deliberadas:
- `IN_STRATEGIC_COMMITTEE`
- `READY_FOR_COMMITTEE`
- `PRIORITIZED`

### Transições de Estado

| Decisão | Estado Final | Decision Field | Approval Type |
|---------|--------------|----------------|---------------|
| APPROVE | APPROVED | APPROVED | NORMAL |
| CONDITIONALLY_APPROVE | APPROVED | CONDITIONALLY_APPROVED | CONDITIONAL |
| REVISION_REQUESTED | IN_ANALYSIS | REVISION_REQUESTED | - |
| BACKLOG | BACKLOG | BACKLOG | - |

### Validações

1. A decisão deve ser uma das permitidas
2. Se `decision = REVISION_REQUESTED`, a justificação é obrigatória
3. Apenas demandas não arquivadas são consideradas
4. Demandas já convertidas em projeto são ignoradas
5. Filtros são aplicados em conjunto (AND)

## Exemplos de Uso

### Aprovar todas as demandas com score >= 80

```json
{
  "filters": {
    "scoreMin": 80
  },
  "decision": "APPROVE"
}
```

### Aprovar condicionalmente demandas de alta urgência

```json
{
  "filters": {
    "urgency": "HIGH",
    "scoreMin": 70
  },
  "decision": "CONDITIONALLY_APPROVE",
  "condition": "Aprovação sujeita a alocação de orçamento"
}
```

### Solicitar revisão de demandas de alto risco

```json
{
  "filters": {
    "riskLevel": "HIGH"
  },
  "decision": "REVISION_REQUESTED",
  "justification": "Necessário plano de mitigação de riscos mais detalhado"
}
```

### Mover para backlog demandas de baixo score

```json
{
  "filters": {
    "scoreMax": 50
  },
  "decision": "BACKLOG"
}
```

### Aprovar demandas de um comité específico

```json
{
  "filters": {
    "committeeId": "123e4567-e89b-12d3-a456-426614174000",
    "scoreMin": 70
  },
  "decision": "APPROVE"
}
```

## Códigos de Erro

| Código HTTP | Mensagem | Descrição |
|-------------|----------|-----------|
| 400 | Decisão inválida | A decisão informada não é uma das permitidas |
| 400 | justification é obrigatória quando decision = REVISION_REQUESTED | Falta justificação obrigatória |
| 400 | Validation failed | Erro de validação nos campos do request |

## Histórico

Cada deliberação cria um registo no histórico da demanda com:
- Event Type conforme a decisão
- Descrição detalhada da deliberação
- Metadata com flag `bulkDeliberation: true`
- Actor ID do utilizador que realizou a deliberação

## Notas

- A operação é transacional - todas as demandas são processadas numa única transação
- Demandas que não podem ser processadas são adicionadas à lista `skipped` com o motivo
- A flag `inStrategicCommittee` é desmarcada após a deliberação
- O score lifecycle é atualizado conforme a transição de estado
