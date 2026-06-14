package onlexnet.sejmapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(classes = Program.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
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
