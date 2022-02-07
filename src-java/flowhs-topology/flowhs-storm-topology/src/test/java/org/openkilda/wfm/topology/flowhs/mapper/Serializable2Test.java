package org.openkilda.wfm.topology.flowhs.mapper;


import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.utils.SourceRoot;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
public class Serializable2Test {


    public class MethodVisitor extends VoidVisitorAdapter
    {
        public void visit(MethodDeclaration n, Object arg)
        {
            // extract method information here.
            // put in to hashmap
            log.info(n.toString());
        }
    }

    @Test
    public void runTest() throws IOException {
        Path path = Paths.get("/home/nm/p/kilda/open-kilda/src-java/flowhs-topology/flowhs-storm-topology/src/main/java");
        SourceRoot sourceRoot = new SourceRoot(path);
        // ParserConfiguration parserConfiguration = n;
        // sourceRoot.setParserConfiguration(parserConfiguration);
        List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParse("");
        int count = 0;
        for (ParseResult<CompilationUnit> r: parseResults) {
            log.info(r.toString());
//            MethodVisitor visitor = new MethodVisitor();
//            visitor.visit(r.getResult().get(), null);


            List<ObjectCreationExpr> objectCallExpr = r.getResult().get().findAll(ObjectCreationExpr.class);
            for (ObjectCreationExpr expr : objectCallExpr) {
                expr.getTypeAsString();
                if (expr.getTypeAsString().equals("Values")) {
                    //System.out.println("1");
                    count++;
                }
                //
//                Expression arg = expr.getArgument(0);
//                // test if it's a NameExpr
//                ResolvedValueDeclaration vd = arg.asNameExpr().resolve();
//                if (vd.isField()) {
//                    FieldDeclaration vde = ((JavaParserFieldDeclaration) vd).getWrappedNode();
//                    System.out.println(vde.getVariable(0).getInitializer().get());
//                }
            }
        }
        log.info("result {}", count);
    }
}
