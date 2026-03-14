import com.google.ai.edge.litertlm.Conversation
import java.lang.reflect.Modifier

fun main() {
    val clazz = Conversation::class.java
    clazz.declaredMethods.forEach { println("${Modifier.toString(it.modifiers)} ${it.returnType.simpleName} ${it.name}(${it.parameterTypes.map { p -> p.simpleName }.joinToString(", ")})") }
}
