<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# ADR-0011 · Campos personalizados: definicións por concello e valores en táboas tipadas

- **Estado:** aceptada · 2026-10-22 (requisito de produto; revisada tras a revisión do equipo)
- **Revisada 2026-10-25 (dominio 1, [especificación](../produto/especificacion.md) bloque 1b):** valores en táboas tipadas, uso por destino e orde alfabética.
- **Revisada 2026-11-04 (maqueta 57, decisión do mantedor):** un campo, un sitio (un só uso por definición); destino novo `BOOKING`, sen campos públicos; arquivar pasa a **desactivar** (reversible) e engádese **eliminar**, só sen valores, para o campo e para cada opción. Detalle en «Revisión do 2026-11-04», ao final da decisión.

## Contexto
Cada concello quere datos propios (código interno, «leva desfibrilador», «nº de federado» no formulario de reserva) sen tocar código e cunha UX coidada. Se cada un fai a súa copia, o proxecto pártese. A especificación pide ademais que **a base garanta o tipo** de cada valor e que un mesmo campo se poida usar en varios sitios (instalación, máis adiante reserva ou perfil) con regras distintas en cada un. A revisión do 2026-11-04 deixouno nun sitio por campo: no editor, varios sitios co seu obrigatorio e visibilidade cada un resultaban difíciles de ler e de explicar.

## Decisión
Módulo transversal **`customfields`** (só depende de `shared`). Destinos rexistrados polos módulos donos (`CustomFieldTarget` e un bean `CustomFieldTargetProvider`, ADR-0005): `MUNICIPALITY`, `FACILITY` e `BOOKING` (desde o 2026-11-04). Outros (`RESIDENT_PROFILE`, `ORGANIZATION`) rexístraos o seu módulo cando use campos; a base só comproba o formato do código.

**Táboas** (todas con `municipality_id`, FK compostas e RLS, ADR-0006; detalle no [plan](../plan/dominio-1-instalacions.md) §2.1):

| Táboa | Garda | Garantías na base |
|---|---|---|
| `custom_field` | `code` (`^[a-z][a-z0-9_]{1,39}$`, único no concello, **inmutable**), `type` (`TEXT`, `LONG_TEXT`, `NUMBER`, `DATE`, `BOOLEAN`, `SELECT`, `MULTI_SELECT`; inmutable con valores), `label`/`help` `jsonb {gl,es}`, `personal_data`, regras tipadas (`min_length`, `max_length`, `min_number`, `max_number`, `decimals`, `min_date`, `max_date`, `text_format` = `email`/`phone`/`url`, `max_choices`; **sen regex libre**: ReDoS e erros do xestor), `status` (`ACTIVE`, `INACTIVE`; bórrase só sen valores), `version` | `check` de código, tipo e regras por tipo; `unique (municipality_id, id, type)` e `unique (municipality_id, id, personal_data)` para as FK de abaixo |
| `custom_field_option` | `code` inmutable, `label {gl,es}`, `inactive` | único por campo; bórrase só sen valores (FK dos valores) |
| `custom_field_usage` | `target`, `required` (non retroactivo), `visibility` (`PUBLIC`, `OWNER`, `STAFF`), copia de `personal_data` | **un por campo** (`unique (municipality_id, field_id)`, 2026-11-04); FK `(…, field_id, personal_data)` con `on update cascade`; `check (not personal_data or visibility <> 'PUBLIC')`; `check (target <> 'BOOKING' or visibility <> 'PUBLIC')` |
| `custom_field_value` | `field_id`, copia de `field_type`, `target`, `entity_id`, `text_value`, `number_value numeric`, `date_value`, `bool_value`, `option_id` | FK ao tipo do campo, ao uso (non hai valor nun destino onde o campo non se usa) e á opción **do mesmo campo**; `check` «exactamente a columna do tipo» (`MULTI_SELECT`: todas nulas); un valor por (destino, entidade, campo) |
| `custom_field_value_option` | opcións marcadas dunha selección múltiple | FK ao valor e á opción do mesmo campo |

