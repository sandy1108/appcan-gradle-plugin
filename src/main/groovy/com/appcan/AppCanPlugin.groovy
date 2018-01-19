package com.appcan

import com.android.build.gradle.BasePlugin
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.builder.model.SourceProvider
import com.android.utils.FileUtils
import net.koiosmedia.gradle.sevenzip.SevenZip
import org.codehaus.groovy.runtime.ResourceGroovyMethods
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import proguard.gradle.ProGuardTask

import java.util.regex.Matcher
import java.util.regex.Pattern

public class AppCanPlugin implements Plugin<Project> {


    static final String PLUGIN_NAME = "appcan"
    static final VERSION_PROPERTIES_FILE_NAME = 'local-appcanengine-build-versions.properties'
    Project mProject;
    AppCanPluginExtension mExtension;
    BasePlugin androidPlugin
    List<Task> flavorsJarTask=new ArrayList<Task>()
    List<Task> flavorsProguardTask=new ArrayList<Task>()
    List<String> flavors=new ArrayList<String>()
    static final String BUILD_APPCAN_DIR ="build/appcan"
    public static String version=""
    public static String buildVersion="01"
    static boolean isVersionUpdated = false //用于标记在本次编译过程中，版本号是否已经+1

    @Override
    public void apply(Project project) {
        this.mProject=project;
        this.mExtension=project.extensions.create(PLUGIN_NAME,AppCanPluginExtension)
        project.afterEvaluate {
            version=getEngineVersion(project)
            buildVersion=getEngineLocalBuildVersionCode(version)
            androidPlugin=getAndroidBasePlugin(project)
            def variantManager=getVariantManager(androidPlugin)
            processVariantData(variantManager.variantDataList,androidPlugin)

            createIncreaseEngineLocalBuildVersionCodeTask()
            variantManager.getProductFlavors().keySet().each { flavor ->
                createFlavorsJarTask(project,androidPlugin,flavor)
                createFlavorsProguardTask(project,flavor)
                createCopyBaseProjectTask(project,flavor)
                createCopyEngineJarTask(project,flavor)
                createWebkitCorePalmZipTask(project,flavor)
                createBuildEngineZipTask(project,flavor)
                createFlavorsCopyAarTask(flavor)
                createFlavorsBuildAarTask(flavor)
            }
            createJarTask(project)
            createProguardJarTask(project)
            createBuildEngineTask()
            createBuildAarTask(project)
        }

    }


    /**
     * 生成所有的aar
     */
    private void createBuildAarTask(Project project){
        def task=project.tasks.create("buildAar")
        flavors.each { flavor ->
            task.dependsOn(project.tasks.findByName("build${flavor.capitalize()}Aar"))
        }
    }

    /**
     * 生成所有的引擎
     */
    private void createBuildEngineTask(){
        def task=mProject.tasks.create("buildEngine")
        //增长版本号。preventTask-->flavorTask-->task-->continueTask-->doTask
        Task preventTask = mProject.tasks.findByName("preventIncreaseEngineLocalBuildVersionCode")
        task.dependsOn(preventTask)
        flavors.each { flavor->
            Task flavorTask = mProject.tasks.findByName("build${flavor.capitalize()}Engine")
            task.dependsOn(flavorTask)
            flavorTask.mustRunAfter(preventTask)
        }
        //任务执行完成的末尾，先放开版本增长的锁，然后提升版本记录，以备下次出包使用
        Task continueTask = mProject.tasks.findByName("continueIncreaseEngineLocalBuildVersionCode")
        Task doTask = mProject.tasks.findByName("doIncreaseEngineLocalBuildVersionCode")
        task.finalizedBy(continueTask, doTask)
        doTask.mustRunAfter(continueTask)
    }

    /**
     * 拷贝基础工程
     */
    private static void createCopyBaseProjectTask(Project project, String name){
        def task=project.tasks.create("copy${name.capitalize()}Project",Copy)
        task.from("../en_baseEngineProject")
        task.into("$BUILD_APPCAN_DIR/$name/en_baseEngineProject")
        task.dependsOn(project.tasks.findByName("build${name.capitalize()}Jar"))
    }

