* 本项目基于kotlin 2.0.0，在mac x64系统上进行kotlin native鸿蒙化

1、环境配置
  * ohos 3.2 release版本
  * 安装jdk 1.7 1.8 11 17
  * 本地配置yarn，需下载nodejs

2、执行编译命令
  * ./gradlew publish   该命令是因为后续发布鸿蒙化的kn需要用到一些包，这些包对应的源码做了适配修改，需发布至本地
  * 然后在kotlin源码根目录创建local.properties，加上代码 kotlin.native.enabled=true
  * 修改gradle.properties，将bootstrap.local=true的注释关闭
  * 修改 kotlin/kotlin-native/konan/konan.properties中的llvm路径为自己的实际路径
    以macos_x64为例，修改 targetToolchain.macos_x64-ohos_arm64 = 实际ohos-sdk路径/darwin/native/llvm
                        targetSysRoot.ohos_arm64 = 实际ohos-sdk路径/darwin/native/sysroot
  * 修改 kotlin/kotlin-native/platformLibs/build.gradle.kts中的路径为自己实际路径
      compilerOptions.add("-I/实际ohos-sdk路径/darwin/native/sysroot/usr/include/aarch64-linux-ohos")
  * 最后执行发布
     ./gradlew :kotlin-native:dist :kotlin-native:platformLibs:ohos_arm64Install
    编译成功的产物位于 kotlin-native/dist
