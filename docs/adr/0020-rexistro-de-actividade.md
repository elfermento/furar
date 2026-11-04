<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0020 · Rexistro de actividade: táboa só de engadir e porto `AuditLog`

- **Estado:** aceptada · 2026-10-23 (SEC3-02, AP3-02) · revisada o 2026-10-28 e o 2026-10-29
- **Revisada 2026-10-28 (dominio 4, [ADR-0027](0027-persoal-e-permisos-fase-1.md) punto 12):** roles de quen actúa, eventos do persoal e lectura do rexistro.
- **Revisada 2026-10-29 (LOPDGDD art. 32):** conservación en tres tramos (visible, bloqueado, borrado) e exportación dos bloqueados.

## Contexto
A exportación completa dun concello ([ADR-0018](0018-portabilidade.md)) leva todos os seus datos, tamén os campos marcados como dato persoal, e medra con cada módulo (`people`, `booking`). Sen rexistro non quedaría rastro de quen a descarga: o ENS (op.exp.8) pídeo en categoría MEDIA e, sen el, unha brecha non se podería detectar nin notificar (RGPD art. 33). O persoal do concello actúa sobre reservas, instalacións e outras persoas: o rexistro serve tamén para exixir responsabilidades, tamén disciplinarias, a quen actuou ([ADR-0017](0017-datos-persoais.md) punto 9).

