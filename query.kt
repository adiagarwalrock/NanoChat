import com.google.ai.edge.litertlm.EngineConfig
import java.lang.reflect.Modifier

fun main() {
    val clazz = EngineConfig::class.java
    println("Constructors:")
    clazz.constructors.forEach { println(it) }
    println("\nFields:")
    clazz.declaredFields.forEach { println("${Modifier.toString(it.modifiers)} ${it.type.simpleName} ${it.name}") }
    println("\nMethods:")
    clazz.declaredMethods.forEach { println("${Modifier.toString(it.modifiers)} ${it.returnType.simpleName} ${it.name}") }
}
