package onlexnet.infra.starters;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.restfb.FacebookClient;

import onlexnet.infra.adapters.in.facebook.FacebookPublishingFunctions;
import onlexnet.infra.adapters.in.facebook.FacebookPublishingHttpFunction;
import onlexnet.infra.adapters.in.facebook.FacebookPublishingTimerFunction;
import onlexnet.testsupport.AppTest;
import onlexnet.testsupport.PostgresIntegrationTestSupport;

/**
 * Verifies that the Spring application context starts the same way Azure Functions starts it:
 * via {@link CustomFunctionInstanceInjector} calling {@code SpringApplication.run(Program.class)}.
 *
 * <p>All primary function beans (those created by Azure Functions through the injector) must
 * be resolvable from the context when all required infrastructure is provided.
 */
@AppTest
class ApplicationContextTest extends PostgresIntegrationTestSupport {

    @MockitoBean
    FacebookClient facebookClient;
  
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void givenAllRequiredPropertiesConfigured_whenContextStartsLikeAzureFunctions_thenAllPrimaryFunctionBeansAreResolvable() {
        assertThat(this.applicationContext.getBean(FacebookPublishingFunctions.class))
                .as("FacebookPublishingFunctions must be available when FB_TOKEN is configured")
                .isNotNull();
        assertThat(this.applicationContext.getBean(FacebookPublishingTimerFunction.class))
                .as("FacebookPublishingTimerFunction must be available when FB_TOKEN is configured")
                .isNotNull();
        assertThat(this.applicationContext.getBean(FacebookPublishingHttpFunction.class))
                .as("FacebookPublishingHttpFunction must be available when FB_TOKEN is configured")
                .isNotNull();
    }

}
