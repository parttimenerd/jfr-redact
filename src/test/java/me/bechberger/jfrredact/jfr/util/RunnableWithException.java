package me.bechberger.jfrredact.jfr.util;

@FunctionalInterface
public interface RunnableWithException {
    void run() throws Exception;
}