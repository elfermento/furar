<#ftl output_format="plainText">
<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-License-Identifier: EUPL-1.2

Código do segundo factor por correo (ADR-0029): a mesma información ca o correo HTML, en texto.
-->
<#import "footer.ftl" as footer>
${msg("furarEmailCodeTitle")}

${msg("furarEmailCodeLead", realmName)}

${code}

${msg("furarEmailCodeNote", ttlMinutes)}

${msg("furarEmailCodeIgnore")}

<@footer.text/>
