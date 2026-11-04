<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0016 · CI pública, cadea de subministración e `scripts/verificar.sh`

- **Estado:** aceptada · 2026-10-22 (revisada tras a revisión do equipo; forxa neutral tras LIC-1; estado real das forxas tras LIC-8 e OPS-R2-1; forxa e dous carrís tras OPS-R3-1 e ARQ3-3; imaxes só no público e e2e diario tras OPS-R4-2 e OPS-R4-3, 2026-10-23)
- **Revisada 2026-11-04:** repositorio público <https://github.com/elfermento/furar> e imaxes en `ghcr.io/elfermento`; contacto de seguridade e conduta polo aviso privado do repositorio, sen correo; `scripts/preparar-release.sh` escribe a versión e a data no CHANGELOG e en `publiccode.yml` antes de etiquetar.

## Contexto
Un erro habitual é unha CI que constrúe imaxes sen pasar os tests. O xurado debe poder comprobar a calidade sen fiarse de ninguén. En 2026 houbo ataques a ferramentas de CI (etiquetas de Actions e imaxes de escáneres reescritas): fixar por etiqueta non abonda.

## Decisión
**`scripts/verificar.sh`**
- Antes de nada comproba o contrato (D53, `--contrato`): cada fila da DoD ten o seu paso no script, e ningún paso queda sen fila; se non, falla sen executar nada. Así unha fila nova non pode quedar sen executar (pasara con D50).
- Comproba requisitos ao empezar (docker, jq, Java 25, Node 24, Colima ≥ 4 GiB) e pon un `trap` que baixa o compose (`down -v`) e borra os contedores de Testcontainers.
- Executa en serie, parando no primeiro fallo, as comprobacións de [`definicion-de-feito.md`](../definicion-de-feito.md) en catro partes: `compliance` → `api` → `web` → `e2e` (build de imaxes en serie → `up --wait` → seguridade e token → e2e → memoria → non root → Trivy → reinicio → `down`) e despois os scripts de instalación limpa, copia (tamén cifrada) e o overlay de produción levantado de verdade (D48). Remata con `VERIFICADO`.
- `verificar.sh --parte <compliance|api|web|e2e>` executa só unha parte.
- yq, reuse, Trivy e gitleaks córrense desde imaxes fixadas, non desde o host.
- Duración estimada 25–40 min en 2 CPU; a primeira execución real mídese e anótase aquí.

**GitHub Actions** (`.github/workflows/ci.yml`, pública)
- Un workflow. Traballo `changes` (`dorny/paths-filter`) con saídas `api`, `web`, `deploy`, `docs`; cada traballo ten `if:` coa súa saída; `e2e` corre se cambia `api`, `web` ou `deploy`.
- Cada traballo chama **a `scripts/verificar.sh --parte X`**: a CI e o local non poden diverxer (D43 busca comandos soltos).
- `ci-ok` (`if: always()`) falla se algún `needs` deu `failure` ou `cancelled`: é **o único check obrigatorio** de `main` no repositorio público. No privado (GitHub Free) non hai protección de rama nin rulesets (OPS-R4-3): por iso o e2e corre cada noite (03:00 UTC) sobre `main` se houbo commits nas últimas 25 h (só e2e; ~20 min/día, ~600 dos 2.000 min/mes). Unha regresión vese en < 24 h e non o día da etiqueta.
- `images` e `release` **só no repositorio público** (`github.event.repository.visibility == 'public'`, OPS-R4-2; D43 compróbao): no privado, GHCR privado ten 500 MB e as tres imaxes (~450 MB comprimidas) non caben dúas veces, e cosign sen chaves escribiría en Rekor (rexistro público e permanente) a identidade do repo privado. Alí, unha etiqueta `v*` só corre `compliance`, `api`, `web` e `e2e` (a que precisa `publicar-v1.sh`). No público, `images` corre nunha etiqueta `v*` ou a man (`workflow_dispatch`) desde `main` (OPS-R3-1), e só se `compliance`, `api`, `web` e `e2e` rematan en **`success`** (nos dous casos corre todo sen filtros). O `e2e` corre nos PR, nas etiquetas e a man, non en cada push a `main`. Un push só de docs non publica nada (T4, OPS-5). **Non reconstrúe:** carga as imaxes que pasaron o e2e (`docker save` → artefacto de 3 días → `docker load`), pasa Trivy (HIGH e CRITICAL con corrección fallan; excepcións con motivo e caducidade en `.trivyignore.yaml`), xera o SBOM de cada imaxe, publica en GHCR e **asina con cosign sen chaves** (OIDC de GitHub → Sigstore), co SBOM como atestación. O digest de `furar-VERSIÓN.env` é o da imaxe probada.
- **Release** (OPS-2): unha etiqueta `vX.Y.Z` corre todo sen filtros e o traballo `release` crea a GitHub Release (permanente e pública) con `furar-vX.Y.Z.env`, os SBOM e as ordes `cosign verify` (`scripts/notas-release.sh`). A guía de instalación parte desa release.
- **Escaneo semanal** (`escaneo.yml`, SEC-06): os luns repite a parte `compliance` (D05 SBOM e D49 Trivy sobre os SBOM de Maven e npm) e escanea as imaxes da última release coa mesma política. As CVE aparecen despois de publicar.
- `permissions: contents: read` por defecto; `packages: write` e `id-token: write` só en `images`; `contents: write` só en `release`.

