package io.github.toolfactory.jvm.function.catalog;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;

import io.github.toolfactory.jvm.util.ObjectProvider;

public class PrivateLookupInMethodHandleSupplierForJava7 extends PrivateLookupInMethodHandleSupplier.Abst {

    public PrivateLookupInMethodHandleSupplierForJava7(Map<Object, Object> context) throws NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup consulter = ObjectProvider.get(context).getOrBuildObject(ConsulterSupplier.class, context).get();
        methodHandle = consulter.unreflect(MethodHandles.Lookup.class.getDeclaredMethod("in", Class.class));
    }

}
