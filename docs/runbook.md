# SejmStream Runbook

## 1. Local Development Setup

### Prerequisites
- Java 21 (via SDKMAN: `sdk install java 21-ms`)
- Maven 3.9+
- Azure Functions Core Tools v4 (`npm install -g azure-functions-core-tools@4`)
- PostgreSQL (local or Docker)
- Azure Storage emulator or Azurite (`npm install -g azurite`)

### Environment Setup
```bash
cd fun_sejmlive
cp local.settings.json.example local.settings.json
```

Edit `local.settings.json` with:
```json
{
  "IsEncrypted": false,
  "Values": {
    "AzureWebJobsStorage": "UseDevelopmentStorage=true",
    "FUNCTIONS_WORKER_RUNTIME": "java",
    "DB_URL": "jdbc:postgresql://localhost:5432/sejmstream",
    "DB_USERNAME": "postgres",
    "DB_PASSWORD": "postgres",
    "FB_TOKEN": "<your-facebook-page-token>",
    "TELEGRAM_BOT_TOKEN": "<your-bot-token>",
    "TELEGRAM_ALLOWED_CHAT_ID": "<your-chat-id>",
    "INTERPELLATION_PUBLISH_QUEUE_NAME": "sejm-interpellations-publish",
    "INTERPELLATION_PUBLISH_MAX_ATTEMPTS": "5",
    "INTERPELLATION_PUBLISH_RETRY_DELAY_SECONDS": "60"
  }
}
```

### Database Setup
```bash
# Start PostgreSQL (Docker)
docker run -d --name sejmstream-db \
  -e POSTGRES_DB=sejmstream \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 postgres:16

# Migrations run automatically on app startup via Liquibase
```

### Start Azurite (Storage Emulator)
```bash
azurite --silent --location /tmp/azurite --debug /tmp/azurite-debug.log
```

---

## 2. Building & Testing

### Full Build
```bash
cd fun_sejmlive
mvn clean package
```

### Run Tests Only
```bash
mvn test
```

### Run Single Test
```bash
mvn test -Dtest=DefaultCollectDailyDigestUseCaseTest
```

### Skip Tests (for quick iteration)
```bash
mvn package -DskipTests
```

### Build Output
- Function package: `target/azure-functions/sejmstream-fun-sejmlive-local/`
- Test reports: `target/surefire-reports/`

---

## 3. Running Locally

### Start Azure Functions Runtime
```bash
cd fun_sejmlive
mvn clean package -DskipTests
cd target/azure-functions/sejmstream-fun-sejmlive-local
func start
```

Functions will be available at:
- `http://localhost:7071/api/collect` — trigger collection
- `http://localhost:7071/api/publish` — trigger publishing
- `http://localhost:7071/api/telegram/webhook` — Telegram webhook

### Manual Trigger (Collection)
```bash
curl -X POST http://localhost:7071/api/collect
```

### Manual Trigger (Publishing)
```bash
curl -X POST http://localhost:7071/api/publish
```

---

## 4. Deployment

### Deploy to Azure
```bash
cd fun_sejmlive
mvn clean package -DskipTests
# Deploy via Azure CLI or CI/CD pipeline
az functionapp deployment source config-zip \
  --resource-group <rg-name> \
  --name <function-app-name> \
  --src target/azure-functions/sejmstream-fun-sejmlive-local.zip
```

### Infrastructure Changes (Terraform)
```bash
cd infra
terraform init
terraform plan -var-file=terraform.tfvars
terraform apply -var-file=terraform.tfvars
```

### Set Telegram Webhook (after deployment)
```bash
cd infra
./set-telegram-webhook.sh
```

---

## 5. Monitoring

### Application Insights
- **Portal**: Azure Portal → Application Insights → sejmstr-*-appi
- **Live Metrics**: Real-time request/failure stream
- **Transaction Search**: Find specific function executions

### Key Queries (KQL)
```kql
// Failed function executions (last 24h)
requests
| where success == false
| where timestamp > ago(24h)
| project timestamp, name, resultCode, duration
| order by timestamp desc

// Collection duration trend
requests
| where name contains "Collect"
| where timestamp > ago(7d)
| summarize avg(duration), max(duration) by bin(timestamp, 1h)
| render timechart

// Queue processing failures
traces
| where message contains "DeadLetter" or message contains "RetryScheduled"
| where timestamp > ago(24h)
| project timestamp, message, severityLevel
```

### Key Metrics to Watch
| Metric | Healthy | Alert |
|--------|---------|-------|
| Collection success rate | >95% | <80% |
| Daily digest published | 1/day | 0 for 2+ days |
| Dead-letter queue depth | 0 | >5 messages |
| Function execution time (collection) | <60s | >180s |
| Function execution time (publish) | <10s | >30s |

---

## 6. Troubleshooting

### Facebook Token Expired
**Symptom**: Publishing fails with 401/403 from Facebook API  
**Fix**:
1. Generate new long-lived page token from Facebook Developer Portal
2. Update Key Vault secret: `az keyvault secret set --vault-name <vault> --name FB-TOKEN --value <new-token>`
3. Restart function app: `az functionapp restart --name <app> --resource-group <rg>`