- **`entity_id` é polimórfico e sen FK.** Unha FK por destino obrigaría a unha táboa de valores por módulo ou a columnas por destino. A integridade cóbrena: RLS (o valor só se ve no seu concello), o almacén, que comproba que a entidade existe e é do concello antes de `replace` (chámao o módulo dono, na súa transacción), e o borrado e o anonimizado vía `PersonalDataHolder` (abaixo). Sen FK non hai `on delete cascade`: as entidades con campos non se borran (desactívanse ou cancélanse), e unha que se borre borra os seus valores no seu caso de uso.
- **Titular** para `OWNER`: dunha reserva ou perfil, a súa persoa; do concello, quen ten algún rol nese concello (`Audiences.forMunicipality`: o persoal), non calquera conta: o realm admite autorrexistro (SEC-03). Na lista dun destino cuxo módulo di que o titular é quen crea a entidade (`CustomFieldTargetProvider.ownedByItsCreator`, a reserva), quen ten sesión ve os campos de titular para enchelos.
- **Dato persoal** marcado: visibilidade `STAFF` por defecto no editor (RGPD art. 25.2); ao marcalo, `PUBLIC` desactívase e aparece o aviso fixo de categorías especiais (art. 9).
- Límites: 50 usos por destino; 32 KB de valores por entidade.
- **Orde:** alfabética no idioma de quen mira, calculada no servidor. Sen orde manual nin grupos.

**Almacén de valores** (raíz, A19, xunto co validador e o proxector de abaixo): `CustomFieldValueStore.replace(target, entityId, values)` e `loadAll(target, ids)` (unha consulta por páxina). O módulo dono valida, chama a `replace` **na mesma transacción** e sobe a súa propia versión (gardar só campos cambia o `ETag` da entidade).

**Validación no servidor, no caso de uso do módulo dono** (nunca só na web):
- O valor validado é **`shared.domain.CustomFieldValues`**: mapa inmutable `code → CustomFieldValue` selado (`Text`, `Number(BigDecimal)`, `Date(LocalDate)`, `Bool`, `Choice`, `Choices`). Vive en `shared.domain` para que o dominio de calquera módulo o garde sen depender de `customfields` (A1, M2). Só se crea con `CustomFieldValues.trusted(..)` desde `customfields` ou `fromStorage(..)` desde un adaptador de persistencia (regra A16; o lector das táboas tipadas): o tipo garante que todo o que chega ao agregado pasou polo validador.
- API pública de `customfields`: `CustomFieldValidator.validate(target, rawValues, actor)` → `CustomFieldValues` ou `CustomFieldValidationException` con `errors[]` (`field: "customFields.<code>"`, `code: "customfield.required" | "customfield.too-long" | "customfield.unknown-field" | "customfield.invalid-option" | "customfield.limit-exceeded" | …`).
- `customfields` só depende de `shared`: o concello sae de `TenantContext` (M3 fíxao).
- Claves descoñecidas rexéitanse. Valores de campos desactivados consérvanse pero non se poden cambiar (`customfield.inactive-field`); unha opción desactivada non se escolle nun valor novo, pero un gardado consérvaa. Campos que o actor non pode ver non se poden escribir.
- `CustomFieldProjector.project(target, values, viewer)` quita na lectura o que o visitante non pode ver; nas listas e CSV, `projectAll(target, List<values>, viewer)` le as definicións **unha vez** para toda a páxina (ARQ-6), nunca `project` por fila.

**RGPD dos campos personalizados** (AP-03):
- `personalData ⇒ visibility ≠ PUBLIC`, no dominio e na base.
- O editor (web) avisa das **categorías especiais** (art. 9 RGPD: saúde, discapacidade, alerxias, relixión…): son típicas dun formulario deportivo e piden base legal e finalidade explícitas. Mentres non exista a finalidade como dato da definición, o editor pide escribila na axuda do campo e lembra que a decisión é do responsable do tratamento (o concello).
- **Porto de dereitos** que implementará cada módulo dono dunha entidade con persoa (`people`, `booking`, `organization`), en `shared`: `PersonalDataHolder { Map<String,Object> export(PersonId); void anonymize(PersonId); void erase(PersonId); }`. Para os campos personalizados, cada implementación pide a `customfields` os valores `personal_data` do seu destino e: expórtaos, bórraos ao anonimizar (nas táboas tipadas «sen valor» é non ter fila) e ao suprimir; os campos núcleo trátaos o propio módulo. Un IT por módulo comproba os tres camiños. O porto créase co módulo `people`. `BOOKING` (2026-11-04) é o primeiro destino de persoas: o almacén xa ofrece `personalData`, `erasePersonalData` e `deleteAll`, pero `booking` aínda non ten camiño de anonimizar nin de suprimir reservas (nin traballo de retención que as borre); cando o teña, chamará a eses métodos na mesma transacción. Pendente ata entón.