**Cadea de subministración**
- Actions fixadas por **SHA de commit**; imaxes por `etiqueta@sha256`; Trivy desde a súa imaxe, sen `trivy-action`.
- Renovate con `helpers:pinGitHubActionDigests`, `pinDigests: true`, `minimumReleaseAge: "14 days"` para todo, e grupos (Angular + TypeScript + angular-eslint suben xuntos). `renovate-config-validator` na parte `compliance`.
- gitleaks na parte `compliance`.
- Ramas: no repositorio público, `main` protexida; PR con `ci-ok` verde e Conventional Commits con DCO. No privado non se pode protexer (plan Free): suple o e2e diario.

**Forxa neutral (LIC-1, OPS-1, AP-08)**
- A lóxica está en `scripts/`; cada forxa só ten un ficheiro fino que chama a `verificar.sh --parte X`: `.github/workflows/ci.yml` (GitHub) e **`.gitlab-ci.yml`** (GitLab: A Eira de Mancomún ou calquera outro), cun runner `shell` que ten os mesmos requisitos ca unha máquina de desenvolvemento. O de GitLab publica en `$CI_REGISTRY_IMAGE` despois de pasar todo; non asina con cosign (queda para cando se escolla a forxa canónica).
- **Ningún valor por defecto apunta a un espazo alleo:** `FURAR_REGISTRY` non ten valor por defecto en produción (`compose.prod.yaml` e `publicar-imaxes.sh --publicar` fallan sen el; o `furar-VERSIÓN.env` da release trae `ghcr.io/elfermento`); o enlace «Código fonte» e a guía apuntan ao repositorio do proxecto, `https://github.com/elfermento/furar`. Ata decidilo levaban marcadores que `scripts/sen-marcadores.sh` (X11) non deixa etiquetar.
- **Estado real das dúas forxas (LIC-8, OPS-R2-1):** o camiño completo (release con `furar-vX.Y.Z.env` e SBOM, cosign, escaneo semanal, Renovate) só existe en GitHub. `.gitlab-ci.yml` só ten `verificar` e `imaxes`, sen release, sinatura nin programación, e precisa un runner `shell` propio (Docker, Java 25, Node 24, jq, yq, age, zstd) que non cabe nun equipo de 8 GB. `notas-release.sh` e `escanear-release.sh` len `GITHUB_REPOSITORY`. A de GitHub corre no repositorio privado de desenvolvemento (cada push a `main` e cada noite); a de GitLab non se executou aínda.
- **Forxa: decidida polo mantedor o 2026-10-22 (OPS-R3-1; substitúe as opcións A/B anteriores):**
  - **Durante o desenvolvemento:** o repositorio privado de desenvolvemento en GitHub, con CI privada. Para non esgotar a cota de Actions e Packages, en cada push a `main` só corren `compliance`, `api` e `web`; o `e2e` corre nos PR, nas etiquetas, a man e cada noite se houbo commits; `images` e `release` non corren nunca (só no público, OPS-R4-2). A fonte de verdade local é `scripts/verificar.sh --estrito`, antes de cada etiqueta.
  - **v1 pública:** nun **repositorio novo, sen o historial** do privado (un único commit asinado). O privado nunca se fai público. `scripts/publicar-v1.sh vX.Y.Z <URL>` exporta a árbore da etiqueta sen os ficheiros de traballo internos (a lista privada `.publicar-excluir`, que tampouco se publica, e falla se algún deles ou un dos seus termos chegaría á árbore), crea o commit con `Signed-off-by`, pasa REUSE e `sen-marcadores.sh --release`, comproba que a árbore coincide coa da etiqueta menos eses ficheiros e, só con `--publicar`, fai o push. Hai que probalo en seco antes do 18-11. A release, cosign e o escaneo semanal das imaxes corren no repositorio público (o semanal de dependencias corre en ambos).
  - **A Eira:** espello da v1 cando haxa acceso (pedilo á Oficina de Software Libre de Amtega), co `publiccode.yml`. `.gitlab-ci.yml` queda para quen faga un fork alí.
  - **Decidido polo mantedor o 2026-11-04:**
    1. **Contacto sen correo:** seguridade e conduta polo aviso privado do repositorio (*Security* → *Report a vulnerability*); a oferta escrita das partes GPL, polo contacto de `publiccode.yml` ou unha *issue*. Identidade dos commits fixada neste repo (`git config --local user.name/user.email`, nome legal no `Signed-off-by`, `core.hooksPath .githooks`).
    2. **Repositorio público:** `https://github.com/elfermento/furar` (organización do proxecto); imaxes en `ghcr.io/elfermento/furar-{api,web,keycloak}`.
    3. **v0.1.0** é a primeira versión pública, con `publicar-v1.sh`, para que CI pública, GHCR, cosign, notas de release e instalación desde a release corran antes da entrega. Cada versión seguinte é un commit enriba do anterior público (fast-forward, probado con dúas versións seguidas). Versión e data: `scripts/preparar-release.sh vX.Y.Z` no commit que se etiqueta; `publicar-v1.sh` compróbao.
