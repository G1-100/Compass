package edu.usc.compass.common;

/** Body for state-changing actions that only need the version the user loaded, e.g. POST /archive. */
public record VersionRequest(int version) {
}
