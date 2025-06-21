package com.github.sidedev.sidekick.testing

import com.github.sidedev.sidekick.api.SidekickService
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import io.mockk.mockk

abstract class SidekickBaseTestCase : UsefulTestCase() {
    protected lateinit var sidekickService: SidekickService
    protected var fixture: IdeaTestFixture? = null

    @Throws(Exception::class)
    override fun setUp() {
        sidekickService = mockk()
        super.setUp()
        val newFixture = IdeaTestFixtureFactory.getFixtureFactory().createBareFixture()
        newFixture.setUp()
        this.fixture = newFixture
    }

    @Throws(Exception::class)
    override fun tearDown() {
        try {
            fixture?.tearDown()
        } finally {
            super.tearDown()
        }
    }
}