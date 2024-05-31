// TARGET_BACKEND: JVM
// LANGUAGE: +CheckVisibilityOfTypesInCommonSuperTypeCalculation
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

fun box(): String {
    temp = if ("true" == "false") AClass() else BClass()
    temp.toString()
    return "OK"
}
