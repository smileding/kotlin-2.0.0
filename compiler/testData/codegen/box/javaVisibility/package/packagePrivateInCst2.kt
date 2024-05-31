// TARGET_BACKEND: JVM
// IGNORE_BACKEND_K1: ANY
// LANGUAGE: +CheckVisibilityOfTypesInCommonSuperTypeCalculation
// ^ Was always broken in K1
// ISSUE: KT-68401
// FILE: other/AClass.java
package other;
public class AClass extends PrivateSuper {}

// FILE: other/BClass.java
package other;
public class BClass extends PrivateSuper {}

// FILE: other/PrivateSuper.java
package other;
class PrivateSuper extends PublicSuper {}

// FILE: other/PublicSuper.java
package other;
public class PublicSuper {}

// FILE: box.kt
package foo

import other.AClass
import other.BClass
import other.PublicSuper

var temp: PublicSuper? = null

fun <T> select(vararg t: T): T = t[0]

fun box(): String {
    temp = select(AClass(), BClass())
    temp.toString()
    return "OK"
}
