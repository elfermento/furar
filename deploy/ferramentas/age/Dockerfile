# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# age (BSD-3-Clause) para as copias cifradas cando o sistema non o ten (scripts/lib/age.sh).
# Só ferramenta local: non se publica nin vai en compose. Constrúese sen contexto
# (docker build - <este ficheiro). Base fixada por digest (Renovate) e age = binario oficial da
# release de GitHub comprobado por SHA-256 (OPS-R3-3): non depende de que Alpine conserve unha
# revisión concreta do paquete, que desaparece cando a recompila. Para subir de versión: cambiar
# AGE_VERSION e as dúas sumas (`digest` de cada asset en
# https://api.github.com/repos/FiloSottile/age/releases/tags/vX.Y.Z) e FURAR_AGE_IMAXE en age.sh.
FROM alpine:3.24.3@sha256:b3413572228e34bd1c9245f5565e6cd127005655e8a7899aef33d8e83255c9e6
ARG AGE_VERSION=1.3.2
ARG AGE_SHA256_AMD64=cbe24006683f8eb669266162894b9a522a1af52f2665fbc63a4bb032ed26ac10
ARG AGE_SHA256_ARM64=6b8dc4333c53a5a57c9e5834e3a48f92605d7154014cd07269ff3327db5d37f4
RUN set -eu; \
    case "$(uname -m)" in \
      x86_64) arq=amd64 suma=$AGE_SHA256_AMD64 ;; \
      aarch64) arq=arm64 suma=$AGE_SHA256_ARM64 ;; \
      *) echo "arquitectura non soportada: $(uname -m)" >&2; exit 1 ;; \
    esac; \
    f=age-v$AGE_VERSION-linux-$arq.tar.gz; \
    for i in 1 2 3; do wget -q -O "/tmp/$f" "https://github.com/FiloSottile/age/releases/download/v$AGE_VERSION/$f" && break; [ "$i" = 3 ] && exit 1; sleep $((i * 5)); done; \
    echo "$suma  /tmp/$f" | sha256sum -c -; \
    tar -xzf "/tmp/$f" -C /tmp; \
    install -m 0755 /tmp/age/age /tmp/age/age-keygen /usr/local/bin/; \
    rm -rf /tmp/age "/tmp/$f"; \
    age --version; \
    adduser -S -D -H -u 10001 furar
USER 10001:10001
WORKDIR /tmp
