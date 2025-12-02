package com.hh.ecom.common.lock;

@FunctionalInterface
public interface LockableResource {
    String getLockKey();
}
