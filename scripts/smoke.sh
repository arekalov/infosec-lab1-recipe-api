#!/usr/bin/env bash
# Сквозная проверка API: функциональность плюс все заявленные меры защиты.
#
# Приложение должно быть запущено (scripts/run.sh).
# Использование: scripts/smoke.sh [базовый-URL]
set -uo pipefail

BASE="${1:-http://localhost:8080}"
PASSED=0
FAILED=0

# Уникальные логины, чтобы скрипт можно было прогонять повторно.
SUFFIX="$(date +%s)"
ALICE="alice_${SUFFIX}"
MALLORY="mallory_${SUFFIX}"
PASSWORD="correct-horse-battery-staple"

req() { # req METHOD PATH [DATA] [TOKEN] -> печатает "<тело>\n<код>"
  local method="$1" path="$2" data="${3:-}" token="${4:-}"
  local args=(-s -w '\n%{http_code}' -X "$method" "${BASE}${path}")
  [[ -n "$data" ]] && args+=(-H 'Content-Type: application/json' -d "$data")
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer ${token}")
  curl "${args[@]}"
}

# Обе функции принимают ответ и аргументом, и через конвейер.
status() { if [[ $# -gt 0 ]]; then tail -n1 <<<"$1"; else tail -n1; fi; }
body() { if [[ $# -gt 0 ]]; then sed '$d' <<<"$1"; else sed '$d'; fi; }

check() { # check "описание" ожидаемое фактическое
  if [[ "$2" == "$3" ]]; then
    printf '  \033[32mOK\033[0m   %s\n' "$1"
    PASSED=$((PASSED + 1))
  else
    printf '  \033[31mFAIL\033[0m %s (ожидалось %s, получено %s)\n' "$1" "$2" "$3"
    FAILED=$((FAILED + 1))
  fi
}

json() { python3 -c "import sys,json;print(json.load(sys.stdin)$1)"; }

echo "== Регистрация и вход =="
R=$(req POST /auth/register "{\"username\":\"${ALICE}\",\"password\":\"${PASSWORD}\"}")
check "регистрация нового пользователя" 201 "$(status "$R")"
check "хэш пароля не возвращается" "" "$(body "$R" | grep -o 'passwordHash' || true)"

R=$(req POST /auth/register "{\"username\":\"${ALICE}\",\"password\":\"${PASSWORD}\"}")
check "повторная регистрация отклонена" 409 "$(status "$R")"

R=$(req POST /auth/register "{\"username\":\"ok_user_${SUFFIX}\",\"password\":\"short\"}")
check "короткий пароль отклонён" 400 "$(status "$R")"

R=$(req POST /auth/login "{\"username\":\"${ALICE}\",\"password\":\"${PASSWORD}\"}")
check "вход с верным паролем" 200 "$(status "$R")"
TOKEN=$(body "$R" | json "['accessToken']")

R=$(req POST /auth/login "{\"username\":\"${ALICE}\",\"password\":\"wrong-password-here\"}")
check "вход с неверным паролем" 401 "$(status "$R")"

R=$(req POST /auth/login "{\"username\":\"no_such_user_${SUFFIX}\",\"password\":\"wrong-password-here\"}")
check "вход с несуществующим логином даёт тот же 401" 401 "$(status "$R")"

echo
echo "== Защита эндпоинтов =="
check "без токена — 401" 401 "$(status "$(req GET /api/data)")"
check "с мусорным токеном — 401" 401 "$(status "$(req GET /api/data '' 'not.a.token')")"

# Валидный по структуре токен, подписанный чужим ключом.
FORGED=$(python3 - <<'PY'
import base64, hmac, hashlib, json, time
def b64(x): return base64.urlsafe_b64encode(x).rstrip(b'=').decode()
header = b64(json.dumps({"alg":"HS256","typ":"JWT"}).encode())
payload = b64(json.dumps({"sub":"admin","roles":["USER"],"exp":int(time.time())+3600}).encode())
sig = b64(hmac.new(b"attacker-secret-key-32-bytes-long!!", f"{header}.{payload}".encode(), hashlib.sha256).digest())
print(f"{header}.{payload}.{sig}")
PY
)
check "с подделанной подписью — 401" 401 "$(status "$(req GET /api/data '' "$FORGED")")"

echo
echo "== CRUD =="
RECIPE='{"title":"Борщ","description":"Классический борщ",
  "ingredients":["свёкла","капуста","говядина"],
  "instructions":"Сварить бульон, добавить овощи, тушить 40 минут.",
  "cookMinutes":120,"servings":6}'
R=$(req POST /api/recipes "$RECIPE" "$TOKEN")
check "создание рецепта" 201 "$(status "$R")"
ID=$(body "$R" | json "['id']")

check "чтение рецепта" 200 "$(status "$(req GET "/api/recipes/${ID}" '' "$TOKEN")")"
check "список /api/data" 200 "$(status "$(req GET /api/data '' "$TOKEN")")"

UPDATED='{"title":"Борщ украинский","description":"С пампушками",
  "ingredients":["свёкла","капуста"],"instructions":"Варить.","cookMinutes":90,"servings":4}'
check "обновление рецепта" 200 "$(status "$(req PUT "/api/recipes/${ID}" "$UPDATED" "$TOKEN")")"
check "несуществующий рецепт — 404" 404 "$(status "$(req GET /api/recipes/999999 '' "$TOKEN")")"

echo
echo "== Защита от SQL-инъекций =="
R=$(req GET "/api/data?q=%27%20OR%20%271%27%3D%271" '' "$TOKEN")
check "payload \"' OR '1'='1\" не ломает запрос" 200 "$(status "$R")"
check "и ничего не находит (сравнивается как текст)" 0 "$(body "$R" | json "['totalItems']")"

R=$(req GET "/api/data?q=%27%3B%20DROP%20TABLE%20recipes%3B--" '' "$TOKEN")
check "payload \"'; DROP TABLE recipes;--\" не выполняется" 200 "$(status "$R")"
check "таблица цела, рецепт на месте" 200 "$(status "$(req GET "/api/recipes/${ID}" '' "$TOKEN")")"

echo
echo "== Защита от XSS =="
XSS='{"title":"<script>alert(1)</script>","description":"<img src=x onerror=alert(1)>",
  "ingredients":["<b>жирный</b>"],"instructions":"javascript:alert(document.cookie)",
  "cookMinutes":10,"servings":1}'
R=$(req POST /api/recipes "$XSS" "$TOKEN")
check "рецепт с XSS-нагрузкой создаётся" 201 "$(status "$R")"
XSS_ID=$(body "$R" | json "['id']")
TITLE=$(req GET "/api/recipes/${XSS_ID}" '' "$TOKEN" | body | json "['title']")
check "тег экранирован в ответе" '&lt;script&gt;alert(1)&lt;/script&gt;' "$TITLE"

echo
echo "== Заголовки безопасности =="
HEADERS=$(curl -s -D - -o /dev/null -H "Authorization: Bearer ${TOKEN}" "${BASE}/api/data")
check "X-Content-Type-Options" "nosniff" "$(grep -i '^x-content-type-options:' <<<"$HEADERS" | tr -d '\r' | awk '{print $2}')"
check "X-Frame-Options" "DENY" "$(grep -i '^x-frame-options:' <<<"$HEADERS" | tr -d '\r' | awk '{print $2}')"
check "Content-Security-Policy присутствует" "yes" "$(grep -qi '^content-security-policy:' <<<"$HEADERS" && echo yes || echo no)"

echo
echo "== Разграничение доступа =="
req POST /auth/register "{\"username\":\"${MALLORY}\",\"password\":\"${PASSWORD}\"}" >/dev/null
MALLORY_TOKEN=$(req POST /auth/login "{\"username\":\"${MALLORY}\",\"password\":\"${PASSWORD}\"}" | body | json "['accessToken']")
check "чужой рецепт не виден (404, а не 403)" 404 "$(status "$(req GET "/api/recipes/${ID}" '' "$MALLORY_TOKEN")")"
check "чужой рецепт нельзя изменить" 404 "$(status "$(req PUT "/api/recipes/${ID}" "$UPDATED" "$MALLORY_TOKEN")")"
check "чужой рецепт нельзя удалить" 404 "$(status "$(req DELETE "/api/recipes/${ID}" '' "$MALLORY_TOKEN")")"
check "у чужого пользователя свой пустой список" 0 "$(req GET /api/data '' "$MALLORY_TOKEN" | body | json "['totalItems']")"

echo
echo "== Ограничение попыток входа =="
BRUTE="brute_${SUFFIX}"
req POST /auth/register "{\"username\":\"${BRUTE}\",\"password\":\"${PASSWORD}\"}" >/dev/null
BRUTE_PAYLOAD="{\"username\":\"${BRUTE}\",\"password\":\"definitely-wrong\"}"
LAST=""
for _ in 1 2 3 4 5 6; do
  LAST=$(req POST /auth/login "$BRUTE_PAYLOAD" | status)
done
check "после 5 неудач вход блокируется" 429 "$LAST"

echo
echo "== Удаление =="
check "удаление своего рецепта" 204 "$(status "$(req DELETE "/api/recipes/${ID}" '' "$TOKEN")")"
check "удалённый рецепт больше не читается" 404 "$(status "$(req GET "/api/recipes/${ID}" '' "$TOKEN")")"
req DELETE "/api/recipes/${XSS_ID}" '' "$TOKEN" >/dev/null

echo
printf 'Пройдено: %d, провалено: %d\n' "$PASSED" "$FAILED"
[[ "$FAILED" -eq 0 ]]
