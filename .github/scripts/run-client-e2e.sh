#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${E2E_DB_MANAGED:-false}" == "true" && -z "${E2E_DB_PASSWORD:-}" ]]; then
  echo "E2E_DB_PASSWORD é obrigatório para o banco gerenciado do workflow." >&2
  exit 2
fi

# Executa o checkout do cliente sem modificar os helpers ou o código testado.
# A revisão exata é registrada pelo workflow antes da preparação do ambiente.

# Capture Android startup errors even when flutter test produces no test output.
# The workflow uploads this directory after the emulator runner exits.
mkdir -p e2e-logs
adb logcat -v time -s Flutter flutter AndroidRuntime ActivityTaskManager ActivityManager \
  >e2e-logs/emulator.log 2>&1 &
logcat_pid=$!
trap 'kill "$logcat_pid" 2>/dev/null || true; wait "$logcat_pid" 2>/dev/null || true' EXIT

./tool/run_e2e.sh
