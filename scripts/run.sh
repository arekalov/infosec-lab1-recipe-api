#!/usr/bin/env bash
# Локальный запуск приложения.
#
# Секрет подписи JWT берётся из .env или из переменной окружения и никогда
# не хранится в коде. Без него приложение сознательно не стартует.
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

if [[ -z "${JWT_SECRET:-}" ]]; then
  echo "JWT_SECRET не задан." >&2
  echo "Создайте .env по образцу .env.example:" >&2
  echo "  cp .env.example .env && echo \"JWT_SECRET=\$(openssl rand -base64 32)\" >> .env" >&2
  exit 1
fi

exec ./gradlew bootRun
