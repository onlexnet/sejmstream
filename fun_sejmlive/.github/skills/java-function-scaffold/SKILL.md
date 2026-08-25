---
name: java-function-scaffold
description: 'Create a new Azure Functions Java entry point with the correct prefix and a SpringBootTest startup check. Use when adding a new function, choosing Fun_ vs Intern_ names, or validating that the app starts with the existing configuration.'
---

# Java Function Scaffold

## When to Use
- Add a new Azure Function in this module.
- Decide whether the function is a public entry point or an internal helper/activity.
- Create or update a startup test that proves the function app boots with the current Spring configuration.
- Refactor an existing large function class into maintainable, split entrypoints.

## Naming Rules
- Use `Fun_` for main/public function entry points.
- Use `Intern_` for helper, activity, or internal orchestration functions.
- Keep the Java method name descriptive and aligned with the function role.
- Keep the `@FunctionName` value consistent with the chosen prefix.

## Procedure
1. Identify the function role.
   - If it is a public trigger, choose `Fun_`.
   - If it is a helper or activity bound to another workflow, choose `Intern_`.
2. Choose placement strategy.
   - New function: add a dedicated entrypoint class.
   - Refactor existing large class: split to one entrypoint class per trigger/orchestrator/activity.
   - Keep shared runtime logic in a support class (for example `...FunctionSupport`).
   - Keep function-name constants in a stable constants class to avoid churn.
3. Add the new function class or method.
   - Use the module's existing Azure Functions and Spring style.
   - Keep dependencies constructor-injected.
   - Prefer thin entrypoint methods delegating to support logic.
4. Add a startup-focused Spring Boot test.
   - Prefer `@SpringBootTest(classes = Program.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)`.
   - Assert that the function bean loads in the existing app context.
   - If the function depends on a collaborator, inject a test double or existing bean replacement.
5. Keep the test focused on bootability.
   - The purpose is to prove the existing configuration can start the function.
   - Do not over-test business logic in the startup test.
6. For split refactors, split tests along architecture boundaries.
   - Contract/annotation tests.
   - Trigger behavior tests.
   - Orchestrator behavior tests.
   - Activity behavior tests.
   - Spring wiring/startup tests.
   - Shared test fakes/helpers in one support file.
7. Run the narrowest useful test set.
   - Verify the new test passes.
   - Verify any existing function-name assertions still match the new prefix.

## Completion Checks
- The function name uses the correct prefix.
- The function is reachable through the Azure Functions annotation metadata.
- The Spring Boot startup test passes with the current configuration.
- Existing tests or documentation that reference the function name are updated.
- If a class was split, each entrypoint remains thin and delegates shared logic.
- If lifecycle fields exist (state/context), they are not modeled with nullable fields.

## Reference Pattern
- Startup test style: [SejmCollectFunctionsSpringBootWiringTest.java](../../src/test/java/onlexnet/infra/adapters/in/collect/SejmCollectFunctionsSpringBootWiringTest.java)
- Split contract test style: [SejmCollectFunctionContractsTest.java](../../src/test/java/onlexnet/infra/adapters/in/collect/SejmCollectFunctionContractsTest.java)
- Shared support style: [SejmCollectFunctionSupport.java](../../src/main/java/onlexnet/infra/adapters/in/azurefunc/SejmCollectFunctionSupport.java)
