package onlexnet.sejmapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import onlexnet.infra.adapters.in.collect.SejmCollectFunctions;
import onlexnet.infra.adapters.out.SejmCollectService;

@AppTest
class SejmCollectFunctionsSpringBootTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SejmCollectFunctions sejmCollectFunctions;

    @Autowired
    private SejmCollectService sejmCollectService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void givenSpringBootContext_whenResolvingCollectBeans_thenFunctionsServiceAndObjectMapperAreAvailable() {
        assertThat(this.applicationContext).isNotNull();
        assertThat(this.sejmCollectFunctions).isNotNull();
        assertThat(this.sejmCollectService).isNotNull();
        assertThat(this.objectMapper).isNotNull();
        assertThat(this.applicationContext.getBean(ObjectMapper.class)).isSameAs(this.objectMapper);
    }
}
