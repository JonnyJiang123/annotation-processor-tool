package org.mj.apt.core.proccessor;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import org.mj.apt.core.annotation.Data;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * author: jun.jiang
 * Date: 2024/1/31
 * Time: 10:38
 * Description:
 */
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

    /**
     * 日志处理
     */
    private Messager messager;

    private Filer filer;

    public DataProcessor() {
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.trees = JavacTrees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.treeMaker = TreeMaker.instance(context);
        this.messager =  processingEnv.getMessager();
        this.names = Names.instance(context);
        this.filer = processingEnv.getFiler();
    }

    @Override
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

    private JCTree generateGetterMethod(JCTree.JCVariableDecl jcVariableDecl) {
        // 设置方法级别
        JCTree.JCModifiers modifiers = treeMaker.Modifiers(Flags.PUBLIC);
        // 添加方法名称
        Name methodName = handleMethodName(jcVariableDecl.getName(),"get");

        // 添加方法内容
        ListBuffer<JCTree.JCStatement> jcStatements = new ListBuffer<>();
        jcStatements.append(treeMaker.Return(treeMaker.Select(treeMaker.Ident(getNameFromString("this")), jcVariableDecl.getName())));
        JCTree.JCBlock jcBlock = treeMaker.Block(0, jcStatements.toList());

        // 添加返回值类型
        JCTree.JCExpression returnType = jcVariableDecl.vartype;

        // 参数类型
        List<JCTree.JCTypeParameter> typeParameters = List.nil();

        // 参数变量
        List<JCTree.JCVariableDecl> parameters = List.nil();

        // 声明异常
        List<JCTree.JCExpression> throwsClause = List.nil();

        // 构建方法
        return treeMaker.MethodDef(modifiers,methodName,returnType,typeParameters,parameters,throwsClause,jcBlock,null);
    }

    private Name getNameFromString(String name) {
        return names.fromString(name);
    }

    private Name handleMethodName(Name name, String prefix) {
        String upperCamelName = name.toString().substring(0,1).toUpperCase() + name.toString().substring(1);
        return names.fromString(prefix.concat(upperCamelName));
    }
}