**API:**
- `GET /api/v1/municipalities/{slug}/custom-fields?target=MUNICIPALITY` → definicións que se usan nese destino e que quen pregunta pode ver, con `usages[] {target, required, visibility}` e `status` (para pintar formularios; as desactivadas, `INACTIVE`, só para ler os valores gardados, punto «Desactivar e eliminar»). Pública só a parte `PUBLIC`. Con `includeInactive=true` (xestor) e, sen `target`, todas, con `hasValues`.
- `GET …/custom-fields/{target}/schema` → JSON Schema 2020-12 xerado (para terceiros e importacións).
- `POST`, `PUT …/custom-fields/{id}`, `POST …/{id}/deactivate`, `POST …/{id}/activate`, `DELETE …/{id}` (permiso `customfields.manage`: `MANAGER` e a plataforma). Na lista do xestor (`includeInactive=true`) e nas respostas de xestión, `hasValues` no campo e en cada opción (para abrir o diálogo axeitado); a API compróbao igual ao eliminar e ao gardar.
- Nos DTO da entidade non cambia nada: `"customFields": { "phone": "981…", "has_defibrillator": true }`.
- Exportación: cada definición leva `usages[]` (un elemento) en `furar-export` v3; un ficheiro da versión 1 (cun `target` único) e da 2 (`ARCHIVED`, `archived`) convértense ao importar (`CustomFieldsDataSection.upgrade`, [ADR-0018](0018-portabilidade.md)). Unha definición v2 con varios usos rexéitase (`customfield.single-usage`): os valores das outras seccións van polo código e partila cambiaría as claves.

**Web:** `shared/custom-fields/`: `CustomFieldsFormComponent` pinta calquera conxunto de definicións con Reactive Forms (etiqueta visible, axuda entre a etiqueta e o control, `aria-describedby` para axuda e erro, erros do servidor no campo, gl/es); `CustomFieldDefinitionEditor` para o xestor con vista previa en vivo usando o mesmo compoñente.

**Revisión do 2026-11-04 (maqueta 57):**
- **Un campo, un sitio.** O editor ten arriba o selector «Concello | Instalación | Reserva» e, debaixo, só o obrigatorio e quen o ve nese sitio. Co campo xa con valores o sitio non cambia (`customfield.target-has-values`, 409) e o editor dío nunha liña. Na API, `usages` leva exactamente un elemento (400 na petición, `customfield.single-usage` no dominio); a táboa `custom_field_usage` queda como estaba, cun `unique` máis.
- **Migración dos campos con varios usos (`V202611041200`):** pártense, sen perder valores. O uso con máis valores (e, se empatan, o primeiro por orde alfabética) queda co campo e o seu código; cada un dos outros pasa a un campo novo, copia do orixinal (textos, regras, opcións e estado), co código `<código>_<destino>`, e os seus valores e opcións marcadas móvense a el. Recontos nun bloque `DO`. Escolleuse partir, e non deixar os vellos con varios usos, para que o modelo teña unha soa regra e a base a garanta.
- **`BOOKING`:** rexístrao `booking` (`BookingCustomFieldTarget`): `OWNER` («a persoa que reserva e o persoal») ou `STAFF` («só o persoal»), nunca `PUBLIC` (dominio, `customfield.visibility-not-allowed`, e base). Quen reserva enche os activos no mesmo paso, antes de «Confirmar reserva»; o caso de uso de reservar valídaos como titular e gárdaos coa reserva na mesma transacción (`CustomFieldValueStore.replace`); van na pegada da clave de reintento. O persoal ve todos no detalle da reserva; quen reservou, os seus. O persoal aínda non crea reservas por outra persoa nin hai CSV de reservas: cando existan, usarán o mesmo validador e `projectAll`.
- **Desactivar e eliminar** (campo e opcións; substitúe «arquivar»): desactivar oculta dos formularios e dos valores novos, conserva os gardados e séguense vendo (a lista pública dun destino trae tamén os desactivados, con `status`, só para ler); activar devólveo. Eliminar pregunta sempre; sen valores, borra; con valores, `customfield.has-values` ou `customfield.option-has-values` (409) e o diálogo ofrece desactivar. As opcións quítanse da lista na edición; a API bórraas se non teñen valores.

