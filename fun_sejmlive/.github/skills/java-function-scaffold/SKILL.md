---
name: java-function-scaffold
description: 'Create a new Azure Functions Java entry point with the correct prefix and a SpringBootTest startup check. Use when adding a new function, choosing Fun_ vs Intern_ names, or validating that the app starts with the existing configuration.'
---

# Java Function Scaffold

## When to Use
- Add a new Azure Function in this module.
- Decide whether the function is a public entry point or an internal helper/activity.
- Create or update a startup test that proves the function app boots with the current Spring configuration.

## Naming Rules
- Use `Fun_` for main/public function entry points.
- Use `Intern_` for helper, activity, or internal orchestration functions.
- Keep the Java method name descriptive and aligned with the function role.
- Keep the `@FunctionName` value consistent with the chosen prefix.

## Procedure
1. Identify the function role.
   - If it is a public trigger, choose `Fun_`.
   - If it is a helper or activity bound to another workflow, choose `Intern_`.
2. Add the new function class or method.
   - Use the module's existing Azure Functions and Spring style.
   - Keep dependencies constructor-injected.
3. Add a startup-focused Spring Boot test.
   - Prefer `@SpringBootTest(classes = Program.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)`.
   - Assert that the function bean loads in the existing app context.
   - If the function depends on a collaborator, inject a test double or existing bean replacement.
4. Keep the test focused on bootability.
   - The purpose is to prove the existing configuration can start the function.
   - Do not over-test business logic in the startup test.
5. Run the narrowest useful test set.
   - Verify the new test passes.
   - Verify any existing function-name assertions still match the new prefix.

## Completion Checks
- The function name uses the correct prefix.
- The function is reachable through the Azure Functions annotation metadata.
- The Spring Boot startup test passes with the current configuration.
- Existing tests or documentation that reference the function name are updated.

## Reference Pattern
- Startup test style: [SejmCollectFunctionsSpringBootTest.java](../../src/test/java/onlexnet/sejmapi/SejmCollectFunctionsSpringBootTest.java)
- Existing prefix examples: [SejmCollectFunctions.java](../../src/main/java/onlexnet/sejmapi/SejmCollectFunctions.java), [FacebookPublishingFunctions.java](../../src/main/java/onlexnet/sejmapi/FacebookPublishingFunctions.java)
