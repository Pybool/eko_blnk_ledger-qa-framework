package io.portfolio.ledgerqa.base;

import io.portfolio.ledgerqa.testsupport.EnvironmentHealthExtension;
import io.portfolio.ledgerqa.testsupport.TestSuiteLifecycleExtension;

import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({
        TestSuiteLifecycleExtension.class,
        EnvironmentHealthExtension.class
})
public abstract class BaseTest {
}