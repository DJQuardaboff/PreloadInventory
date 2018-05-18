package com.porterlee.preload;

public class Utils {
    public static class Holder <T> {
        private T value;

        public Holder(T initialValue) {
            value = initialValue;
        }

        public T get() {
            return value;
        }

        public void set(T object) {
            this.value = object;
        }
    }
}
