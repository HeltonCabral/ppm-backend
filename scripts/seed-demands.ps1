<#
.SYNOPSIS
    Cria demandas em massa via API e move-as para o status pretendido (por omissao IN_ANALYSIS).

.DESCRIPTION
    Fluxo por demanda:
      1. POST /demands                                       -> cria a demanda + ciclo de planeamento em DRAFT
      2. PATCH /demands/{id}/planning-cycles/{planId}/status  -> DRAFT -> IN_ANALYSIS

    Pre-requisitos no backend:
      - Existir exatamente um Plano Estrategico em planeamento (GET /strategic-plans/planning)
      - Esse plano ter um Plano Operacional associado
      - Existir pelo menos um LookupValue ativo na categoria DEMAND_TYPE

.EXAMPLE
    ./scripts/seed-demands.ps1
    ./scripts/seed-demands.ps1 -Count 50 -BaseUrl http://localhost:8082/api
    ./scripts/seed-demands.ps1 -Count 1000 -Status DRAFT
#>
[CmdletBinding()]
param(
    [string] $BaseUrl = 'http://localhost:8082/api',
    [int]    $Count = 1000,
    [string] $Actor = 'seed-script',
    [ValidateSet('DRAFT', 'IN_ANALYSIS', 'IN_REVIEW')]
    [string] $Status = 'IN_ANALYSIS',
    [string] $TitlePrefix = 'Demanda de carga',
    [int]    $ProgressEvery = 25,
    [switch] $NoParticipatingDirections
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
$headers = @{ 'X-User-Id' = $Actor }

function Invoke-Api {
    param(
        [Parameter(Mandatory)][string] $Method,
        [Parameter(Mandatory)][string] $Path,
        $Body
    )
    $uri = "$BaseUrl$Path"
    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers
    }
    $json = $Body | ConvertTo-Json -Depth 10 -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -Body $bytes `
        -ContentType 'application/json; charset=utf-8'
}

# No PowerShell 5.1 o Invoke-RestMethod devolve um array JSON como um unico objeto
# (array aninhado); esta funcao normaliza sempre para uma lista plana.
function Expand-List {
    param($Value)
    $out = New-Object System.Collections.Generic.List[object]
    foreach ($item in @($Value)) {
        if ($null -eq $item) { continue }
        if ($item -is [System.Collections.IEnumerable] -and
            $item -isnot [string] -and
            $item -isnot [System.Collections.IDictionary]) {
            foreach ($inner in $item) { $out.Add($inner) }
        } else {
            $out.Add($item)
        }
    }
    return $out.ToArray()
}

function Get-ApiError {
    param($ErrorRecord)
    $resp = $ErrorRecord.Exception.Response
    if ($null -ne $resp) {
        try {
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $body = $reader.ReadToEnd()
            $reader.Close()
            if ($body) { return $body }
        } catch { }
    }
    return $ErrorRecord.Exception.Message
}

# ---------------------------------------------------------------- pre-verificacoes
Write-Host '== Pre-verificacoes ==' -ForegroundColor Cyan

try {
    $plan = Invoke-Api -Method GET -Path '/strategic-plans/planning'
} catch {
    Write-Host 'ERRO: nao foi possivel obter o Plano Estrategico em planeamento.' -ForegroundColor Red
    Write-Host (Get-ApiError $_)
    Write-Host "Inicie um ciclo com POST $BaseUrl/strategic-plans/start-planning antes de correr este script."
    exit 1
}
$planId = $plan.id
Write-Host ("  Plano em planeamento : {0} ({1})" -f $plan.name, $planId)

try {
    $types = @(Expand-List (Invoke-Api -Method GET -Path '/lookup-values/category/DEMAND_TYPE') |
        Where-Object { $_.active -ne $false })
} catch {
    Write-Host "ERRO ao obter lookup DEMAND_TYPE: $(Get-ApiError $_)" -ForegroundColor Red
    exit 1
}
if ($types.Count -eq 0) {
    Write-Host 'ERRO: nenhum LookupValue ativo na categoria DEMAND_TYPE.' -ForegroundColor Red
    exit 1
}
Write-Host ("  Tipos de demanda     : {0}" -f (($types | ForEach-Object { $_.code }) -join ', '))

$domains = @()
try {
    $domains = @(Expand-List (Invoke-Api -Method GET -Path '/lookup-values/category/DOMAIN') |
        Where-Object { $_.active -ne $false })
} catch { }

$budgetLines = @()
try {
    $budgetLines = @(Expand-List (Invoke-Api -Method GET -Path '/lookup-values/category/BUDGET_LINE') |
        Where-Object { $_.active -ne $false })
} catch { }

Write-Host ("  Dominios disponiveis : {0}" -f $domains.Count)
Write-Host ("  Linhas orcamentais   : {0}" -f $budgetLines.Count)
Write-Host ("  Alvo                 : {0} demandas -> {1}" -f $Count, $Status)
Write-Host ''

# ---------------------------------------------------------------- dados sinteticos
$urgencies = @('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
$natures = @('NORMAL', 'NORMAL', 'NORMAL', 'NORMAL', 'REGULATORY', 'LEGAL', 'CONTRACTUAL', 'CORPORATE_MANDATORY')
$directions = @(
    @{ name = 'Direcao de Tecnologia'; code = 'DTI' },
    @{ name = 'Direcao Comercial'; code = 'DCM' },
    @{ name = 'Direcao Financeira'; code = 'DFI' },
    @{ name = 'Direcao de Operacoes'; code = 'DOP' },
    @{ name = 'Direcao de Recursos Humanos'; code = 'DRH' },
    @{ name = 'Direcao de Marketing'; code = 'DMK' }
)
$areas = @(
    @{ name = 'Sistemas de Informacao'; code = 'SI' },
    @{ name = 'Infraestrutura'; code = 'INFRA' },
    @{ name = 'Atendimento ao Cliente'; code = 'ATC' },
    @{ name = 'Planeamento e Controlo'; code = 'PCG' },
    @{ name = 'Qualidade'; code = 'QLD' }
)
$systems = @('Core Billing', 'CRM', 'ERP', 'Portal do Cliente', 'Data Warehouse', 'App Movel', 'Gestao Documental')
$themes = @(
    'automatizacao do processo de faturacao',
    'melhoria da experiencia do cliente no autoatendimento',
    'integracao entre sistemas legados',
    'reforco de seguranca e conformidade',
    'reducao do tempo de resposta operacional',
    'modernizacao da infraestrutura de rede',
    'digitalizacao do arquivo documental',
    'painel de indicadores de gestao',
    'otimizacao de custos de licenciamento',
    'suporte a novos produtos comerciais'
)
$participationTypes = @('IMPLEMANTATION', 'INTERVENIENT')

$rand = New-Object System.Random 20260901
function Pick {
    param($arr)
    $items = @($arr)
    return $items[$rand.Next(0, $items.Count)]
}

# ---------------------------------------------------------------- execucao
$created = 0
$transitioned = 0
$failures = New-Object System.Collections.Generic.List[string]
$sw = [System.Diagnostics.Stopwatch]::StartNew()

for ($i = 1; $i -le $Count; $i++) {
    $direction = Pick $directions
    $area = Pick $areas
    $theme = Pick $themes
    $type = Pick $types
    $seq = '{0:D4}' -f $i

    $body = [ordered]@{
        title                      = "$TitlePrefix $seq - $theme"
        summary                    = "Demanda sintetica $seq gerada para testes de volume: $theme."
        requester                  = "Requisitante $seq"
        areaName                   = $area.name
        areaCode                   = $area.code
        directionName              = $direction.name
        directionCode              = $direction.code
        directionParticipationType = 'IMPLEMANTATION'
        nature                     = Pick $natures
        sponsor                    = "Sponsor $($direction.code)"
        typeId                     = $type.id
        origin                     = 'MANUAL'
        impactedSystem             = Pick $systems
        currentEvidence            = "A situacao atual apresenta limitacoes relacionadas com $theme."
        problemStatement           = "O processo atual nao suporta $theme, gerando retrabalho e atrasos operacionais."
        desiredOutcome             = "Garantir $theme com reducao de esforco manual e maior fiabilidade dos dados."
        proposedResponse           = "Implementar solucao incremental cobrindo $theme, com entregas faseadas."
        urgency                    = Pick $urgencies
        estimatedBudget            = [math]::Round($rand.Next(50000, 5000000) / 100.0, 2)
        desiredDate                = (Get-Date).AddDays($rand.Next(60, 540)).ToString('yyyy-MM-dd')
        notes                      = "Registo criado por seed-demands.ps1 (lote de $Count)."
        risksIdentified            = 'Risco de dependencia de fornecedor externo e de disponibilidade de equipa.'
        dependenciesIdentified     = 'Depende de janela de manutencao e de dados do sistema impactado.'
    }

    if ($domains.Count -gt 0) { $body.domainId = (Pick $domains).id }
    if ($budgetLines.Count -gt 0) { $body.budgetLineId = (Pick $budgetLines).id }

    if (-not $NoParticipatingDirections) {
        $others = @($directions | Where-Object { $_.code -ne $direction.code })
        $extra = @($others | Get-Random -Count $rand.Next(1, 3))
        $body.participatingDirections = @(
            foreach ($d in $extra) {
                [ordered]@{
                    directionName     = $d.name
                    directionCode     = $d.code
                    areaName          = $area.name
                    areaCode          = $area.code
                    participationType = Pick $participationTypes
                    observations      = "Participacao na demanda $seq."
                }
            }
        )
    }

    try {
        $demand = Invoke-Api -Method POST -Path '/demands' -Body $body
        $created++
    } catch {
        $msg = "[$seq] POST /demands falhou: $(Get-ApiError $_)"
        $failures.Add($msg)
        if ($i -eq 1) {
            Write-Host $msg -ForegroundColor Red
            Write-Host 'A primeira criacao falhou - a abortar antes de gerar mais erros.' -ForegroundColor Red
            exit 1
        }
        continue
    }

    if ($Status -ne 'DRAFT') {
        try {
            $transition = @{ status = $Status; reason = 'Encaminhada para analise pelo script de carga' }
            Invoke-Api -Method PATCH -Path "/demands/$($demand.id)/planning-cycles/$planId/status" -Body $transition | Out-Null
            $transitioned++
        } catch {
            $failures.Add("[$seq] transicao para $Status falhou ($($demand.code)): $(Get-ApiError $_)")
        }
    }

    if ($ProgressEvery -gt 0 -and ($i % $ProgressEvery -eq 0)) {
        $pct = [int](($i / $Count) * 100)
        Write-Progress -Activity 'A criar demandas' -Status "$i/$Count (falhas: $($failures.Count))" -PercentComplete $pct
    }
}

$sw.Stop()
Write-Progress -Activity 'A criar demandas' -Completed

Write-Host ''
Write-Host '== Resumo ==' -ForegroundColor Cyan
Write-Host ("  Criadas   : {0}/{1}" -f $created, $Count)
if ($Status -ne 'DRAFT') { Write-Host ("  Em {0} : {1}" -f $Status, $transitioned) }
Write-Host ("  Falhas    : {0}" -f $failures.Count)
Write-Host ("  Duracao   : {0:mm\:ss}" -f $sw.Elapsed)

if ($failures.Count -gt 0) {
    $logPath = Join-Path $PSScriptRoot 'seed-demands-failures.log'
    $failures | Out-File -FilePath $logPath -Encoding utf8
    Write-Host ("  Detalhe das falhas: {0}" -f $logPath) -ForegroundColor Yellow
    $failures | Select-Object -First 5 | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkYellow }
}
