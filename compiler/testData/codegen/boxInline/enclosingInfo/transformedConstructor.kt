// FILE: 1.kt

package test

interface Z {
    fun a() : String
}

inline fun test(crossinline z: () -> String) =
        object : Z {

            val p = z()

            override fun a() = p
        }

// FILE: 2.kt

import test.*

fun box(): String {
    /*This captured parameter would be added to object constructor*/
    val captured = "OK";
    var z: Any = "fail"
    val res = test {

        z = {
            captured
        }
        (z as Function0<String>)()
    }


    val enclosingConstructor = z.javaClass.enclosingConstructor
    if (enclosingConstructor?.name != "TransformedConstructor_1Kt\$box$\$inlined\$test$1") return "fail 1: ${enclosingConstructor?.name}"

    val enclosingClass = z.javaClass.enclosingClass
    if (enclosingClass?.name != "TransformedConstructor_1Kt\$box$\$inlined\$test$1") return "fail 2: ${enclosingClass?.name}"

    return res.a()
}