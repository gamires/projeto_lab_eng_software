
set -euo pipefail
cd "$(dirname "$0")"

./backend/run.sh &
BACK=$!
trap 'kill $BACK 2>/dev/null || true' EXIT INT TERM

echo "Aguardando a API em http://localhost:8080 ..."
for i in $(seq 1 90); do
  if curl -s -m 2 http://localhost:8080/api/health >/dev/null; then
    echo "API no ar: $(curl -s http://localhost:8080/api/health)"
    break
  fi
  sleep 1
done

cd frontend-react
[ -d node_modules ] || npm install
npm run dev
