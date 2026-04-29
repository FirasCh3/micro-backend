$ErrorActionPreference = "Stop"

$python = Join-Path $PSScriptRoot ".venv-win\python.exe"

& $python -m pip install -r (Join-Path $PSScriptRoot "requirements-separation.txt")
& $python -m pip install --no-deps asteroid
