## Plan: Azure Queue dla publikacji interpelacji FB

Zmiana decyzji: zamiast kolejki tabelarycznej w DB używamy natywnej Azure Storage Queue. Każda nowa interpelacja (INTERPELLATION) jest enqueue do kolejki, a dedykowany procesor Azure Functions publikuje wiadomości sekwencyjnie jako osobne posty feed na Facebooku. Retry realizujemy przez re-enqueue z opóźnieniem i licznik prób, a po 5 próbach komunikat trafia do dead-letter (poison) kolejki.

**Steps**
1. Faza 1: Kontrakt komunikatu kolejki i idempotencja.
2. Zdefiniować schemat wiadomości kolejki (messageId domenowe: term + interpellationNum, payload, attempt, firstQueuedAt).
3. Ustalić idempotencję po stronie publikacji (sprawdzenie istniejącego logu publikacji dla klucza term + interpellationNum) tak, aby ponowna dostawa komunikatu nie tworzyła duplikatu posta.
4. Faza 2: Rozszerzenie aplikacji (fun_sejmlive).
5. Dodać output port do enqueue interpelacji do Azure Queue oraz adapter out oparty o Azure Storage Queue SDK.
6. Wpiąć enqueue do collectInterpellations po zapisie danych interpelacji, tylko dla INTERPELLATION.
7. Dodać nowy input use case i adapter in (QueueTrigger), który pobiera komunikaty i publikuje pojedynczo, sekwencyjnie per wiadomość.
8. Zaadresować retry: przy błędzie publikacji zwiększyć attempt, wyznaczyć backoff, ponownie enqueue; po 5 próbie przekazać do dead-letter queue i zapisać błąd.
9. Utrzymać istniejący daily digest publisher bez zmian zakresowych.
10. Faza 3: Rozszerzenie infra w ../infra.
11. Dodać zasób kolejki głównej (np. sejm-interpellations-publish) w Terraform w ../infra/main.tf.
12. Dodać zasób kolejki dead-letter (np. sejm-interpellations-publish-deadletter) w ../infra/main.tf.
13. Rozszerzyć app settings Function App w ../infra/main.tf o nazwy kolejek i parametry retry/backoff (max attempts=5), aby kod używał konfiguracji środowiskowej.
14. Dodać odpowiednie outputy terraformowe w ../infra/outputs.tf (queue names / endpointy), aby operacyjnie łatwo diagnozować stan.
15. Dodać/uzupełnić zmienne w ../infra/variables.tf dla nazw kolejek i polityki retry, z bezpiecznymi domyślnymi wartościami.
16. Zweryfikować, że istniejące RBAC Storage Queue Data Contributor pokrywa dostęp runtime Function App (bez dodatkowych ról, chyba że plan wykaże inaczej).
17. Faza 4: Testy i walidacja end-to-end.
18. Testy jednostkowe: enqueue wiadomości, przetwarzanie sukces, retry, dead-letter po 5 próbach, idempotentny skip duplikatu.
19. Testy adaptera triggera kolejki i mapowania konfiguracji app settings.
20. Testy terraformowe: terraform fmt/validate/plan w ../infra z nowymi zasobami.
21. Smoke test: collect interpelacji -> wiadomości w Azure Queue -> publikacja postów pojedynczo -> awaria FB -> retry -> dead-letter po limicie.

**Relevant files**
- /sejmstream/fun_sejmlive/src/main/java/onlexnet/app/ports/out/SejmDailyDigestPersistence.java — idempotency/publish logs do sprawdzania duplikatów publikacji.
- /sejmstream/fun_sejmlive/src/main/java/onlexnet/infra/adapters/out/SejmCollectService.java — punkt enqueue dla nowych interpelacji.
- /sejmstream/fun_sejmlive/src/main/java/onlexnet/app/ports/out/FacebookPublisher.java — istniejący port publikacji pojedynczego posta.
- /sejmstream/fun_sejmlive/src/main/java/onlexnet/infra/adapters/in/facebook/FacebookPublishingFunctions.java — pozostaje dla daily digest (out of scope zmiany).
- /sejmstream/fun_sejmlive/src/main/java/onlexnet/infra/adapters/in/collect/SejmCollectFunctions.java — referencja wzorców trigger/retry/orchestration.
- /sejmstream/infra/main.tf — dodanie zasobów Azure Storage Queue i app settings.
- /sejmstream/infra/variables.tf — nowe zmienne queue/retry.
- /sejmstream/infra/outputs.tf — nowe outputy dla kolejek.
- /sejmstream/infra/README.md — aktualizacja dokumentacji wdrożenia i konfiguracji.

**Verification**
1. Testy jednostkowe use case/adapters w fun_sejmlive dla queue publish flow.
2. Testy funkcji z QueueTrigger i obsługą błędów/retry.
3. Weryfikacja logów publikacji: jedna interpelacja = jeden post, bez duplikatów przy redelivery.
4. Weryfikacja dead-letter: komunikat trafia do kolejki DLQ po 5 nieudanych próbach.
5. Weryfikacja IaC: terraform fmt -check -recursive, terraform validate, terraform plan w ../infra.

**Decisions**
- Zakres: tylko INTERPELLATION.
- Format: oddzielny post feed na stronę Facebook.
- Kolejka: Azure Storage Queue (główna + dead-letter).
- Klucz interpelacji: term + interpellationNum.
- Retry: maksymalnie 5 prób, potem dead-letter.
- Przetwarzanie: sekwencyjnie, jedna wiadomość = jeden post.
- Out of scope: zmiany logiki istniejącego daily digest publikowanego timerem.

**Further Considerations**
1. Rekomendacja: dodać dashboard operacyjny (queue depth, dead-letter count, oldest message age) w Azure Monitor.
2. Rekomendacja: rozważyć migrację na Service Bus Queue tylko jeśli pojawią się wymagania FIFO/session, większej przepustowości albo zaawansowanych polityk lock/retry.