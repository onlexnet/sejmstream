package onlexnet.infra.adapters.in.facebook;

import org.springframework.stereotype.Component;

/**
 * Function name constants and compatibility Spring bean for Facebook publishing Azure Functions.
 *
 * <p>Runtime entry points are intentionally split into dedicated classes:
 * {@link FacebookPublishingTimerFunction} and {@link FacebookPublishingHttpFunction}.
 */
@Component
public final class FacebookPublishingFunctions {

    static final String TIMER_FUNCTION_NAME = "Fun_FacebookPublish";
    static final String HTTP_FUNCTION_NAME = "Fun_FacebookPublishStart";
    static final String HTTP_FUNCTION_ROUTE = "Fun_FacebookPublishStart";
}