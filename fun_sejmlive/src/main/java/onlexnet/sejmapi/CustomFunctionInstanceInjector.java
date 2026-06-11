package onlexnet.sejmapi;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

import com.microsoft.azure.functions.spi.inject.FunctionInstanceInjector;

/**
 * Custom injector used by Azure Functions Java worker to create function classes.
 */
public final class CustomFunctionInstanceInjector implements FunctionInstanceInjector {

    private static final ApplicationContext APPLICATION_CONTEXT =
            SpringApplication.run(Program.class);

    @Override
    public <T> T getInstance(final Class<T> functionClass) throws Exception {
        return APPLICATION_CONTEXT.getBean(functionClass);
    }
}
