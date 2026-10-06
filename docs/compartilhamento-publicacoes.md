# Compartilhamento de publicações

`GET /publicacao/{id}` é uma página pública HTTPS que permite ler a publicação sem instalar o aplicativo. O botão abre `nhac://app/publicacao/{id}`; publicações excluídas retornam 404. Conteúdo textual é escapado e a página não permite scripts.

Para a abertura automática dos links Android, configure `NHAC_ANDROID_SHA256_CERTIFICATES` no ambiente do backend com o SHA-256 do certificado que assina o APK distribuído (ou do certificado de assinatura do Google Play). Use o formato com dois dígitos hexadecimais separados por `:`; se houver mais de um certificado, separe por vírgula. Consulte com `keytool -list -v -keystore <arquivo>`.

A associação é publicada em `/.well-known/assetlinks.json` para `com.feentzs.nhac`. Sem certificado configurado, a leitura no navegador e o botão explícito continuam disponíveis; o Android não verifica a abertura automática. Não publique o certificado de debug como certificado de produção.

No iOS o esquema `nhac` está registrado. Universal Links requerem o Team ID/certificado da distribuição e uma associação Apple para o domínio; essas credenciais não estão no repositório.
