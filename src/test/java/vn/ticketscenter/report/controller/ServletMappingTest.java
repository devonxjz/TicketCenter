package vn.ticketscenter.report.controller;

import jakarta.servlet.annotation.WebServlet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServletMappingTest {
    @Test void fullApplicationHasNoDuplicateServletUrlMappings() throws Exception {
        Path root=Path.of("target/classes");
        var mappings=new HashMap<String,String>();
        try(var files=Files.walk(root.resolve("vn/ticketscenter"))) {
            for(Path file:files.filter(p->p.toString().endsWith(".class")).toList()) {
                String name=root.relativize(file).toString().replace('\\','.').replace('/','.').replaceAll("\\.class$","");
                var annotation=Class.forName(name,false,getClass().getClassLoader()).getAnnotation(WebServlet.class);
                if(annotation==null)continue;
                for(String pattern:annotation.urlPatterns()) assertNull(mappings.putIfAbsent(pattern,name),"Duplicate servlet mapping: "+pattern+" for "+name);
                for(String pattern:annotation.value()) assertNull(mappings.putIfAbsent(pattern,name),"Duplicate servlet mapping: "+pattern+" for "+name);
            }
        }
        assertFalse(mappings.isEmpty(),"The real application annotations must be inspected");
    }
}
