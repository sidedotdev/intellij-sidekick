# Testing an IntelliJ Plugin

Most of the IntelliJ Platform codebase tests are model-level, run in a headless environment using an actual IDE
instance. The tests usually test a feature as a whole rather than individual functions that comprise its implementation,
like in unit tests.

In src/test/kotlin, you will find a basic MyPluginTest test that utilizes BasePlatformTestCase and runs a few checks
against the XML files to indicate an example operation of creating files on the fly or reading them from
src/test/testData/rename test resources.


# https://plugins.jetbrains.com/docs/intellij/testing-plugins.html

## Testing Overview
Last modified: 10 March 2025

Most of the tests in the IntelliJ Platform codebase are model-level functional tests. What this means is the following:

    The tests run in a headless environment that uses real production implementations for most components, except for many UI components.

    The tests usually test a feature as a whole rather than individual functions that comprise its implementation.

    The tests do not test the Swing UI and work directly with the underlying model instead (see also Integration and UI Tests).

    Most tests take a source file or a set of source files as input data, execute a feature, and compare the output with expected results. Results can be specified as another set of source files, special markup in the input file, or directly in the test code.

The most significant benefit of this test approach is that tests are very stable and require very little maintenance once written, no matter how much the underlying implementation is refactored or rewritten.

In a product with 20+ years of a lifetime that has gone through many internal refactorings, we find that this benefit dramatically outweighs the downsides of slower test execution and more difficult debugging of failures compared to more isolated unit tests.

#### Mocks

Another consequence of our testing approach is that we do not provide a recommended approach to mocking. We have a few tests in our codebase that use JMock. Still, in general, we find it difficult to mock all the interactions with IntelliJ Platform components that your plugin class will need to have. We recommend working with real components instead.


# https://plugins.jetbrains.com/docs/intellij/tests-and-fixtures.html

## Tests and Fixtures

Last modified: 28 October 2024

The IntelliJ Platform testing infrastructure is not tied to any specific test framework. In fact, the IntelliJ IDEA Team uses JUnit, TestNG, and Cucumber for testing different parts of the project. However, most of the tests are written using JUnit 3.

When writing your tests, you have the choice between using a standard base class to perform the test set up for you and using a fixture class, which lets you perform the setup manually and does not tie you to a specific test framework.

    tip

    Configuring Test Frameworks (2024.2+)

    All required test-framework dependencies must be declared explicitly.

With the former approach, you can use classes such as BasePlatformTestCase (LightPlatformCodeInsightFixtureTestCase before 2019.2).

With the latter approach, you use the IdeaTestFixtureFactory class to create instances of fixtures for the test environment. You need to call the fixture creation and setup methods from the test setup method used by your test framework.

# https://plugins.jetbrains.com/docs/intellij/light-and-heavy-tests.html

## Light and Heavy Tests
Last modified: 28 October 2024

    tip

    Configuring Test Frameworks (2024.2+)

    All required test-framework dependencies must be declared explicitly.

Plugin tests run in a real, rather than mocked, IntelliJ Platform environment and use real implementations for most application and project services.

Loading and initializing all the project components and services for a project to run tests is a relatively expensive operation, and it is desired to avoid doing it for each test. Dependently on the loading and execution time, we make a difference between light tests and heavy tests available in the IntelliJ Platform test framework:

    Light tests reuse a project from the previous test run when possible.

    Heavy tests create a new project for each test.

Light and heavy tests use different base classes or fixture classes, as described below.

    note

    Because of the performance difference, we recommend plugin developers to write light tests whenever possible.

## Light Tests

The standard way of writing a light test is to extend one of the following classes:
[active tab] Default
[inactive tab] Plugins using Java PSI

Use LightPlatformTestCase or BasePlatformTestCase for tests that don't have any dependency on Java functionality.

For 2019.2 and earlier, use LightPlatformCodeInsightFixtureTestCase.

Examples:

    JavaCopyrightTest
    HtmlDocumentationTest
    AcceptWordAsCorrectTest

### LightProjectDescriptor

When writing a light test, it is possible to specify the requirements of the project used in test, such as the module type, the configured SDK, facets, libraries, etc. It is done by extending the LightProjectDescriptor class and returning the project descriptor (usually stored in static final field) from getProjectDescriptor().

Before executing each test, the project instance will be reused if the test case returns the same project descriptor as the previous one or recreated if the descriptor is different (equals() = false).

When testing JVM languages, see also DefaultLightProjectDescriptor.