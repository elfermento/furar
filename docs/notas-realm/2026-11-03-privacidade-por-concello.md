<!--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: CC0-1.0
-->

# 2026-11-03 · Os datos de privacidade, de cada concello: sen `FURAR_PRIVACY_*` no realm

Desde o 2026-11-03 ([ADR-0017](../adr/0017-datos-persoais.md), revisión do 2026-11-03), o responsable, o DPD, as canles dos dereitos e o encargado son datos legais de cada concello, que se enchen na web. A instalación xa non os pregunta e o realm xa non os leva: desaparecen as variables `FURAR_PRIVACY_CONTROLLER`, `FURAR_PRIVACY_DPO`, `FURAR_PRIVACY_RIGHTS`, `FURAR_PRIVACY_PROCESSOR` e `FURAR_PRIVACY_POLICY_URL` e os textos do realm `furarPrivacy*` (*Localization* → *Realm overrides*).

O rexistro mostra agora unha frase xenérica («O concello trata os teus datos para xestionar a túa conta. Privacidade e os teus dereitos») coa ligazón á «Privacidade» do concello desde o que se entra: a web pon o camiño no parámetro `furar_privacy` e a extensión de Keycloak (o paso `furar-invitation-email` do fluxo de rexistro, que xa está) dá a ligazón na orixe do `redirect_uri`. Sen concello non sae a frase. Os correos de Keycloak deixan a ligazón «Privacidade» do pé.

Nun Keycloak xa instalado non hai que cambiar nada para que funcione: o tema e a extensión veñen coa imaxe e xa non len eses textos. Para deixar o realm limpo (opcional), na consola de administración (túnel SSH, [`instalacion.md`](../instalacion.md) §5), realm `furar`:

1. *Realm settings* → *Localization* → *Realm overrides*, en `gl` e en `es`: borra `furarPrivacyController`, `furarPrivacyDpo`, `furarPrivacyRights`, `furarPrivacyProcessor` e `furarPrivacyPolicyUrl`. Quedan sen uso.
2. No `.env`, borra as liñas `FURAR_PRIVACY_*` (`scripts/instalar.sh --substituir` xa non as copia) e aplica con `docker compose up -d`.
3. Enche os datos legais de cada concello na web (Concello › Legal).

**Comprobar:** abre o rexistro desde a web dun concello («Entrar» → «Crear a conta»): a frase leva a `https://‹servidor›/‹concello›/privacidade`. D51 (`scripts/proba-identidade-keycloak.sh rexistro`) próbao cun Keycloak de usar e tirar.
