package com.example.funsejmapi;

import com.microsoft.azure.functions.spi.inject.FunctionInstanceInjector;

/**
 * Custom injector used by Azure Functions Java worker to create function classes.
 */
public final class CustomFunctionInstanceInjector implements FunctionInstanceInjector {

    private static final SimpleMessageService MESSAGE_SERVICE =
            new SimpleMessageService("from FunctionInstanceInjector");

    @Override
    public <T> T getInstance(final Class<T> functionClass) throws Exception {
        if (DiSampleFunctions.class.equals(functionClass)) {
            return functionClass.cast(new DiSampleFunctions(MESSAGE_SERVICE));
        }
        return functionClass.getDeclaredConstructor().newInstance();
    }
}