    /**
     * 拷贝引擎jar
     */
    private static void createCopyEngineJarTask(Project project, String name){
        def task=project.tasks.create("copy${name.capitalize()}EngineJar",Copy)
        task.from("build/outputs/jar/AppCanEngine-${name}-${version}.jar","src/${name}/libs/")

        task.into("$BUILD_APPCAN_DIR/$name/en_baseEngineProject/WebkitCorePalm/libs")
        task.dependsOn(project.tasks.findByName("copy${name.capitalize()}Project"))
    }

    /**
     * 压缩WebkitCorePalm工程
     */
    private void createWebkitCorePalmZipTask(Project project, String name){
        def task=project.tasks.create("build${name.capitalize()}EngineTemp",SevenZip)
        task.from("$BUILD_APPCAN_DIR/${name}/en_baseEngineProject/WebkitCorePalm")
        task.destinationDir=project.file("$BUILD_APPCAN_DIR/$name/en_baseEngineProject")
        task.archiveName=getPackageName(name)
        task.dependsOn(project.tasks.findByName("copy${name.capitalize()}EngineJar"))

    }

    /**
     * 生成引擎包
     */
    private void createBuildEngineZipTask(Project project, String name){
        def task=project.tasks.create("build${name.capitalize()}Engine",Zip)
        task.from("$BUILD_APPCAN_DIR/$name/en_baseEngineProject/${getPackageName(name)}",
                "$BUILD_APPCAN_DIR/$name/en_baseEngineProject/androidEngine.xml")
        task.into("")
        task.exclude("$BUILD_APPCAN_DIR/$name/en_baseEngineProject/WebkitCorePalm")
        task.destinationDir=project.file("build/outputs/engine")
        task.baseName=getPackageName(name)
        task.metadataCharset="UTF-8"
        task.dependsOn(project.tasks.findByName("build${name.capitalize()}EngineTemp"))
        task.doFirst{
             setXmlContent(new File(project.getProjectDir(),
                    "$BUILD_APPCAN_DIR/${name}/en_baseEngineProject/androidEngine.xml"),name)
        }
        task.finalizedBy(mProject.tasks.findByName("doIncreaseEngineLocalBuildVersionCode"))
    }

    /**
     * 获取package name
     */
    private String getPackageName(String flavor){
        def date = new Date().format("yyMMdd")
        def versionTemp=version
        def buildVersionTemp = buildVersion
        def enginePackageName = "android_Engine_${versionTemp}_${date}_${buildVersionTemp}_${flavor}"
        println("getEnginePackageName: ${enginePackageName}")
        return enginePackageName
    }

    /**
     * 获取xml文件里面的version
     */
    private static String getEngineZipVersion(){
        def date = new Date().format("yyMMdd")
        def versionTemp=version.substring(0,version.lastIndexOf("."))
        def buildVersionTemp = buildVersion;
        def engineZipVersion = "sdksuit_${versionTemp}_${date}_${buildVersionTemp}"
        println("getEngineZipVersion: ${engineZipVersion}")
        return engineZipVersion
    }

    /**
     * 获取展示在网页的提示信息
     * @param flavor
     * @return
     */
    private static String  getKernelString(String flavor){
        if ("x5".equals(flavor)){
            return "腾讯X5内核"
        }else if ("system".equals(flavor)){
            return "系统内核"
        }else if ("crosswalk".equals(flavor)){
            return "Crosswalk内核"
        }
        return flavor+"内核"
    }

    private void setXmlContent(File xmlFile,String flavor){
        def content=xmlFile.getText('UTF-8')
                .replace("\$version\$",getEngineZipVersion())
                .replace("\$package\$",getPackageName(flavor))
                .replace("\$kernel\$",getKernelString(flavor))
//        xmlFile.write(content)
        ResourceGroovyMethods.write(xmlFile,content,'UTF-8')
    }

