$ErrorActionPreference = "Stop"

Write-Host "=== Backend Rebuild Script ===" -ForegroundColor Cyan
Write-Host ""

# Check if pom.xml exists
if (-not (Test-Path "pom.xml")) {
    Write-Host "ERROR: pom.xml not found. Please run this script from the backend directory." -ForegroundColor Red
    exit 1
}

# Try to find Maven
$mavenPaths = @(
    "mvn",
    "$env:MAVEN_HOME\bin\mvn.cmd",
    "$env:M2_HOME\bin\mvn.cmd",
    "C:\Program Files\Apache\maven\bin\mvn.cmd",
    "C:\Program Files\Maven\bin\mvn.cmd",
    "$env:ProgramFiles\Apache\maven\bin\mvn.cmd"
)

$mvnCmd = $null
foreach ($path in $mavenPaths) {
    try {
        if (Get-Command $path -ErrorAction SilentlyContinue) {
            $mvnCmd = $path
            break
        }
    } catch {
        continue
    }
}

if (-not $mvnCmd) {
    Write-Host "ERROR: Maven not found!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please rebuild using your IDE:" -ForegroundColor Yellow
    Write-Host "  - IntelliJ IDEA: Right-click pom.xml -> Maven -> Reload -> Lifecycle -> package"
    Write-Host "  - Eclipse: Right-click project -> Run As -> Maven build -> Goals: 'clean package -DskipTests'"
    Write-Host "  - VS Code: Use Java extension to build"
    exit 1
}

Write-Host "Found Maven: $mvnCmd" -ForegroundColor Green
Write-Host ""

# Clean and build
Write-Host "Building backend..." -ForegroundColor Cyan
& $mvnCmd clean package -DskipTests

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "=== BUILD SUCCESSFUL ===" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Cyan
    Write-Host "1. Stop the current backend server (Ctrl+C)"
    Write-Host "2. Run: java -jar target/hospital-management-system-1.0.0.jar"
    Write-Host "3. Uncomment ActivityFeed in HospitalAdminDashboard.jsx (lines 16 and 508)"
} else {
    Write-Host ""
    Write-Host "=== BUILD FAILED ===" -ForegroundColor Red
    Write-Host "Please check the error messages above and fix any compilation errors."
    exit 1
}
