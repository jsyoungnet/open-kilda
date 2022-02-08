package org.openkilda.wfm.topology.flowhs.mapper;


import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedLambdaConstraintType;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.SourceRoot;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.openkilda.messaging.BaseMessage;
import org.reflections.Reflections;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.fail;

@Slf4j
public class Serializable2Test {

    @Test
    public void runTest() throws IOException, ClassNotFoundException {

        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver(false));
//        combinedTypeSolver.add(new JavaParserTypeSolver(new File("/home/nm/p/kilda/open-kilda/src-java/flowhs-topology/flowhs-storm-topology/src/main/java")));
//        combinedTypeSolver.add(new JavaParserTypeSolver(new File("/home/nm/p/kilda/open-kilda/src-java/floodlight-service/floodlight-api/src/main/java")));
//        combinedTypeSolver.add(new JavaParserTypeSolver(new File("/home/nm/p/kilda/open-kilda/src-java/base-topology/base-messaging/src/main/java")));
//        combinedTypeSolver.add(new JavaParserTypeSolver(new File("/home/nm/p/kilda/open-kilda/src-java/blue-green/src/main/java")));
//        combinedTypeSolver.add(new JavaParserTypeSolver(new File("/home/nm/p/kilda/open-kilda/src-java/flowhs-topology/flowhs-messaging/src/main/java")));
//        combinedTypeSolver.add(new JavaParserTypeSolver(new File("/home/nm/p/kilda/open-kilda/src-java/stats-topology/stats-messaging/src/main/java")));
//        combinedTypeSolver.add(new JavaParserTypeSolver(new File("/home/nm/p/kilda/open-kilda/src-java/base-topology/base-storm-topology/src/main/java")));
//        combinedTypeSolver.add(new JarTypeSolver(new File("/home/nm/.gradle/caches/modules-2/files-2.1/org.apache.storm/storm-core/1.2.1/78f02ababb889e6fcf684e74b2e355276b32d27c/storm-core-1.2.1.jar")));

        JavaParserFacade javaParserFacade = JavaParserFacade.get(combinedTypeSolver);


        Path path = Paths.get("/home/nm/p/kilda/open-kilda/src-java/flowhs-topology/flowhs-storm-topology/src/main/java");
        SourceRoot sourceRoot = new SourceRoot(path);
        ReflectionTypeSolver reflectionTypeSolver = new ReflectionTypeSolver(false);
        ParserConfiguration parserConfiguration = new ParserConfiguration();
        JavaSymbolSolver javaSymbolSolver = new JavaSymbolSolver(reflectionTypeSolver);
        parserConfiguration.setSymbolResolver(javaSymbolSolver);
        sourceRoot.setParserConfiguration(parserConfiguration);
        List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParse("");

        // TODO: START HERE --> JavaParserFacade

