package org.rg.util;

@SuppressWarnings("unchecked")
public class Throwables {

    public static <T> T sneakyThrow(Throwable exc) {
        throwException(exc);
        return null;
    }

	public static <E extends Throwable> void throwException(Throwable exc) throws E {
        throw (E)exc;
    }

}
