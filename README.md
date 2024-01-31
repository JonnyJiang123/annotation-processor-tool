模拟lombok实现自动生成get、set方法。基于java原生api实现字节码增强。

```java
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Data {
}
```
通过标注这个注解，自动在编译的时候生成对应的方法。除此之外还可以用于字节码增强相关的功能

![image](https://github.com/JonnyJiang123/annotation-processor-tool/assets/56102991/07230580-b939-405b-9f8b-c2d91d7b09c3)
实现这个功能核心在于实现AbstractProcessor实现字节码增强的功能
├── core
└── test
# core 模块
core模块为核心的字节码增强模块
## pom.xml
```xml
   <dependencies>
        <dependency>
            <groupId>com.sun</groupId>
            <artifactId>tools</artifactId>
            <version>1.8</version>
            <scope>system</scope>
            <systemPath>${java.home}/../lib/tools.jar</systemPath>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.8.1</version>
                <configuration>
                    <source>8</source>
                    <target>8</target>
                </configuration>
            </plugin>
        </plugins>
    </build>
```
需要引入java内置tools核心包，目标jdk8没有办法直接使用

## DataProcessor
```java
@SupportedAnnotationTypes("org.mj.apt.core.annotation.Data")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class DataProcessor extends AbstractProcessor {
    /**
     * 抽象语法树
     */
    private JavacTrees trees;
    /**
     * AST 组件创建器
     */
    private TreeMaker treeMaker;

    /**
     * 标识符
     */
    private Names names;
}
```
DataProcessor继承了AbstractProcessor同时标注了该Processor需要处理的注解（SupportedAnnotationTypes）以及jdk版本（SupportedAnnotationTypes）
在DataProcessor里核心使用的组件为：JavacTrees解析为抽象语法树、TreeMaker创建语法树相关的组件（变量、方法、代码块等等）、Names创建方法名、变量名等
核心逻辑在<b>process</b>方法
```java
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // 获取有@Data 注解的元素
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Data.class);
        // 遍历元素进行处理
        for (Element element : elements) {
            // 将元素解析成树
            JCTree tree = this.trees.getTree(element);
            // 进行树的转换
            tree.accept(new TreeTranslator(){
                @Override
                public void visitClassDef(JCTree.JCClassDecl tree) {
                    // 获取类里面的每一部分（因为是标注在类上）
                    for (JCTree def : tree.defs) {
                        // 判断是否是变量
                        if (def.getKind().equals(Tree.Kind.VARIABLE)){
                            JCTree.JCVariableDecl jcVariableDecl = (JCTree.JCVariableDecl)def;

                            messager.printMessage(Diagnostic.Kind.NOTE,String.format("fields: %s",((JCTree.JCVariableDecl) def).getName().toString()));
                            try{
                                // get
                                tree.defs = tree.defs.prepend(generateGetterMethod(jcVariableDecl));
                                // set
                                tree.defs = tree.defs.prepend(generateSetterMethod(jcVariableDecl));
                            }catch (Exception e){
                                messager.printMessage(Diagnostic.Kind.ERROR,e.toString());
                            }
                            super.visitClassDef(tree);
                        }
                    }
                }

                @Override
                public void visitMethodDef(JCTree.JCMethodDecl methodTree) {
                    messager.printMessage(Diagnostic.Kind.NOTE,methodTree.toString());
                    super.visitMethodDef(methodTree);
                }

                @Override
                public void visitVarDef(JCTree.JCVariableDecl tree) {
                    super.visitVarDef(tree);
                }
            });
        }
        return true;
    }
```
以及generateSetterMethod

```java
    private JCTree generateSetterMethod(JCTree.JCVariableDecl jcVariableDecl) {
        // 设置访问修饰符
        JCTree.JCModifiers modifiers = treeMaker.Modifiers(Flags.PUBLIC);
        // 添加方法名称
        Name variableDeclName = jcVariableDecl.getName();
        Name methodName = handleMethodName(variableDeclName,"set");

        // 设置方法体
        ListBuffer<JCTree.JCStatement> jcStatements = new ListBuffer<>();
        jcStatements.append(treeMaker.Exec(treeMaker
                .Assign(
                        treeMaker.Select(treeMaker.Ident(getNameFromString("this")),variableDeclName),
                        treeMaker.Ident(variableDeclName)
                )));
        // 定义方法体
        JCTree.JCBlock jcBlock = treeMaker.Block(0,jcStatements.toList());
        // 定义返回值类型
        JCTree.JCExpression returnType = treeMaker.Type(new Type.JCVoidType());
        // 泛型参数
        List<JCTree.JCTypeParameter> typeParameters = List.nil();

        // 定义参数
        JCTree.JCVariableDecl variableDecl = treeMaker.
                VarDef(
                        treeMaker.Modifiers(Flags.PARAMETER,List.<JCTree.JCAnnotation>nil()),
                        jcVariableDecl.getName(),
                        jcVariableDecl.vartype,
                        null);
        List<JCTree.JCVariableDecl> parameters = List.of(variableDecl);

        // 声明异常
        List<JCTree.JCExpression> throwsClauses = List.nil();
        return treeMaker
                .MethodDef(modifiers,methodName,returnType,typeParameters,parameters,throwsClauses,jcBlock,null);
    }
```
## 打包
`mvn clean install `

# test 模块
test模块为需要是要改功能的模块
## 引入核心apt处理模块
```xml
   <dependencies>
        <dependency>
            <groupId>org.mj.apt.core</groupId>
            <artifactId>core</artifactId>
            <version>1.0-SNAPSHOT</version>
            <optional>true</optional>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.8.1</version>
                <configuration>
                    <target>8</target>
                    <source>8</source>
                </configuration>
            </plugin>
        </plugins>
    </build>
```
## 配置SPI待maven加载对应的类
![image](https://github.com/JonnyJiang123/annotation-processor-tool/assets/56102991/3e341ff4-f9eb-4d26-9e7d-4c51efcc08d3)
```
org.mj.apt.core.proccessor.DataProcessor
```
## 使用对应的注解
```java
@Data
public class User {
    private String name;

}
```
## 查看编译后的
```java
public class User {
    private String name;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public User() {
    }
}

```
# 其他应用
通过注解触发字节码增强的功能。可以实现AOP做不到的事情。
