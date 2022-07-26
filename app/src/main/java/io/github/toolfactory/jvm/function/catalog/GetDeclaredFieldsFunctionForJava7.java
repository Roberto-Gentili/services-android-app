package io.github.toolfactory.jvm.function.catalog;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Map;

import io.github.toolfactory.jvm.util.ObjectProvider;

public class GetDeclaredFieldsFunctionForJava7 extends GetDeclaredFieldsFunction.Abst {

    public GetDeclaredFieldsFunctionForJava7(Map<Object, Object> context) throws Throwable {
        super(context);
        ObjectProvider functionProvider = ObjectProvider.get(context);
        ConsulterSupplyFunction getConsulterFunction =
                functionProvider.getOrBuildObject(ConsulterSupplyFunction.class, context);
        MethodHandles.Lookup consulter = getConsulterFunction.apply(Class.class);
        Class<?> cls = Class.class;
        /*java.lang.reflect.Method method = Class.class.getDeclaredMethod("getDeclaredFields", Class.class, boolean.class);
        methodHandle = consulter.unreflect(method);*/
        methodHandle = consulter.findStatic(
                Class.class,
                "getDeclaredFields",
                MethodType.methodType(Field[].class, Class.class, boolean.class)
        );
    }

    @Override
    public Field[] apply(Class<?> cls) throws Throwable {
        return (Field[])methodHandle.invokeWithArguments(cls, false);
    }
}
