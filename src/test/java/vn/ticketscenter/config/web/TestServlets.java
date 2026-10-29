package vn.ticketscenter.config.web;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import vn.ticketscenter.config.identity.IdentityServices;

import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Initialises a servlet the way the container does, with a servlet context that already holds the given
 * fakes. Servlets keep no instance variables and look their services up in the context, so this is how a
 * test hands them a fake: each fake is stored under its class name and the name of every interface it
 * implements (the key every {@code XxxRuntime.resolve} reads), and identity fakes are also wrapped in an {@link IdentityServices}.
 */
public final class TestServlets {

    private TestServlets() {}

    public static <S extends HttpServlet> S init(S servlet, Object... fakes) {
        return init(servlet, context(fakes));
    }

    public static <S extends HttpServlet> S init(S servlet, ServletContext context) {
        ServletConfig config =
                (ServletConfig)
                        Proxy.newProxyInstance(
                                ServletConfig.class.getClassLoader(),
                                new Class<?>[] {ServletConfig.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getServletContext" -> context;
                                            case "getServletName" -> servlet.getClass().getSimpleName();
                                            case "getInitParameterNames" -> Collections.emptyEnumeration();
                                            default -> null;
                                        });
        try {
            servlet.init(config);
        } catch (ServletException failure) {
            throw new IllegalStateException(failure);
        }
        return servlet;
    }

    /** A context whose attributes start as the given fakes. */
    public static ServletContext context(Object... fakes) {
        Map<String, Object> attributes = new ConcurrentHashMap<>();
        ServletContext context =
                (ServletContext)
                        Proxy.newProxyInstance(
                                ServletContext.class.getClassLoader(),
                                new Class<?>[] {ServletContext.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getAttribute" -> attributes.get((String) args[0]);
                                            case "setAttribute" -> {
                                                if (args[1] == null) attributes.remove((String) args[0]);
                                                else attributes.put((String) args[0], args[1]);
                                                yield null;
                                            }
                                            case "removeAttribute" -> attributes.remove((String) args[0]);
                                            case "getAttributeNames" ->
                                                    Collections.enumeration(attributes.keySet());
                                            case "getInitParameterNames" -> Collections.emptyEnumeration();
                                            case "getContextPath" -> "";
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            case "equals" -> proxy == args[0];
                                            case "toString" -> "TestServlets.context";
                                            default -> null;
                                        });
        store(context, fakes);
        return context;
    }

    /**
     * Puts the fakes into an existing context, e.g. from {@code init()} of a servlet that an embedded
     * container initialises itself.
     */
    public static void store(ServletContext context, Object... fakes) {
        List<Object> identity = new ArrayList<>();
        for (Object fake : fakes) {
            if (fake == null) {
                continue;
            }
            context.setAttribute(fake.getClass().getName(), fake);
            for (Class<?> type : interfaces(fake.getClass())) {
                context.setAttribute(type.getName(), fake);
            }
            identity.add(fake);
        }
        IdentityServices services = identityServices(identity);
        if (services != null) {
            context.setAttribute(IdentityServices.ATTRIBUTE, services);
        }
    }

    private static Set<Class<?>> interfaces(Class<?> type) {
        Set<Class<?>> found = new LinkedHashSet<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> declared : current.getInterfaces()) {
                found.add(declared);
                found.addAll(interfaces(declared));
            }
        }
        return found;
    }

    /** The fakes placed in the identity record by component type, or null when none is an identity service. */
    private static IdentityServices identityServices(List<Object> fakes) {
        RecordComponent[] components = IdentityServices.class.getRecordComponents();
        Object[] values = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        boolean any = false;
        for (int index = 0; index < components.length; index++) {
            types[index] = components[index].getType();
            for (Object fake : fakes) {
                if (types[index].isInstance(fake)) {
                    values[index] = fake;
                    any = true;
                }
            }
        }
        if (!any) {
            return null;
        }
        try {
            return IdentityServices.class.getDeclaredConstructor(types).newInstance(values);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
