<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Cambios

Formato [Keep a Changelog](https://keepachangelog.com/gl/1.1.0/) e versións [SemVer](https://semver.org/lang/gl/). Cada PR que cambia algo visible para un concello engade unha liña en «Sen publicar»; ao etiquetar, `scripts/preparar-release.sh vX.Y.Z` pasa a sección a «[X.Y.Z] - data» e as notas da release lévana. As notas da release (`scripts/notas-release.sh`) engaden os digests e as sinaturas.

## [Sen publicar]

## [1.0.3] - 2026-11-19

### Arranxado
- O PDF dos indicadores xa non remata nunha páxina en branco.
- Un diálogo co contido máis alto ca a pantalla sempre se pode desprazar co teclado, tamén cando o contido cambia.

## [1.0.2] - 2026-11-18

### Arranxado
- No PDF dos indicadores, un gráfico xa non queda só nunha páxina sen o seu título.

## [1.0.1] - 2026-11-18

### Arranxado
- Validar o QR dunha reserva devolve a mesma hora de asistencia que queda gardada, tamén en servidores Linux.

## [1.0.0] - 2026-11-18

### Engadido
- Primeira versión pública. Concellos (varios nunha mesma instalación), campos personalizados, instalacións e horario, reservas (tamén por outra persoa e por tempadas), avisos e recordatorios por correo, lista de espera e ocos parecidos, QR e non presentados, persoas a cargo, partidas abertas, incidencias, valoracións, eventos e actividades con horario, datos abertos, indicadores, portada común, preferencias de accesibilidade, persoal con segundo factor e eliminación da propia conta. Acceso con correo e contrasinal ou por SAML 2.0 (Cl@ve e Chave365). Imaxes para `linux/amd64` e `linux/arm64`, asinadas, cos seus SBOM. Estado detallado en [`docs/arquitectura.md`](docs/arquitectura.md#estado).
