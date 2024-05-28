// LANGUAGE: -ErrorAboutDataClassCopyVisibilityChange, -DataClassCopyRespectsConstructorVisibility
data class Data <!DATA_CLASS_COPY_VISIBILITY_WILL_BE_CHANGED_WARNING!>private<!> constructor(val x: Int) {
    fun member() {
        copy()
        this.copy()
        ::copy
        this::copy
        Data::copy
    }

    companion object {
        fun of(): Data {
            return Data(1).copy()
        }
    }
}

fun topLevel(data: Data) {
    data.<!DATA_CLASS_INVISIBLE_COPY_USAGE_WARNING!>copy<!>()
    data::<!DATA_CLASS_INVISIBLE_COPY_USAGE_WARNING!>copy<!>
    Data::<!DATA_CLASS_INVISIBLE_COPY_USAGE_WARNING!>copy<!>
}

fun Data.topLevelExtension() {
    <!DATA_CLASS_INVISIBLE_COPY_USAGE_WARNING!>copy<!>()
    ::<!DATA_CLASS_INVISIBLE_COPY_USAGE_WARNING!>copy<!>
    Data::<!DATA_CLASS_INVISIBLE_COPY_USAGE_WARNING!>copy<!>
}

fun local() {
    data class Local <!DATA_CLASS_COPY_VISIBILITY_WILL_BE_CHANGED_WARNING!>private<!> constructor(val x: Int)

    fun Local.foo() {
        <!DATA_CLASS_INVISIBLE_COPY_USAGE_WARNING!>copy<!>()
        ::<!DATA_CLASS_INVISIBLE_COPY_USAGE_WARNING!>copy<!>
        Local::<!DATA_CLASS_INVISIBLE_COPY_USAGE_WARNING!>copy<!>
    }
}

data class GenericData<T> <!DATA_CLASS_COPY_VISIBILITY_WILL_BE_CHANGED_WARNING!>private<!> constructor(val value: T) {
    fun member() {
        copy()
        this.copy()
        ::copy
        this::copy
        GenericData<Int>::copy
        GenericData<*>::copy
        GenericData<in Int>::copy
        GenericData<out Int>::copy
    }
}

fun topLevel(data: GenericData<Int>) {
    data.<!DATA_CLASS_INVISIBLE_COPY_USAGE_WARNING!>copy<!>()
    data::<!DATA_CLASS_INVISIBLE_COPY_USAGE_WARNING!>copy<!>
    GenericData<Int>::<!DATA_CLASS_INVISIBLE_COPY_USAGE_WARNING!>copy<!>
    GenericData<*>::<!DATA_CLASS_INVISIBLE_COPY_USAGE_WARNING!>copy<!>
    GenericData<in Int>::<!DATA_CLASS_INVISIBLE_COPY_USAGE_WARNING!>copy<!>
    GenericData<out Int>::<!DATA_CLASS_INVISIBLE_COPY_USAGE_WARNING!>copy<!>
}
