<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0043 · Eliminar a propia conta desde a web

- **Estado:** aceptada · 2026-11-17 (decidida polo mantedor)
- **Constrúese enriba de:** [ADR-0017](0017-datos-persoais.md) (dereitos e conservación), [ADR-0020](0020-rexistro-de-actividade.md) (rexistro de actividade), [ADR-0029](0029-segundo-factor-do-persoal.md) (Keycloak chama á API polo porto interno coa clave) e [ADR-0031](0031-persoas-a-cargo.md) (`close_account`)
- **Migracións:** `V202611171130__people_account_deletion.sql`

## Contexto
A supresión (RGPD art. 17) exercíase polas canles do concello: quen opera o despregue executaba `close_account('<sub>')` como dono da base e despois borraba a conta na consola de Keycloak. A API non sabía cando se borraba unha conta e o seu rol non podía chamar a función. Ademais, borrar unha conta con reservas futuras deixaría horas ocupadas sen ninguén detrás, e as reservas con pago teñen que conservarse 5 anos pola normativa tributaria.

## Decisión
1. **Entrada.** A última fila da pantalla «A miña conta», en vermello: «Eliminar a miña conta» / «Eliminar mi cuenta», para todas as contas (revisión do 2026-11-18: antes, a última do menú da conta). A web pregunta antes á API (`GET /api/v1/me/account-deletion`, `@PersonUseCase`) e, se queda algo pendente, un diálogo do kit lístao coas ligazóns e non vai a Keycloak.
2. **Impedimentos** (función `account_deletion_blockers`, unha soa lectura en todos os concellos, só tipos e cifras): reservas confirmadas que aínda non remataron (→ «As miñas reservas» do concello), partidas abertas que organiza e aínda non se xogaron (→ «As miñas reservas»), inscricións que piden ou teñen praza en eventos ou actividades sen cancelar que aínda non remataron (→ a ficha do evento), ser persoal activo dun concello ou administración da plataforma («pide que te quiten do persoal»). As esperas non impiden: bórranse. **Con impedimentos non se borra nada**: primeiro hai que cancelalo. Eliminar a conta nunca cancela reservas nin avisa a ninguén.
3. **Keycloak.** A acción `furar-delete-account` da extensión (`AccountDeletion`), que a web lanza con `kc_action=furar-delete-account`. A `delete_account` de Keycloak segue desactivada: non se pode condicionar. A de Furar pide entrar outra vez (idade máxima do acceso 0), pregunta á API polo porto interno (`POST /actuator/accountdeletion`, clave `furar-account-closure-v1`), mostra a confirmación do tema (que se borra e que se conserva) e, ao confirmar, **pregunta outra vez** e só entón borra. Se a API di que non ou non responde, non borra (fallo pechado) e só ofrece volver á web. Non fai falta o rol `account/delete-account`: a acción é propia.
4. **Aviso á API.** Despois do commit, o listener global `furar-account-closure` manda o `DELETE_ACCOUNT` a `POST /actuator/accountclosed`. Para non perder ningún, unha tarefa da extensión repasa cada 10 minutos os `DELETE_ACCOUNT` gardados no almacén de eventos de Keycloak (na mesma transacción ca o borrado; 90 días) desde unha marca do realm (`furarAccountClosureMark`), que só avanza ata o último que a API recibiu. A API é idempotente, así que repetir un aviso non fai nada.
5. **Limpeza na API** (`AccountClosureService`, módulo `people`): (a) `close_account` nunha transacción, a través de `close_account_counted`, que conta antes as filas de cada concello e despois chama a función; (b) os pasos dos módulos (SPI `AccountClosureStep` en `shared`): `booking` anonimiza xa as reservas **sen pago** da conta coa mesma anonimización ca a conservación; as **de pago** quedan ata o seu prazo (5 anos) e anonimízanse daquela. As dúas funcións novas corren como o dono (`security definer`), devolven só JSON con tipos e cifras e só as pode executar o rol da API.
6. **Rexistro de actividade.** Un evento `people.account-close` en cada concello onde se tocou algo, coa tarefa como quen actúa (`furar:account-closure`) e só as cifras (`rows`, `bookingsAnonymized`): nin a conta nin o correo. Unha segunda vez non toca nada nin deixa evento. Se un paso falla, queda o evento co que xa se fixo e o erro volve a Keycloak, que avisa outra vez.
7. **Claves.** `InternalCallKey` pasa a `shared.security` e deriva unha clave por propósito: a do segundo factor só pregunta si ou non; a de eliminar contas é outra.

## Consecuencias
- A supresión xa non precisa de quen opera o despregue nin do acceso de dono á base. A baixa manual segue sendo posible (borrar a conta na consola de Keycloak tamén deixa un evento de administración, pero ese non avisa á API: para iso segue `close_account`).
- Mentres o realm non leve os eventos activados (`eventsEnabled`, por defecto en Furar) e `DELETE_ACCOUNT` entre nos tipos gardados (os de por defecto inclúeno), a tarefa non ten de onde reintentar.
- Se a API pasa máis de 90 días sen responder, os avisos dese período pérdense (o rexistro técnico de Keycloak dio en cada pasada).
- Unha reserva feita noutra lapela entre a confirmación e o borrado quedaría (se é sen pago) anonimizada pero ocupando a hora: a xanela son segundos e a xestión vea como «Persoa anonimizada».
- A confirmación di «5 anos»: se un despregue cambia `furar.booking.paid-retention`, o texto do tema non o segue.
- A descarga dos datos da persoa segue pendente.

## Alternativas descartadas
- **A `delete_account` de Keycloak co rol `account/delete-account`:** non hai como condicionala á API; borraría contas con reservas futuras.
- **Reimplementar `close_account` en cada módulo con eventos:** máis código e a mesma lóxica en dous sitios; a función xa é a fonte única e cada módulo redefínea.
- **Unha tarefa da API que busque contas desaparecidas:** a API non ten credenciais de Keycloak e tería que listar todas as contas; o almacén de eventos xa garda cada borrado na mesma transacción.
- **Cancelar as reservas futuras ao eliminar a conta:** avisaría o concello dunha baixa e liberaría horas sen que a persoa o decida; mellor que as cancele ela.