    /**
     * 获取引擎版本号
     * @return
     */
    private String getEngineVersion(Project project){
        def versionFilePath = "src/main/java/org/zywx/wbpalmstar/base/BConstant.java"
        def version=""
        Pattern p=Pattern.compile("ENGINE_VERSION=\"(.*?)\"")
        Matcher m=p.matcher(new File(project.getProjectDir(),versionFilePath).getText('UTF-8'))
        if (m.find()){
            version=m.group(1)
        }
        println("AppCanEngine version is $version")
        return version
    }


    /**
     * 生成所有的flavor未混淆引擎jar
     * @param project
     */
    private void createJarTask(Project project){
        def jarTask=project.tasks.create("buildJarTemp")
        for (Task task:flavorsJarTask){
            jarTask.dependsOn(task)
        }
     }

    /**
     * 生成所有的flavor混淆过的jar
     * @param project
     */
    private void createProguardJarTask(Project project){
        def proguardTask=project.tasks.create("buildJar")
        for (Task task:flavorsProguardTask){
            proguardTask.dependsOn(task)
        }
    }

    /**
     * 对每个flavor创建Task生成不混淆的jar
     **/
    private void createFlavorsJarTask(Project project, BasePlugin androidPlugin, def name){
        def jarBaseName="AppCanEngine-${name}-un-proguard"
        def applicationId=androidPlugin.extension.defaultConfig.applicationId;
        def jarEngineTask = project.tasks.create("build${name.capitalize()}JarTemp",Jar)
        jarEngineTask.setBaseName(jarBaseName)
        jarEngineTask.description="build $name Engine jar"
        jarEngineTask.destinationDir=project.file("build/outputs/jar")
        jarEngineTask.from("build/intermediates/classes/$name/release/")
        jarEngineTask.into("")
        jarEngineTask.exclude('**/R.class')
        jarEngineTask.exclude('**/R\$*.class')
        jarEngineTask.exclude('**/BuildConfig.class')
        if (applicationId!=null) {
            jarEngineTask.exclude(applicationId.replace('.', '/'))
        }
        jarEngineTask.dependsOn(project.tasks.findByName("compile${name.capitalize()}ReleaseSources"))
        flavorsJarTask.add(jarEngineTask)
    }

    /**
     * 对每个flavor创建Task生成混淆的jar
     **/
    private void createFlavorsProguardTask(Project project, def name){
        def jarTaskName="build${name.capitalize()}JarTemp"
        def taskName="build${name.capitalize()}Jar"
        def proguardTask=project.tasks.create(taskName,ProGuardTask)
        proguardTask.dependsOn(project.tasks.findByName(jarTaskName))

        def androidSDKDir = androidPlugin.extension.sdkDirectory

        def androidJarDir = androidSDKDir.toString() + '/platforms/' + androidPlugin.extension.getCompileSdkVersion() +
                '/android.jar'

        proguardTask.injars("build/outputs/jar/AppCanEngine-${name}-un-proguard.jar")
        proguardTask.outjars("build/outputs/jar/AppCanEngine-${name}-${version}.jar")
        proguardTask.libraryjars(androidJarDir)
        proguardTask.libraryjars("libs")
        proguardTask.libraryjars("src/${name}/libs")
        proguardTask.configuration('proguard.pro')
        flavors.add(name)
        flavorsProguardTask.add(proguardTask)
    }

    private void processVariantData(
            List<BaseVariantData<? extends BaseVariantData>> variantDataList, BasePlugin androidPlugin) {

        variantDataList.each { variantData ->
            def variantDataName = variantData.name

            def javaTask = getJavaTask(variantData)
            if (javaTask == null) {
                project.logger.info("javaTask is missing for $variantDataName, so Groovy files won't be compiled for it")
                return
            }
            def providers = variantData.variantConfiguration.sortedSourceProviders
            providers.each { SourceProvider provider ->

            }

        }
    }

