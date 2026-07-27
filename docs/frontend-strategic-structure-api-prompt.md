# Prompt: integrar Pilares, Elementos e Objetivos Estrategicos

Integra no frontend as APIs de Pilares Estrategicos, Elementos Estrategicos e Objetivos Estrategicos, reutilizando exatamente o mesmo cliente HTTP, configuracao da API, hooks, gestao de cache, estados de carregamento e tratamento de erros ja utilizados na integracao de Ciclos Estrategicos.

Nao cries outro cliente HTTP, nao dupliques a base URL e nao alteres o design visual. Implementa apenas a integracao das APIs e os respetivos tipos de request e response.

## Base URL

O backend utiliza o context path `/api`. Reutiliza a configuracao ja existente no frontend.

## 1. Pilares Estrategicos

### Listar pilares

    GET /strategic-pillars

Response `200 OK`:

    [
      {
        "id": "76ef3546-0426-45b4-bd48-c0c30266b970",
        "name": "Transformacao Digital",
        "description": "Digitalizacao dos servicos e processos",
        "icon": "devices",
        "strategicPlanId": "f294b743-6ed6-4df2-b9ee-d706064dd665",
        "strategicElements": []
      }
    ]

### Consultar pilar

    GET /strategic-pillars/{pillarId}

Response `200 OK`:

    {
      "id": "76ef3546-0426-45b4-bd48-c0c30266b970",
      "name": "Transformacao Digital",
      "description": "Digitalizacao dos servicos e processos",
      "icon": "devices",
      "strategicPlanId": "f294b743-6ed6-4df2-b9ee-d706064dd665",
      "strategicElements": []
    }

### Criar pilar

    POST /strategic-pillars
    Content-Type: application/json

Request:

    {
      "name": "Transformacao Digital",
      "description": "Digitalizacao dos servicos e processos",
      "icon": "devices",
      "strategicPlanId": "f294b743-6ed6-4df2-b9ee-d706064dd665"
    }

Response `201 Created`: objeto `StrategicPillar` criado.

### Atualizar pilar

    PUT /strategic-pillars/{pillarId}
    Content-Type: application/json

Request:

    {
      "name": "Transformacao Digital e Inovacao",
      "description": "Digitalizacao e inovacao dos servicos e processos",
      "icon": "devices",
      "strategicPlanId": "f294b743-6ed6-4df2-b9ee-d706064dd665"
    }

Response `200 OK`: objeto `StrategicPillar` atualizado.

### Eliminar pilar

    DELETE /strategic-pillars/{pillarId}

Response `204 No Content`.

## 2. Elementos Estrategicos

### Listar elementos

    GET /strategic-elements

Response `200 OK`:

    [
      {
        "id": "3fc89f36-c4b2-4344-a3f8-a7106b912778",
        "name": "Experiencia Digital",
        "description": "Melhorar os canais e a experiencia digital",
        "icon": "smartphone",
        "strategicPillar": {
          "id": "76ef3546-0426-45b4-bd48-c0c30266b970",
          "name": "Transformacao Digital"
        },
        "strategicObjectives": []
      }
    ]

### Consultar elemento

    GET /strategic-elements/{elementId}

Response `200 OK`: objeto `StrategicElement`.

### Criar elemento

    POST /strategic-elements
    Content-Type: application/json

Request:

    {
      "name": "Experiencia Digital",
      "description": "Melhorar os canais e a experiencia digital",
      "icon": "smartphone",
      "strategicPillarId": "76ef3546-0426-45b4-bd48-c0c30266b970"
    }

Response `201 Created`: objeto `StrategicElement` criado.

### Atualizar elemento

    PUT /strategic-elements/{elementId}
    Content-Type: application/json

Request:

    {
      "name": "Experiencia Digital Omnicanal",
      "description": "Melhorar a experiencia digital em todos os canais",
      "icon": "smartphone",
      "strategicPillarId": "76ef3546-0426-4344-a3f8-a7106b912778"
    }

Response `200 OK`: objeto `StrategicElement` atualizado.

### Eliminar elemento

    DELETE /strategic-elements/{elementId}

