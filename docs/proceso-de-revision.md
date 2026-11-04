<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# Proceso de revisión da base técnica

Do 2026-10-22 ao 2026-10-23. Un equipo de catro roles (arquitecto, backend, frontend, infra) construíu a base técnica e seis revisores independentes revisárona en catro roldas: software libre, seguridade, administración pública, arquitecto externo, operación e testing. Despois houbo dúas roldas de QA independente. Cada hallazgo tiña un dono, e o dono corrixíao ou rebatíao razoadamente. Un xuíz decidía as réplicas.

**Conclusión:** a arquitectura non cambiou en ningunha rolda: monolito modular hexagonal, multiconcello nunha base, campos personalizados en JSONB validado e ocupación resolta en PostgreSQL. O que cambiou foi canto se pode probar cada promesa. Ningún rol asinou «base terminada», por tres motivos: non hai commits nin CI pública, quedan catro fallos pequenos e falta o dominio de reservas.

## Roldas

| Rolda | Hallazgos | Principais |
|---|---|---|
| 1 | 58 (27 major) | Forxa atada a GitHub; API como superusuario; probas de illamento que fallaban por casualidade; falta RGPD, ENS, accesibilidade legal e portabilidade; plantilla só co esqueleto; galego incompleto en Keycloak; CI sen executar |
| 2 | 35 (14 major) | A Eira sen release nin firma; 2FA dependente de grupos; RLS sen probar con Hikari; booking cruzaba a fronteira de facility; portabilidade só no papel; `skipped` sen controlar; plantilla web só coa lista |
| 3 | 29 (11 major) | Keycloak master aberto na LAN; a API non esixía OTP; exportación sen auditoría; Q12 e Q13 (SQL que o build aceptaba); ARQ2-6 só no papel; exceso de cerimonia (54 filas, 19 min) |
| 4 | 25 (10 major) | RLS e rexistro por módulo dados por «fixed» sen código; datos legais sen implementar; OTP nunca probado con Keycloak real; etiqueta `v*` no repo privado publicaba; plan do 26-oct en risco |

Na rolda 4 aprobou o especialista en software libre. Os outros cinco revisores pediron cambios, e ao final da rolda todos os seus puntos abertos estaban arranxados no código.

## Que se cambiou (o máis relevante)

- **Illamento entre concellos:** as probas sementan por JDBC e fallan nas asercións; FK compostas `(municipality_id, id)`; **RLS no código** (`V202610231100`, `TenantJpaTransactionManager`, `TenantRlsSpringIT`), cunha proba que a esixe en cada táboa nova; A22 (sen SQL en anotacións); A8b analiza o bytecode e rexeita SQL partido e comas no FROM; `sen-omitidos.sh` impide `skipped > 0`.
- **Base de datos:** roles `furar_owner`/`furar_app`, servizo `migrate` dun só uso, historial de Flyway e `audit_event` só de lectura e engadido para a API.
- **Seguridade:** consola de Keycloak pechada, Caddy en 127.0.0.1, límites de corpo, CSP sen `unsafe-inline`, contedores con `cap_drop: ALL`, 2FA ligado ao rol, OTP esixido pola API (`auth.step-up-required`) e probado con Keycloak real. Esa proba atopou un fallo real: `maxAge 0` perdía `otp` ao renovar o token. (O segundo factor retirouse despois, o 2026-11-02, por decisión do mantedor: [ADR-0007](adr/0007-seguridade-pechada.md).)
- **Administración pública:** RGPD (ADR-0017, cláusula no rexistro, privacidade), ENS (`ens.md`), declaración de accesibilidade, portabilidade `furar-export` implementada, Cl@ve simulada (ADR-0019), ENI, datos legais do concello na API, galego completo en Keycloak.
- **Software libre:** comprobación SPDX completa, avisos de terceiros dentro das tres imaxes, etiquetas OCI, `publiccode.yml` validado, CODE_OF_CONDUCT, GOVERNANCE, CHANGELOG, oferta do código fonte das bases GPL e `sen-marcadores.sh`, que bloquea unha release con contactos pendentes.
- **Reutilización:** a plantilla xera CRUD editable con ETag, campos personalizados, portabilidade, formulario web, e2e con axe e permisos e mensaxes propios (`novo-modulo.sh`, D45 en ~1 min).
- **Operación:** fluxo de release, proba real do overlay de produción (D48), copias con versión, cifrado age, rotación e cerrollo, e CI só publica no repo público.
- **Ritmo:** carril rápido `rapido.sh` (~20 s; con `--tenancy`, ~30 s) e DoD conxelada.

## Que se rebateu e por que

- **CI pública (T3, LIC-8, OPS-R2-1):** correcto, pero depende do mantedor (commit e forxa). O xuíz deu a razón á réplica.
- **Multiarquitectura (OPS-4):** só amd64. Publicar arm64 sen e2e nesa arquitectura rompería «publícase o que pasou o e2e».
- **Límite de taxa (SEC-05):** aprazado; precisa unha imaxe de Caddy propia. Documentado en `ens.md`.
- **Variante lixeira de módulo (ARQ-9 → ARQ2-5):** introducida e despois retirada. Unha soa forma de módulo, a da plantilla.
- **Fusionar ADR (ARQ3-3):** non. Son un rexistro e cítanse en 30 ficheiros; en troques, unha lista de 8 para ler en dez minutos.
- **Reescribir A8b ou partir `shared` (ARQ3-1, ARQ3-4):** non. Coa RLS esixida, a regex é só unha barreira grosa; A20 pecha `shared` sen mover 40 clases.
- **Menos portos de entrada (ARQ4-3):** non agora. Son 114 liñas; refacer a plantilla antes de facility vai contra o prazo. Revísase despois do 9-nov.
- **`FURAR_ADMIN_IPS` só loopback en local (SEC3-01):** non. En Linux docker-proxy entrega como 172.x; abondou co bind a 127.0.0.1.

## Desacordos e débedas abertos

1. **Prioridade:** os catro roles e o arquitecto externo coinciden en que hai que deixar de endurecer a base (62 filas, 25 min de verificación, ~3.600 liñas de shell) e pasar a `facility` e `booking`. Ninguén o discute; falta que o mantedor decida o plan do 26-oct.
2. **Fallos pequenos (< 1 h en total):** D14 e D16 (backend), P1 e P7 (frontend). Hoxe `verificar.sh` dáos por bos aínda que non cumpren a súa fila.
3. **Web:** scopes de Transloco e `loadChildren` por feature (antes de facility); páxinas legais co `legal` do concello.
4. **Dúas CI:** infra propón deixar GitLab fóra da verificación ata despois da entrega.
5. **Sen demostrar:** CI, `publicar-v1.sh` con etiqueta real, actualización vN-1 → vN, e2e xerado pola plantilla executándose (criterio: facility en D34).
