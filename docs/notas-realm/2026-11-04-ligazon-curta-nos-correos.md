<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# 2026-11-04 · Ligazón curta nos correos de Keycloak: un provedor de modelos de correo e un recurso do realm

Desde o 2026-11-04, os correos de Keycloak cunha ligazón de acción (verificar o correo, cambiar o contrasinal, accións pedidas pola administración, cambio de correo e vinculación cunha conta externa) levan baixo o botón «Non funciona o botón? Abre esta ligazón:» e unha ligazón curta, `<orixe>/auth/realms/furar/l/XXXX-XXXX-XXXX`, no canto do URL longo. O correo da conta pasa a «Verifica o teu correo», co botón «Verificar o meu correo».

Fano dúas pezas novas da extensión de Furar ([`deploy/keycloak/README.md`](../../deploy/keycloak/README.md)):

- **Provedor de modelos de correo** `furar-short-link` (`ShortLinkEmailTemplateProvider`, `order` 100): Keycloak tómao por defecto, sen configurar nada, porque ten un `order` maior ca o de base (`freemarker`, 0). Garda o URL da acción baixo un código de 12 caracteres no almacén de obxectos dun só uso de Keycloak, coa mesma vida ca a ligazón. O botón leva sempre o URL orixinal; se o código non se pode gardar, o correo sae co URL longo.
- **Recurso do realm** `l` (`ShortLinkResource`): `GET /auth/realms/<realm>/l/<código>` responde 302 ao URL gardado ou, se non existe ou caducou, 404 coa páxina de erro do tema.

Nun Keycloak xa instalado **non hai que cambiar nada no realm**: o provedor, o recurso e os textos dos temas veñen coa imaxe `furar-keycloak` da versión nova. Basta con actualizar as imaxes ([`instalacion.md`](../instalacion.md)). Non hai ningún paso na consola de administración nin ningunha variable nova.

Os códigos viven na caché local de Keycloak: un reinicio pérdeos e, ata que caduque a ligazón, só vale o botón. Non se rexistran nin os códigos nin os URL.

**Comprobar:** desde «Entrar» → «Esqueciches o contrasinal?», pide o correo. Debe chegar co botón e, debaixo, a ligazón `…/auth/realms/furar/l/XXXX-XXXX-XXXX`; ao abrila, Keycloak leva á páxina para poñer o contrasinal. Un código inventado (`…/l/AAAA-AAAA-AAAA`) mostra a páxina de erro do tema, en galego, con como pedir outra ligazón.
