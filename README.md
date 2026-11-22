# MY SECRET

Aplicativo Android pessoal para guardar publicações, anexos, notas, favoritos, álbuns e arquivos localmente.

## Stack
- Kotlin + Jetpack Compose
- Room + SQLCipher para o banco local
- Android Keystore + AES-GCM para proteger a chave do banco
- PBKDF2-HMAC-SHA256 para verificador do PIN e derivação da chave de backup
- BiometricPrompt com operação AES-GCM usando chave do Android Keystore
- GitHub Actions para build Android

## Recursos implementados no código
- Configuração local de PIN de 4 a 12 dígitos.
- Verificação do PIN com PBKDF2-HMAC-SHA256 e bloqueio temporário após oito tentativas inválidas.
- Código de recuperação aleatório, armazenado como hash, para redefinir o PIN.
- Opção de desbloqueio biométrico forte quando o aparelho oferece suporte.
- Bloqueio automático quando a atividade sai de primeiro plano.
- Feed cronológico com criação e exclusão de publicações.
- Categorias, legendas, favoritos e pesquisa.
- Anexos de fotos, vídeos e documentos nas publicações.
- Galeria com seleção de arquivos via Storage Access Framework, abertura e remoção de referências.
- Álbuns.
- Cofre de notas, links e informações.
- Backup AES-GCM protegido por senha do usuário, com exportação e restauração.
- Migração Room 1 para 2 sem apagar tabelas existentes.

## Limitações de segurança e mídia que ainda exigem trabalho
- Os arquivos selecionados na galeria permanecem no provedor/armazenamento escolhido pelo Android. O banco guarda URIs com permissões persistidas; os bytes dos arquivos ainda não são copiados para um cofre privado criptografado. Portanto, a galeria ainda não oferece criptografia de ponta a ponta dos anexos.
- O backup criptografa os registros e as referências dos anexos, mas ainda não inclui o conteúdo binário dos arquivos. As referências podem não funcionar ao restaurar em outro aparelho.
- A biometria está vinculada a uma operação criptográfica do Android Keystore usada como autenticação do app; ainda falta integrar a autorização biométrica diretamente ao desbloqueio da chave do banco.
- A restauração valida o formato e a autenticação AES-GCM, mas a substituição dos registros ainda precisa ser envolvida numa transação atômica.
- Ainda faltam testes automatizados abrangentes, auditoria de segurança, APK de release assinado e teste físico em aparelhos.

## Build
O workflow GitHub Actions executa `gradle :app:assembleDebug` com JDK 17 e Gradle 8.9. O APK de depuração esperado é `app/build/outputs/apk/debug/app-debug.apk`.

**Estado:** desenvolvimento ativo. Não use ainda para armazenar segredos de alto risco nem considere o aplicativo pronto para distribuição. A conclusão depende de corrigir as limitações listadas e de confirmar o build e os testes no aparelho.
