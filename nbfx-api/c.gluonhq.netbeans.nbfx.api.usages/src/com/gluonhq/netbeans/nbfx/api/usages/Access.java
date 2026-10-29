package com.gluonhq.netbeans.nbfx.api.usages;

/** How a usage of a variable (field, local, parameter) touches it, as NetBeans' read/write filters classify it. */
public enum Access {
    READ,
    WRITE,
    READ_WRITE
}