    private static SourceTask getJavaTask(BaseVariantData baseVariantData) {
        if (baseVariantData.metaClass.getMetaProperty('javaCompileTask')) {
            return baseVariantData.javaCompileTask
        } else if (baseVariantData.metaClass.getMetaProperty('javaCompilerTask')) {
            return baseVariantData.javaCompilerTask
        }
        return null
    }

    private static BasePlugin getAndroidBasePlugin(Project project) {
        def plugin = project.plugins.findPlugin('android') ?:
                project.plugins.findPlugin('android-library')

        return plugin as BasePlugin
    }


    private static VariantManager getVariantManager(BasePlugin plugin) {
        return plugin.variantManager
    }

    /**
     * 解压aar内容，并删除widget，替换混淆过的Jar
     * @param flavor
     * @return
     */
    def createFlavorsCopyAarTask(String flavor) {
        def copyAarTaskName="build${flavor.capitalize()}AarTemp"
        def jarTaskName="build${flavor.capitalize()}Jar"
        def assembleTask="assemble${flavor.capitalize()}Release"
        def copyAarTask=mProject.tasks.create(copyAarTaskName,Copy)
        def tempFile=mProject.file("build/outputs/aar/temp/${flavor}")
        if (tempFile.exists()){
            FileUtils.deleteDirectoryContents(tempFile)
        }
         def aarFile=mProject.file("build/outputs/aar/Engine-${flavor}-release.aar")
        copyAarTask.dependsOn(mProject.tasks.findByName(jarTaskName))
        copyAarTask.dependsOn(mProject.tasks.findByName(assembleTask))
        copyAarTask.from(mProject.zipTree(aarFile))
        copyAarTask.into tempFile
        copyAarTask.doLast {
            println("clean widget ...")
            FileUtils.deleteDirectoryContents(project.file("build/outputs/aar/temp/${flavor}/assets/widget"))
            print("process Manifest ...")
            processManifest(tempFile)
            println("replace classes.jar ...")
            FileUtils.delete(new File(tempFile,"classes.jar"))
            FileUtils.copyFileToDirectory(mProject.file("build/outputs/jar/AppCanEngine-${flavor}-${version}.jar"),
                    mProject.file(tempFile))
            FileUtils.renameTo(new File(tempFile,"AppCanEngine-${flavor}-${version}.jar"),
                    new File(tempFile,"classes.jar"))
        }
    }

    def processManifest(File tempFile){
        def mainfestFile=new File(tempFile,"AndroidManifest.xml");
        def content=mainfestFile.getText('UTF-8')
                .replace("android:label=\"@string/app_name\"","")
        ResourceGroovyMethods.write(mainfestFile,content,'UTF-8')
    }

    /**
     *  重新生成aar
     **/
    def createFlavorsBuildAarTask(String flavor) {
        def aarTaskName="build${flavor.capitalize()}Aar"
        def copyAarTaskName="build${flavor.capitalize()}AarTemp"
        def aarTask=mProject.tasks.create(aarTaskName,Zip)
        def tempFile=mProject.file("build/outputs/aar/temp/${flavor}")
        aarTask.dependsOn(mProject.tasks.findByName(copyAarTaskName))
        aarTask.from(tempFile)
        aarTask.into("")
        aarTask.include('**/*')
        aarTask.destinationDir=mProject.file('build/outputs/aar/')
        aarTask.extension="aar"
        aarTask.baseName="Engine-${flavor}-release-${version}"
        aarTask.doLast {
            FileUtils.delete(mProject.file("build/outputs/aar/${mProject.name}-${flavor}-release.aar"))
            if (tempFile.exists()){
                FileUtils.deleteDirectoryContents(tempFile)
            }
        }
    }

