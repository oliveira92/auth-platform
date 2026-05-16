---
description: Aciona o unit-test-agent para gerar testes unitários JUnit/Mockito mirando 100% de cobertura e roda o JaCoCo. Aceita argumento opcional com o nome do módulo (auth-service, audit-service, authorization-service); sem argumento, processa todos.
argument-hint: "[modulo]"
allowed-tools: Task, Bash, Read
---

Invoque o subagente `unit-test-agent` para executar o ciclo completo de geração de testes unitários no escopo informado.

**Argumento recebido:** `$ARGUMENTS`

**Instruções de delegação:**

1. Se `$ARGUMENTS` estiver vazio, peça ao agente para processar **todos os módulos Maven** da raiz (`find . -maxdepth 3 -name pom.xml -not -path '*/target/*'`), um a um, em sequência.
2. Se `$ARGUMENTS` contiver o nome de um módulo (ex.: `auth-service`), restrinja a execução a esse diretório.
3. O subagente deve seguir as 7 etapas do seu manual: descoberta → resolução de versões → JaCoCo → inventário → geração dos testes → execução `mvn clean verify` → relatório final.
4. Ao terminar, exibir:
   - Versões detectadas (Java, Spring Boot, JUnit, Mockito, JaCoCo).
   - Lista de arquivos de teste criados ou alterados.
   - Cobertura final (instructions / branches) por módulo.
   - Caminho dos relatórios `target/site/jacoco/index.html`.
   - Qualquer exclusão ou pendência que mereça atenção do humano.

Não modifique código de produção sem confirmar com o usuário. Não baixe a meta de 100% sem autorização explícita.
