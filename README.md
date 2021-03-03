# appcan-gradle-plugin

用于Android AppCanEngine自动化出包。

## 使用示例代码

1. Gradle依赖配置

```groovy
buildscript {
    repositories {
        maven {
            url 'https://raw.githubusercontent.com/android-plugin/mvn-repo/master/'
        }
    }
    dependencies {
        classpath 'org.appcan.gradle.plugins:appcan-gradle-plugin:2.2.4'
    }
}
```

2. gradle.properties可选参数配置示例

```properties
### 是否开启debug日志，默认false，留空或者移除即可
appcan.engine.package.debug=false
### 出包时是否需要禁用混淆功能（如混淆出现错误，或者不需要混淆功能，可以启用为true，否则默认false。）
appcan.engine.package.disable_proguard=false
### 出包时是否增加后缀，默认情况下不需要，留空或者移除即可
appcan.engine.package.version.suffix=beta
```

## changelog

- 2.5.0

1. 适配Gradle6.5和AndroidGradleBuildTools4.1.2

- 2.4.0

1. 增加在gradle.properties中配置appcan.engine.package.disable_proguard，来控制引擎出包的混淆代码的开关。


- 2.3.1

1. 适配Gradle5.4.1和AndroidGradleBuildTools3.5.0
2. 该版本可能无法向前兼容低版本gradle（因为API有变动），如有旧版本Gradle需要，请使用2.2.x版本。
3. 增加在gradle.properties中配置appcan.engine.package.debug和appcan.engine.package.version.suffix

- 2.2.4 

记录build号的key增加日期的区分（每天从01开始）

- 2.2.3 

修复mapping文件没有区分内核的问题

- 2.2.0 

修复开启InstantRun时无法Run到手机中调试Engine的问题

- 2.1.2 

支持携带mapping文件；支持3.4引擎出包。

- 2.0.0 

支持Gradle4.1和AndroidGradleBuildTools3.0.1

- 1.0.0 

末尾版本号自增长功能

- 0.9.9 

旧版稳定版本
