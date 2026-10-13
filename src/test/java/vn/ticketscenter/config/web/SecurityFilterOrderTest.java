package vn.ticketscenter.config.web;

import jakarta.servlet.annotation.WebFilter;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Annotation filters have no defined order, so the security chain must stay declared in web.xml. */
class SecurityFilterOrderTest {
    private static final List<String> CHAIN = List.of(
            "vn.ticketscenter.identity.security.DatabaseActorFilter",
            "vn.ticketscenter.config.web.RequestValidationFilter",
            "vn.ticketscenter.identity.security.SessionValidationFilter",
            "vn.ticketscenter.identity.security.CsrfFilter",
            "vn.ticketscenter.identity.security.RoleAuthorizationFilter");

    @Test
    void securityFiltersAreMappedInWebXmlInChainOrder() throws Exception {
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(Path.of("src/main/webapp/WEB-INF/web.xml").toFile());
        NodeList mappings = document.getElementsByTagName("filter-mapping");
        List<String> order = new ArrayList<>();
        for (int index = 0; index < mappings.getLength(); index++) {
            order.add(((Element) mappings.item(index)).getElementsByTagName("filter-name").item(0).getTextContent().trim());
        }
        assertEquals(CHAIN, order);
    }

    @Test
    void descriptorNamesReplaceTheAnnotationMappings() throws Exception {
        for (String name : CHAIN) {
            WebFilter annotation = Class.forName(name).getAnnotation(WebFilter.class);
            // An unnamed @WebFilter is registered under its class name; a different name would map it twice.
            assertTrue(annotation == null || annotation.filterName().isEmpty(), name);
        }
    }
}