    /**
     *  创建增长本地配置文件记录的版本号的任务
     **/
    def createIncreaseEngineLocalBuildVersionCodeTask(){
        def versionTaskName = "doIncreaseEngineLocalBuildVersionCode"
        def versionTask = mProject.tasks.create(versionTaskName)
        versionTask.doLast {
            increaseEngineLocalBuildVersionCode(version)
        }
        mProject.tasks.create("preventIncreaseEngineLocalBuildVersionCode").doLast {
            isVersionUpdated = true
            println("preventIncreaseEngineLocalBuildVersionCode")
        }
        mProject.tasks.create("continueIncreaseEngineLocalBuildVersionCode").doLast {
            isVersionUpdated = false
            println("continueIncreaseEngineLocalBuildVersionCode")
        }
    }

    def static getEngineLocalBuildVersionCode(String engineVersion) {
        def propertiesFileName = VERSION_PROPERTIES_FILE_NAME
        def versionPropertiesFile = new File(propertiesFileName)
        if (!versionPropertiesFile.exists()){
            println("Local BuildVersion Record file is not found，create it：${propertiesFileName}")
            versionPropertiesFile.createNewFile();
        }
        if (versionPropertiesFile.canRead()) {
            Properties versionProps = new Properties()
            versionPropertiesFile.withInputStream {
                fileStream -> versionProps.load(fileStream)
            }
            //获取当前配置文件中的build版本号，如果不存在默认为1
            def versionCode = 1;
            def buildVersionCodeStr = versionProps[engineVersion]
            if (buildVersionCodeStr != null) {
                versionCode = buildVersionCodeStr.toInteger()
            }else{
                //不存在本版本的出包build号，写入当前初始值
                versionProps[engineVersion] = (versionCode).toString()
                versionProps.store(versionPropertiesFile.newWriter(), "Output AppCanEngine Increasement Local BuildVersion Record. \nIt is an AUTO-GENERATE file in AppCanEngine's compiling. Please Do NOT modify it manually.")
            }
            if (versionCode < 10){
                buildVersionCodeStr = "0${versionCode}"
            }else{
                buildVersionCodeStr = versionCode;
            }
            println("AppCanEngine current buildVersion is ${buildVersionCodeStr}")
            return buildVersionCodeStr
        } else {
            throw GradleException("Cannot find or create ${propertiesFileName}!")
        }
    }

    /**
     *  增长本地配置文件记录的版本号
     **/
    def static increaseEngineLocalBuildVersionCode(String engineVersion){
        //判断是否为buildEngine出包（即批量生成所有内核），是则无需再增长版本号，因为已经增长过了
        if (isVersionUpdated){
            println("increaseEngineLocalBuildVersionCode is prevented.")
            return
        }
        def propertiesFileName = VERSION_PROPERTIES_FILE_NAME
        def versionPropertiesFile = new File(propertiesFileName)
        if (!versionPropertiesFile.exists()){
            println("Local BuildVersion Record file is not found，create it：${propertiesFileName}")
            versionPropertiesFile.createNewFile();
        }
        Properties versionProps = new Properties()
        versionPropertiesFile.withInputStream {
            fileStream -> versionProps.load(fileStream)
        }
        //获取当前配置文件中的build版本号，如果不存在默认为0，最后基于此版本号+1并写入配置文件
        def versionCode = 0;
        def buildVersionCodeStr = versionProps[engineVersion]
        if (buildVersionCodeStr != null) {
            versionCode = buildVersionCodeStr.toInteger()
        }
        versionProps[engineVersion] = (++versionCode).toString()
        versionProps.store(versionPropertiesFile.newWriter(), "Output AppCanEngine Increasement Local BuildVersion Record. \nIt is an AUTO-GENERATE file in AppCanEngine's compiling. Please Do NOT modify it manually.")
        if (versionCode < 10){
            buildVersionCodeStr = "0${versionCode}"
        }else{
            buildVersionCodeStr = versionCode;
        }
        buildVersion = buildVersionCodeStr
        println("AppCanEngine buildVersion is increased to ${buildVersionCodeStr}")
    }

}
