package com.pulumi.kotlin

import java.util.*
import kotlin.reflect.KProperty

/**
 * this is disgusting but might work for the PoC
 */


fun <T1> isPulumiType(clazz: Class<T1>): Boolean {
    return clazz.packageName.contains("pulumi")
}


object PulumiJavaKotlinInterop {

    fun getTargetClassForFromJavaToKotlin(fromClass: Class<*>): Class<*> {
        val parts = fromClass.name.replace("com.pulumi", "com.pulumi.kotlin").replace(".outputs", "")

        return Class.forName(parts)
    }

    fun getTargetClassForFromKotlinToJava(fromClass: Class<*>): Class<*> {
        val replaced = fromClass.name.replace("com.pulumi.kotlin", "com.pulumi")

        val className = if(replaced.endsWith("Args")) {
            replaced.split(".").dropLast(1) + "inputs" + replaced.split(".").last()
        } else {
            replaced.split(".").dropLast(1) + "outputs" + replaced.split(".").last()
        }

        return Class.forName(className.joinToString("."))
    }

    fun <T> toKotlin(from: Any?): T {
        return pulumiArgsFromJavaToKotlin(emptyMap(), from) as T
    }

    fun <T> toJava(from: Any?): T {
        return pulumiArgsFromKotlinToJava(emptyMap(), from) as T
    }
    fun pulumiArgsFromJavaToKotlin(fromToRegistry: Map<Class<*>, Class<*>>, from: Any?): Any? {
        if(from == null) return null

        when(from) {
            is Number, is String, is Char, is Boolean -> return from

            is Map<*, *> -> return from.map { (k, v) ->
                pulumiArgsFromJavaToKotlin(fromToRegistry, k) to pulumiArgsFromJavaToKotlin(fromToRegistry, v)
            }.toMap()

            is Array<*> -> return from.map { v ->
                pulumiArgsFromJavaToKotlin(fromToRegistry, v)
            }.toTypedArray()

            is List<*> -> return from.map { v ->
                pulumiArgsFromJavaToKotlin(fromToRegistry, v)
            }

            is Optional<*> -> return from.map { pulumiArgsFromJavaToKotlin(fromToRegistry, it) }.orElse(null)
        }

        val javaGetterMethods = from::class.java.methods.filter { it.parameterCount == 0 }

        val nameToMethod = javaGetterMethods.associateBy { it.name }

        val to = fromToRegistry[from.javaClass] ?: try { getTargetClassForFromJavaToKotlin(from.javaClass) } catch(e: Exception) {
            throw Error("could not find for ${from.javaClass.name}", e)
        }

        println("DEBUG:")

        println("from: ${from.javaClass} to: ${to}")

        val toConstructor = to.kotlin.constructors.first()
        val constructorArgsByKParameter = toConstructor.parameters.associate {
            val result = nameToMethod[it.name!!]!!.invoke(from)
            val goodResult = pulumiArgsFromJavaToKotlin(fromToRegistry, result)

            println("${it.name}: ${it.type} is going to be set to ${goodResult?.javaClass}")

            it to goodResult
        }

        return toConstructor.callBy(constructorArgsByKParameter)
    }

