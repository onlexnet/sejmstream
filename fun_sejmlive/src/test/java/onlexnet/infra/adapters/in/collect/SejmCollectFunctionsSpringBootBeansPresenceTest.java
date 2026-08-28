package onlexnet.infra.adapters.in.collect;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restfb.FacebookClient;

import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.infra.adapters.out.SejmCollectService;
import onlexnet.testsupport.AppTest;
import onlexnet.testsupport.PostgresIntegrationTestSupport;

@AppTest
class SejmCollectFunctionsSpringBootBeansPresenceTest extends PostgresIntegrationTestSupport {

    @MockitoBean
    FacebookClient facebookClient;
  
    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SejmCollectService sejmCollectService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JsonValidator jsonValidator;

    @Test
    void givenSpringBootContext_whenResolvingCollectBeans_thenAllRequiredBeansAreAvailable() {
        assertThat(this.applicationContext).isNotNull();
        assertThat(this.sejmCollectService).isNotNull();
        assertThat(this.objectMapper).isNotNull();
        assertThat(this.jsonValidator).isNotNull();
    }
}
