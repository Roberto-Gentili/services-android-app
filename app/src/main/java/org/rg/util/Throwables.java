package org.rg.util;

@SuppressWarnings("unchecked")
public class Throwables {

	public static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

}
