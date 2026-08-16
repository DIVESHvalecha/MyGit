package org.divesh.core;

public record DiffLine(Type type, String content) {
    public enum Type { ADD, REMOVE, EQUAL }
}