        Set<String> allTypes = new HashSet<>();
        int count = 0;
        for (ParseResult<CompilationUnit> r : parseResults) {
            log.info("{}", r.getResult().get().getStorage().get().getPath());
            List<ObjectCreationExpr> objectCallExpr = r.getResult().get().findAll(ObjectCreationExpr.class);
            for (ObjectCreationExpr expr : objectCallExpr) {
                if (expr.getTypeAsString().equals("Values")) {
                    count++;
                    log.info("{} {}", expr.getRange().get().toString(), expr.toString());
                    for (Expression arg : expr.getArguments()) {
                        if (arg.isNameExpr()) {
                            NameExpr expr1 = arg.asNameExpr();
                            SymbolReference<? extends ResolvedValueDeclaration> solve = javaParserFacade.solve(expr1);
                            int i = 0;
                            if (solve.isSolved()) {
                                ResolvedValueDeclaration correspondingDeclaration = solve.getCorrespondingDeclaration();
                                log.info("arg {} {}", correspondingDeclaration.getName(), correspondingDeclaration.getType().erasure().describe());

                                if (correspondingDeclaration.getType().isReferenceType()) {
                                    ResolvedReferenceType resolvedReferenceType = correspondingDeclaration.getType().asReferenceType();
                                    allTypes.add(correspondingDeclaration.getType().erasure().describe());
                                } else if (correspondingDeclaration.getType().isConstraint()) {
                                    ResolvedLambdaConstraintType resolvedLambdaConstraintType = correspondingDeclaration.getType().asConstraintType();
                                    allTypes.add(resolvedLambdaConstraintType.getBound().describe());
                                } else {
                                    fail("can't get type of NameExpr");
                                }

                            } else {
                                fail("can't solve type of NameExpr");
                            }
                        } else if (arg.isMethodCallExpr()) {
                            MethodCallExpr expr1 = arg.asMethodCallExpr();
                            SymbolReference<ResolvedMethodDeclaration> solve = javaParserFacade.solve(expr1);
                            if (solve.isSolved()) {
                                ResolvedMethodDeclaration correspondingDeclaration = solve.getCorrespondingDeclaration();
                                log.info("arg {} {}", correspondingDeclaration.getName(), correspondingDeclaration.getReturnType().erasure().describe());
                                allTypes.add(correspondingDeclaration.getReturnType().erasure().describe());
                            } else {
                                log.info("Arg Not resolved {}", expr1.getName());
                                fail("can't solve type of MethodCallExpr");
                            }
                        } else if (arg.isObjectCreationExpr()) {
                            ObjectCreationExpr expr1 = arg.asObjectCreationExpr();
                            SymbolReference<ResolvedConstructorDeclaration> solve = javaParserFacade.solve(expr1);
                            if (solve.isSolved()) {
                                ResolvedConstructorDeclaration correspondingDeclaration = solve.getCorrespondingDeclaration();
                                log.info("arg {} {}", correspondingDeclaration.getName(), correspondingDeclaration.declaringType().asReferenceType().getQualifiedName());
                                allTypes.add(correspondingDeclaration.declaringType().asReferenceType().getQualifiedName());
                            } else {
                                fail("can't solve type of ObjectCreationExpr");
                            }
                        } else {
                            fail("Unknown expression");
                        }
                    }
                }
            }
        }
        log.info("result {}", count);
        for (String c: allTypes) {
            log.info(c);
        }

        log.info("start analyze");
        for (String c: allTypes) {
            log.info("type: {}", c);
            findNotSerializable(c);
        }
    }


    public void findNotSerializable(String className) throws ClassNotFoundException {

        Reflections reflections = new Reflections("org.openkilda");

        Set<Class<?>> s1 = new HashSet<>();
        Map<Class<?>, String> desc = new HashMap<>();
        Stack<Class<?>> stack = new Stack<>();
        recursiveFind(reflections, s1, desc, stack, Class.forName(className));
        for (Class<?> subType : s1) {
            if (Serializable.class.isAssignableFrom(subType)) {
                log.info(subType.getName());
            }
        }
        log.info("====================================================================================================");
        for (Class<?> subType : s1) {
            if (!Serializable.class.isAssignableFrom(subType)) {
                log.warn(subType.getName());
                log.warn(desc.get(subType));
            }
        }
    }

    private <T> void recursiveFind(Reflections reflections,
                                   Set<Class<?>> searchList,
                                   Map<Class<?>, String> desc,
                                   Stack<Class<?>> stack,
                                   final Class<T> type) {


        stack.push(type);
        Set<Class<? extends T>> subTypes = reflections.getSubTypesOf(type);
        Field[] declaredFields = type.getDeclaredFields();
        Set<Class<?>> fields = new HashSet<>();
        for (Field f : declaredFields) {
            if (Modifier.isStatic(f.getModifiers())
                    || Modifier.isTransient(f.getModifiers())
                    || f.getType().isPrimitive())
                continue;
            fields.add(f.getType());
        }

        desc.put(type, stack.toString());
        searchList.add(type);

        if (type.getName().contains("java.util.Set")) {
            return;
        }


        else {
            for (Class<?> field : fields) {
                if (!searchList.contains(field)) {
                    recursiveFind(reflections, searchList, desc, stack, field);
                }
            }

            for (Class<?> subType : subTypes) {
                if (!searchList.contains(subType)) {
                    recursiveFind(reflections, searchList, desc, stack, subType);
                }
            }
        }
        stack.pop();
    }
}
