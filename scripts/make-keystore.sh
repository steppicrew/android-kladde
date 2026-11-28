#!/usr/bin/env bash
# Creates the release upload keystore from the settings in .env (KEYSTORE_PATH, KEY_ALIAS,
# KEYSTORE_PASSWORD). The password reaches keytool through an environment variable
# (-storepass:env), never through argv, where any process could read it.
#
# The keystore is written OUTSIDE the repository. Losing it means no update can ever be
# published for the app. Back it up somewhere safe.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [ ! -f "$ROOT_DIR/.env" ]; then
    echo "No .env; copy .env.sample and fill in the signing settings." >&2
    exit 1
fi
set -a
# shellcheck disable=SC1091
. "$ROOT_DIR/.env"
set +a

: "${KEYSTORE_PATH:?KEYSTORE_PATH is not set in .env}"
: "${KEY_ALIAS:?KEY_ALIAS is not set in .env}"
if [ -z "${KEYSTORE_PASSWORD:-}" ]; then
    echo "KEYSTORE_PASSWORD is empty in .env." >&2
    exit 1
fi
if [ -n "${KEY_PASSWORD:-}" ] && [ "$KEY_PASSWORD" != "$KEYSTORE_PASSWORD" ]; then
    # PKCS12, keytool's format, has one password for store and key.
    echo "KEY_PASSWORD differs from KEYSTORE_PASSWORD; PKCS12 needs them equal (or leave it empty)." >&2
    exit 1
fi
if [ -e "$KEYSTORE_PATH" ]; then
    echo "Refusing to overwrite the existing keystore at $KEYSTORE_PATH:" >&2
    echo "overwriting it would make Play Store updates impossible." >&2
    exit 1
fi

mkdir -p "$(dirname "$KEYSTORE_PATH")"
chmod 700 "$(dirname "$KEYSTORE_PATH")"
umask 077

keytool -genkeypair \
    -keystore "$KEYSTORE_PATH" \
    -storetype PKCS12 \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -dname "CN=Kladde, O=steppicrew" \
    -storepass:env KEYSTORE_PASSWORD

chmod 600 "$KEYSTORE_PATH"
echo "Keystore created: $KEYSTORE_PATH (alias $KEY_ALIAS)"
echo "Back it up now: it cannot be regenerated."
