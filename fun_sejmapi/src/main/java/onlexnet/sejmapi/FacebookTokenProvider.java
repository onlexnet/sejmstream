package onlexnet.sejmapi;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;

/**
 * Resolves the Facebook token from the injected environment.
 * <p>
 * Azure Functions Flex Consumption has a known issue where Key Vault references
 * in app settings are not resolved automatically (IMDS returns 400 for MSI tokens
 * on the Kudu SCM host). When an unresolved {@code @Microsoft.KeyVault(...)} reference
 * is detected, this class fetches the secret directly via the Azure Key Vault SDK.
 */
final class FacebookTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(FacebookTokenProvider.class);

    private static final List<String> ENVIRONMENT_VARIABLE_CANDIDATES = List.of("FB_TOKEN");

    /** Matches {@code @Microsoft.KeyVault(SecretUri=<uri>)} and captures the URI. */
    private static final Pattern KEY_VAULT_REF_PATTERN =
            Pattern.compile("@Microsoft\\.KeyVault\\(SecretUri=([^)]+)\\)", Pattern.CASE_INSENSITIVE);

    String resolveToken() {
        return resolveFromEnvironment().orElse(null);
    }

    private Optional<String> resolveFromEnvironment() {
        return ENVIRONMENT_VARIABLE_CANDIDATES.stream()
                .map(System.getenv()::get)
                .filter(value -> value != null && !value.isBlank())
                .map(this::resolveIfKeyVaultReference)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private String resolveIfKeyVaultReference(String value) {
        Matcher matcher = KEY_VAULT_REF_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return value;
        }

        var secretUri = matcher.group(1).trim();
        // Azure Functions Flex Consumption cannot resolve @Microsoft.KeyVault(...) app-setting
        // references automatically because its Kudu SCM host fails to obtain an MSI token from
        // the Instance Metadata Service (IMDS returns HTTP 400). The same managed identity works
        // fine at function-invocation time via the Azure SDK, so we perform a direct SDK call here
        // as a workaround until Microsoft fixes the Flex Consumption + MSI deployment issue.
        // Tracking: https://github.com/hashicorp/terraform-provider-azurerm/issues/29993
        log.warn("FB_TOKEN contains an unresolved Key Vault reference; fetching secret directly from Key Vault. URI: {}", secretUri);

        // Derive the vault URL from the secret URI (everything up to /secrets/...)
        var vaultUrl = secretUri.replaceAll("/secrets/.*", "");
        var secretName = secretUri.replaceAll(".*/secrets/([^/]+).*", "$1");

        SecretClient secretClient = new SecretClientBuilder()
                .vaultUrl(vaultUrl)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();

        return secretClient.getSecret(secretName).getValue();
    }

}
