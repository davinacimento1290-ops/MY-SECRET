# MY SECRET

Aplicativo Android privado para organizar publicações, notas, favoritos e álbuns localmente.

## Tecnologias
- Kotlin + Jetpack Compose
- Room + SQLCipher para banco local criptografado
- Android Keystore + AES-GCM para proteger a chave do banco e o conteúdo dos backups
- PBKDF2-HMAC-SHA256 (210.000 iterações) para derivar o verificador do PIN
- GitHub Actions para build Android

## Funcionalidades presentes no código
- Configuração local de PIN e desbloqueio.
- Bloqueio temporário após sequência de tentativas inválidas.
- Feed cronológico com criação, pesquisa e exclusão de publicações.
- Categorias e favoritos.
- Cofre privado com itens de texto, links e informações.
- Criação e listagem de álbuns.
- Exportação e restauração de backup autenticado com AES-GCM.
- Banco Room configurado com SQLCipher e chave aleatória protegida pelo Android Keystore.

## Limitações que ainda precisam ser concluídas
- Desbloqueio biométrico criptograficamente vinculado.
- Anexos de fotos/documentos e visualização real da galeria.
- Recuperação de acesso com procedimento seguro.
- Backup portátil protegido por senha definida pelo usuário. O backup atual usa uma chave do Android Keystore do aparelho e só restaura no mesmo aparelho.
- Testes automatizados, auditoria de segurança, build de release e assinatura final.

## Build
O workflow de GitHub Actions executa `gradle :app:assembleDebug` com JDK 17 e Gradle 8.9. O APK de depuração esperado é `app/build/outputs/apk/debug/app-debug.apk`.

**Estado:** código em desenvolvimento. Não considere o aplicativo pronto para guardar informações críticas até concluir as limitações acima e validar o APK em um aparelho.
