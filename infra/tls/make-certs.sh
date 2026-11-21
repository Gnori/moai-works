#!/usr/bin/env bash
# 로컬 개발용 TLS 인증서를 만든다 — moai-works 의 모든 서비스(api · web-backend · web-frontend 개발 서버)가
# 이 인증서로 HTTPS 를 연다. 로컬 인증 기관은 mkcert 의 것을 쓴다(없으면 mkcert 가 처음 한 번 만든다).
#
#   certs/localhost.pem       서버 인증서 (localhost · 127.0.0.1 · ::1)
#   certs/localhost-key.pem   그 열쇠 — git 에 넣지 않는다(certs/ 통째로 제외)
#   certs/ca.pem              이 인증서를 발급한 로컬 인증 기관의 인증서. 서비스끼리 부를 때 이것을 믿는다
#   certs/ca-bundle.pem       운영체제가 믿는 인증 기관 + ca.pem — 믿는 목록을 파일 하나로만 받는 프로그램(파이썬)이 쓴다
#
# 브라우저의 "안전하지 않음" 경고를 없애려면 로컬 인증 기관을 이 PC 의 신뢰 목록에 넣는다(한 번, 비밀번호를 묻는다):
#   mkcert -install
# 다시 돌리면 서버 인증서를 새로 발급한다(유효 기간 약 2년 3개월). 서비스는 다시 띄워야 새 인증서를 읽는다.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/certs"

if ! command -v mkcert >/dev/null 2>&1; then
  echo "mkcert 가 없다 — 먼저 깐다: brew install mkcert" >&2
  exit 1
fi

mkdir -p "$DIR"
chmod 700 "$DIR"
mkcert -cert-file "$DIR/localhost.pem" -key-file "$DIR/localhost-key.pem" localhost 127.0.0.1 ::1 >/dev/null 2>&1
chmod 600 "$DIR/localhost-key.pem"
cp "$(mkcert -CAROOT)/rootCA.pem" "$DIR/ca.pem"

# 운영체제가 믿는 인증 기관 묶음 (macOS · Debian 계열 · RHEL 계열)
SYSTEM=""
for candidate in /etc/ssl/cert.pem /etc/ssl/certs/ca-certificates.crt /etc/pki/tls/certs/ca-bundle.crt; do
  if [ -s "$candidate" ]; then
    SYSTEM="$candidate"
    break
  fi
done
if [ -n "$SYSTEM" ]; then
  cat "$SYSTEM" "$DIR/ca.pem" > "$DIR/ca-bundle.pem"
else
  cp "$DIR/ca.pem" "$DIR/ca-bundle.pem"
fi

echo "만들었다: $DIR"
openssl x509 -in "$DIR/localhost.pem" -noout -enddate | sed 's/notAfter=/  서버 인증서 만료: /'
if security verify-cert -c "$DIR/ca.pem" >/dev/null 2>&1; then
  echo "  로컬 인증 기관: 이 PC 가 믿는다 — 브라우저 경고가 없다"
else
  echo "  로컬 인증 기관: 이 PC 의 신뢰 목록에 없다 — 브라우저가 경고한다. 넣으려면: mkcert -install"
fi