### Database Connection Failed
**Symptom**: Function startup fails with JDBC connection error  
**Check**:
1. Verify PostgreSQL is accessible: `psql -h <host> -U <user> -d sejmstream`
2. Check Key Vault secrets: `az keyvault secret show --vault-name <vault> --name DB-URL`
3. Verify network rules allow function app's outbound IPs

### Collection Returns Empty Data
**Symptom**: Collection succeeds but 0 items stored  
**Check**:
1. Verify Sejm API is responding: `curl https://api.sejm.gov.pl/sejm/term`
2. Check if current term number is correct
3. Review Application Insights traces for API response details

### Queue Poison Messages
**Symptom**: Messages repeatedly fail and land in dead-letter queue  
**Investigate**:
```bash
# View dead-letter queue messages
az storage message peek --queue-name sejm-interpellations-publish-deadletter \
  --account-name <storage-account> --num-messages 10
```
**Fix**: Review `lastError` in message body, fix root cause, then re-enqueue:
```bash
# After fixing the issue, move messages back to main queue
# (Manual process - read from DLQ, enqueue to main queue)
```

### Durable Functions Stuck
**Symptom**: Orchestrator instance never completes  
**Check**:
```bash
# List running instances
func durable get-instances --connection-string-setting AzureWebJobsStorage
```
**Fix**: Terminate stuck instance:
```bash
func durable terminate --id <instance-id> --reason "Manual termination"
```

---

## 7. Admin Operations via Telegram

### Available Commands
| Command | Description |
|---------|-------------|
| `/help` | Show available commands |
| `/data` | Show current Sejm term info |
| `/collect` | Trigger immediate data collection |
| `/publish` | Trigger immediate digest publishing |

### Authorization
Only the chat ID configured in `TELEGRAM_ALLOWED_CHAT_ID` (Key Vault) can execute commands. To add a new admin:
1. Get the chat ID (send a message to the bot and check webhook logs)
2. Update Key Vault secret with new chat ID
3. Restart function app

---

## 8. Queue Management

### Queue Names
| Queue | Purpose |
|-------|---------|
| `sejm-interpellations-publish` | Main processing queue |
| `sejm-interpellations-publish-deadletter` | Failed messages after max retries |

### View Queue Depth
```bash
az storage queue metadata show \
  --name sejm-interpellations-publish \
  --account-name <storage-account> \
  --query "approximateMessageCount"
```

### Purge Queue (caution)
```bash
az storage message clear \
  --queue-name sejm-interpellations-publish-deadletter \
  --account-name <storage-account>
```

---

## 9. Database Maintenance

### Run Migrations Manually
Migrations run automatically on startup. To verify schema:
```bash
psql -h <host> -U <user> -d sejmstream -c "\dt"
```

### Check Liquibase State
```bash
psql -h <host> -U <user> -d sejmstream -c "SELECT * FROM databasechangelog ORDER BY orderexecuted"
```

### Useful Queries
```sql
-- Today's collected items
SELECT data_type, COUNT(*) FROM sejm_daily_digest_item
WHERE collection_date = CURRENT_DATE GROUP BY data_type;

-- Recent publish history
SELECT publish_date, success, error_message FROM sejm_publish_log
ORDER BY published_at DESC LIMIT 10;

-- Interpellation publish states
SELECT status, COUNT(*) FROM sejm_interpellation_publish_state
GROUP BY status;

-- Failed interpellations with errors
SELECT interpellation_title, attempt, last_error, updated_at
FROM sejm_interpellation_publish_state
WHERE status = 'DEAD_LETTER'
ORDER BY updated_at DESC;
```

---

## 10. Infrastructure (Terraform)

### Key Resources
| Resource | Name Pattern | Purpose |
|----------|-------------|---------|
| Function App | `sejmstr-{env}-func-*` | Application runtime |
| Storage Account | `sejmstr{env}st*` | Queues + function state |
| Key Vault | `sejmstr{env}kv*` | Secrets management |
| App Insights | `sejmstr-{env}-appi-*` | Monitoring |

### Common Operations
```bash
cd infra

# View current state
terraform show

# Plan changes
terraform plan -var-file=terraform.tfvars

# Apply (with approval)
terraform apply -var-file=terraform.tfvars

# Import existing resource
terraform import <resource_address> <azure_resource_id>
```

### Secrets Rotation
```bash
# Rotate Facebook token
az keyvault secret set --vault-name <vault> --name FB-TOKEN --value <new-value>

# Rotate DB password
az keyvault secret set --vault-name <vault> --name DB-PASSWORD --value <new-value>
# Also update the actual PostgreSQL password

# After rotation, restart function app to pick up new values
az functionapp restart --name <app-name> --resource-group <rg>
```

---

## 11. Incident Response

### Collection Not Running
1. Check Application Insights for timer trigger invocations
2. Verify function app is running: `az functionapp show --name <app> --query "state"`
3. Check if Sejm API is accessible from Azure network
4. Manual trigger: `curl https://<app>.azurewebsites.net/api/collect`

### Daily Digest Not Published
1. Check `sejm_publish_log` for today's date
2. Check if collection ran (items exist for today)
3. Verify Facebook token is valid
4. Manual trigger: `curl https://<app>.azurewebsites.net/api/publish`

### High Dead-Letter Count
1. Check `sejm_interpellation_publish_state` for error patterns
2. Common causes: Facebook rate limit, token expired, network issues
3. Fix root cause, then re-enqueue dead-lettered messages