    fun pulumiArgsFromKotlinToJava(fromToRegistry: Map<Class<*>, Class<*>>, from: Any?): Any? {

        if(from == null) return null

        when(from) {
            is Number, String, Char, Boolean -> return from
        }
        if(from::class.java.isPrimitive || from::class.java == String::class.java) return from

        if(Map::class.java.isInstance(from)) {
            return (from as Map<*, *>).map { (k, v) ->
                pulumiArgsFromKotlinToJava(fromToRegistry, k) to pulumiArgsFromKotlinToJava(fromToRegistry, v)
            }.toMap()
        }

        if(Array::class.java.isInstance(from)) {
            return (from as Array<*>).map { v ->
                pulumiArgsFromKotlinToJava(fromToRegistry, v)
            }.toTypedArray()
        }

        if(List::class.java.isInstance(from)) {
            return (from as List<*>).map { v ->
                pulumiArgsFromKotlinToJava(fromToRegistry, v)
            }
        }

        val kotlinProperties = from::class.members.filterIsInstance<KProperty<*>>()

        val to = fromToRegistry[from.javaClass] ?: try { getTargetClassForFromKotlinToJava(from.javaClass) } catch(e: Exception) {
            throw Error("could not find for ${from.javaClass.name}", e)
        }

        val toBuilder = to.getMethod("builder").invoke(null)
        val builderMethods = toBuilder.javaClass.methods
            .filter { it.returnType.name.endsWith("Builder") }

        val propertyNameToValue = kotlinProperties.associate { it.name to it }
        builderMethods
            .filterNot { it.parameterTypes.any { type -> type.name.contains("Output") } || it.isVarArgs }
            .forEach { builderMethod ->
                propertyNameToValue.get(builderMethod.name) ?. let {
                    val getterValue = it.getter.call(from)
                    val valueToSet = pulumiArgsFromKotlinToJava(fromToRegistry, getterValue)
                    builderMethod.invoke(toBuilder, valueToSet)
                }
            }

        val result = toBuilder.javaClass.getMethod("build").invoke(toBuilder)

        return result
    }
}


/* Copied from aws-java repo
* package com.pulumi.kotlin

import com.pulumi.aws.s3.Bucket
import com.pulumi.aws.s3.outputs.BucketCorsRule
import com.pulumi.core.Output
import java.util.*
import kotlin.jvm.optionals.getOrDefault
import kotlin.reflect.KProperty

/**
 * this is disgusting but might work for the PoC
 */


fun <T1> isPulumiType(clazz: Class<T1>): Boolean {
    return clazz.packageName.contains("pulumi")
}

data class A(val aOutput: Output<B>, val aOutput2: Output<String>)
data class B(val bOutput: Output<C>)
data class C(val cOutput: Output<String>)


val Output<A>.aOutput: Output<B>
    get() = this.apply { x -> x.aOutput }

val Output<A>.aOutput2: Output<String>
    get() = this.apply { x -> x.aOutput2 }

val Output<B>.bOutput: Output<C>
    get() = this.apply { x -> x.bOutput }

val Output<C>.cOutput: Output<String>
    get() = this.apply { x -> x.cOutput }

fun main() {
    val a: A = null!! // just for it to compile

    a.aOutput.bOutput.cOutput
}

fun Output<Bucket>.corsRules(): Output<List<BucketCorsRule>?> {
    return this.apply { it.corsRules().applyValue { it.orElseGet(null) } }
}

@JvmName("get_T")
operator fun <T> Output<List<T>>.get(i: Int): Output<T> {
    return this.applyValue { x -> x.get(i) }
}

@JvmName("get_T_nullable")
operator fun <T> Output<List<T>?>.get(i: Int): Output<T?> {
    return this.applyValue { x -> x?.getOrNull(i) }
}

fun Output<BucketCorsRule?>.allowedMethods(): Output<List<String>> {
    return this.applyValue { x -> x?.allowedMethods() }
}


object PulumiJavaKotlinInterop {

    fun getTargetClassForFromJavaToKotlin(fromClass: Class<*>): Class<*> {
        val parts = fromClass.name.replace("com.pulumi", "com.pulumi.kotlin").replace(".outputs", "")

        return Class.forName(parts)
    }

    fun getTargetClassForFromKotlinToJava(fromClass: Class<*>): Class<*> {
        val replaced = fromClass.name.replace("com.pulumi.kotlin", "com.pulumi")

        val className = if(replaced.endsWith("Args")) {
            replaced.split(".").dropLast(1) + "inputs" + replaced.split(".").last()
        } else {
            replaced.split(".").dropLast(1) + "outputs" + replaced.split(".").last()
        }

        return Class.forName(className.joinToString("."))
    }

    fun <T> toKotlin(from: Any?): T {
        return pulumiArgsFromJavaToKotlin(emptyMap(), from) as T
    }

