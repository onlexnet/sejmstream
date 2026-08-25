package onlexnet.infra.adapters.in.collect;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.testsupport.AppTest;

@AppTest
class SejmCollectFunctionsSpringBootWiringTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JsonValidator jsonValidator;

    @Test
    void givenSpringBootContext_whenResolvingCoreInfraBeans_thenContextReturnsSameSingletons() {
        assertThat(this.applicationContext.getBean(ObjectMapper.class)).isSameAs(this.objectMapper);
        assertThat(this.applicationContext.getBean(JsonValidator.class)).isSameAs(this.jsonValidator);
    }
}
