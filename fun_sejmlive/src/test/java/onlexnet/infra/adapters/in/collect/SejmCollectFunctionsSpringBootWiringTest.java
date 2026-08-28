package onlexnet.infra.adapters.in.collect;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restfb.FacebookClient;

import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.testsupport.AppTest;

@AppTest
class SejmCollectFunctionsSpringBootWiringTest {

    @MockitoBean
    FacebookClient facebookClient;
  
    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JsonValidator jsonValidator;

    @Test
    void shouldResolveCoreInfraBeansAsSameSingletons() {
        assertThat(this.applicationContext.getBean(ObjectMapper.class)).isSameAs(this.objectMapper);
        assertThat(this.applicationContext.getBean(JsonValidator.class)).isSameAs(this.jsonValidator);
    }

    @Test
    void shouldSerializeLocalDateAsIsoDateString() throws Exception {
        var payload = this.objectMapper.writeValueAsString(java.time.LocalDate.of(2026, 8, 28));

        assertThat(payload).isEqualTo("\"2026-08-28\"");
    }
}