## Decisión
- **Porto `AuditLog`** en `shared.audit` (`record(AuditEvent)`): acción estable (`municipality.export`), resultado (`SUCCESS`/`FAILURE`), concello, slug e detalles **sen datos persoais** (seccións e número de filas, código de erro). O adaptador (`JdbcAuditLog`) engade `sub`, hora, `X-Request-Id` e **`actor_roles text[]`**: os roles efectivos de quen actúa nese concello (da táboa de persoal e, para a plataforma, da táboa `platform_admin`, [ADR-0028](0028-administracion-da-plataforma-e-primeiro-acceso.md); `'{}'` sen ningún). Así cada acción di con que rol e en que concello se fixo.
- **Táboa `audit_event`** da plataforma (en `TenancyAllowlist`: recolle accións sen concello e sobrevive a el, por iso `municipality_id` non ten FK). `furar_app` só fai `INSERT` e `SELECT`: unha inxección SQL non borra nin cambia o rastro (`DatabaseRolesIT`).
- **Transacción propia** (`REQUIRES_NEW`): queda aínda que o caso de uso falle ou sexa de só lectura. **Antes de devolver os datos:** se non se pode escribir, a exportación falla (sen rastro, sen descarga).
- Cada evento vai tamén ao logger `furar.audit` (JSON ECS en `prod`), para un recolector fóra do servidor (op.exp.10).
- Cobertura: exportar e importar un concello, boas e fallidas (`MunicipalityPortabilityIT`); as escrituras de `facility` (ficha, publicación `facility.publish`, usos, horario, bloqueos, fotos e deportes propios); a primeira administración da plataforma (`platform.first-admin`, [ADR-0028](0028-administracion-da-plataforma-e-primeiro-acceso.md)); o cobro e a cancelación de reservas desde a xestión e as cancelacións por un cambio da instalación (`booking.payment`, `booking.cancel`); e o persoal: `staff.invite`, `staff.invite-resend`, `staff.accept`, `staff.role-change`, `staff.deactivate`, `staff.reactivate`, `staff.sign-in` (un por sesión e concello) e `staff.anonymize` (a tarefa de conservación), con éxito e fallo, e detalles `memberId`, `role`, `fromRole`/`toRole` (e `obligationsVersion` en `staff.accept` ata o 2026-11-03) e código de erro, nunca correo nin token. Cada módulo novo engade as súas accións sensibles; a plantilla de módulo o lembra.
- **Lectura:** porto `shared.audit.AuditTrail` (`JdbcAuditTrail`, `@NativeSql(GLOBAL)` co concello explícito e paxinación por `(occurred_at, id)`), usado polos casos de uso de `staff`, que poñen nome e correo a cada `sub`. O xestor le o seu concello (`audit.view`), tamén o que fixo a plataforma; a plataforma, só os seus eventos e os sen concello (`audit.platform-view`). Táboa de eventos e prazos: [`ens.md`](../ens.md).
- **Conservación en tres tramos**, contados desde o evento (as infraccións máis graves da LOPDGDD, art. 72, e as faltas moi graves do persoal, TREBEP art. 97 e Lei 2/2015 do emprego público de Galicia, prescriben aos 3 anos):
  1. **Visible** ata `FURAR_AUDIT_VISIBLE` (`P1Y` por defecto): a xestión leo na pantalla, cos filtros.
  2. **Bloqueado** (LOPDGDD art. 32) ata `FURAR_AUDIT_RETENTION` (`P3Y` por defecto): ningunha lista da API o devolve (nin a do concello nin a da plataforma: o período recórtase ao tramo visible) e só o xestor do concello o exporta (`POST …/audit-events/blocked-exports`, permiso `audit.export-blocked`, nunca a plataforma) para un período de ata 366 días e 10 000 eventos, cun **motivo pechado** (requirimento xudicial, Ministerio Fiscal, autoridade de protección de datos ou expediente aberto) e unha **referencia** curta (número de expediente ou de dilixencias, ata 80 caracteres). A exportación deixa `audit.export-blocked` co motivo, a referencia, o período e cantos eventos, sen datos persoais; os intentos fallidos tamén.
  3. **Borrado** físico ao pasar `FURAR_AUDIT_RETENTION`, pola tarefa diaria.
  - A configuración valida que `retention` sexa máis longo ca `visible` e polo menos `P1Y`. Os prazos van no porto `shared.audit.AuditRetention` e a web lenos de `GET /api/v1/privacy-notice` (`activityVisible`, `activityRetention`).
  - **A identidade do persoal desactivado** segue os mesmos tramos desde a desactivación: visible ata `visible`, despois bloqueada («Persoa desactivada», sen nome nin correo na lista de persoal, no filtro e no rexistro; si na exportación dos bloqueados) e anonimizada ao pasar `retention`, cando xa non queda ningún evento seu ([ADR-0027](0027-persoal-e-permisos-fase-1.md), punto 14). Riscos e fase 2: [`ens.md`](../ens.md#riscos-aceptados-e-coñecidos).

## Alternativas descartadas
- **Só liñas de rexistro:** Docker rótaas (3 × 10 MB) e dependen de que o concello monte un recolector; a táboa non.
- **Limitar a exportación a `PLATFORM_ADMIN`:** co rexistro non fai falta; o RGPD (art. 20) e a reutilización piden que o concello poida levar os seus datos sen depender da plataforma, e a exportación leva datos da veciñanza, que a plataforma non ve.
- **Borrar cada evento ao ano** (prazo inicial, retirado o 2026-10-29): perdíase a proba para un expediente disciplinario ou un requirimento antes de que prescriban.
- **Deixalo visible tres anos:** o art. 32 da LOPDGDD pide bloquear o que só se garda para responsabilidades.
- **Que a plataforma exporte os eventos bloqueados:** o responsable é o concello.
- **Auditar todo caso de uso con `@RequiresPermission` por AOP:** rexistraría lecturas sen valor e non sabería as filas; mellor explícito nas accións sensibles.

## Consecuencias
- Unha consulta responde «quen exportou Narón e cando» (`select … from audit_event where municipality_slug = 'naron'`).
- **Borrado sen dar `DELETE` (SEC4-03, AP4-02, RGPD art. 5.1.e):** `furar.audit.visible` e `furar.audit.retention` (ISO 8601). Unha tarefa diaria (`JdbcAuditRetention`) chama a `purge_audit_events` (`V202610231110`; o mínimo dun ano, `V202610291200`), unha función `security definer` do dono: `furar_app` segue sen `DELETE` nin `TRUNCATE` en `audit_event` e, aínda cunha inxección SQL, só pode borrar o que ten máis dun ano. Probas: `AuditRetentionTest`, `AuditRetentionIT`, `ActivityIT`.
