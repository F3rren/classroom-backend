# Script PowerShell per monitorare i log dell'applicazione PrenotazioniAule
# Salva questo file come monitor-logs.ps1 e eseguilo mentre l'applicazione è in funzione

param(
    [string]$LogFile = "logs\app-log.txt",
    [string]$ErrorFile = "logs\error-log.txt",
    [switch]$ErrorsOnly = $false,
    [int]$TailLines = 20
)

Write-Host "=== Monitor Log PrenotazioniAule ===" -ForegroundColor Cyan
Write-Host "Percorso: $(Get-Location)" -ForegroundColor Gray

if ($ErrorsOnly) {
    Write-Host "Monitoraggio solo errori da: $ErrorFile" -ForegroundColor Yellow
    Write-Host "Premi Ctrl+C per terminare" -ForegroundColor Gray
    Write-Host "`n"
    
    if (Test-Path $ErrorFile) {
        Get-Content $ErrorFile -Tail $TailLines -Wait | ForEach-Object {
            if ($_ -match "ERROR|WARN") {
                Write-Host $_ -ForegroundColor Red
            } else {
                Write-Host $_ -ForegroundColor Yellow
            }
        }
    } else {
        Write-Host "File di errori non trovato: $ErrorFile" -ForegroundColor Red
    }
} else {
    Write-Host "Monitoraggio tutti i log da: $LogFile" -ForegroundColor Green
    Write-Host "Premi Ctrl+C per terminare" -ForegroundColor Gray
    Write-Host "`n"
    
    if (Test-Path $LogFile) {
        Get-Content $LogFile -Tail $TailLines -Wait | ForEach-Object {
            if ($_ -match "ERROR") {
                Write-Host $_ -ForegroundColor Red
            } elseif ($_ -match "WARN") {
                Write-Host $_ -ForegroundColor Yellow  
            } elseif ($_ -match "INFO.*com\.prenotazioni") {
                Write-Host $_ -ForegroundColor Green
            } elseif ($_ -match "DEBUG.*com\.prenotazioni") {
                Write-Host $_ -ForegroundColor Blue
            } else {
                Write-Host $_ -ForegroundColor White
            }
        }
    } else {
        Write-Host "File di log non trovato: $LogFile" -ForegroundColor Red
        Write-Host "Assicurati che l'applicazione sia in esecuzione e che i log siano abilitati." -ForegroundColor Yellow
    }
}

# Esempi di utilizzo:
# .\monitor-logs.ps1                    # Monitor tutti i log
# .\monitor-logs.ps1 -ErrorsOnly        # Monitor solo errori 
# .\monitor-logs.ps1 -TailLines 50      # Mostra ultime 50 righe