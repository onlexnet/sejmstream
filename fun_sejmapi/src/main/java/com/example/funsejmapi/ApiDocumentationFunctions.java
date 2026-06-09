package com.example.funsejmapi;

import java.util.Optional;

import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

/**
 * Exposes a simple OpenAPI document and Swagger UI for this demo Function App.
 */
public final class ApiDocumentationFunctions {

    /** Swagger UI function name. */
    static final String SWAGGER_UI_FUNCTION_NAME = "SejmApiDemo_SwaggerUi";
    /** OpenAPI specification function name. */
    static final String OPENAPI_FUNCTION_NAME = "SejmApiDemo_OpenApi";

    private static final String OPENAPI_JSON = """
            {
              "openapi": "3.0.3",
              "info": {
                "title": "Sejm API Demo Durable Functions",
                "version": "1.0.0",
                "description": "Demo-only Durable Functions workflow endpoints for Sejm API scaffolding."
              },
              "paths": {
                "/api/SejmApiDemo_HttpStart": {
                  "post": {
                    "summary": "Start demo durable orchestration",
                    "description": "Starts a new orchestration and returns status-query URLs.",
                    "security": [
                      { "functionKey": [] }
                    ],
                    "requestBody": {
                      "required": false,
                      "content": {
                        "application/json": {
                          "schema": {
                            "$ref": "#/components/schemas/DemoWorkflowRequest"
                          }
                        }
                      }
                    },
                    "responses": {
                      "202": {
                        "description": "Accepted. Includes durable status URLs.",
                        "content": {
                          "application/json": {
                            "schema": {
                              "type": "object",
                              "properties": {
                                "id": {
                                  "type": "string"
                                },
                                "statusQueryGetUri": {
                                  "type": "string"
                                },
                                "sendEventPostUri": {
                                  "type": "string"
                                },
                                "terminatePostUri": {
                                  "type": "string"
                                },
                                "rewindPostUri": {
                                  "type": "string"
                                },
                                "purgeHistoryDeleteUri": {
                                  "type": "string"
                                }
                              }
                            }
                          }
                        }
                      },
                      "401": {
                        "description": "Unauthorized. Provide a valid function key via the x-functions-key header."
                      }
                    }
                  }
                }
              },
              "components": {
                "securitySchemes": {
                  "functionKey": {
                    "type": "apiKey",
                    "in": "header",
                    "name": "x-functions-key",
                    "description": "Azure Function key. Obtain from the Azure Portal under Functions → App keys or Function keys."
                  }
                },
                "schemas": {
                  "DemoWorkflowRequest": {
                    "type": "object",
                    "properties": {
                      "correlationId": {
                        "type": "string",
                        "example": "demo-123"
                      },
                      "sampleSize": {
                        "type": "integer",
                        "format": "int32",
                        "minimum": 1,
                        "maximum": 20,
                        "example": 3
                      }
                    }
                  },
                  "DemoWorkflowResult": {
                    "type": "object",
                    "properties": {
                      "correlationId": {
                        "type": "string"
                      },
                      "source": {
                        "type": "string",
                        "example": "demo-only"
                      },
                      "demoRows": {
                        "type": "array",
                        "items": {
                          "type": "string"
                        }
                      }
                    }
                  }
                }
              }
            }
            """;

    private static final String SWAGGER_HTML = """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>Sejm API Demo Swagger UI</title>
              <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css" />
            </head>
            <body>
              <div id="swagger-ui"></div>
              <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
              <script>
                SwaggerUIBundle({
                  url: '/api/openapi.json',
                  dom_id: '#swagger-ui',
                  persistAuthorization: true
                });
              </script>
            </body>
            </html>
            """;

    /**
     * Serves OpenAPI v3 JSON description for this module.
     *
     * @param request incoming HTTP request
     * @return OpenAPI JSON document
     */
    @FunctionName(OPENAPI_FUNCTION_NAME)
    public HttpResponseMessage openApi(
            @HttpTrigger(name = "request", methods = {
                    HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "openapi.json")
                    final HttpRequestMessage<Optional<String>> request) {

        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json; charset=utf-8")
                .body(OPENAPI_JSON)
                .build();
    }

    /**
     * Serves Swagger UI configured to read this app's OpenAPI JSON endpoint.
     *
     * @param request incoming HTTP request
     * @return Swagger UI HTML page
     */
    @FunctionName(SWAGGER_UI_FUNCTION_NAME)
    public HttpResponseMessage swaggerUi(
            @HttpTrigger(name = "request", methods = {
                    HttpMethod.GET }, authLevel = AuthorizationLevel.ANONYMOUS,
                    route = "swagger")
                    final HttpRequestMessage<Optional<String>> request) {

        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "text/html; charset=utf-8")
                .body(SWAGGER_HTML)
                .build();
    }
}