    fun <T> toJava(from: Any?): T {
        return pulumiArgsFromKotlinToJava(emptyMap(), from) as T
    }
    fun pulumiArgsFromJavaToKotlin(fromToRegistry: Map<Class<*>, Class<*>>, from: Any?): Any? {
        if(from == null) return null

        when(from) {
            is Number, is String, is Char, is Boolean -> return from

            is Map<*, *> -> return from.map { (k, v) ->
                pulumiArgsFromJavaToKotlin(fromToRegistry, k) to pulumiArgsFromJavaToKotlin(fromToRegistry, v)
            }.toMap()

            is Array<*> -> return from.map { v ->
                pulumiArgsFromJavaToKotlin(fromToRegistry, v)
            }.toTypedArray()

            is List<*> -> return from.map { v ->
                pulumiArgsFromJavaToKotlin(fromToRegistry, v)
            }

            is Optional<*> -> return from.map { pulumiArgsFromJavaToKotlin(fromToRegistry, it) }.orElse(null)
        }

        val javaGetterMethods = from::class.java.methods.filter { it.parameterCount == 0 }

        val nameToMethod = javaGetterMethods.associateBy { it.name }

        val to = fromToRegistry[from.javaClass] ?: try { getTargetClassForFromJavaToKotlin(from.javaClass) } catch(e: Exception) {
            throw Error("could not find for ${from.javaClass.name}", e)
        }

        println("DEBUG:")

        println("from: ${from.javaClass} to: ${to}")

        val toConstructor = to.kotlin.constructors.first()
        val constructorArgsByKParameter = toConstructor.parameters.associate {
            val result = nameToMethod[it.name!!]!!.invoke(from)
            val goodResult = pulumiArgsFromJavaToKotlin(fromToRegistry, result)

            println("${it.name}: ${it.type} is going to be set to ${goodResult?.javaClass}")

            it to goodResult
        }

        return toConstructor.callBy(constructorArgsByKParameter)
    }

    fun pulumiArgsFromKotlinToJava(fromToRegistry: Map<Class<*>, Class<*>>, from: Any?): Any? {

        if(from == null) return null

        when(from) {
            is Number, String, Char, Boolean -> return from
        }
        if(from::class.java.isPrimitive || from::class.java == String::class.java) return from

        if(Map::class.java.isInstance(from)) {
            return (from as Map<*, *>).map { (k, v) ->
                pulumiArgsFromKotlinToJava(fromToRegistry, k) to pulumiArgsFromKotlinToJava(fromToRegistry, v)
            }.toMap()
        }

        if(Array::class.java.isInstance(from)) {
            return (from as Array<*>).map { v ->
                pulumiArgsFromKotlinToJava(fromToRegistry, v)
            }.toTypedArray()
        }

        if(List::class.java.isInstance(from)) {
            return (from as List<*>).map { v ->
                pulumiArgsFromKotlinToJava(fromToRegistry, v)
            }
        }

        val kotlinProperties = from::class.members.filterIsInstance<KProperty<*>>()

        val to = fromToRegistry[from.javaClass] ?: try { getTargetClassForFromKotlinToJava(from.javaClass) } catch(e: Exception) {
            throw Error("could not find for ${from.javaClass.name}", e)
        }

        val toBuilder = to.getMethod("builder").invoke(null)
        val builderMethods = toBuilder.javaClass.methods
            .filter { it.returnType.name.endsWith("Builder") }

        val propertyNameToValue = kotlinProperties.associate { it.name to it }
        builderMethods
            .filterNot { it.parameterTypes.any { type -> type.name.contains("Output") } || it.isVarArgs }
            .forEach { builderMethod ->
                propertyNameToValue.get(builderMethod.name) ?. let {
                    val getterValue = it.getter.call(from)
                    val valueToSet = pulumiArgsFromKotlinToJava(fromToRegistry, getterValue)
                    builderMethod.invoke(toBuilder, valueToSet)
                }
            }

        val result = toBuilder.javaClass.getMethod("build").invoke(toBuilder)

        return result
    }
}

* */