Response `204 No Content`.

## 3. Objetivos Estrategicos

### Listar objetivos

    GET /strategic-objectives

Response `200 OK`:

    [
      {
        "id": "75288caf-b902-4552-9639-903d1590da8f",
        "name": "Aumentar a adocao digital",
        "description": "Aumentar a utilizacao dos canais digitais",
        "fiscalYear": 2027,
        "startYear": 2027,
        "endYear": 2029,
        "perspective": "Cliente",
        "strategicPlanId": "f294b743-6ed6-4df2-b9ee-d706064dd665",
        "strategicElement": {
          "id": "3fc89f36-c4b2-4344-a3f8-a7106b912778",
          "name": "Experiencia Digital"
        },
        "annualTargets": [
          {
            "id": "a7cedb69-72af-4561-942a-a884b3bfa5d6",
            "year": 2027,
            "targetLabel": "Adocao digital",
            "targetValue": 60,
            "weight": 1
          }
        ],
        "kpis": [
          {
            "id": "558d988a-3d27-46e8-a9b4-bce4d0ed46c7",
            "name": "Taxa de adocao digital",
            "target": "60%",
            "current": 45,
            "goal": 60
          }
        ]
      }
    ]

### Consultar objetivo

    GET /strategic-objectives/{objectiveId}

Response `200 OK`: objeto `StrategicObjective`.

### Criar objetivo

    POST /strategic-objectives
    Content-Type: application/json

Request:

    {
      "name": "Aumentar a adocao digital",
      "description": "Aumentar a utilizacao dos canais digitais",
      "fiscalYear": 2027,
      "startYear": 2027,
      "endYear": 2029,
      "perspective": "Cliente",
      "strategicElementId": "3fc89f36-c4b2-4344-a3f8-a7106b912778",
      "strategicPlanId": "f294b743-6ed6-4df2-b9ee-d706064dd665",
      "annualTargets": [
        {
          "year": 2027,
          "targetLabel": "Adocao digital",
          "targetValue": 60,
          "weight": 1
        }
      ],
      "kpis": [
        {
          "name": "Taxa de adocao digital",
          "target": "60%",
          "current": 45,
          "goal": 60
        }
      ]
    }

Response `201 Created`: objeto `StrategicObjective` criado.

### Atualizar objetivo

    PUT /strategic-objectives/{objectiveId}
    Content-Type: application/json

Request: mesmo contrato utilizado na criacao, com os valores atualizados.

Response `200 OK`: objeto `StrategicObjective` atualizado.

### Eliminar objetivo

    DELETE /strategic-objectives/{objectiveId}

Response `204 No Content`.

## Hierarquia obrigatoria

    Ciclo Estrategico
      -> Pilar Estrategico
        -> Elemento Estrategico
          -> Objetivo Estrategico

Regras da integracao:

- O pilar recebe `strategicPlanId`.
- O elemento recebe `strategicPillarId`.
- O objetivo recebe `strategicElementId` e `strategicPlanId`.
- Ao selecionar um pilar, apresenta apenas os elementos associados a esse pilar.
- Ao alterar o pilar, limpa o elemento selecionado caso este nao pertenca ao novo pilar.
- Nao envies IDs, campos calculados ou relacionamentos completos quando o request espera apenas os respetivos IDs.

## Erros

Trata os seguintes codigos HTTP usando o mesmo mecanismo ja implementado para ciclos:

- `400 Bad Request`: request invalido.
- `404 Not Found`: recurso inexistente.
- `409 Conflict`: conflito ou dependencias que impedem a operacao.
- `422 Unprocessable Entity`: erro de validacao.
- `500 Internal Server Error`: erro inesperado.

Formato esperado quando o backend devolver um erro de dominio:

    {
      "code": "RESOURCE_ERROR",
      "message": "Mensagem do erro",
      "details": {},
      "traceId": "uuid"
    }

Implementa todas as chamadas usando exclusivamente a infraestrutura ja existente para a integracao dos ciclos. No final, indica os ficheiros alterados e confirma quais endpoints foram integrados.