- **Dous carrís (ARQ3-3, 2026-10-23):**
  - **Rápido, obrigatorio en cada cambio:** `scripts/rapido.sh` (formato, ArchUnit/Modulith coas autoprobas, unitarios da API, lint, i18n, formato, unitarios e tipos da web). Sen Docker; medido: 20 s con caché quente, obxectivo < 3 min.
  - **Completo:** `scripts/verificar.sh` (Testcontainers, compose full, e2e, imaxes, copias), na CI e antes de cada etiqueta.
  - **DoD conxelada:** non entra ningunha fila nova salvo para cubrir a regresión dun fallo real; un caso novo dunha garda existente vai dentro da súa fila (como D28 q e r).

## Consecuencias
- «Feito» significa unha cousa só e calquera pode comprobala no seu ordenador.
- Un PR que só toca docs non queda bloqueado por checks pendentes.

## Alternativas descartadas
- **Só CI, sen script local:** non se pode comprobar antes de subir nin sen conta.
- **`on.paths` por workflow:** os checks obrigatorios quedan pendentes para sempre nos PR de docs.
- **Verificar con cosign as imaxes base de terceiros:** o digest xa as fai inmutables e a idade mínima filtra publicacións maliciosas recentes. As imaxes **propias** si se asinan (ver Decisión).
- **Reconstruír as imaxes no traballo `images`:** o digest publicado non sería o probado.
- **osv-scanner ademais de Trivy:** Trivy xa le os SBOM CycloneDX (GHSA, NVD, OSV de Alpine) cunha soa ferramenta fixada e unha soa lista de excepcións; dúas listas acabarían diverxendo.
- **Só CRITICAL en Trivy** (decisión inicial): deixaba pasar HIGH con corrección dispoñible.
- **Traballos en paralelo en local:** non caben en 8 GB.
- **CI só en GitHub e GHCR por defecto:** quen faga un fork en A Eira ou noutra forxa libre quedaría sen CI nin imaxes; contradí a soberanía (20 % nas bases v3).