**Goberno:** se 3 ou máis concellos definen un campo equivalente, ábrese issue para promovelo a campo núcleo: columna nova + migración que copia o valor desde `custom_field_value` + desactivado da definición.

**Migración (D1-10a, `V202610250900`):** unha soa migración crea as táboas, copia definicións e valores de `MUNICIPALITY` desde `custom_field_definition` e `municipality.custom_fields`, compara recontos nun bloque `DO` (falla se non casan) e só entón borra a columna JSONB e o seu índice GIN.

**Tests:**
- `CustomFieldValidatorTest`: cada tipo e regra.
- `JsonSchemaEquivalenceTest`: o corpus **xérase desde as definicións** (valores frontera por tipo e regra: mín−1, mín, máx, máx+1, opción desactivada, tipo errado, clave descoñecida…) e o JSON Schema (networknt) e o validador Java teñen que aceptar e rexeitar o mesmo.
- Ida e volta (IT): os 7 tipos gardan e volven sen perda (decimais incluídos); a base rexeita tipo errado, opción doutro campo, `PUBLIC` con dato persoal e cambio de tipo con valores; a migración próbase desde unha base co esquema anterior.
- `CustomFieldsLimitsIT`: uso 51 nun destino e valores > 32 KB → `customfield.limit-exceeded`.
- `CustomFieldsVisibilityIT`: o mesmo recurso lido como anónimo, titular e persoal devolve só `PUBLIC`, `PUBLIC+OWNER` e todo; unha conta sen rol no concello (ou cun rol doutro) ve só `PUBLIC`.
- `CustomFieldsTenantIT`: as definicións de A non validan nin aparecen en B.
- Web: test do compoñente, test de teclado (orde de Tab, foco ao primeiro erro, erros anunciados) e e2e con axe en gl e es.

## Consecuencias
- Un concello engade un dato en minutos e sen despregue.
- A base garante o tipo, a opción e a visibilidade dos datos persoais, non só o validador.
- Filtrar por selección e si/non é un `join` indexado por (campo, opción); serve para indicadores e CSV. Texto libre non é filtrable nin agregable.
- Ler unha páxina de entidades custa unha consulta máis (`loadAll`), nunca unha por fila.
- Os campos núcleo non se poden «sobrescribir» cun personalizado do mesmo nome (lista reservada).

## Alternativas descartadas
- **Un campo en varios sitios (deseño do 2026-10-25, retirado o 2026-11-04):** un formulario con caixas por sitio e regras por sitio custaba de entender; para pedir o mesmo dato noutro sitio, defínese outro campo.
- **Arquivar sen eliminar (ata o 2026-11-04):** os campos e opcións creados por erro quedaban para sempre na lista; agora elimínanse se non teñen valores e, se os teñen, desactívanse.
- **Valores en `custom_fields jsonb` con índice GIN (deseño do 2026-10-22, retirado o 2026-10-25):** a base non garantía o tipo nin que a opción existise ou fose do campo, un campo non se podía reutilizar noutro destino con outras regras, e borrar ou anonimizar datos persoais obrigaba a reescribir o documento enteiro.
- **EAV xenérico (unha columna `text` de valor):** tipos perdidos e consultas con conversións. As táboas tipadas son unha táboa de valores cunha columna por tipo e un `check`: a base garda o tipo.
- **Unha táboa de valores por destino con FK á entidade:** cada módulo novo crearía a súa táboa e repetiría as garantías; a lista de destinos medra e o código común non.
- **`appliesWhen`, orde manual e grupos:** non están na especificación (P28); fase 2 se os piden.
- **Columnas engadidas por concello:** DDL en caliente e esquema distinto por instalación.
- **Validar só con JSON Schema:** códigos de erro pobres e sen contexto (visibilidade, desactivado, condición); quédase como exportación e proba de equivalencia.
- **Só na web:** calquera cliente da API saltaríao.
- **`ValidatedCustomFields` como tipo de `customfields`** (deseño inicial): o dominio doutros módulos dependería dun subpaquete alleo (A1/M2) e habería ciclo con `municipality`.
- **Probas por propiedades con jqwik:** válidas (EPL-2.0), pero o xerador de fronteiras desde as definicións cobre os mesmos casos sen outra dependencia.
