package com.fcm.nanochat

import com.google.ai.edge.litertlm.EngineConfig
import org.junit.Test

class ReflectionTest {
    @Test
    fun testReflection() {
        EngineConfig::class.java.declaredFields.forEach {
            println("FIELD: ${it.name} - ${it.type.name}")
        }
        EngineConfig::class.java.constructors.forEach {
            println("CONSTRUCTOR: ${it.parameters.joinToString { p -> "${p.name}: ${p.type.name}" }}")
        }
    }
}
