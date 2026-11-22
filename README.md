# secret

Aplicativo Android privado para organizar publicações, anexos, notas, favoritos, álbuns e arquivos localmente.

## Tecnologias
- Kotlin + Jetpack Compose
- Room + SQLCipher para o banco de dados local
- Android Keystore + AES-256-GCM para proteger a chave do banco e arquivos privados
- PBKDF2-HMAC-SHA256 para PIN e derivação da senha de backup
- BiometricPrompt com operação criptográfica vinculada a chave do Android Keystore
- GitHub Actions para compilar APK de depuração

## Funcionalidades implementadas no código
- Configuração local de PIN de 4 a 12 dígitos.
- Hash do PIN com PBKDF2-HMAC-SHA256 e bloqueio temporário após oito tentativas inválidas.
- Código de recuperação aleatório, guardado como hash, para redefinir o PIN.
- Desbloqueio biométrico forte quando suportado pelo aparelho.
- Bloqueio automático ao sair de primeiro plano.
- Feed cronológico, publicações, categorias, legendas, favoritos e pesquisa.
- Anexos de fotos, vídeos e documentos em publicações.
- Galeria e álbuns com seleção de arquivos pelo seletor do Android.
- Arquivos importados copiados para o armazenamento interno do app e criptografados com AES-GCM por chave protegida no Android Keystore.
- Cofre de notas, links e informações.
- Controle financeiro local: receitas, despesas, categorias, histórico, resumo mensal, metas de economia e contas com vencimento.
- Movimentações financeiras armazenadas no banco Room/SQLCipher, incluídas no backup criptografado e na restauração.
- Backup AES-GCM com senha definida pelo usuário, incluindo registros e bytes dos arquivos de mídia.
- Restauração do backup em outro aparelho, recriptografando os anexos com a chave local do novo aparelho.
- Migrações Room 1 para 2 e 2 para 3 sem apagar as tabelas anteriores.

## Limitações e validações restantes
- A pré-visualização de anexos cria uma cópia temporária descriptografada no cache para que outro aplicativo possa abrir o arquivo; essa cópia é removida ao abrir outra pré-visualização ou quando o cache for limpo pelo Android.
- A restauração substitui registros; ainda é necessário envolver a substituição do banco numa transação atômica e reforçar a recuperação caso o aparelho fique sem espaço durante a restauração.
- A biometria é um segundo método de autenticação do app baseado numa operação de chave do Android Keystore; a chave SQLCipher continua protegida separadamente pelo Keystore.
- Ainda faltam testes automatizados abrangentes, auditoria independente de segurança, APK de release assinado e teste físico em aparelhos Android.
- Não armazene informações de risco elevado até concluir auditoria e testes de segurança.

## Compilação
GitHub Actions executa `gradle :app:assembleDebug` com JDK 17 e Gradle 8.9. O APK de depuração fica em `app/build/outputs/apk/debug/app-debug.apk` e é publicado como artefato do workflow.

**Estado:** módulo financeiro integrado ao código; compilação e testes do commit atual ainda precisam ser confirmados pelo GitHub Actions antes de considerar o APK pronto.
